package com.aeropad.remote.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * AeroPad Room database.
 *
 * Layouts/macros/gamepad profiles carry their payload as JSON, so feature
 * work iterates WITHOUT schema migrations. v3 dropped the legacy `skins`
 * table (theme gallery removed — the app is Light/Dark only now).
 */
@Database(
    entities = [
        LayoutProfileEntity::class,
        MacroEntity::class,
        GamepadProfileEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AeroPadDatabase : RoomDatabase() {
    abstract fun layoutProfileDao(): LayoutProfileDao
    abstract fun macroDao(): MacroDao
    abstract fun gamepadProfileDao(): GamepadProfileDao
}
