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
./gradlew bundleRelease                # signed Play bundle → app/build/outputs/bundle/release/app-release.aab
```
Release signing reads the git-ignored `keystore.properties` (upload key in `~/.android-keys/`). Never commit either. Bump `versionCode` in `app/build.gradle.kts` before every Play upload.

## Layout
- `app/src/main/java/com/sixteenthplus/calc/CalcEngine.kt`: all math and key handling. Pure Kotlin, no Android imports. Keep it that way so it stays unit-testable.
- `app/src/main/java/com/sixteenthplus/ui/ShopCalcScreen.kt`: Compose UI; `Theme.kt` holds `Palette` and fonts.
- `app/src/test/.../CalcEngineTest.kt`: engine tests, using a key-script DSL: `press("7 sp 1 /4 × 3 =")`.

## How the engine works
- `CalcState` is immutable; every key press returns a new state. The UI only calls `update { digit("7") }` etc.
- State is `text` + `cursor` (char index); keys edit at the cursor. `tokenize(text)` gives tokens on demand. `snap` keeps the cursor out of fraction tokens (it can only sit at their ends). Backspace deletes one char.
- Mixed-number entry: `a b/c` (`space()`) once gives `7 `, a second press in the numerator opens the denominator (`7 3⁄`). Digits right after a fraction extend its denominator, and a preset key replaces it.
- The UI shows the text as one `AnnotatedString` (fractions are inline content), and `Expr.toDisplay`/`toEngine` map cursor positions between display and engine offsets.
- Tokens are strings. Operators are Unicode `+ − × ÷` (U+2212 minus, not ASCII `-`); ASCII `-` only appears as a sign inside a number token. Fractions use U+2044 `⁄` (`FRAC_SLASH`), not `/`.
- A space token joins a mixed number (`7 1⁄4`). `mergeMixedNumbers` collapses it to one value **before** precedence, so `3 × 7 1⁄4` = 21.75.
- Implicit `×` (`2(3)`, `(2)3`, `-(…)`) is added in `evaluateTokens`, not when typing, so it works wherever the cursor inserts.

## Rules
- Any change to engine behaviour gets a test in `CalcEngineTest` written with `press(...)`.
- Rounding and display (`toNearest16Plus`, `gaugeFor`) are the product. Check edge cases: exact sixteenths, 32nds, values just under a whole number, negatives, `−0`.
- Keep it lean: no new dependencies, DI, ViewModel or Material library unless a feature truly needs it. The UI is built on `foundation` + `BasicText` on purpose.
- Package/app id is `com.sixteenthplus`. Changing `applicationId` makes Android treat it as a different app, so never change it as a side effect.

## Workflow
- Run `testDebugUnitTest` before calling a change done; run `assembleDebug` for UI changes.
- Commit messages: short imperative subject line.
