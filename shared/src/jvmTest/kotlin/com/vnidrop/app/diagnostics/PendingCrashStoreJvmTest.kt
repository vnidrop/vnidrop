package com.vnidrop.app.diagnostics

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingCrashStoreJvmTest {
	@Test
	fun replacesReportsAndPrunesOldCorruptAndTemporaryFiles() {
		val root = Files.createTempDirectory("vnidrop-crash-store").toFile()
		try {
			val store = createPendingCrashStore(root.absolutePath)
			val older = report("10000000-0000-4000-8000-000000000001", 1, "older")
			val current = report("10000000-0000-4000-8000-000000000002", 2, "current")
			store.write(older)
			store.write(current)
			store.write(current.copy(exceptionMessage = "replaced"))

			val directory = File(root, "diagnostics/crashes")
			File(directory, "corrupt.crash").writeText("not a crash envelope")
			File(directory, ".orphan.tmp").writeText("partial")
			store.write(current.copy(id = "../../escape"))
			val escapedPath = File(directory, "../../escape.crash").canonicalFile
			Files.setLastModifiedTime(File(directory, "${older.id}.crash").toPath(), FileTime.fromMillis(2_000))
			Files.setLastModifiedTime(File(directory, "${current.id}.crash").toPath(), FileTime.fromMillis(1_000))

			assertEquals(
				listOf("replaced", "older"),
				store.list().map(CrashReport::exceptionMessage),
			)
			store.prune(olderThanTimestampMillis = 0, maxCount = 1)

			assertEquals(listOf("replaced"), store.list().map(CrashReport::exceptionMessage))
			assertFalse(File(directory, "corrupt.crash").exists())
			assertFalse(File(directory, ".orphan.tmp").exists())
			assertFalse(escapedPath.exists())
			assertTrue(directory.listFiles().orEmpty().all { it.parentFile == directory })
		} finally {
			root.deleteRecursively()
		}
	}

	private fun report(id: String, timestampMillis: Long, message: String) = CrashReport(
		id = id,
		timestampMillis = timestampMillis,
		installId = "install",
		appVersion = "1.0",
		platform = "Desktop",
		exceptionType = "TestError",
		exceptionMessage = message,
		stackTrace = "stack",
		breadcrumbs = emptyList(),
		diagnosticsEnabledAtCapture = true,
	)
}
