package com.pydroidx.app

import android.app.Application
import android.util.AtomicFile
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
class ProjectFileWriterTest {
    @Test fun terminalBarrierObservesAllPreviouslyQueuedSaves() {
        val root = Files.createTempDirectory("py4u-terminal-barrier-").toFile()
        val file = File(root, "helper.py")
        val done = CountDownLatch(1)
        var observed = ""
        ProjectFileWriter.enqueue(file, "latest") { }
        ProjectFileWriter.afterQueuedWrites { observed = file.readText(); done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals("latest", observed)
    }

    @Test fun oversizedDraftReportsThatRecoveryCannotBeGuaranteed() {
        val root = Files.createTempDirectory("py4u-draft-limit-").toFile()
        val file = File(root, "main.py").apply { writeText("saved") }
        val failure = CountDownLatch(1)
        var message: String? = null
        ProjectFileWriter.journalDraft(root, file, "x".repeat(2_000_001)) {
            message = it; failure.countDown()
        }
        assertTrue(failure.await(5, TimeUnit.SECONDS))
        assertTrue(message?.contains("2 MB") == true)
        assertEquals(null, ProjectFileWriter.recoverDraft(root, file))
    }

    @Test fun draftJournalRejectsRecoverySymlinkOutsideProject() {
        val root = Files.createTempDirectory("py4u-draft-root-").toFile()
        val outside = Files.createTempDirectory("py4u-draft-outside-").toFile()
        Files.createSymbolicLink(File(root, ".recovery").toPath(), outside.toPath())
        val file = File(root, "main.py").apply { writeText("saved") }
        var message: String? = null
        ProjectFileWriter.journalDraft(root, file, "unsaved") { message = it }
        assertTrue(message?.contains("outside") == true)
        assertTrue(outside.listFiles().isNullOrEmpty())
    }

    @Test fun projectEditRejectsStaleFileBeforeWritingAnyTarget() {
        val root = Files.createTempDirectory("py4u-project-edit-").toFile()
        val first = File(root, "first.py").apply { writeText("first\n") }
        val second = File(root, "second.py").apply { writeText("changed\n") }
        val finished = CountDownLatch(1)
        var failed = false
        ProjectFileWriter.applyProjectEdits(root, listOf(
            ProjectEdit("first.py", "first\n", "FIRST\n"),
            ProjectEdit("second.py", "old\n", "SECOND\n"))) {
            failed = it.isFailure; finished.countDown()
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(failed)
        assertEquals("first\n", first.readText())
        assertEquals("changed\n", second.readText())
    }

    @Test fun projectEditCheckpointsEverySelectedFile() {
        val root = Files.createTempDirectory("py4u-project-edit-good-").toFile()
        val first = File(root, "first.py").apply { writeText("first\n") }
        val second = File(root, "second.py").apply { writeText("second\n") }
        val finished = CountDownLatch(1)
        var success = false
        ProjectFileWriter.applyProjectEdits(root, listOf(
            ProjectEdit("first.py", "first\n", "FIRST\n"),
            ProjectEdit("second.py", "second\n", "SECOND\n"))) {
            success = it.isSuccess; finished.countDown()
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(success)
        assertEquals("FIRST\n", first.readText())
        assertEquals("SECOND\n", second.readText())
        assertEquals("first\n", ProjectFileHistory.versions(first).first().readText())
    }

    @Test fun latestUnsavedDraftSurvivesAndSavedContentClearsOnlyMatchingDraft() {
        val root = Files.createTempDirectory("py4u-draft-").toFile()
        val file = File(root, "nested/main.py").apply { parentFile.mkdirs(); writeText("saved") }
        ProjectFileWriter.journalDraft(root, file, "first")
        ProjectFileWriter.journalDraft(root, file, "unsaved 🐍")
        val drained = CountDownLatch(1)
        ProjectFileWriter.enqueue(file, "saved") { drained.countDown() }
        assertTrue(drained.await(5, TimeUnit.SECONDS))
        assertEquals("unsaved 🐍", ProjectFileWriter.recoverDraft(root, file))
        ProjectFileWriter.clearSavedDraft(root, file, "saved")
        val next = CountDownLatch(1)
        ProjectFileWriter.enqueue(file, "unsaved 🐍") { next.countDown() }
        assertTrue(next.await(5, TimeUnit.SECONDS))
        ProjectFileWriter.clearSavedDraft(root, file, "unsaved 🐍")
        val barrier = CountDownLatch(1)
        ProjectFileWriter.enqueue(file, "unsaved 🐍") { barrier.countDown() }
        assertTrue(barrier.await(5, TimeUnit.SECONDS))
        assertEquals(null, ProjectFileWriter.recoverDraft(root, file))
    }

    @Test fun writesInSubmissionOrderAndPreservesUnicode() {
        val dir = Files.createTempDirectory("py4u-writer-").toFile()
        val file = File(dir, "main.py")
        val finished = CountDownLatch(3)
        val results = mutableListOf<Result<Unit>>()
        listOf("old", "second", "print('🐍')\n").forEach { content ->
            ProjectFileWriter.enqueue(file, content) { result ->
                synchronized(results) { results.add(result) }
                finished.countDown()
            }
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(results.all { it.isSuccess })
        assertEquals("print('🐍')\n", file.readText())
    }

    @Test fun renameWaitsForQueuedSave() {
        val dir = Files.createTempDirectory("py4u-rename-").toFile()
        val original = File(dir, "untitled_1.py")
        val renamed = File(dir, "calculator.py")
        val finished = CountDownLatch(2)
        var renamedSuccessfully = false
        ProjectFileWriter.enqueue(original, "print('saved')") { finished.countDown() }
        ProjectFileWriter.rename(original, renamed) { result ->
            renamedSuccessfully = result.isSuccess
            finished.countDown()
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(renamedSuccessfully)
        assertEquals("print('saved')", renamed.readText())
        assertTrue(!original.exists())
    }

    @Test fun failedRenameLeavesBothExistingFilesUntouched() {
        val dir = Files.createTempDirectory("py4u-rename-conflict-").toFile()
        val original = File(dir, "untitled_1.py").apply { writeText("original") }
        val target = File(dir, "script.py").apply { writeText("existing") }
        val finished = CountDownLatch(1)
        var failed = false
        ProjectFileWriter.rename(original, target) { result ->
            failed = result.isFailure
            finished.countDown()
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(failed)
        assertEquals("original", original.readText())
        assertEquals("existing", target.readText())
    }

    @Test fun duplicateCopiesLatestQueuedTextWithoutChangingOriginal() {
        val dir = Files.createTempDirectory("py4u-copy-").toFile()
        val source = File(dir, "main.py")
        val copy = File(dir, "main_copy.py")
        val finished = CountDownLatch(2)
        var copied = false
        ProjectFileWriter.enqueue(source, "print('🐍')") { finished.countDown() }
        ProjectFileWriter.duplicate(source, copy) { result ->
            copied = result.isSuccess
            finished.countDown()
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(copied)
        assertEquals("print('🐍')", source.readText())
        assertEquals(source.readText(), copy.readText())
    }

    @Test fun renameCarriesExistingHistory() {
        val dir = Files.createTempDirectory("py4u-rename-history-").toFile()
        val source = File(dir, "untitled_1.py").apply { writeText("old") }
        ProjectFileHistory.recoveryPoint(source, "older".toByteArray())
        val finished = CountDownLatch(1)
        var succeeded = false
        val target = File(dir, "calculator.py")
        ProjectFileWriter.rename(source, target) { result ->
            succeeded = result.isSuccess
            finished.countDown()
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(succeeded)
        assertEquals("older", ProjectFileHistory.versions(target).first().readText())
    }

    @Test fun moveCarriesHistoryAndPreservesTheFile() {
        val root = Files.createTempDirectory("py4u-move-").toFile()
        val source = File(root, "src/main.py").apply { parentFile.mkdirs(); writeText("current") }
        val target = File(root, "lib/main.py").apply { parentFile.mkdirs() }
        ProjectFileHistory.recoveryPoint(source, "previous".toByteArray())
        val finished = CountDownLatch(1)
        var succeeded = false
        ProjectFileWriter.move(source, target) { result -> succeeded = result.isSuccess; finished.countDown() }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(succeeded)
        assertTrue(!source.exists())
        assertEquals("current", target.readText())
        assertEquals("previous", ProjectFileHistory.versions(target).first().readText())
    }

    @Test fun deletedFileCanBeRestoredFromProjectRecovery() {
        val root = Files.createTempDirectory("py4u-delete-").toFile()
        val source = File(root, "nested/main.py").apply { parentFile.mkdirs(); writeText("recover me") }
        val deleted = CountDownLatch(1)
        var recovered: File? = null
        ProjectFileWriter.moveToRecovery(source, root) { result ->
            recovered = result.getOrNull()
            deleted.countDown()
        }
        assertTrue(deleted.await(5, TimeUnit.SECONDS))
        assertTrue(!source.exists())
        val recovery = recovered ?: throw AssertionError("No recovery copy was created")
        val restored = CountDownLatch(1)
        var succeeded = false
        ProjectFileWriter.move(recovery, source) { result -> succeeded = result.isSuccess; restored.countDown() }
        assertTrue(restored.await(5, TimeUnit.SECONDS))
        assertTrue(succeeded)
        assertEquals("recover me", source.readText())
    }

    @Test fun replacingFileKeepsRecoverablePreviousVersion() {
        val file = File(Files.createTempDirectory("py4u-history-").toFile(), "main.py")
        file.writeText("print('before')\n")
        val finished = CountDownLatch(1)
        ProjectFileWriter.enqueue(file, "print('after')\n") { finished.countDown() }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertEquals("print('after')\n", file.readText())
        assertEquals("print('before')\n", ProjectFileHistory.versions(file).first().readText())
    }

    @Test fun deliberateRestoreCanProtectCurrentUnsavedCode() {
        val file = File(Files.createTempDirectory("py4u-restore-").toFile(), "main.py")
        val finished = CountDownLatch(1)
        ProjectFileWriter.checkpoint(file, "unsaved 🐍") { finished.countDown() }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertEquals("unsaved 🐍", ProjectFileHistory.versions(file).first().readText())
    }

    @Test fun interruptedAtomicWriteKeepsPreviousCodeReadable() {
        val file = File(Files.createTempDirectory("py4u-interrupted-").toFile(), "main.py")
        file.writeText("print('safe')")
        val atomic = AtomicFile(file)
        atomic.startWrite().use { it.write("partial".toByteArray()) }
        assertEquals("print('safe')", atomic.openRead().bufferedReader().use { it.readText() })
    }
}
