package com.nblaisdell.home.ui.screens.home

import com.nblaisdell.home.ui.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class HomeViewModelTest {

    @Test
    fun `greeting follows the time of day`() {
        assertEquals("Good morning", greetingFor(LocalTime.of(7, 0)))
        assertEquals("Good afternoon", greetingFor(LocalTime.of(13, 30)))
        assertEquals("Good evening", greetingFor(LocalTime.of(21, 0)))
        assertEquals("Good evening", greetingFor(LocalTime.of(2, 0)))
    }

    @Test
    fun `shopping tile shows the item count`() {
        fun summary(count: Int) =
            dashboardTiles(count).single { it.destination == TopLevelDestination.Shopping }.summary

        assertEquals("Nothing on the list", summary(0))
        assertEquals("1 item on the list", summary(1))
        assertEquals("4 items on the list", summary(4))
    }
}
