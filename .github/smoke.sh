#!/bin/bash
# Ставит нативный APK, запускает и делает несколько ходов, печатая текст на экране.
set -x
APK=nativeapp/build/outputs/apk/release/nativeapp-release.apk
adb install -r $APK
adb logcat -c
adb shell am start -n ru.ugolnyyar.nativegame/.MainActivity
sleep 8
screen() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | sed 's/text="//;s/"$//' | grep -v '^$'
}
tap_text() {   # нажать на элемент, текст которого начинается с $1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  B=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep "text=\"$1" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  set -- $B
  if [ -n "$4" ]; then adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); else echo "NOT FOUND"; fi
  sleep 6
}
echo "===== ЭКРАН 1"; screen
tap_text "Начать"
echo "===== ЭКРАН 2"; screen
tap_text "Восстановить"
echo "===== ЭКРАН 3"; screen
tap_text "Дальше"
tap_text "Отсыпать"
echo "===== ЭКРАН 4"; screen
adb shell screencap -p /sdcard/s.png
echo "===== CRASHES"
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ugolnyyar" | head -40
echo "===== PROCESS"
adb shell pidof ru.ugolnyyar.nativegame && echo "APP ALIVE" || echo "APP DEAD"
