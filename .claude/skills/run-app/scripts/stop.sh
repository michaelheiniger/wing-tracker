#!/usr/bin/env bash
# Stops what start.sh started, by the pids it saved. Never pkill -f by pattern: it also matches the calling shell.
run=${1:?run folder}
for name in gradle xvfb; do
    [ -f "$run/$name.pid" ] && kill "$(cat "$run/$name.pid")" 2>/dev/null
done
# Cancelling the Gradle client stops the run task, but give the app a moment and make sure it is gone.
sleep 3
for pid in $(pgrep -f 'compose.application.resources.dir=.*app-desktop'); do
    tr '\0' '\n' <"/proc/$pid/environ" 2>/dev/null | grep -qx "DISPLAY=$(cat "$run/display")" && kill "$pid"
done
exit 0
