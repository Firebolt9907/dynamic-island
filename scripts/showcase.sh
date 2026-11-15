#!/usr/bin/env bash
# Cycle the island through common live activities, for recording smoothness GIFs.
# Debug builds only. Usage:
#   scripts/showcase.sh start [--step MS] [--once] [--no-music] [--scenes a,b,c] [--install]
#   scripts/showcase.sh stop
# Scenes: agent sports nav transfer timer call ride stopwatch music multi
# Default: agent,sports,nav,transfer,timer,call,ride,multi
set -euo pipefail

PKG=com.island.dynamicisland
RECEIVER="$PKG/.ShowcaseDemoReceiver"
ADB=${ADB:-adb}

cmd=${1:-start}
shift || true
extras=()
install=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --step) extras+=(--ei stepMs "$2"); shift ;;
    --once) extras+=(--ez loop false) ;;
    --no-music) extras+=(--ez music false) ;;
    --scenes) extras+=(--es scenes "$2"); shift ;;
    --install) install=true ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

case "$cmd" in
  start)
    if $install; then
      "$(dirname "$0")/../gradlew" -p "$(dirname "$0")/.." :app:installDebug
    fi
    "$ADB" shell am broadcast -n "$RECEIVER" --es action start ${extras[@]+"${extras[@]}"} >/dev/null
    echo "Showcase running. Stop with: $0 stop"
    ;;
  stop)
    "$ADB" shell am broadcast -n "$RECEIVER" --es action stop >/dev/null
    echo "Showcase stopped."
    ;;
  *) echo "Usage: $0 start|stop [options]" >&2; exit 2 ;;
esac
