#!/usr/bin/env bash
#
# Build + install the Dynamic Island app with the correct JDK, then launch it.
#
# Usage:
#   ./run.sh            build, install, launch
#   ./run.sh build      build only
#   ./run.sh install    build + install (no launch)
#   ./run.sh logs       tail the app's touch/island logs
#
set -euo pipefail

# AGP 8.7 / Gradle 8.9 need JDK 17-21. Pick JDK 21 explicitly so it doesn't fall back to 25.
JDK21="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
if [ -d "$JDK21" ]; then
  export JAVA_HOME="$JDK21"
elif [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
echo "Using JAVA_HOME=$JAVA_HOME"

cd "$(dirname "$0")"

PKG="com.island.dynamicisland"
ACTIVITY="$PKG/.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"

SVC="$PKG/$PKG.service.IslandAccessibilityService"

build()   { ./gradlew :app:assembleDebug --console=plain; }
install() { adb install -r "$APK"; }
launch()  { adb shell am start -n "$ACTIVITY" >/dev/null && echo "Launched $ACTIVITY"; }

# Installing an updated APK does NOT reload an already-bound AccessibilityService — the old
# code keeps running until it's toggled off/on. So after each install, if our service is
# enabled, disable+re-enable it to force onServiceConnected() to run the fresh code.
restart_service() {
  local cur stripped
  cur=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
  case "$cur" in
    *IslandAccessibilityService*) : ;;                 # enabled → restart it
    *) echo "Island a11y service not enabled; skipping restart."; return ;;
  esac
  stripped=$(printf '%s' "$cur" | tr ':' '\n' | grep -v 'IslandAccessibilityService' | paste -sd: -)
  adb shell settings put secure enabled_accessibility_services "$stripped"
  sleep 1
  if [ -n "$stripped" ] && [ "$stripped" != "null" ]; then
    adb shell settings put secure enabled_accessibility_services "$stripped:$SVC"
  else
    adb shell settings put secure enabled_accessibility_services "$SVC"
  fi
  adb shell settings put secure accessibility_enabled 1
  echo "Reloaded Island accessibility service (fresh code)."
}

case "${1:-all}" in
  build)   build ;;
  install) build && install && restart_service ;;
  restart) restart_service ;;
  logs)    adb logcat -s IslandTouch:D ;;
  all|"")  build && install && restart_service && launch ;;
  *) echo "Unknown command: $1"; exit 1 ;;
esac
