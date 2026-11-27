package com.nblaisdell.home.ui.screens.shopping

import com.nblaisdell.home.data.repository.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchHistoryTest {

    private fun entry(name: String) = HistoryEntry(name, name, null, null, null, 0)
    private val history = listOf(entry("Oat milk"), entry("Milk"), entry("Milkshake mix"), entry("Bread"))

    @Test
    fun `prefix matches come before other matches`() {
        assertEquals(listOf("Milk", "Milkshake mix", "Oat milk"), matchHistory("mil", history).map { it.name })
    }

    @Test
    fun `an exact match is not suggested back`() {
        assertEquals(listOf("Milkshake mix", "Oat milk"), matchHistory("milk", history).map { it.name })
    }

    @Test
    fun `nothing until something is typed, and at most the limit`() {
        assertEquals(emptyList<HistoryEntry>(), matchHistory("  ", history))
        assertEquals(1, matchHistory("m", history, limit = 1).size)
    }
}
