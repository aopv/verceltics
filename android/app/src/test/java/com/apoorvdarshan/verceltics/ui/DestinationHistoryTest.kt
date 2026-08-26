package com.apoorvdarshan.verceltics.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DestinationHistoryTest {
    @Test
    fun boundedHistoryKeepsOneRootAtItsBoundary() {
        var history = listOf("hosting")
        repeat(7) {
            history = appendDestinationHistory(history, "sites")
            history = appendDestinationHistory(history, "hosting")
            history = appendDestinationHistory(history, "registrars")
        }

        assertEquals("hosting", history.first())
        assertNotEquals(listOf("hosting", "hosting"), history.take(2))
        assertEquals(11, history.size)
    }
}
