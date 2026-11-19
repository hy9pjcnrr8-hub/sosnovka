#!/bin/bash
# Ставит нативный APK, запускает в режиме автоигры и снимает скриншоты по ходу партии.
APK=nativeapp/build/outputs/apk/release/nativeapp-release.apk
adb install -r $APK
adb logcat -c
adb shell wm size
adb shell am start -n ru.ugolnyyar.nativegame/.MainActivity --ez autoplay true
shot() {
  adb exec-out screencap -p > /tmp/s.png
  echo "===SHOT $1 BEGIN"; base64 -w 0 /tmp/s.png; echo; echo "===SHOT $1 END"
}
alive() { adb shell pidof ru.ugolnyyar.nativegame >/dev/null && echo "ALIVE at $1" || echo "DEAD at $1"; }
sleep 12; shot t012; alive 12
sleep 20; shot t032; alive 32
sleep 30; shot t062; alive 62
sleep 40; shot t102; alive 102
sleep 60; shot t162; alive 162
sleep 80; shot t242; alive 242
sleep 90; shot t332; alive 332
echo "===== FPS"
adb shell dumpsys gfxinfo ru.ugolnyyar.nativegame | grep -E "Total frames|Janky|50th|90th" | head -5
echo "===== CRASHES"
adb logcat -d | grep -E "FATAL EXCEPTION" -A 25 | head -60
alive end
