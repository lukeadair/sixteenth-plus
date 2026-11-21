# Sixteenth+ (Android)

Native Kotlin + Jetpack Compose port of `cabinet_calc.html`: a fractional-inch
shop calculator that rounds answers to the nearest 1/16" (with a `+` flag when
the value sits halfway to the next 1/16) and shows a gauge of where the exact
answer falls between ticks.

## Layout
- `app/src/main/java/com/shopcalc/calc/CalcEngine.kt` – pure math/state engine (no Android deps)
- `app/src/main/java/com/shopcalc/ui/` – Compose UI and palette/fonts
- `app/src/test/…/CalcEngineTest.kt` – unit tests for the engine
- `third_party/` – OFL licenses for the bundled Oswald and JetBrains Mono fonts

## Build
Open the folder in Android Studio, or from a terminal:

    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    ./gradlew testDebugUnitTest assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk
