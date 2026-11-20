package com.aeropad.remote.features

import com.aeropad.remote.domain.RouteGuard
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 14 — found by the feature audit: a bad start destination crashed the
 * app before its first frame.
 */
class RouteGuardTest {

    @Test
    fun `an unknown destination falls back to home instead of crashing`() {
        assertEquals("home", RouteGuard.sanitize("not_a_screen"))
        assertEquals("home", RouteGuard.sanitize(""))
        assertEquals("home", RouteGuard.sanitize(null))
        assertEquals("home", RouteGuard.sanitize("HOME"))       // case matters
    }

    @Test
    fun `a route that needs an argument gets its default`() {
        // This exact value crashed the app in the audit run.
        assertEquals("layout_editor/0", RouteGuard.sanitize("layout_editor"))
    }

    @Test
    fun `a fully formed route is passed through untouched`() {
        assertEquals("layout_editor/12", RouteGuard.sanitize("layout_editor/12"))
        assertEquals("mouse", RouteGuard.sanitize("mouse"))
        assertEquals("settings", RouteGuard.sanitize("settings"))
    }

    @Test
    fun `surrounding whitespace and unregistered queries are ignored`() {
        assertEquals("mouse", RouteGuard.sanitize("  mouse  "))
        assertEquals("home", RouteGuard.sanitize("home?tab=2"))
        assertEquals("layout_editor/12", RouteGuard.sanitize("layout_editor/12?preview=true"))
    }

    @Test
    fun `unregistered paths and malformed profile ids fall back to home`() {
        listOf("mouse/extra", "layout_editor/", "layout_editor/abc", "layout_editor/1/2",
            "layout_editor/-1", "layout_editor/9223372036854775808").forEach {
            assertEquals("home", RouteGuard.sanitize(it))
        }
    }

    @Test
    fun `every top level route is accepted as itself`() {
        RouteGuard.TOP_LEVEL.forEach { route ->
            val out = RouteGuard.sanitize(route)
            assertEquals(
                "route $route must not degrade to home",
                route,
                out.substringBefore('/')
            )
        }
    }

    @Test
    fun `path traversal cannot invent a destination`() {
        assertEquals("home", RouteGuard.sanitize("../settings"))
        assertEquals("home", RouteGuard.sanitize("/mouse"))
    }
}
