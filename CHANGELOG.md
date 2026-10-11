# Changelog

All user-facing changes are listed here, newest first.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) (ADR 0009).

## [Unreleased]

### Fixed
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
