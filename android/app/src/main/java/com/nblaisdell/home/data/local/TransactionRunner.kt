package com.nblaisdell.home.data.local

import androidx.room.withTransaction
import javax.inject.Inject

/**
 * Runs a block in one database transaction. An interface (rather than calling Room directly)
 * so sync logic can be unit-tested with an in-memory fake.
 */
interface TransactionRunner {
    suspend operator fun <T> invoke(block: suspend () -> T): T
}

class RoomTransactionRunner @Inject constructor(
    private val db: HomeDatabase,
) : TransactionRunner {
    override suspend fun <T> invoke(block: suspend () -> T): T = db.withTransaction { block() }
}
