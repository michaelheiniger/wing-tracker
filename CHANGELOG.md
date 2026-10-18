# Changelog

All user-facing changes are listed here, newest first.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) (ADR 0009).

## [Unreleased]

### Added
- Back up everything in one file: "Back up now…" in the settings' new "Backup" section saves your flights, gear, settings and attached files as a zip where you choose, ideally on another drive. The section tells when the last backup was made, and after a backup where it went, how many files it holds and its size. A backup cannot be saved inside the data folder.
- Restore a backup from the settings' "Backup" section: "Restore a backup…" checks the file first and shows when and with which version it was made, how many files it holds and its size. A backup that is incomplete or was changed is refused. It is then unpacked into an empty folder you choose, which becomes the data folder after a restart; your current data stays untouched in its folder.

## [0.4.0] - 2026-10-18

### Added
- The statistics' "Totals" card tells how much of it was hike and fly, overall and per year in two more columns of its table, once your logbook has flights with a type.
- Search the logbook by flight type: "From a site" and "Hike & fly" chips sit next to the wings, once your logbook has flights with a type. Flights of unknown type are left out while a type is chosen. The statistics banner names the chosen types.
- Importing from Syride again offers to complete flights imported earlier: "Complete N flights" gives them Syride's flight type and the id of their take-off site. Only what is missing is filled in; a type you set and site names you typed stay as they are.
- Flights imported from Syride remember Syride's own id of their take-off site. Their page has a "Site on Syride" button under the take-off site, which opens the site's page on Syride in your browser.
- Flights have a type: "From a site" or "Hike & fly", or unknown. Set it in the flight form, see it in the flight's "Flight" card, and spot hike-and-fly flights in the logbook by their "Hike & fly" badge. Flights imported from Syride get the type Syride gives them.
- A flight with a track shows its take-off and landing position in the "Sites" card, read from the track each time the page opens and marked "IGC track". The positions are in decimal degrees, latitude first, so they can be copied into a map site.

### Changed
- Take-off and landing sites remember where their name came from: typed by you, Syride or a spreadsheet. A flight's page shows the name with its source, and below it what other sources call the site. When editing a flight, the site fields hold only your own name; an imported name stays as a hint below the field and is shown in the logbook while your field is empty. Searching the logbook finds a flight by any of its site names. Sites stored before are counted as Syride's for the take-off site of flights imported from Syride, and as typed by you otherwise.

## [0.3.0] - 2026-10-17

