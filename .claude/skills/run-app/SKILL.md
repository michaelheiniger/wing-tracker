---
name: run-app
description: Launch the wing-tracker desktop app (dev mode, sample data) on a private X display, drive it with clicks and typing, and take screenshots, to see a UI change working in the real app. Use when asked to run, start, try or screenshot the app, or to check a screen after a change.
---

# Run and drive the desktop app

The app is a Compose Desktop window. It runs on a private Xvfb display so it never opens on the user's screen. `java.awt.Robot` sends the clicks and keys, and PIL takes the screenshots. Everything below has been tried on this machine; follow it as written.

Scripts are in `.claude/skills/run-app/scripts/`. Keep the logs, pids and screenshots in a run folder inside the session scratchpad, e.g. `<scratchpad>/run`.

## 1. Sample data (optional)

```bash
./gradlew seedDevData -q
```

This resets the dev data folder and prints where it is. The dev build reads only that folder, never the user's real data. If you need data the seeder does not make (e.g. a flight with names from several sources), edit a JSON file in that folder *before* starting the app, since the app reads files only at start. `seedDevData` undoes such edits.

## 2. Start

```bash
.claude/skills/run-app/scripts/start.sh "$RUN"
```

It prints `ready on :<display>` once the window "Wing Tracker (dev)" exists. The first start compiles and can take a minute or more. The display defaults to the one in `start.sh`; set `WT_DISPLAY` if that one is taken. The window opens at the top left of the screen, and screen coordinates are what `Drive.java` takes.

What the script handles, and why:
- `WAYLAND_DISPLAY` is emptied and `DISPLAY` points at Xvfb. Otherwise the window would open on the user's desktop.
- `SKIKO_RENDER_API=SOFTWARE`: Xvfb has no GPU.
- Gradle passes the environment through to the app JVM, so `./gradlew :app-desktop:run` is enough. No separate java command is needed.

## 3. Look

```bash
python3 -I .claude/skills/run-app/scripts/shot.py "$(cat "$RUN/display")" "$RUN/1.png"                    # whole screen
python3 -I .claude/skills/run-app/scripts/shot.py "$(cat "$RUN/display")" "$RUN/2.png" 780 470 1130 870   # crop: left top right bottom
```

Then **read the PNG** to look at it. Do not use `Robot.createScreenCapture`: on Xvfb it gives an all-black image even when the app renders fine. A cropped shot is cheaper to read when only one card or dialog matters.

## 4. Drive

```bash
DISPLAY=$(cat "$RUN/display") java .claude/skills/run-app/scripts/Drive.java click 450 533 wait 300 type beatenberg wait 1200
```

Commands, chained in one call: `click X Y`, `type TEXT`, `key NAME` (a `KeyEvent` name without `VK_`, e.g. `ESCAPE`, `ENTER`, `LEFT`), `clear` (select all and delete in the focused field), `wait MS`. Take coordinates from the last full screenshot. After each action, wait and take a screenshot before the next decision; screens animate in.

Useful paths in the app:
- Logbook search field: click it, `type` words, and the list filters as you type. Click a flight row to open its page.
- Flight page: "Edit flight" opens the form dialog; `key ESCAPE` goes back to the logbook; `key LEFT` / `key RIGHT` step through flights.
- Flights with a track show a map; tiles load from the network, so wait a few seconds before the shot.

## 5. Stop

```bash
.claude/skills/run-app/scripts/stop.sh "$RUN"
```

It stops the app, Gradle and Xvfb by the pids `start.sh` saved. Never `pkill -f` with a pattern: the pattern also matches the shell running it and kills your own command. If you check processes by hand, check their `DISPLAY` in `/proc/<pid>/environ`, so you never touch an app the user has open.

## Troubleshooting

- **All-black screenshot:** the shot was taken with Robot, not `shot.py`, or the app had not drawn its first frame yet. Wait and shoot again with `shot.py`.
- **`start.sh` times out:** read `$RUN/app.log`. A compile error shows there, and `./gradlew check` gives the details.
- **Display already in use:** another run is still going. Stop it with its own run folder, or set `WT_DISPLAY`.
