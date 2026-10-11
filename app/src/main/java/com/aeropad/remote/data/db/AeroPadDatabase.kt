package com.aeropad.remote.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * AeroPad Room database.
 *
 * Version 1 schema is intentionally future-proof: layouts/macros/skins carry
 * their payload as JSON, so Modules 5–7 iterate WITHOUT schema migrations.
 */
@Database(
    entities = [
        LayoutProfileEntity::class,
        MacroEntity::class,
        SkinEntity::class,
        GamepadProfileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AeroPadDatabase : RoomDatabase() {
    abstract fun layoutProfileDao(): LayoutProfileDao
    abstract fun macroDao(): MacroDao
    abstract fun skinDao(): SkinDao
    abstract fun gamepadProfileDao(): GamepadProfileDao
}
