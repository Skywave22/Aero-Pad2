# AeroPad — what it takes to be the best remote in the category

**Date:** 21 October 2026 · **Build:** 3.6.0 (360) · **Author:** an audit for the owner, not a marketing page.

The brief was "make it the world's best app". That is not something you declare,
so this document does the unglamorous version: what the category's users
actually complain about, where AeroPad stands against that today, what was
changed in this pass, and what still separates it from "best".

---

## 1. What the market actually says

Searches across Reddit, Play Store and App Store reviews of Remote Mouse,
Unified Remote, Monect and the Bluetooth-HID-only apps produce a remarkably
consistent list. Ranked by how often it appears:

| Rank | Complaint / desire | Where it shows up |
|---|---|---|
| 1 | **Cursor lag, micro-stutter, "trailing" pointer** | "cursor constantly lags with micro-stutters, zero smoothness"; "moves right and hitches back left"; every Wi-Fi remote |
| 2 | **Connection drops / can't reconnect after sleep or lock** | "unable to reconnect unless I reinstall on both devices"; "connection failed" |
| 3 | **Paywalls, subscriptions, ads** | Remote Mouse and Unified Remote dominate this; users call it a scam |
| 4 | **Battery drain** | "uses 30% more energy than Safari" |
| 5 | **Gestures that don't work like a laptop** | "long-press to drag disengages"; two-finger tap missing |
| 6 | **Wanted: custom keys/shortcuts, left-handed mode, physical volume buttons** | feature requests across every listing |
| 7 | **Setup friction** (server software, IP entry, QR scanning) | the whole Wi-Fi category |

And the thing users *praise* most, again and again: apps that do the basic job
with **no server software on the PC**, **no ads**, and a **minimal interface**
("it turns your phone into a remote keyboard or trackpad and strips unnecessary
features out of view").

## 2. Where AeroPad already wins

- **No server, no account, no network.** AeroPad has *no `INTERNET` permission at
  all* — it is Bluetooth HID only. That is structurally better for privacy and
  setup than every Wi-Fi competitor, and it is a claim they cannot copy without
  removing their own product.
- **No ads, no paywall, no telemetry.** Nothing to monetise, nothing to leak.
- **Honest diagnostics.** Connection Health reports only measured numbers, and
  Setup Doctor turns them into "do this next" instead of a wall of graphs.
  Competitors have no equivalent.
- **Deep customisation.** Layouts with 7 widget types, per-host profiles,
  macros, sticky modifiers, your own named shortcuts — beyond what the paid
  tiers of most rivals offer.
- **Robust persistence.** Corrupt JSON ⇒ empty list, never a crash. Most rivals
  crash on bad state (see the "reinstall both devices" reports).

## 3. What this pass changed — aimed squarely at #1, #5 and #6

### 3.1 Pointer feel (the #1 complaint in the category)

New `domain/PointerCoalescer.kt`, wired inside `TrackpadEngine`:

- A 240 Hz digitizer can fire ~240 reports/second at a Bluetooth link that
  cannot drain them. The backlog *is* the stutter.
- Movement is now merged inside a **12 ms window** into one report, and the
  **total delta is preserved**, so the pointer lands in exactly the same place —
  just carried by fewer packets (up to ~3× fewer on bursty input).
- The **first** delta of a gesture is sent immediately, so starting a movement
  feels instant; a large flick is never held; `endGesture()` flushes the tail so
  the cursor never stops short of the finger.
- Side effect: fewer radio wake-ups ⇒ less battery (complaint #4).

This is the kind of change you cannot see in a screenshot, which is exactly why
it matters — it is what people feel.

### 3.2 Trackpad that behaves like a laptop (#5)

- **Two-finger tap = secondary click.** It was simply missing. Implemented as a
  non-consuming detector (`ui/gestures/TwoFingerTap.kt`) so it cannot steal a
  drag, a long-press or a single tap.
- **Left-handed mode.** Mirrors tap/long-press semantics *and* the on-screen
  click buttons, so the thumb lands on the primary button.

### 3.3 Look (#6, and the owner's explicit ask)

- **Six-accent palette** (`ui/theme/AccentPalette.kt`) with separate light and
  dark variants, so contrast holds on both white and OLED-black surfaces. It
  layers on top of the existing Material / Glass / Neon finishes rather than
  replacing them. Default stays the AeroPad blue, so no existing user sees a
  surprise.
- Picked swatches are announced to TalkBack as radio buttons with their state.

### 3.4 Earlier in this session (context)

New floating dock with a sliding indicator, Setup Doctor, Shortcut Packs, sticky
modifiers, host triggers, per-host overrides, and the My Layout key pad with
user-named combos.

## 4. What still stands between this and "best"

Honest ordering, with the reason each is not done yet:

1. **Reconnect after the host sleeps (complaint #2).** Backoff exists and
   triggers fire on real edges, but a host that drops the HID link while the
   phone is locked is the single most damaging failure mode in the category.
   Needs work in `HidEngine` around re-registration and a "wake the host" path.
   **Highest-value next item.**
2. **Latency measurement the user can see.** Connection Health measures
   `sendReport()` duration, not perceived end-to-end latency (HID has no ACK).
   A phone-side "time from touch to report handed to the stack" figure would let
   a user prove a fix instead of trusting it.
3. **Physical volume keys as remote keys** (a repeated feature request). Deferred
   because a bug there breaks the user's actual volume control; needs a careful,
   opt-in, focus-scoped implementation.
4. **Turbo / hold-to-repeat and double-tap bindings** (F10 / F17 from the feature
   brief). Natural next step inside the new key pad.
5. **Play Store readiness.** `targetSdk 35`; Play requires 36. It installs fine
   as an APK, but it cannot be published as-is.
6. **Localisation.** Most labels are hard-coded Kotlin, so the existing
   `values-*` files cover very little.

## 5. The uncomfortable part

The app's real advantage is that it needs nothing installed on the PC and
touches no network. Every "feature" that would require a companion app, an
account, or a socket makes it *more* like the products it beats. The discipline
that keeps it the best in its niche is refusing those features — which is why
the anti-goals in the feature brief (X1–X7) are worth honouring even when
"more features" sounds like progress.

---

### Verification for this pass

- `:app:testDebugUnitTest` → **404 tests, 0 failures** (20 new in
  `features/PointerFeelTest.kt`).
- `:app:assembleDebug` and CI release build green; version unchanged at
  **3.6.0 / 360**.
