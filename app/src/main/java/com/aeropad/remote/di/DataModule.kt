package com.aeropad.remote.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aeropad.remote.data.db.AeroPadDatabase
import com.aeropad.remote.data.db.GamepadProfileDao
import com.aeropad.remote.data.db.LayoutProfileDao
import com.aeropad.remote.data.db.MacroDao
import com.aeropad.remote.data.db.SkinDao
import com.aeropad.remote.data.settings.DataStoreSettings
import com.aeropad.remote.domain.SettingsStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt wiring for the data layer: DataStore settings + Room database. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsStore(impl: DataStoreSettings): SettingsStore
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidersModule {

    /**
     * v1 -> v2: adds the gamepad_profiles table (Section 2 builder).
     * A REAL migration — existing user layouts/macros/skins are preserved.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `gamepad_profiles` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `layoutJson` TEXT NOT NULL,
                    `isBuiltIn` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )"""
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AeroPadDatabase =
        Room.databaseBuilder(context, AeroPadDatabase::class.java, "aeropad.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideLayoutProfileDao(db: AeroPadDatabase): LayoutProfileDao = db.layoutProfileDao()
    @Provides fun provideMacroDao(db: AeroPadDatabase): MacroDao = db.macroDao()
    @Provides fun provideSkinDao(db: AeroPadDatabase): SkinDao = db.skinDao()
    @Provides fun provideGamepadProfileDao(db: AeroPadDatabase): GamepadProfileDao = db.gamepadProfileDao()
}
