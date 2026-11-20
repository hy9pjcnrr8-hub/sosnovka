#!/bin/bash
# Ставит нативный APK, запускает в режиме автоигры и снимает скриншоты по ходу партии в папку ci-shots/.
APK=nativeapp/build/outputs/apk/release/nativeapp-release.apk
mkdir -p ci-shots
adb install -r $APK
adb logcat -c
adb shell am start -n ru.ugolnyyar.nativegame/.MainActivity --ez autoplay true --ez a3test true
shot() {
  timeout 60 adb shell screencap -p /sdcard/s.png && timeout 60 adb pull /sdcard/s.png ci-shots/$1.png >/dev/null && echo "shot $1 ok" || echo "shot $1 FAILED"
}
alive() { adb shell pidof ru.ugolnyyar.nativegame >/dev/null && echo "ALIVE at $1" || echo "DEAD at $1"; }
for t in 15 60 120 200 290 380 470 560 650 740; do
  sleep $(( t - ${prev:-0} )); prev=$t
  shot t$(printf %03d $t); alive $t
done
adb shell dumpsys gfxinfo ru.ugolnyyar.nativegame | grep -E "Total frames|Janky|50th|90th|99th" | head -6 > ci-shots/fps.txt
adb logcat -d | grep -E "FATAL EXCEPTION" -A 30 | head -80 > ci-shots/crash.txt
adb logcat -d | grep -iE "ugolnyyar|AndroidRuntime" | tail -40 > ci-shots/log.txt
cat ci-shots/fps.txt ci-shots/crash.txt
alive end | tee -a ci-shots/fps.txt
