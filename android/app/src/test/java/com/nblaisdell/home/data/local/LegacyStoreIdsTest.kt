package com.nblaisdell.home.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyStoreIdsTest {

    /** Same value the API pins in test/stores-migration.test.ts; the two must agree. */
    @Test
    fun `matches the id the server migration gives Costco`() {
        assertEquals("75f0a4c6-1f22-bad4-e2d2-a99e048314f3", LegacyStoreIds.idFor("Costco"))
    }

    @Test
    fun `ignores case and surrounding spaces`() {
        assertEquals(LegacyStoreIds.idFor("Costco"), LegacyStoreIds.idFor("  cOSTCO "))
    }
}
