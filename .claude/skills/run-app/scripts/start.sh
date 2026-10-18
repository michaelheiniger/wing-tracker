#!/usr/bin/env bash
# Starts the dev build on a private X display, so the window never opens on the user's own screen.
# Usage: start.sh <run folder>   (pids and logs go there; WT_DISPLAY overrides the display number)
set -euo pipefail
run=${1:?run folder}
display=${WT_DISPLAY:-:97}
mkdir -p "$run"
echo "$display" >"$run/display"

Xvfb "$display" -screen 0 1600x1000x24 >"$run/xvfb.log" 2>&1 &
echo $! >"$run/xvfb.pid"
sleep 1

cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
# An empty WAYLAND_DISPLAY keeps AWT on X11; software rendering avoids a GPU that Xvfb does not have.
DISPLAY=$display WAYLAND_DISPLAY='' SKIKO_RENDER_API=SOFTWARE \
    nohup ./gradlew :app-desktop:run -Pwingtracker.dev=true >"$run/app.log" 2>&1 &
echo $! >"$run/gradle.pid"

# The first start also compiles, so allow a few minutes.
for _ in $(seq 1 300); do
    if DISPLAY=$display xwininfo -root -tree 2>/dev/null | grep -q '"Wing Tracker (dev)"'; then
        sleep 3 # first frame
        echo "ready on $display"
        exit 0
    fi
    sleep 1
done
echo "the window did not appear; see $run/app.log" >&2
exit 1
