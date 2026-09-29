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
    for name in sorted(os.listdir(root)):
        path = os.path.join(root, name)
        if os.path.isdir(path) and not name.startswith(".") and path not in sys.path:
            sys.path.append(path)
    importlib.invalidate_caches()


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
    visited = set()
    installed = []

    def resolve(raw, depth):
        if cancelled():
            raise InterruptedError("Package installation stopped")
        if depth > 24 or len(visited) > 60:
            raise ValueError("Package dependency graph is too large")
        req = Requirement(raw)
        if req.url or req.extras:
            raise ValueError("URLs and package extras are not supported")
        if req.marker and not req.marker.evaluate():
            return
        name = canonicalize_name(req.name)
        try:
            current = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            current = None
        if current and req.specifier.contains(current):
            return
        if current and not os.path.isdir(os.path.join(root, name)):
            raise ValueError("Bundled %s %s conflicts with %s" % (name, current, raw))
        if name in visited:
            raise ValueError("Circular or conflicting dependency: " + name)
        visited.add(name)
        version, release = _candidate(req, opener, cancelled)
        output("Downloading %s %s…\n" % (name, version))
        data = _download(release["url"], MAX_WHEEL, opener, cancelled)
        if hashlib.sha256(data).hexdigest() != release["digests"]["sha256"]:
            raise ValueError("Wheel checksum mismatch")
        contents, metadata = _wheel_contents(data, name, version)
        for dependency in metadata.get_all("Requires-Dist", []):
            resolve(dependency, depth + 1)
        stage = tempfile.mkdtemp(prefix=".install-", dir=root)
        target = os.path.join(root, name)
        backup = target + ".previous"
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                for item, path in contents:
                    if cancelled():
                        raise InterruptedError("Package installation stopped")
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
            if os.path.exists(backup):
                shutil.rmtree(backup)
            if os.path.exists(target):
                os.replace(target, backup)
            try:
                os.replace(stage, target)
            except BaseException:
                if os.path.exists(backup):
                    os.replace(backup, target)
                raise
            if os.path.exists(backup):
                shutil.rmtree(backup)
        finally:
            if os.path.exists(stage):
                shutil.rmtree(stage)
        activate(root)
        installed.append("%s %s" % (name, version))
        output("Installed %s %s\n" % (name, version))

    for raw in requirements:
        resolve(raw, 0)
    return installed
