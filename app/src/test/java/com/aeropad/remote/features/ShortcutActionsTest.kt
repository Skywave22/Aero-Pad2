package com.aeropad.remote.features

import com.aeropad.remote.domain.ShortcutActions
import org.junit.Assert.assertEquals
import org.junit.Test

/** V2 MATRIX 8 — launcher shortcut → route mapping contract. */
class ShortcutActionsTest {

    @Test
    fun `known actions map to their screens`() {
        assertEquals("full_keyboard", ShortcutActions.routeFor(ShortcutActions.OPEN_KEYBOARD))
        assertEquals("mouse", ShortcutActions.routeFor(ShortcutActions.OPEN_MOUSE))
        assertEquals("gamepad_builder", ShortcutActions.routeFor(ShortcutActions.OPEN_GAMEPAD))
        assertEquals("multimedia", ShortcutActions.routeFor(ShortcutActions.OPEN_MEDIA))
    }

    @Test
    fun `null unknown and hostile actions land on home`() {
        assertEquals(ShortcutActions.ROUTE_HOME, ShortcutActions.routeFor(null))
        assertEquals(ShortcutActions.ROUTE_HOME, ShortcutActions.routeFor("android.intent.action.MAIN"))
        assertEquals(ShortcutActions.ROUTE_HOME, ShortcutActions.routeFor(""))
        assertEquals(ShortcutActions.ROUTE_HOME, ShortcutActions.routeFor("com.aeropad.remote.OPEN_NUKE"))
    }

    @Test
    fun `mapped routes match the real nav graph constants`() {
        // Guard against route-string drift: these literals must stay in sync
        // with Routes.* (compile-time import would create a ui<->domain dep;
        // the test IS the sync check).
        val valid = setOf(
            com.aeropad.remote.ui.navigation.Routes.FULL_KEYBOARD,
            com.aeropad.remote.ui.navigation.Routes.MOUSE,
            com.aeropad.remote.ui.navigation.Routes.GAMEPAD_BUILDER,
            com.aeropad.remote.ui.navigation.Routes.MULTIMEDIA,
            com.aeropad.remote.ui.navigation.Routes.HOME
        )
        listOf(
            ShortcutActions.OPEN_KEYBOARD, ShortcutActions.OPEN_MOUSE,
            ShortcutActions.OPEN_GAMEPAD, ShortcutActions.OPEN_MEDIA, null
        ).forEach { action ->
            val route = ShortcutActions.routeFor(action)
            assert(route in valid) { "route '$route' not in nav graph" }
        }
    }
}
