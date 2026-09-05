import re

with open('app/src/main/java/com/aeropad/remote/domain/MacroEngine.kt', 'r') as f:
    text = f.read()

new_expandInto = """        private fun expandInto(
            plan: MutableList<PlanEntry>,
            spec: MacroSpec,
            random: kotlin.random.Random,
            resolve: (Long) -> MacroSpec?,
            depth: Int,
            visiting: MutableSet<Long>
        ) {
            // V2 M2 b2 — per-step plan boundaries for RepeatLast unrolling.
            val boundaries = mutableListOf<Int>()
            spec.sanitized().steps.forEach { step ->
                if (plan.size >= PLAN_MAX) return   // hard cap — always halts
                boundaries += plan.size
                when (step) {
                    is MacroStep.KeyTap ->
                        plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.KeyTap(step.key, step.modifiers))
                    is MacroStep.TypeText ->
                        if (step.text.isNotEmpty()) {
                            plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.TypeText(step.text))
                        }
                    is MacroStep.Media ->
                        plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.MediaTap(step.usage))
                    is MacroStep.MouseClick ->
                        plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.MouseClick(com.aeropad.remote.model.MouseButton.fromMask(step.buttonMask)))
                    is MacroStep.Delay ->
                        plan += PlanEntry(step.ms, null)
                    is MacroStep.KeyHold -> {
                        plan += PlanEntry(0, HidAction.KeyDown(step.key, step.modifiers))
                        plan += PlanEntry(step.ms, HidAction.KeyRelease)
                    }
                    is MacroStep.RandomDelay -> {
                        val jitter = if (step.maxMs > step.minMs) random.nextLong(step.minMs, step.maxMs) else step.minMs
                        plan += PlanEntry(jitter, null)
                    }
                    is MacroStep.Scroll ->
                        plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.MouseScroll(step.amount))
                    is MacroStep.RepeatLast -> {
                        val start = boundaries.getOrNull(boundaries.size - 1 - step.span) ?: 0
                        val chunk = plan.subList(start, plan.size).toList()
                        repeat(step.times - 1) { // -1 because it already executed once
                            if (plan.size + chunk.size > PLAN_MAX) return
                            plan.addAll(chunk)
                        }
                    }
                    is MacroStep.RunMacro -> {
                        if (depth < MAX_DEPTH && step.macroId !in visiting) {
                            val sub = resolve(step.macroId)
                            if (sub != null) {
                                visiting += step.macroId
                                expandInto(plan, sub, random, resolve, depth + 1, visiting)
                                visiting -= step.macroId
                            }
                        }
                    }
                }
            }
        }"""

text = re.sub(r"        private fun expandInto\((.*?)\} \}\n            \} \}\n        \}", new_expandInto, text, flags=re.DOTALL)
text = text.replace("    companion object {\n        /** Pause inserted between consecutive steps so hosts keep up. */\n        const val INTER_STEP_DELAY_MS = 30L\n\n        /** One entry of the executable plan: optional wait, then optional action. */\n        data class PlanEntry(val delayMs: Long, val action: HidAction?)\n\n        /** V2 M2 b2 — hard cap on the expanded plan (repeat/sub-macro can\n         *  multiply steps; a plan can never explode past this). */", "    companion object {\n        /** Pause inserted between consecutive steps so hosts keep up. */\n        const val INTER_STEP_DELAY_MS = 30L\n\n        /** One entry of the executable plan: optional wait, then optional action. */\n        data class PlanEntry(val delayMs: Long, val action: HidAction?)\n\n        const val PLAN_MAX = 500\n        const val MAX_DEPTH = 5\n\n        /** V2 M2 b2 — hard cap on the expanded plan (repeat/sub-macro can\n         *  multiply steps; a plan can never explode past this). */")
with open('app/src/main/java/com/aeropad/remote/domain/MacroEngine.kt', 'w') as f:
    f.write(text)
