# Sixteenth+

Android calculator for woodworkers: fractional-inch math, answers rounded to the
nearest 1/16" with a `+` when the value sits halfway to the next 1/16.
Kotlin + Jetpack Compose, single activity, no network, no persistence beyond
`rememberSaveable`.

## Commands
Gradle needs the JDK bundled with Android Studio:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest            # unit tests (fast, run after every engine change)
./gradlew assembleDebug                # build APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Layout
- `app/src/main/java/com/shopcalc/calc/CalcEngine.kt`: all math and key handling. Pure Kotlin, no Android imports. Keep it that way so it stays unit-testable.
- `app/src/main/java/com/shopcalc/ui/ShopCalcScreen.kt`: Compose UI; `Theme.kt` holds `Palette` and fonts.
- `app/src/test/.../CalcEngineTest.kt`: engine tests, using a key-script DSL: `press("7 sp 1 /4 × 3 =")`.

## How the engine works
- `CalcState` is immutable; every key press returns a new state. The UI only calls `update { digit("7") }` etc.
- Tokens are strings. Operators are Unicode `+ − × ÷` (U+2212 minus, not ASCII `-`); ASCII `-` only appears as a sign inside a number token. Fractions use U+2044 `⁄` (`FRAC_SLASH`), not `/`.
- A space token joins a mixed number (`7 1⁄4`). `mergeMixedNumbers` collapses it to one value **before** precedence, so `3 × 7 1⁄4` = 21.75.
- `(` after a number or `)`, and a number after `)`, get an implicit `×`.

## Rules
- Any change to engine behaviour gets a test in `CalcEngineTest` written with `press(...)`.
- Rounding and display (`toNearest16Plus`, `gaugeFor`) are the product. Check edge cases: exact sixteenths, 32nds, values just under a whole number, negatives, `−0`.
- Keep it lean: no new dependencies, DI, ViewModel or Material library unless a feature truly needs it. The UI is built on `foundation` + `BasicText` on purpose.
- Package/app id is still `com.shopcalc` (the old name). Don't rename it as a side effect of another change; that changes the installed app identity.

## Workflow
- Run `testDebugUnitTest` before calling a change done; run `assembleDebug` for UI changes.
- Commit messages: short imperative subject line.
