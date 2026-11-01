// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedFileStagerTest {
    private fun looks(bytes: ByteArray) = SharedFileStager.looksLikeActivityFile(bytes)

    @Test
    fun `gpx, tcx, fit and gzip pass, anything else is turned away`() {
        assertTrue(looks("﻿<?xml version=\"1.0\"?>\n<gpx version=\"1.1\" creator=\"x\">".toByteArray()))
        assertTrue(looks("<?xml version=\"1.0\"?><TrainingCenterDatabase xmlns=\"...\">".toByteArray()))
        val fit = ByteArray(14).also { "\u000E\u0010".toByteArray().copyInto(it); ".FIT".toByteArray().copyInto(it, 8) }
        assertTrue(looks(fit))
        assertTrue(looks(byteArrayOf(0x1F, 0x8B.toByte(), 8, 0)))

        assertFalse(looks("%PDF-1.7".toByteArray()))
        assertFalse(looks(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))) // JPEG
        assertFalse(looks("<?xml version=\"1.0\"?><kml>".toByteArray()))
    }

    @Test
    fun `file names keep their extension and lose path tricks`() {
        assertEquals("Morning_Run.gpx", SharedFileStager.safeName("Morning Run.gpx"))
        assertEquals(".._.._etc_passwd.fit", SharedFileStager.safeName("../../etc/passwd.fit"))
        assertEquals("file", SharedFileStager.safeName(""))
    }
}
