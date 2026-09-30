"""Bounded runtime installer for universal pure-Python wheels on Android.

Native Android wheels require a compatible build and are deliberately rejected.
"""

import email
import hashlib
import importlib
import importlib.metadata
import io
import json
import os
import pkgutil
import re
import shutil
import stat
import sys
import tempfile
import urllib.parse
import urllib.request
import zipfile

from packaging.requirements import Requirement
from packaging.tags import sys_tags
from packaging.utils import canonicalize_name, parse_wheel_filename
from packaging.version import Version

MAX_INDEX = 8_000_000
MAX_WHEEL = 30_000_000
MAX_EXPANDED = 100_000_000
MAX_FILES = 3_000
SUPPORTED_TAGS = {tag for tag in sys_tags() if tag.abi == "none" and tag.platform == "any"}


def activate(root):
    if not os.path.isdir(root):
        return
    paths = [os.path.join(root, name) for name in sorted(os.listdir(root))
             if not name.startswith(".") and os.path.isdir(os.path.join(root, name)) and
             any(child.endswith(".dist-info") for child in os.listdir(os.path.join(root, name)))]
    for path in paths:
        while path in sys.path:
            sys.path.remove(path)
    # Keep the active project first, then user-installed wheels ahead of bundled versions.
    first = bool(sys.path and os.path.realpath(sys.path[0]) == os.path.realpath(os.getcwd()))
    sys.path[1 if first else 0:1 if first else 0] = paths
    importlib.invalidate_caches()


def package_state(root):
    activate(root)
    private = set()
    if os.path.isdir(root):
        for name in os.listdir(root):
            path = os.path.join(root, name)
            if not name.startswith(".") and os.path.isdir(path):
                private.update(canonicalize_name(d.metadata.get("Name", d.name))
                               for d in importlib.metadata.distributions(path=[path]))
    seen = set()
    rows = []
    for distribution in importlib.metadata.distributions():
        name = distribution.metadata.get("Name", distribution.name)
        normalized = canonicalize_name(name)
        if normalized in seen:
            continue
        seen.add(normalized)
        rows.append((name, distribution.version, "runtime" if normalized in private else "bundled"))
    return sorted(rows, key=lambda row: row[0].lower())


def importable_module_state(root):
    """List top-level imports visible to this actual runtime, including installed wheels."""
    activate(root)
    return sorted(set(sys.builtin_module_names) | {module.name for module in pkgutil.iter_modules()})


def _download(url, maximum, opener, cancelled):
    if not url.startswith("https://") or urllib.parse.urlsplit(url).hostname not in (
            "pypi.org", "files.pythonhosted.org"):
        raise ValueError("Package download must come from PyPI")
    with opener(url, timeout=25) as response:
        final_url = response.geturl() if hasattr(response, "geturl") else url
        if (urllib.parse.urlsplit(final_url).scheme != "https" or
                urllib.parse.urlsplit(final_url).hostname not in ("pypi.org", "files.pythonhosted.org")):
            raise ValueError("Package download redirected outside PyPI")
        data = bytearray()
        while True:
            if cancelled():
                raise InterruptedError("Package installation stopped")
            chunk = response.read(min(64_000, maximum + 1 - len(data)))
            if not chunk:
                break
            data.extend(chunk)
            if len(data) > maximum:
                raise ValueError("Package download exceeds the size limit")
    return bytes(data)


def _candidate(requirement, opener, cancelled):
    project = canonicalize_name(requirement.name)
    if not re.fullmatch(r"[a-z0-9]+(?:[-][a-z0-9]+)*", project):
        raise ValueError("Invalid package name")
    url = "https://pypi.org/pypi/%s/json" % urllib.parse.quote(project)
    releases = json.loads(_download(url, MAX_INDEX, opener, cancelled))["releases"]
    versions = []
    for raw_version in releases:
        try:
            version = Version(raw_version)
        except ValueError:
            continue
        if version in requirement.specifier:
            versions.append((version, raw_version))
    versions.sort(key=lambda item: item[0], reverse=True)
    for version, raw_version in versions:
        if version.is_prerelease and not requirement.specifier.prereleases:
            continue
        for release in releases[raw_version]:
            if release.get("yanked") or release.get("packagetype") != "bdist_wheel":
                continue
            if release.get("size", MAX_WHEEL + 1) > MAX_WHEEL:
                continue
            try:
                name, wheel_version, _, tags = parse_wheel_filename(release["filename"])
            except ValueError:
                continue
            if canonicalize_name(name) != project or wheel_version != version or not tags & SUPPORTED_TAGS:
                continue
            requires = release.get("requires_python") or ""
            if requires and not Requirement("python" + requires).specifier.contains(".".join(map(str, sys.version_info[:3]))):
                continue
            return version, release
    raise ValueError("No compatible pure-Python wheel for %s on Python %s" %
                     (project, ".".join(map(str, sys.version_info[:2]))))


