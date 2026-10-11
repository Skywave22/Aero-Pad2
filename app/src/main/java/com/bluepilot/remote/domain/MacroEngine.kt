package com.bluepilot.remote.domain

import com.bluepilot.remote.data.macros.MacroRepository
import com.bluepilot.remote.domain.usecase.SendHidActionUseCase
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.macros.MacroSpec
import com.bluepilot.remote.model.macros.MacroStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Macro playback.
 *
 * [expand] is a pure function (unit-tested): MacroStep list → timed HidAction
 * plan. [play] runs the plan on a background scope; only one macro runs at a
 * time (a new play cancels the previous run — prevents runaway loops).
 */
@Singleton
class MacroEngine @Inject constructor(
    private val repository: MacroRepository,
    private val sendAction: SendHidActionUseCase
) {

    companion object {
        /** Pause inserted between consecutive steps so hosts keep up. */
        const val INTER_STEP_DELAY_MS = 30L

        /** One entry of the executable plan: optional wait, then optional action. */
        data class PlanEntry(val delayMs: Long, val action: HidAction?)

        /** V2 M2 b2 — hard cap on the expanded plan (repeat/sub-macro can
         *  multiply steps; a plan can never explode past this). */
        const val PLAN_MAX = 512

        /** V2 M2 b2 — max sub-macro nesting depth (A→B→C ok, deeper cut). */
        const val CALL_DEPTH_MAX = 3

        /**
         * Pure expansion of macro steps into an executable plan.
         * Invalid steps (unknown mouse mask, empty text) are skipped, never fatal.
         * [random] is injectable so RandomDelay is deterministic in tests;
         * playback uses the default source. [resolve] looks up sub-macros for
         * RunMacro steps (null = step skipped). Recursion is fully guarded:
         * cycle set + depth cap + PLAN_MAX hard cap — expansion always halts.
         */
        fun expand(
            spec: MacroSpec,
            random: kotlin.random.Random = kotlin.random.Random.Default,
            resolve: (Long) -> MacroSpec? = { null }
        ): List<PlanEntry> {
            val plan = mutableListOf<PlanEntry>()
            expandInto(plan, spec, random, resolve, depth = 0, visiting = mutableSetOf())
            return plan
        }

        private fun expandInto(
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
                        WidgetActionMapper.maskToButton(step.buttonMask)?.let {
                            plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.MouseClick(it))
                        }
                    is MacroStep.Delay ->
                        plan += PlanEntry(step.ms, null)
                    // V2 MATRIX 2 — timed key hold: down → wait → release.
                    // KeyRelease is ALWAYS in the plan right after its down,
                    // so even mid-plan cancellation risk is one entry wide
                    // (play() additionally neutralizes on cancel).
                    is MacroStep.KeyHold -> {
                        plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.KeyDown(step.key, step.modifiers))
                        plan += PlanEntry(step.ms, HidAction.KeyRelease)
                    }
                    // V2 MATRIX 2 — humanizer jitter, resolved at expand time.
                    is MacroStep.RandomDelay -> {
                        val span = (step.maxMs - step.minMs).coerceAtLeast(0)
                        val ms = step.minMs + if (span > 0) random.nextLong(span + 1) else 0L
                        plan += PlanEntry(ms, null)
                    }
                    // V2 MATRIX 2 — scroll wheel step.
                    is MacroStep.Scroll ->
                        if (step.amount != 0) {
                            plan += PlanEntry(INTER_STEP_DELAY_MS, HidAction.MouseScroll(step.amount))
                        }
                    // V2 M2 b2 — flat loop: re-append the plan entries the
                    // previous [span] steps generated, (times-1) more times.
                    is MacroStep.RepeatLast -> {
                        // boundaries includes THIS step — look back past it.
                        val prior = boundaries.dropLast(1)
                        if (prior.isNotEmpty()) {
                            val from = prior.getOrElse(prior.size - step.span) { prior.first() }
                            val block = plan.subList(from, plan.size).toList()
                            repeat(step.times - 1) {
                                if (plan.size + block.size <= PLAN_MAX) plan += block
                            }
                        }
                    }
                    // V2 M2 b2 — sub-macro call: inline expansion with cycle
                    // + depth guards (self-call or A→B→A is skipped, logged
                    // by the caller's plan simply not growing).
                    is MacroStep.RunMacro -> {
                        if (depth < CALL_DEPTH_MAX && step.macroId !in visiting) {
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
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentRun: Job? = null

    /**
     * V2 MATRIX 2 — stuck-key guard: when a plan contains KeyDown (KeyHold
     * steps) and playback is cancelled mid-hold, force a KeyRelease so the
     * host never keeps a phantom held key. No-op for plans without holds.
     */
    private fun releaseIfHeld(plan: List<PlanEntry>) {
        if (plan.any { it.action is HidAction.KeyDown }) {
            runCatching { sendAction(HidAction.KeyRelease) }
        }
    }

    /**
     * V2 M2 b2 — sub-macro resolver: one snapshot of the macro table per
     * playback (stable during the whole run; no mid-play DB reads on the
     * hot path). Returns specs by id for RunMacro expansion.
     */
    private suspend fun snapshotResolver(): (Long) -> MacroSpec? {
        val all = runCatching {
            repository.observeAll().first()
        }.getOrDefault(emptyList())
        val byId = all.associate { it.id to it.spec }
        return { id -> byId[id] }
    }

    // AEROPAD v1.0 #27/#28 — loop mode + live "running" state for the
    // panic button. isRunning is REAL (reflects the actual job).
    private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRunning: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRunning

    /**
     * V2 PART C dedup — the single playback loop all three entry points
     * share: run [plan] [loops] times with isRunning + stuck-key guard.
     * Behavior byte-identical to the three previous copies.
     */
    private suspend fun runPlan(plan: List<PlanEntry>, loops: Int = 1) {
        _isRunning.value = true
        try {
            repeat(loops.coerceIn(1, 1000)) {
                plan.forEach { entry ->
                    if (entry.delayMs > 0) delay(entry.delayMs)
                    entry.action?.let { sendAction(it) }
                }
            }
        } finally {
            releaseIfHeld(plan)   // V2 M2 — no stuck keys on panic/cancel
            _isRunning.value = false
        }
    }

    /** #27 — loop a macro until [stop] (panic) or [maxLoops] reached. */
    fun playLooped(macroId: Long, maxLoops: Int = 100) {
        currentRun?.cancel()
        currentRun = scope.launch {
            val macro = runCatching { repository.byId(macroId) }.getOrNull() ?: return@launch
            // V2 M2 b2 — sub-macro support; root id excluded (no self-loop).
            val lookup = snapshotResolver()
            val plan = expand(macro.spec, resolve = { id -> if (id == macroId) null else lookup(id) })
            runPlan(plan, maxLoops)
        }
    }

    /** Play a stored macro by id. Cancels any macro already running. */
    fun play(macroId: Long) {
        currentRun?.cancel()
        currentRun = scope.launch {
            val macro = runCatching { repository.byId(macroId) }.getOrNull()
            if (macro == null) {
                Timber.w("macro %d not found", macroId)
                return@launch
            }
            Timber.i("playing macro '%s' (%d steps)", macro.spec.name, macro.spec.steps.size)
            // V2 M2 b2 — sub-macro support; root id excluded (no self-loop).
            val lookup = snapshotResolver()
            val plan = expand(macro.spec, resolve = { id -> if (id == macroId) null else lookup(id) })
            runPlan(plan)
        }
    }

    /** Play an unsaved spec (used by the macro editor's Test button). */
    fun playSpec(spec: MacroSpec) {
        currentRun?.cancel()
        currentRun = scope.launch {
            // V2 M2 b2 — Test button also honors sub-macros (draft has no id,
            // so no self-exclusion needed).
            runPlan(expand(spec, resolve = snapshotResolver()))
        }
    }

    /** #28 — panic/stop: cancels instantly; isRunning drops via finally. */
    fun stop() {
        currentRun?.cancel()
        currentRun = null
        _isRunning.value = false
    }
}