### Added
- A flight's page has an "air-time" card telling whether and when the flight was sent to air-time. A flight sent there has an "Open on air-time" button that opens its page on air-time. "Forget the export" asks first, then removes only the record in your logbook, nothing on air-time, so the next export sends the flight again unless air-time still has it; Undo puts the record back. The card sits below the notes.
- "Export to air-time" in the logbook sends your flights to your air-time account. Paste an API token generated in air-time's settings once; it is kept by your system's password store (on Linux, install libsecret-tools, otherwise it is kept until Wing Tracker closes), and your air-time nick is remembered. A preview lists the flights to send, with a box to untick each one: flights with a track are sent as their IGC file, the others as a manual entry from their take-off time and duration. Wings that have no glider of the same brand, model and size on air-time get one, after you give its colour. Flights sent before, flights already on air-time starting in the same minute, and flights with neither a track nor a take-off time are left out, with a count of each.
- The altitude profile of a flight shows the ground under the track, from an open terrain model. Pointing at the profile also gives the ground altitude and your height above ground. The ground is kept on your computer like the map, so flights viewed before show it offline too; otherwise a note says it could not be loaded.
- All text in Wing Tracker can be selected and copied, including values such as dates, sites and altitudes, in the main window and in dialogs. Drag across text to select it and press Ctrl+C to copy it. Axis labels drawn in charts are the exception; their values show when you point at the chart.
- A flight imported from Syride with a track there has an "Open on Syride" button on its page, which opens the flight on Syride in your browser. It uses the Syride account saved for the import.
- The map of a flight colours the track by climb and sink: light blue for sinking down to 1.2 m/s, dark blue for stronger sink, light green from 0 up to 0.5 m/s, green for weak climb, orange and red for stronger climb, with a legend giving the m/s of each colour. Flights whose track has no altitudes are drawn in a single colour.
- Below the map, an altitude profile shows the altitude over time, the flight in the colour of the track on the map and the time before take-off and after landing in grey. Pointing at it shows the time and altitude, and a dot marks the same moment on the map.
- A flight with a track shows it on a topographic map (OpenTopoMap) next to its data: the flight from take-off to landing in a strong colour, what the logger recorded before and after it faded, and markers at take-off and landing. Drag to move, scroll or double-click to zoom, and "Whole flight" frames it again. Map tiles are kept on your computer, so flights viewed before also show their map offline; otherwise the track is drawn on a plain background with a note that the map is not available.
- A flight with a track shows a "From the track" card on its page: take-off and landing time to the second, take-off, landing and highest altitude, and the best climb and strongest sink, each averaged over a short time like a vario does. Altitudes are GPS altitudes, or barometric where the logger had no GPS altitude. Distance and top altitude stored from Syride stay as they are, next to it.
- Click a flight in the logbook to open its own page listing everything known about it: date, take-off time, duration, wing, sites, distance, top altitude, instrument, where it was imported from, its track file and when it was added, with fields not recorded shown as such. Edit, delete or open the track from there. "Earlier flight" and "Later flight" (or the arrow keys) step through the flights of the current search, and "Back to logbook" (or Esc) returns to the list where you left it.
- Search the logbook: type words found in the take-off or landing site, notes, instrument or wing name (upper/lower case and accents don't matter), pick one or more wings, and limit it to a date range (a "To" day before the "From" day is flagged). The months and a result line count only the matching flights; the hours per wing stay the totals. The search stays while you look at other sections, until Wing Tracker is closed.
- Statistics for a logbook search: the "Statistics" button next to the search result opens the charts and totals for the matching flights only, with a banner naming the search and a "Show all" button. Hours and flights from before tracking and the "since last check" figures are left out there, since a search cannot match them. Statistics opened from the navigation bar always show every flight.
- Flights can have a take-off time, entered next to the date and shown beside it in the logbook. Flights of one day are listed by take-off time; flights without one come after them. A flight imported from an IGC file gets the take-off time found in the track, in your computer's time zone.
- Flights imported from Syride get Syride's take-off time. For flights imported earlier, importing from Syride again lists those whose time is missing or different, with a few examples, and updates them when you click "Update take-off times". This prepares linking tracks imported later to these flights.
- Importing IGC files or tracks from a device now recognises flights already in the logbook, e.g. imported from Syride: a track named after a Syride flight id, or starting in the minute of a flight's take-off, is attached to that flight instead of creating a second one. Each track shows the flight it belongs to with an ⓘ explaining why, and an "Attach | New flight" choice; flights with only the same day and a similar duration are offered as possible matches but not attached unless you choose so. A summary above the list counts the tracks by match and shows only those you click. Tracks that match nothing on a day that already has flights start unticked, since they are usually recordings that were not flights.
- After importing from Syride, the import goes on to a "Tracks" step when Syride flights in your logbook have a track on Syride that is not stored yet: choose the IGC files downloaded from Syride or read them from your logger, and they are attached to these flights. When Syride has no new flights, the same offer appears right away under "There are no new flights to import", with a warning if take-off times listed above are still to update, since tracks match these flights by their time. "Back" in the track review returns to this offer, and "Not now" closes the import.
- The track review has a "How linking works" section explaining how each track is matched to a flight, and why a flight recorded in another time zone only shows as a possible match.
- The version of Wing Tracker is shown at the bottom of the navigation bar; clicking it opens what changed in that version on GitHub.

### Changed
- The flight cards in the logbook no longer have edit and delete buttons: click a flight to open its page, which has both.
- Tracks read from a device keep the name of the folder they were in, e.g. "20260926/114344.IGC" for a Syride logger, which keeps the date in the folder name. Hidden folders on the device, such as the trash of a USB stick, are no longer read, so deleted tracks do not come back.
- A track is only attached to a Syride flight by the number in its file name when the flight is on the same day, so a logger's file named after a time cannot be attached to an old Syride flight that has that number.
- The Import button opens a step-by-step window instead of a menu. It starts with the four sources as cards, recommends starting with Syride and explains why, and shows the steps of the chosen source above. "Back" returns to the sources until something is saved, and cancelling asks first when choices you made would be lost; a click beside the window no longer closes it. Tracks dropped on the logbook open the same window at the review, and importing tracks ends with a summary of how many were attached to flights, logged as new flights or left out. The import button of the track review counts tracks instead of flights.
- A flight imported from a track is dated on your local day of the take-off, not on the UTC date in the file, so a flight shortly after midnight lands on the right day.
- When importing IGC files, the take-off and landing found in a track are shown in your computer's local time instead of UTC, matching the times in the logbook.
- Without an internet connection, the Syride import and the update check and download now say so and suggest checking the connection, instead of showing an empty technical message.
- After a connection problem, the button to read Syride flights or check for updates reads "Try again", and a failed update download offers "Try again" instead of sending you to the release page.
- Reading flights from Syride and checking for updates can be cancelled while they wait, e.g. on a connection that hangs. Cancelling closes the connection right away instead of leaving it open until it times out.
- An update download that stops receiving data no longer leaves the progress bar standing still: after a while it stops with a message and offers "Try again".
- On Windows, tracks in a USB stick's trash left by Linux or macOS (a folder whose name starts with a dot) are no longer read from the device.
- After an update installs on Linux, the log no longer lists the other software on your computer that the package manager mentioned; that output is only kept when the install fails.

### Removed
- The Syride import no longer offers to correct flights it imported earlier with missing accents or cut-off names.

## [0.2.0] - 2026-10-17

### Added
- Wing Tracker is available for Windows: each release has an installer (.msi) that installs for your user only, without administrator rights. It is not signed yet, so Windows warns about an unknown publisher on the first install: choose "More info", then "Run anyway". Updates install from the app: Wing Tracker closes, installs the new version and opens again.
- Wing Tracker tells you when a new version is out and installs it for you: a banner at the top of the window shows what changed in every version since yours. "Install" downloads the new version, checks that it is exactly the published one, installs it after your system asks for your password, and offers to restart. Versions published before this one cannot be installed this way, so for those the banner links to their download page. "Later" hides the banner until the next start; "Skip this version" stops reminding you of that version, and the next one is announced again. While an update is waiting, Settings and About show a badge.
- A new "Updates" section in the settings checks for a new version on request and lets you turn off the check at startup. The check only asks GitHub, where Wing Tracker is published, which versions exist; nothing about you or your logbook is sent.
- Each release lists the SHA-256 checksums of its installers in a `SHA256SUMS` file, so you can check that a download is complete and unaltered (e.g. `sha256sum --check --ignore-missing SHA256SUMS`).

## [0.1.0] - 2026-10-15

### Changed
- The logbook's import buttons are now one "Import" menu: IGC files, from a device, from Syride. Imports that need a wing say so instead of disappearing.
- Imports also suggest an existing wing when the source names it by its model or by manufacturer and model (e.g. "Ozone Ultralite 4").

### Added
- The app keeps a log of what it does and of unexpected errors, so a bug report can include what happened. It is kept in your system's usual folder for logs, and old logs are removed automatically.
- The settings show where the log is kept, with a button to open its folder, so you can attach it to a bug report.
- The unexpected-error window says that the details were saved in the log and opens the log folder.
- Dates can be typed as well as picked from the calendar: day-month-year, with dashes, dots or slashes (e.g. 5.3.2026), shown as 05-03-2026 once you leave the field. A day that does not exist is flagged instead of saved.
- The statistics start with a "Totals" card: your flights and hours over all years, every wing together, then year by year. Flights and hours entered as "before tracking" count in the overall total, and the card says how many. In the table view, the yearly tables end with an "All years" row, and their last column ("All wings") always counts every wing, even hidden ones.
- Import flights from a spreadsheet (ODS or XLSX), e.g. a digitized paper logbook: choose the file under Import, check which column holds the date, wing, duration (whole hours, whole minutes or both, added up), sites, distance and notes, with the first flights shown as you choose, then pick or create the wing for each wing named in the file. Rows that cannot be read are listed with the reason, and rows already in the logbook are left out, so importing a longer version of the same file adds only the new flights.
- The statistics charts show flights per year and cumulative flights; an "Hours / Flights" switch at the top changes both charts, and the table view shows both. A new section lists for each active wing the flights and hours since its last check.
- Wings have an optional "flights before tracking" count, next to the hours flown before tracking, for used wings.
- An "About" button at the bottom of the settings shows the version of the app and links to the changelog.

### Fixed
- Opening your data with an older version of the app no longer moves gear it cannot read yet to the quarantine folder: it is left in place, and the newer version reads it again. If gear went missing this way, move its files from the quarantine folder back into the data folder.
- The Syride import works again in the installed app; it failed with an unexpected error because a part of Java it needs was not packaged.
- The Syride import keeps accents (e.g. "Pléiades" instead of "Pl&eacute;iades") and the part of flight names and sites in parentheses (e.g. "Sonchaux (déco nord)"), which it used to cut off. When you read your Syride account again, the import offers to correct the flights it imported before, showing a few examples first; names and sites you changed yourself are kept.
- An unexpected error no longer closes the app or goes unnoticed: a window shows what went wrong, with the technical details to copy into a bug report. If the error broke the main window, it is rebuilt when you click OK. This also covers failing to save the theme or interface size, to show the reminder at startup, or to follow the desktop's light or dark mode.
- On a system whose configured language (locale) is not installed, choosing, dropping or saving a file whose name or folder contains accents (e.g. "matériel") failed with an unclear error. The app now says which file it cannot open and how to fix the system's language settings. It also warns at startup when the system is in this state, and opens stored reports with such names anyway.

## [0.0.3] - 2026-10-12

### Changed
- Wings created by the Syride import no longer get an in-service date from their first flight; it stays empty until you set it.
- "In service since" is optional for wings, harnesses and reserves. Without it, a maintenance interval counts from the last check, else from the purchase date, and says so when it has no start. An info icon next to the date explains what it is used for.
- Adding a wing asks for manufacturer and model first, and fills in the name from them until you type a name yourself.
- A flight can now be dated before its wing's in-service date, in the flight form and in both imports. The flight form and the IGC import show a warning instead of an error, and the statistics chart starts at the earliest flight.

### Added
- Import from a device: choose a logger mounted as a drive (e.g. a Syride) in the logbook, and all its IGC tracks are read, including those in subfolders. Tracks already imported are left out of the list. The folder chooser opens where you found the device last time.
- The flight form has fields for distance, top altitude and instrument, so they can be added or corrected on any flight.

## [0.0.2] - 2026-10-12

### Added
- Import your flights from Syride: enter your Syride pilot name in the logbook, and the flights are read from your public profile. For each wing used on Syride you pick the matching wing in the app (suggested when the name matches) or create it on the spot, then import. Flights keep their site, flight name, distance, top altitude and instrument, and running the import again skips what was already imported. Tracks are not downloaded.
- Flights can carry a distance, top altitude and instrument, shown in the logbook.

## [0.0.1] - 2026-10-11

### Fixed
- File, folder and save dialogs now look like the rest of your desktop (they use the system's own chooser, including on KDE Plasma).
- The dark appearance of KDE Plasma (and other desktops that report it) is now detected, and followed while the app runs.
- The interface is no longer too small on screens with display scaling: it follows the desktop's scale automatically, and Settings has an Interface size choice.
- French text no longer shows stray backslashes before apostrophes.

### Added
- Settings shows the data folder, can open it in the file manager, and can change it: the current data can be moved to the new folder (or an existing folder, such as a restored backup, can be used as it is), and the app restarts to use it. A button goes back to the default folder.
- Undo: after deleting a flight, wing, harness, reserve or check (still confirmed first), or cancelling a planned check, a notification offers Undo for a few seconds; the deletion is only applied once it goes away or the app closes. Closing a new-flight dialog with typed-in values also offers Undo, which reopens it as you left it.
- A new look: colours taken from the application icon in light and dark, status badges with icons for due, due soon and in service, an orange primary action, and cleaner cards.
- Import flights from IGC files: the date and the flight duration are read from the track (take-off and landing are found from the movement), you pick the wing, the original file is kept with the flight, and a file already imported is left unticked.
- Import several IGC tracks at once: drop files on the logbook (or choose several), review them in one list with a wing for all tracks and a wing per track, and import the ticked ones; tracks that cannot be imported say why and the others still go through.
- Reminders: an attention panel above the logbook and a count badge on Gear show what is due, due soon or planned soon; a notification at start (once per day); lead times in Settings; and an export of planned checks and due dates as a calendar file.
- Harnesses and reserve parachutes: register, edit, retire and delete them, with a check interval in months, checks (harness) and repacks (reserve), planned checks and reports, in new Harnesses and Reserves tabs.
- Maintenance: set a check interval (months and/or flight hours) per wing, record partial, full or other checks with company, notes and PDF reports, plan future checks, and see when a check is due.
- Statistics: hours per year and cumulative hours per wing as charts, with wings you can show or hide and a table view.
- Logbook: log, edit and delete flights on a wing, grouped by month with monthly totals and hours per wing.
- Register, edit and retire wings, including size, serial number, certification class, purchase details and hours flown before tracking.
- Light, dark or system theme, remembered between launches.
- Dates are chosen with a calendar picker and shown as DD-MM-YYYY; the price currency is pre-filled.
- Delete a wing that was entered by mistake. A wing with flights or maintenance history can only be retired.
- The application is available in English, German and French.
- A damaged data file no longer blocks the application: the file is set aside in a quarantine folder and a warning is shown.