def _wheel_contents(data, expected_name, expected_version):
    result = []
    total = 0
    metadata = None
    wheel_is_pure = False
    metadata_paths = set()
    destinations = set()
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        entries = archive.infolist()
        if len(entries) > MAX_FILES:
            raise ValueError("Wheel contains too many files")
        for item in entries:
            path = item.filename
            parts = path.split("/")
            if (path.startswith("/") or "\\" in path or any(part in ("", ".", "..") for part in parts if part != parts[-1])
                    or any(part in (".", "..", "") for part in parts if not item.is_dir())):
                raise ValueError("Wheel contains an unsafe path")
            if stat.S_ISLNK(item.external_attr >> 16):
                raise ValueError("Wheel contains a symbolic link")
            if item.is_dir():
                continue
            if path.lower().endswith((".so", ".pyd", ".dll", ".dylib")):
                raise ValueError("Wheel contains unsupported native code")
            total += item.file_size
            if total > MAX_EXPANDED:
                raise ValueError("Wheel expands beyond the size limit")
            if ".data/" in path:
                prefix, rest = path.split(".data/", 1)
                if not prefix or not rest.startswith("purelib/"):
                    raise ValueError("Wheel needs unsupported installation paths")
                path = rest[len("purelib/"):]
            if path in destinations:
                raise ValueError("Wheel contains duplicate installation paths")
            destinations.add(path)
            if path.endswith(".dist-info/WHEEL"):
                if item.file_size > 100_000:
                    raise ValueError("Wheel metadata is too large")
                wheel_info = email.message_from_bytes(archive.read(item))
                wheel_is_pure = wheel_info.get("Root-Is-Purelib", "").lower() == "true"
                metadata_paths.add(path.split("/", 1)[0])
            if path.endswith(".dist-info/METADATA"):
                if item.file_size > 1_000_000:
                    raise ValueError("Wheel metadata is too large")
                metadata = email.message_from_bytes(archive.read(item))
                metadata_paths.add(path.split("/", 1)[0])
            result.append((item, path))
        if not wheel_is_pure:
            raise ValueError("Wheel is not pure Python")
        if len(metadata_paths) != 1:
            raise ValueError("Wheel metadata directory does not match the requested release")
        info_dir = next(iter(metadata_paths))
        try:
            info_name, info_version = info_dir.removesuffix(".dist-info").rsplit("-", 1)
            valid_info = (canonicalize_name(info_name) == expected_name and
                          Version(info_version) == expected_version)
        except (ValueError, TypeError):
            valid_info = False
        if not valid_info:
            raise ValueError("Wheel metadata directory does not match the requested release")
        if (metadata is None or canonicalize_name(metadata.get("Name", "")) != expected_name or
                Version(metadata.get("Version", "0")) != expected_version):
            raise ValueError("Wheel metadata does not match the requested package")
    return result, metadata


