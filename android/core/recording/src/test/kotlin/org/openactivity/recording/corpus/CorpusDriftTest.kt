// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.corpus

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The checked-in corpus must be exactly what TrackGen produces today. This catches hand edits of golden
 * values and generator drift: goldens can only change by changing the generator and regenerating.
 */
class CorpusDriftTest {
    @TestFactory
    fun `checked-in corpus matches the generator`(): List<DynamicTest> = CorpusFiles.render().map { (path, content) ->
        DynamicTest.dynamicTest(path) {
            val file = Replay.corpusRoot.resolve(path)
            assertTrue(file.isFile, "$path is missing; run ./gradlew :android:core:recording:generateGpsCorpus")
            val onDisk = file.readText().replace("\r\n", "\n")
            assertTrue(onDisk == content, "$path differs from TrackGen output; regenerate the corpus and review the diff")
        }
    }

    @TestFactory
    fun `generator is deterministic`(): List<DynamicTest> = Scenarios.all.map { sc ->
        DynamicTest.dynamicTest(sc.id) {
            assertEquals(SfRaw.write(sc, TrackGen.generate(sc).events), SfRaw.write(sc, TrackGen.generate(sc).events))
        }
    }
}
