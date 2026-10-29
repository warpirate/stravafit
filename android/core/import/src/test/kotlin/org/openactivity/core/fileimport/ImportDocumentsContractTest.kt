// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDocumentsContractTest {
    @Test
    fun `pickers never offer a wildcard or images`() {
        // Regression: "Import GPX route" let PNG and SVG exports be picked.
        for (types in listOf(ImportDocumentsContract.MIME_TYPES, ImportDocumentsContract.ROUTE_MIME_TYPES)) {
            assertFalse(types.any { it.contains('*') })
            assertFalse(types.any { it.startsWith("image/") })
        }
    }

    @Test
    fun `route picker offers gpx`() {
        assertTrue("application/gpx+xml" in ImportDocumentsContract.ROUTE_MIME_TYPES)
    }
}
