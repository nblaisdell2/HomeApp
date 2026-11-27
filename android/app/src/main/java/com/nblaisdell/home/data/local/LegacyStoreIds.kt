package com.nblaisdell.home.data.local

import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Id for a store that used to be free text on an item (before HOME-66):
 * md5("home-store:" + lower(trim(name))) read as a UUID.
 *
 * The API's stores migration computes exactly the same value in SQL
 * (`md5('home-store:' || lower(btrim(store)))::uuid`), so the server and every device turn the
 * same old store name into the same store without talking to each other. Both sides pin the
 * id for "Costco" in a test; keep the two in step.
 */
object LegacyStoreIds {
    fun idFor(name: String): String {
        val key = "home-store:" + name.trim(' ').lowercase(Locale.ROOT)
        val hex = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return UUID.fromString(
            "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20)}",
        ).toString()
    }
}
