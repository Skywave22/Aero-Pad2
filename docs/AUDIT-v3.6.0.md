# Round 14 — every screen read, every defect fixed

The brief was "check one by one everything, its UI and UX, and fix even the
small issues, deeply". Artemis was the reference for *how* to test — it drives a
real device, verifies each action, and keeps logcat plus screenshots as
evidence. It needs a physical phone and an LLM key, neither of which this
pipeline has, so `FeatureAuditTest` is the deterministic equivalent:

* 25 destinations — every route in `Routes`, including `connection` and
  `permissions`
* portrait: screenshot, then up to 8 tappable controls per screen, each checked
  for a logcat crash, a package change, or a blank render
* landscape: a screenshot of every screen, taken **before** any tapping (a tap
  that navigates used to cost that screen its landscape evidence)
* flags labels that use symbol codepoints known to tofu on Android
* writes `findings.txt` plus a logcat slice per crash, and never fails the run —
  one broken screen must not hide the other twenty-four

Then I read every screenshot. The list below is what that found; none of it
would have surfaced from the code alone.

## Fixed

| Screen | Defect | Cause |
| --- | --- | --- |
| All screens | A bad start destination crashed the app before its first frame | `layout_editor` is registered as `layout_editor/{profileId}`; handing NavHost the bare route throws |
| Gamepad Builder | Every gamepad rendered as "Cas / ual / Lay / out" | Six icon buttons left the title column ~10 dp |
| Full Keyboard | Title squeezed to "FN" | FN + Compact + Full + 1-hand + edit in the app bar |
| Layout Editor | Layout name showed as "Ne..." | Five 48 dp actions in the app bar |
| Layout Editor | New layout was an unexplained empty box | No empty state |
| Connection Health | "n/a — Android doesn't expose RSSI…" printed as seven ragged lines | Long values forced into a 1.4-weight column |
| Text & Clips | "Paste on PC (Ctrl+V)" always cut to "Paste >" | Three long chips in a scrolling row |
| Gamepad Builder | "Platformer" tag cut to "Pla" | Bare `horizontalScroll` with no affordance |
| Mouse | "SCROLL" printed through the arrow icon | Label rotated after layout, so it was measured at 56 dp |
| Numpad / Keyboard / Home | Content sliced by the system navigation bar | Bottom padding assumed the nav bar was zero |
| Macros | "● Record" next to a record icon read "● • Record" | Duplicated bullet |
| Macros | Empty list was a blank page | No empty state |
| Touch Screen | Touch surface squeezed to ~70 dp in landscape | Two paragraphs of prose above it |
| Gamepad (landscape) | A button, both triggers and Select/Home/Start were off-screen | ~380 dp of controls in a ~270 dp window, inside a scrollable column so `weight` had no height to resolve against |
| Home | Last tile row showed under the dock | Dock inset did not include the navigation bar |
| Any screen with chips | Last chip hidden under the chevron | Chevron overlaid the scrolling content |

## How the landscape gamepad was actually broken

Three failed theories, each disproved by a screenshot rather than by reasoning:

1. *The deck row was too short.* Fixed to 290 dp — still clipped.
2. *The window was too short.* Added a scale-to-fit wrapper — the screenshot
   showed the block drawn at natural size and clipped by the window, because
   `graphicsLayer` only transforms the draw pass, not layout.
3. *The column scrolled.* That was the real one: a `verticalScroll` column is
   measured with unbounded height, so `weight(1f)` resolved to nothing and every
   cluster collapsed — the A button rendered as a green sliver and the d-pad
   lost three arrows.

Even then, stacking a stick above a d-pad needs ~270 dp of height that a 270 dp
window does not have once the banner is drawn. Landscape now lays the controller
out **across** the screen — where there are 1500 dp to spare — and sizes each
cluster from the row height that remains. Portrait keeps the designed layout.

## Remaining, honestly

* **Scroll-only chip rows still fade at the right edge.** Eight screens have
  long chip lists that must scroll; the fade is the affordance. Where two chips
  were long enough to truncate mid-word they are now stacked instead.
* **One audit finding is not reproduced:** "settings | Teal accent | tap left
  the app". Accent dots are `clickable {}` boxes with no intent, so this is
  most likely the probe reading a stale window; it is recorded rather than
  dismissed, but I could not reproduce it deliberately.
* **Not exercised against a real host.** Everything here ran with no paired
  PC, so "not connected" states are the ones under test. Live HID behaviour
  needs the user's phone and host.
* Media Studio / Casual Layout still have no automated captures, and PC Combo,
  Presenter, Touch and Text & Clips were reviewed by code and screenshot rather
  than by interaction.