def install(requirements, root, output, opener=urllib.request.urlopen, cancelled=lambda: False):
    """Install requested names and dependencies, rejecting incompatible wheels."""
    os.makedirs(root, exist_ok=True)
    activate(root)
    visiting = set()
    constraints = {}
    planned = {}
    expanded_extras = {}
    order = []
    download_bytes = 0
    installed = []

    def resolve(raw, depth, marker_extra=""):
        nonlocal download_bytes
        if cancelled():
            raise InterruptedError("Package installation stopped")
        if depth > 24 or len(constraints) > 24:
            raise ValueError("Package dependency graph is too large")
        req = Requirement(raw)
        if req.url:
            raise ValueError("Direct package URLs are not supported")
        if req.marker and not req.marker.evaluate({"extra": marker_extra}):
            return
        name = canonicalize_name(req.name)
        extras = set(req.extras)
        def check_extras(metadata):
            offered = {canonicalize_name(item) for item in metadata.get_all("Provides-Extra", [])}
            unknown = extras - offered
            if unknown:
                raise ValueError("Unsupported extras for %s: %s" % (name, ", ".join(sorted(unknown))))
        constraints.setdefault(name, []).append(req)
        specifiers = [str(item.specifier) for item in constraints[name] if str(item.specifier)]
        combined = Requirement(name + (",".join(specifiers) if specifiers else ""))
        if name in visiting:
            raise ValueError("Circular dependency: " + name)
        if name in planned:
            if not combined.specifier.contains(planned[name][0]):
                raise ValueError("Conflicting dependency constraints for " + name)
            check_extras(planned[name][3])
            new_extras = extras - expanded_extras.get(name, set())
            if new_extras:
                visiting.add(name)
                expanded_extras.setdefault(name, set()).update(new_extras)
                for dependency in planned[name][3].get_all("Requires-Dist", []):
                    for extra in new_extras:
                        resolve(dependency, depth + 1, extra)
                visiting.remove(name)
                order.remove(name)
                order.append(name)
            return
        try:
            current = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            current = None
        if current and combined.specifier.contains(current):
            distribution = importlib.metadata.distribution(name)
            check_extras(distribution.metadata)
            new_extras = extras - expanded_extras.get(name, set())
            if new_extras:
                visiting.add(name)
                expanded_extras.setdefault(name, set()).update(new_extras)
                for dependency in distribution.requires or []:
                    for extra in new_extras:
                        resolve(dependency, depth + 1, extra)
                visiting.remove(name)
            return
        if current and not os.path.isdir(os.path.join(root, name)):
            raise ValueError("Bundled %s %s conflicts with %s" % (name, current, raw))
        visiting.add(name)
        version, release = _candidate(combined, opener, cancelled)
        output("Downloading %s %s…\n" % (name, version))
        data = _download(release["url"], MAX_WHEEL, opener, cancelled)
        download_bytes += len(data)
        if download_bytes > MAX_EXPANDED:
            raise ValueError("Package dependency downloads exceed the size limit")
        if hashlib.sha256(data).hexdigest() != release["digests"]["sha256"]:
            raise ValueError("Wheel checksum mismatch")
        contents, metadata = _wheel_contents(data, name, version)
        check_extras(metadata)
        expanded_extras[name] = extras
        for dependency in metadata.get_all("Requires-Dist", []):
            for extra in {""} | extras:
                resolve(dependency, depth + 1, extra)
        visiting.remove(name)
        planned[name] = (version, data, contents, metadata)
        order.append(name)

    for raw in requirements:
        resolve(raw, 0)
    stages = {}
    backups = {}
    committed = []
    expanded_bytes = 0
    try:
        for name in order:
            if cancelled():
                raise InterruptedError("Package installation stopped")
            version, data, contents, _ = planned[name]
            stage = tempfile.mkdtemp(prefix=".install-", dir=root)
            stages[name] = stage
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                for item, path in contents:
                    if cancelled():
                        raise InterruptedError("Package installation stopped")
                    expanded_bytes += item.file_size
                    if expanded_bytes > MAX_EXPANDED:
                        raise ValueError("Package dependency contents exceed the size limit")
                    destination = os.path.join(stage, path)
                    os.makedirs(os.path.dirname(destination), exist_ok=True)
                    with archive.open(item) as source, open(destination, "wb") as sink:
                        while True:
                            if cancelled():
                                raise InterruptedError("Package installation stopped")
                            chunk = source.read(64_000)
                            if not chunk:
                                break
                            sink.write(chunk)

        for name in order:
            if cancelled():
                raise InterruptedError("Package installation stopped")
            target = os.path.join(root, name)
            if os.path.exists(target):
                backup = tempfile.mkdtemp(prefix=".rollback-", dir=root)
                os.rmdir(backup)
                os.replace(target, backup)
                backups[name] = backup
            os.replace(stages[name], target)
            committed.append(name)
    except BaseException as failure:
        rollback_errors = []
        for name in reversed(order):
            target = os.path.join(root, name)
            backup = backups.get(name)
            if name in committed and os.path.exists(target):
                try:
                    shutil.rmtree(target)
                except OSError as error:
                    rollback_errors.append(error)
                    continue
            if backup and os.path.exists(backup):
                try:
                    os.replace(backup, target)
                except OSError as error:
                    rollback_errors.append(error)
        if rollback_errors:
            raise RuntimeError("Package rollback failed; previous versions remain in hidden .rollback folders") from failure
        raise
    finally:
        for stage in stages.values():
            if os.path.exists(stage):
                shutil.rmtree(stage, ignore_errors=True)

    for backup in backups.values():
        if os.path.exists(backup):
            shutil.rmtree(backup, ignore_errors=True)
    activate(root)
    for name in order:
        version = planned[name][0]
        installed.append("%s %s" % (name, version))
        output("Installed %s %s\n" % (name, version))

    return installed
