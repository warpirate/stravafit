// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit

import java.io.File
import java.io.InputStream
import kotlin.test.assertEquals

/** Access to the shared `testdata/files` fixtures and golden outputs. */
object Fixtures {
    private val root: File = File(
        requireNotNull(System.getProperty("testdata.dir")) { "testdata.dir system property not set" },
    )
    private val updateGoldens = System.getProperty("goldens.update").toBoolean()

    fun open(path: String): InputStream = File(root, path).inputStream().buffered()

    fun bytes(path: String): ByteArray = File(root, path).readBytes()

    /** Binary variant of [assertGolden]. */
    fun assertGoldenBytes(name: String, actual: ByteArray) {
        val file = File(root, "golden/$name")
        if (updateGoldens || !file.exists()) {
            file.parentFile.mkdirs()
            file.writeBytes(actual)
            if (!updateGoldens) error("Golden $name was missing and has been created; re-run the tests")
            return
        }
        kotlin.test.assertContentEquals(file.readBytes(), actual, "Golden mismatch: $name")
    }

    /** Compares [actual] with `golden/<name>`, or rewrites the golden when UPDATE_GOLDENS=true. */
    fun assertGolden(name: String, actual: String) {
        val file = File(root, "golden/$name")
        if (updateGoldens || !file.exists()) {
            file.parentFile.mkdirs()
            file.writeText(actual, Charsets.UTF_8)
            if (!updateGoldens) error("Golden $name was missing and has been created; re-run the tests")
            return
        }
        assertEquals(file.readText(Charsets.UTF_8).replace("\r\n", "\n"), actual, "Golden mismatch: $name")
    }
}
