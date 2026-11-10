# Notes for Claude

## Keep the docs in step with the action pad

Whenever you change the action pad (anything in `app/src/main/java/com/limelight/ui/Pad*`, `ActionPad*`, `ExternalActionPadView`, `ConnectionKeySink`, its settings, or the Game/activity hooks), update the docs in the same change:

- `README.md`, section "WoW Action Pad (this fork)": features, how to use, settings, known limits. Users read this before downloading.
- `docs/ACTION_PAD.md`: file map (add, remove or rename files and fix line counts if they drift a lot), review order, design decisions, test counts, risks.
- Remove a known limit when it is fixed; add one when you find it. Say honestly what is untested on a device.

Run the action pad tests before committing:
`./gradlew testNonRoot_gameDebugUnitTest --tests 'com.limelight.ui.ActionPad*' --tests 'com.limelight.ui.Pad*'`
