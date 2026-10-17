# Changelog

All user-facing changes are listed here, newest first.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) (ADR 0009).

## [Unreleased]

### Added
- Search the logbook: type words found in the take-off or landing site, notes, instrument or wing name (upper/lower case and accents don't matter), pick one or more wings, and limit it to a date range (a "To" day before the "From" day is flagged). The months and a result line count only the matching flights; the hours per wing stay the totals. The search stays while you look at other sections, until Wing Tracker is closed.
- Statistics for a logbook search: the "Statistics" button next to the search result opens the charts and totals for the matching flights only, with a banner naming the search and a "Show all" button. Hours and flights from before tracking and the "since last check" figures are left out there, since a search cannot match them. Statistics opened from the navigation bar always show every flight.
- The version of Wing Tracker is shown at the bottom of the navigation bar; clicking it opens what changed in that version on GitHub.

### Changed
- Without an internet connection, the Syride import and the update check and download now say so and suggest checking the connection, instead of showing an empty technical message.
- After a connection problem, the button to read Syride flights or check for updates reads "Try again", and a failed update download offers "Try again" instead of sending you to the release page.
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
