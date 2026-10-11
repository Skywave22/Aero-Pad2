package com.bluepilot.remote.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entities — persistence backbone for the customization engine.
 *
 * Design decision: widget layouts, macro steps and skin color values are
 * stored as JSON strings (kotlinx-serialization). This gives us:
 *  - schema stability while Modules 5–7 iterate on widget/step models,
 *  - free import/export (the DB value IS the share format),
 *  - no Room migration churn for every widget property we add.
 * The JSON (de)serializers validate on read and fall back safely.
 */

/**
 * A layout profile: one user-designed screen (widgets + their positions,
 * sizes, styles). `layoutJson` holds a serialized LayoutSpec (Module 5).
 */
@Entity(tableName = "layout_profiles")
data class LayoutProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Serialized LayoutSpec — widgets, zones, positions, styles. */
    val layoutJson: String,
    /** True for built-in starter profiles (not deletable). */
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * A macro: named sequence of HID steps.
 * `stepsJson` holds a serialized List<MacroStep> (Module 7).
 */
@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Serialized List<MacroStep>: key taps, chords, text, delays. */
    val stepsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A skin: full visual preset (color scheme + widget style defaults).
 * `valuesJson` holds a serialized SkinSpec (Module 5).
 */
@Entity(tableName = "skins")
data class SkinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Serialized SkinSpec — colors, radii, fonts, opacities. */
    val valuesJson: String,
    val isBuiltIn: Boolean = false
)

/**
 * A custom gamepad profile (Section 2 builder).
 * `layoutJson` holds a serialized GamepadLayoutSpec.
 */
@Entity(tableName = "gamepad_profiles")
data class GamepadProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val layoutJson: String,
    val isBuiltIn: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
