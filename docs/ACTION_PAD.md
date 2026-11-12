# Action Pad: developer guide

How the second-screen action pad works, and how to review it. Keep this file in step with the code (see `CLAUDE.md`).

## Big picture

The pad is a button panel for a handheld's second display, shown while streaming. It sits on top of Artemis (a Moonlight fork) and lives in `com.limelight.ui`, with small hooks in `Game.java`, `ExternalDisplayControlActivity` and the preferences.

A touch becomes a keystroke on the host in five steps:

1. `ExternalActionPadView` draws tabs and buttons, built by `PadKit` from the current theme and settings.
2. A button tap calls `PadInput`, which decides what to press: the key, any armed Shift/Ctrl/Alt, a macro, or a mouse button.
3. `PadInput` sends the result to a `KeySink` (`sendKey`, `sendMouseMove`, `sendMouseButton`, `sendMouseScroll`).
4. `ConnectionKeySink` is the real sink. It forwards to `NvConnection` and does nothing when no stream is running.
5. Moonlight carries the event to Apollo/Sunshine as a Windows virtual-key code.

Both display paths use the same view. `Game.java` puts it on the secondary display through `ActionPadPresentation`; `ExternalDisplayControlActivity` hosts it as a normal screen.

Customisation lives in `PadConfig`: per-button key, name, colour, hidden flag and order, saved as text in the app preferences and applied on top of the defaults in `ActionPadKeyMap`. The defaults are never modified.

## File map

All in `app/src/main/java/com/limelight/ui/`. "Pure" means plain Java with no Android imports, so it is unit-tested without a device. Line counts are approximate; update them when they drift a lot.

| File | Lines | Kind | Job |
| --- | --- | --- | --- |
| `ActionPadKeyMap` | 331 | Pure | Default layout: pages, number row, extras, key picker list, Windows VK constants |
| `PadConfig` | 191 | Pure | The player's changes (key, name, colour, hidden, order), saved and loaded as text |
| `PadInput` | 270 | Pure | Press logic: modifiers, combos, chat macros, mouse buttons, hold, scroll, release-all |
| `ActionPadTheme` | 91 | Pure | The 15 colour themes |
| `ActionPadSettings` | 170 | Android prefs | Reads every setting once; saves config and 3 profiles, and cycles through them |
| `WhatsNew` | 40 | Android UI | The "What's new" dialog, shown once per version and from Settings |
| `PadKit` | 231 | Android UI | Shared factory for buttons, colours and sizes; refresh callbacks |
| `ExternalActionPadView` | 343 | Android UI | Main view: tabs, pages, extras panel, chat flow, rebuild |
| `PadTouchpad` | 206 | Android UI | Mouse tab: touchpad, Left/Hold/Right, scroll strip |
| `PadKeyboard` | 106 | Android UI | Keyboard tab and the typing capture used for rename and chat |
| `PadEditor` | 330 | Android UI | Customize screen: remap, rename, recolour, hide, reorder, profiles, theme, fade time |
| `PadDimmer` | 77 | Android UI | Fades the pad after idle time |
| `ActionPadPresentation` | 41 | Android UI | The window on the secondary display |
| `ConnectionKeySink` | 54 | Glue | Sends pad events through `NvConnection` |

Outside that folder:

- `Game.java`: shows the presentation once the stream is connected and hides it when the stream ends.
- `utils/ExternalDisplayControlActivity.java`: hosts the same view as a screen.
- `preferences/PreferenceConfiguration.java`: the `enableActionPad` switch.
- `res/xml/preferences.xml`, `res/values/strings.xml`, `res/values/action_pad_arrays.xml`: settings screen, text and option lists.

## Suggested review order

Start with the pure logic, where bugs hurt most and the tests help. Finish with the Android UI, the least verified part.

1. **`ActionPadKeyMap`**: skim the data. Each `Slot` has a stable `id` (its default label) that remaps and saved profiles depend on.
2. **`PadConfig`**: `resolve`, `serialize`, `parse`. Check how bad or old saved text is handled and that an unknown id cannot crash the pad.
3. **`PadInput`**: the core. Armed versus locked modifiers, one-shot release, isolated buttons suspending armed modifiers, `releaseAll`. Key question: can any sequence leave a key held on the host?
4. **`ConnectionKeySink`** and the `KeySink` interface: every send must be null-safe when the stream is gone.
5. **`PadKit`**: how `addRefresher` and `forget` keep modifier highlights in sync. Look for callbacks leaked after a rebuild.
6. **`ExternalActionPadView`**: `rebuild`, the tab list, the chat keyboard flow with `chatReturnTab`, `releaseAll` on hide.
7. **`PadTouchpad`**: drag maths (fractional remainder carried between events), tap versus drag, two-finger tap, scroll strip.
8. **`PadEditor` and `PadKeyboard`**: the largest UI surface. Every edit must save and redraw the same button screen; rename capture must hand typing back.
9. **`Game.java` and `ExternalDisplayControlActivity`**: show/hide timing, and key release on both stream end and activity stop.

