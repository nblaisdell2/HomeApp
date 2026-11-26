package com.nblaisdell.home.di

import android.content.Context
import androidx.room.Room
import com.nblaisdell.home.data.local.HomeDatabase
import com.nblaisdell.home.data.local.RoomTransactionRunner
import com.nblaisdell.home.data.local.ShoppingItemDao
import com.nblaisdell.home.data.local.SyncStateDao
import com.nblaisdell.home.data.local.TransactionRunner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HomeDatabase =
        Room.databaseBuilder(context, HomeDatabase::class.java, HomeDatabase.NAME).build()

    @Provides
    fun provideSyncStateDao(db: HomeDatabase): SyncStateDao = db.syncStateDao()

    @Provides
    fun provideShoppingItemDao(db: HomeDatabase): ShoppingItemDao = db.shoppingItemDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindingsModule {
    @Binds
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner
}
