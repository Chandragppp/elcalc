#!/usr/bin/env bash
# Dijalankan di CI saat emulator sudah menyala:
# 1) screenshot tiap tab terkunci & terbuka (terang/gelap, Inggris & Indonesia), 2) tes UI instrumentasi + screenshot per skenario.
set -euo pipefail

OUT=screenshots
PKG=id.controlengineer.elcalc
mkdir -p "$OUT"

adb install -r "$(ls app/build/outputs/apk/debug/*.apk | head -1)"
adb install -r "$(ls app/build/outputs/apk/androidTest/debug/*.apk | head -1)"

# Emulator CI lambat: jaga layar menyala, beri waktu System UI stabil, tutup dialog ANR sistem sebelum tiap screenshot.
adb shell svc power stayon true
sleep 30
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
close_dialogs() {
    adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null || true
    sleep 1
}

shot_tabs() { # $1 = akhiran nama file
    # Tab 0 (Dasar gratis) & Tab 1 (Kabel gratis), serta Tab 2 (Motor), Tab 3 (Kapasitor), Tab 4 (3 Fasa) terkunci (pro=off)
    for tab in 0 1 2 3 4; do
        adb shell am force-stop "$PKG"
        adb shell am start -W -n "$PKG/.MainActivity" --ei tab "$tab" --es pro off
        sleep 4
        close_dialogs
        adb exec-out screencap -p > "$OUT/tab${tab}-locked-$1.png"
    done
    # Subtab di Tab 0 (Dasar: 0=Ohm, 1=Daya 1P/3P, 2=Trafo)
    for subtab in 0 1 2; do
        adb shell am force-stop "$PKG"
        adb shell am start -W -n "$PKG/.MainActivity" --ei tab 0 --ei subtab "$subtab" --es pro off
        sleep 4
        close_dialogs
        adb exec-out screencap -p > "$OUT/tab0-sub${subtab}-$1.png"
    done
    # Tab 2 (Motor: 0=DOL, 1=Star-Delta), Tab 3 (Kapasitor Bank), dan Tab 4 (3 Fasa Unbalance) dalam keadaan terbuka (pro=on)
    for subtab in 0 1; do
        adb shell am force-stop "$PKG"
        adb shell am start -W -n "$PKG/.MainActivity" --ei tab 2 --ei subtab "$subtab" --es pro on
        sleep 4
        close_dialogs
        adb exec-out screencap -p > "$OUT/tab2-sub${subtab}-pro-$1.png"
    done
    for tab in 3 4; do
        adb shell am force-stop "$PKG"
        adb shell am start -W -n "$PKG/.MainActivity" --ei tab "$tab" --es pro on
        sleep 4
        close_dialogs
        adb exec-out screencap -p > "$OUT/tab${tab}-pro-$1.png"
    done
}

for night in no yes; do
    adb shell cmd uimode night "$night"
    shot_tabs "night_${night}"
done
adb shell cmd uimode night no

if adb shell cmd locale set-app-locales "$PKG" --locales id-ID; then
    shot_tabs "id"
    adb shell cmd locale set-app-locales "$PKG" --locales en-US || true
else
    echo "set-app-locales gagal; screenshot Bahasa Indonesia dilewati"
fi

# Tab Kabel di ponsel kecil (360x800 dp, mode gelap, seperti ponsel pemilik): input utama + hasil harus terlihat tanpa scroll.
adb shell wm size 900x2000
adb shell wm density 360
adb shell cmd uimode night yes
for loc in en-US id-ID; do
    adb shell cmd locale set-app-locales "$PKG" --locales "$loc" || true
    adb shell am force-stop "$PKG"
    adb shell am start -W -n "$PKG/.MainActivity" --ei tab 1 --es pro on
    sleep 4
    close_dialogs
    adb exec-out screencap -p > "$OUT/cable-small-$loc.png"
done
adb shell cmd locale set-app-locales "$PKG" --locales en-US || true
adb shell cmd uimode night no
adb shell wm size reset
adb shell wm density reset

# Screenshot khusus fitur Pro (paywall, dialog sambutan trial, chip trial).
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ei tab 2 --es pro off --ez paywall true
sleep 4
close_dialogs
adb exec-out screencap -p > "$OUT/paywall.png"

adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ei tab 0 --es trial fresh --ez welcome true
sleep 4
close_dialogs
adb exec-out screencap -p > "$OUT/trial-welcome.png"

adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ei tab 2 --ei subtab 1 --es trial fresh
sleep 4
close_dialogs
adb exec-out screencap -p > "$OUT/trial-chip.png"

# Tes UI. `am instrument` tidak gagal saat tes gagal, jadi hasilnya diperiksa manual di bawah.
adb shell am force-stop "$PKG"

adb shell am instrument -w -r "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/ui-tests.txt"

mkdir -p "$OUT/ui"
adb exec-out run-as "$PKG" tar cf - files/shots | tar xf - -C "$OUT/ui" || echo "tidak ada screenshot tes UI"

adb logcat -d -s AndroidRuntime:E > "$OUT/crash-log.txt" || true
ls -laR "$OUT"

if grep -q "FAILURES!!!" "$OUT/ui-tests.txt" || ! grep -q "^OK (" "$OUT/ui-tests.txt"; then
    echo "Tes UI GAGAL"
    exit 1
fi
