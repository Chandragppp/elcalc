# Panel Calc (`id.controlengineer.elcalc`)

Kalkulator listrik industri & perakitan panel (*offline-first*) untuk teknisi listrik dan *panel maker* Indonesia.
Posisi jualan: **"Bayar sekali, tanpa langganan"** (*"Pay once, no subscription"*).

## Modul & Struktur Proyek
- **`:core`** (`core/src/main/kotlin/id/controlengineer/elcalc/core/`): Modul Kotlin/JVM murni tanpa dependensi Android beserta unit test JUnit (`ElectricalCoreTest.kt`).
- **`:app`** (`app/src/main/java/id/controlengineer/elcalc/`): Aplikasi UI Jetpack Compose Material 3 (terang/gelap), lokalisasi EN (`values/`) + ID (`values-in/`), Google Play Billing (`pro_lifetime`), AdMob Rewarded Ad (ID TES Google di kode, `TODO_ADMOB` di `app/build.gradle.kts`), dan uji instrumen Compose (`UiFlowTest.kt`).
- **`docs/`**: `sources.md` (sumber rumus & status `PERLU CEK MANUAL`), `play-listing.md` (EN + ID), `privacy-policy.md` (EN + ID).

## Perintah Build & Verifikasi
```bash
gradle :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --no-daemon
```