To see everything this work changed: `git diff c5cf27f wow-action-pad`.

## Key design decisions

- **Pure logic split from Android UI.** `PadInput`, `PadConfig` and `ActionPadKeyMap` import nothing from Android and run in plain JUnit, with a fake sink and scheduler.
- **One sink interface.** The pad never touches `NvConnection` directly. Tests, the editor (which uses a silent input) and both host screens plug in through `KeySink`.
- **Stable button ids.** Remaps are stored against a button's default label, not its position, so reordering or hiding never breaks a saved change. Cost: renaming a default label in code orphans saved changes.
- **Defaults are never edited.** `PadConfig` is a list of overrides, so "reset button" just deletes an override.
- **Settings read once.** `ActionPadSettings.load` snapshots every preference when the view is created. Changes in the settings screen apply the next time the pad is created. The editor can change the theme and fade time live: it saves them, then `rebuild()` re-applies both.
- **Plain preferences, text-serialised config.** No database or JSON library; the format is hand-rolled (tab-separated lines).
- **minSdk 21, Java 11.** Main code avoids newer APIs such as `Map.getOrDefault`.
- **Shared presses are counted.** Mouse buttons use hold counts, so tapping Left while Hold is on does not release the held button.

## Tests

50 plain JUnit tests in `app/src/test/java/com/limelight/ui/`:

| Test file | Tests | Covers |
| --- | --- | --- |
| `PadInputTest` | 10 | Modifiers, combos, macros, mouse hold, and a 300-seed fuzz that checks no key is left stuck |
| `PadConfigTest` | 13 | Set, move, hide, reset, and save/load including bad input |
| `ActionPadKeyMapTest` | 15 | Layout data is consistent: unique ids, valid key codes, picker list |
| `ActionPadThemeTest` | 6 | Theme list matches the settings arrays; text is readable on each theme |
| `ActionPadSettingsTest` | 4 | Which profile the Profile button loads next; fade choices match the settings screen |
| `WhatsNewTest` | 2 | When the notes show |

```
./gradlew testNonRoot_gameDebugUnitTest --tests 'com.limelight.ui.ActionPad*' --tests 'com.limelight.ui.Pad*'
```

Not covered: every Android view (`ExternalActionPadView`, `PadTouchpad`, `PadEditor`, `PadKeyboard`, `PadKit`, `PadDimmer`), the scroll strip, `ActionPadSettings`, and the `Game.java` wiring. The older `StreamViewCommitTextTest` is not part of this work and needs network access to run.

## Risks and gaps before release

- **The new UI has had little device testing.** Editor, keyboard tab, chat flow, extras, side-by-side mode and scroll strip compile and their logic is tested, but expect layout and touch bugs first.
- **Scroll direction is unconfirmed.** Dragging down should scroll down; the Reverse setting exists in case it feels wrong.
- **No format version in saved config.** A future format change needs a migration plan.
- **Renaming a default button label orphans saved changes.**
- **Settings changes apply on the next stream**, not live.
- **Release build is untested on a device.** It is signed with a private key kept outside the repo (see Releasing below) and shrunk with R8, which can strip code that only reflection uses.
- **Icon.** The launcher icon is still Artemis's.
- **Update links removed.** The Settings entries that linked to ClassicOldSong's releases are gone; add your own repo's links once it exists.
- **Licence.** Artemis and Moonlight are GPL-licensed; a public release must stay open source under a compatible licence with notices kept. Check this before publishing.
- **Style.** Mixed lambda and anonymous-class handlers and a few long methods in `PadEditor` and `ExternalActionPadView` are the likeliest review comments.

## Releasing

- The app id is `com.thortemis` (`com.thortemis.root` for the root flavor), so it installs beside Moonlight and Artemis.
- Bump `versionName` and `versionCode` in `app/build.gradle`, and update `whats_new_body` in `strings.xml` so the notes show on update.
- The keystore is never committed (`*.keystore` is git-ignored). Set `THORTEMIS_KEYSTORE`, `THORTEMIS_KEYSTORE_PASSWORD`, `THORTEMIS_KEY_ALIAS` and `THORTEMIS_KEY_PASSWORD`, then run `./gradlew assembleNonRoot_gameRelease`. Losing the key means users cannot update in place, so back it up.
