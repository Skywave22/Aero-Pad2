# Changelog

All notable changes to AeroPad Remote. The in-app copy lives in
`app/src/main/java/com/aeropad/remote/ui/screens/settings/ReleaseNotes.kt`
(Settings → About → What's new); the Play Store block lives in
`store/PLAY_STORE_LISTING.md`. Keep the three in step when cutting a build.

## [3.6.1] — 2026-11-01

A design pass. Same controls, same behaviour — the app stops looking like a
generated template and starts looking made.

### Changed
- **Home is one screen, not a dashboard of duplicated state.** The header used to
  carry a title, a `Customize` chip, a `v3.6.0` chip and a category tagline, and
  then the connection state appeared twice — once as a bordered status card with
  a headline, a sub-line and (when permission was missing) a numbered three-step
  paragraph, and once as a 52 dp `Connect to a device` button. It is now a brand
  mark, a benefit line, two quiet icon actions and **one tap-to-act status row**.
- **The version number moved to Settings → About**, next to the new release
  notes. It has no business on the front page.
- **New icon set (`ui/theme/AeroIcons.kt`).** Twenty vectors drawn on a 24 dp
  grid with a shared cut-corner language replace the eight `Icons.Rounded.*`
  glyphs that every Android app ships with.
- **Colour is a category, not decoration.** Controls share the brand blue,
  builders share green, system entries stay neutral. Previously twelve tiles had
  twelve unrelated hues on twelve identical circles.
- **Quick Tools is a 4×2 strip** of 17 dp icon chips. It was a horizontally
  scrolling row of eight emoji `AssistChip`s, half of them off-screen.
- **Section labels** are uppercase micro-type in the muted colour instead of
  `titleSmall` in the accent colour competing with the tile titles.
- **`NotConnectedBanner` is calm.** It was a full-width `errorContainer` card —
  a saturated red slab above the controls on all 20 control screens, before the
  user had done anything wrong. Being unpaired is the normal starting state, so
  it is now one quiet line. Real failures still surface in full on Home and on
  the Connect screen.
- **Full Board keycaps have depth**: a lit top face, a body and a 2 dp bottom
  lip, instead of a flat fill under a drop shadow.

### Fixed
- **Tiles clipped by the dock.** Home reserves the dock height plus the real
  navigation-bar inset, so the final row always scrolls clear.
- **Pressed keys flashed a square ripple** behind their rounded cap in the light
  theme (Round 18 regression guard retained).

### Added
- `CHANGELOG.md`, in-app release notes, and a Settings → About card.
- `.maestro/` UI walk flows — the CI job referenced `.maestro/` but the
  directory was missing, so the emulator job had nothing to run.

## [3.6.0] — 2026-10-20

### Fixed
- Crash on opening Mouse or Keyboard: a state-reset `init {}` block ran before
  the fields it wrote had been initialised.
- The on-screen keyboard covered text fields on 11 screens.
- Settings could not be scrolled clear of the dock.
- Duplicate back-stack entries made Back feel broken.

See `docs/FIXES-v3.6.0.md` for the full root-cause write-ups.
