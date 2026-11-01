package com.aeropad.remote.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Round 18 — whole-app guard against the "square ripple on a rounded control"
 * defect.
 *
 * What went wrong in the Full Board: the keycap painted a rounded background
 * but never clipped its modifier chain, so the press ripple was drawn into the
 * cap's *square layout bounds*. In light mode that showed up as grey square
 * corners behind every pressed key; in dark mode the ripple is nearly invisible
 * against the background, which is why it survived so many rounds of review.
 *
 * Compose has no runtime API that says "this node's indication is clipped", so
 * this is a source-level check: any `clickable` / `combinedClickable` whose
 * modifier chain paints a rounded background (or a rounded border) must also
 * contain a `clip(...)` call by the time the click modifier is applied.
 *
 * It runs in the normal JVM unit-test suite — no emulator, no device — so the
 * guard is enforced on every build.
 */
class RoundedRippleGuardTest {

    /**
     * Lines that start a new modifier chain, so the look-back stops there.
     *
     * Deliberately NOT a bare `Modifier$` match: a chain may legitimately
     * contain `) else Modifier` (conditional modifiers), which previously
     * terminated the scan early and made the guard blind to exactly the bug it
     * was written for.
     */
    private val chainStart = Regex(
        """^\s*(Modifier|modifier)\s*$""" + "|" +
            """=\s*Modifier\s*$""" + "|" +
            """^\s*modifier\s*=\s*[A-Za-z_][\w.]*\s*$"""
    )

    // `combinedClickable` has a capital C — a lowercase-only pattern silently
    // matched only the plain `clickable` and left the very control that broke
    // (the Full Board keycap) invisible to this guard.
    // Every modifier that installs an Indication: clickable, combinedClickable,
    // selectable, toggleable. `combinedClickable` has a capital C — a
    // lowercase-only pattern silently matched only the plain `clickable` and
    // left the very control that broke (the Full Board keycap) invisible.
    private val clickLine = Regex(
        """\.((combined)?[Cc]lickable|selectable|toggleable|triStateToggleable)\s*[({]"""
    )

    /** `.background(...)` with a Shape anywhere in its argument list. */
    private val roundedBackground = Regex(
        """\.background\([^;]{0,160}?(CircleShape|RoundedCornerShape|CutCornerShape|shapes\.\w+|Shape\b|(?<![\w.])shape\b)""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    /** `.border(..., shape = X)` — a rounded outline has rounded corners too. */
    private val roundedBorder = Regex(
        """\.border\([^;]{0,140}?shape\s*=""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    private val clipCall = Regex("""\.clip\(""")

    /**
     * Returns the 1-based line numbers of offending click modifiers.
     * [source] is the whole file; the scan is line-local with a bounded
     * look-back so it stays readable and cannot swallow a neighbouring block.
     */
    private fun findViolations(source: String, lookBack: Int = 30): List<Int> {
        val lines = source.lines()
        val out = mutableListOf<Int>()
        lines.forEachIndexed { i, line ->
            if (!clickLine.containsMatchIn(line)) return@forEachIndexed

            val chain = StringBuilder(line)
            var j = i - 1
            var steps = 0
            while (j >= 0 && steps < lookBack) {
                val prev = lines[j]
                if (chainStart.containsMatchIn(prev)) break
                chain.insert(0, prev + "\n")
                j--; steps++
            }
            val blob = chain.toString()

            val paintsRounded = roundedBackground.containsMatchIn(blob) ||
                roundedBorder.containsMatchIn(blob)
            if (paintsRounded && !clipCall.containsMatchIn(blob)) {
                out += i + 1
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // The detector must actually detect: a test that can never fail guards
    // nothing. This fixture is the exact shape of the Full Board bug.
    // ------------------------------------------------------------------
    @Test
    fun `detector flags the old unclipped keycap`() {
        val broken = """
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(color, CircleShape)
                    .border(1.dp, outline, CircleShape)
                    .clickable { onPick(argb) }
            )
        """.trimIndent()

        assertEquals("unclipped rounded control must be reported", listOf(6), findViolations(broken))
    }

    @Test
    fun `detector accepts a rounded control that is clipped`() {
        val fixed = """
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color, CircleShape)
                    .clickable { onPick(argb) }
            )
        """.trimIndent()

        assertTrue("clipped control must not be reported", findViolations(fixed).isEmpty())
    }

    @Test
    fun `detector ignores square controls`() {
        val square = """
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick() }
            )
        """.trimIndent()

        assertTrue("a square control has no corners to leak", findViolations(square).isEmpty())
    }

    // ------------------------------------------------------------------
    // The real guard: the whole UI source tree.
    // ------------------------------------------------------------------
    @Test
    fun `no rounded control in the app paints an unclipped ripple`() {
        val roots = listOf(
            "src/main/java",
            "app/src/main/java",
            File(System.getProperty("user.dir"), "src/main/java").path,
            File(System.getProperty("user.dir"), "app/src/main/java").path
        )
        val srcRoot = roots.map(::File).firstOrNull { it.isDirectory }

        assertTrue(
            "could not locate the source tree from ${System.getProperty("user.dir")}; " +
                "looked in $roots",
            srcRoot != null
        )

        val violations = mutableListOf<String>()
        var scanned = 0
        srcRoot!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                scanned++
                findViolations(file.readText()).forEach { line ->
                    violations += "${file.relativeTo(srcRoot).path}:$line"
                }
            }

        assertTrue("scanned no Kotlin sources — the guard is not running", scanned > 100)
        assertEquals(
            "These clickable controls paint a rounded background but never clip, so their " +
                "press ripple will spill into square corners (visible in light mode). " +
                "Add .clip(shape) to the modifier chain.\n" +
                violations.joinToString("\n") { "  $it" },
            emptyList<String>(),
            violations
        )
    }
}
