#!/bin/bash
# Ставит нативный APK, делает несколько ходов, печатает текст экрана и скриншоты (base64).
APK=nativeapp/build/outputs/apk/release/nativeapp-release.apk
adb install -r $APK
adb logcat -c
adb shell am start -n ru.ugolnyyar.nativegame/.MainActivity
sleep 10
screen() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | sed 's/text="//;s/"$//' | grep -v '^$'
}
shot() {
  adb exec-out screencap -p > /tmp/s.png
  echo "===SHOT $1 BEGIN"; base64 -w 0 /tmp/s.png; echo; echo "===SHOT $1 END"
}
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  B=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep "text=\"$1" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  set -- $B
  if [ -n "$4" ]; then adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); else echo "NOT FOUND"; fi
  sleep ${2:-6}
}
echo "===== ЭКРАН 1"; screen; shot prolog
tap_text "Начать"
tap_text "Восстановить" ; sleep 1
shot heat
echo "===== ЭКРАН 2"; screen
tap_text "Дальше"
tap_text "Отсыпать"
shot road
tap_text "Дальше"
tap_text "Купить и"
tap_text "Дальше"
sleep 4
shot mine
echo "===== CRASHES"
adb logcat -d | grep -E "FATAL EXCEPTION" -A 25 | head -60
echo "===== PROCESS"
adb shell pidof ru.ugolnyyar.nativegame && echo "APP ALIVE" || echo "APP DEAD"
