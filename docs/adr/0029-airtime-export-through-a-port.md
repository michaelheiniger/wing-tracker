# 29. Flights are exported to air-time through its API, with a token kept by the OS

Date: 2026-10-17

## Status

Accepted

## Context

Pilots also keep their flights on air-time, a paragliding logbook and flight-sharing site, and want to send them there instead of entering them twice. air-time publishes an OpenAPI description of the API its own web app uses: it lists and creates gliders, registers a flight from an IGC file or from a manual entry, and lists a pilot's flights publicly. Logging in goes through a hosted identity provider behind a bot check, so a desktop app cannot log in with a password without imitating a browser, which ADR 0021 already rejects for Syride. air-time offers a personal API token for exactly this use. Creating a glider or a flight answers with no id, and air-time may already hold a pilot's flights from its own Syride import. Alternatives considered: driving the site's "Add flight" form (fragile, needs the password), keeping the token in the settings (plain text, and synchronised with the data folder), and the java-keyring library (unmaintained).

## Decision

- **Port.** The application layer defines an `AirtimeAccount` port and the `PrepareAirtimeExport` and `ExportFlightsToAirtime` use cases in the `flights.airtime` package. The port is implemented with the JDK HTTP client in a new JVM-only module, `infrastructure-airtime`, on top of `infrastructure-http`. The site's address is configured in the composition root. Failures are typed (no connection, refused token, unexpected answer) so only composables turn them into text (ADR 0014).
- **Authentication.** The pilot generates a token on air-time and pastes it once; the app never asks for the air-time password. The pilot's air-time nick is remembered in the settings. The token is kept by the operating system through a `CredentialStore` platform port (ADR 0017): libsecret's `secret-tool` on Linux, the Credential Manager through JNA (version in the version catalog) on Windows. When no store is available, the token is asked for and kept for the session only; it is never written in plain text.
- **What is sent.** A flight with a stored track is sent as its original IGC file (ADR 0011), and air-time derives everything else from it. A flight without a track is sent as a manual entry: its start is the take-off date and time in the computer's time zone, its end the start plus the duration, its type air-time's default flight type, and its take-off site air-time's site of the same name (ignoring case) when there is one; no site is ever created. A flight without a track nor a take-off time is not exported, since air-time needs an exact start.
- **Wings.** Each flight carries the air-time glider of its wing, found by brand, model and size (ignoring case and surrounding spaces) every time; no link is stored. A wing without such a glider is created on air-time from the wing's name, brand, model and size; air-time requires a colour, which wing-tracker does not keep, so the pilot gives it in the preview.
- **No duplicates.** Each exported flight remembers locally when it was exported to air-time, so exporting again skips it. Before exporting, the pilot's public flights on air-time are read too, and a flight that already starts in the same minute there is skipped; that covers flights air-time imported itself. A flight's start for that comparison is its take-off time or, without one, the first fix of its stored track.
- **Show.** The export is started from the flights page. A preview lists the gliders that will be created (asking each one's colour), the flights sent with their track, those sent as manual entries, and those skipped with the reason, with a check box per flight. Nothing is sent before the pilot confirms.

## Consequences

Pilots publish their logbook on air-time in one step, and the token stays out of the data folder. The integration depends on an API that air-time does not document as public or stable; it is isolated behind a port and its reading is tested on answers saved from air-time. Renaming a glider's brand, model or size on air-time makes the next export create a new one. Flights exported once are never updated on air-time. The stored flight format gains the export marker (schema version 5), which older application versions treat as newer data and leave untouched. On Linux, `secret-tool` is not always installed and the package cannot declare it, so the settings explain how to install it when it is missing.
