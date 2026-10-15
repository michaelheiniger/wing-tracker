# 23. Logging through a facade to rotating files in the platform log folder

Date: 2026-10-15

## Status

Accepted

## Context

Unexpected errors were only printed to the console, which a pilot running the packaged app never sees, so a bug report rarely came with what happened. Development needs more detail than the packaged app should keep. Logs must not grow without bound, and must not end up in the data folder, which pilots synchronise or back up (ADR 0020). The shared layers will run on other targets later (ADR 0002), and the domain stays free of frameworks (ADR 0004). Writing our own logger with a port, or using a multiplatform logger without file rotation, were considered; both mean maintaining code a mature library already provides.

## Decision

Code logs through the multiplatform `kotlin-logging` facade. The backend, Logback, is a runtime dependency of the composition root only and is configured in `app-desktop/src/jvmMain/resources/logback.xml`: a file that rolls over by date and when it grows too big, with a bounded history and total size, plus the console. The configuration asks `DesktopLogging` for the folder and level itself, so they are right however early the first logger is created. Development runs log in more detail than the packaged app (`DesktopLogging.level`). The folder (`LogDirectory`) is the per-user log or state location of each OS, separate for development runs, unless the system property named by `DesktopLogging.DIRECTORY_PROPERTY` gives another one. The domain does not log; Konsist rules forbid it, and forbid every module but the composition root from using the backend or SLF4J directly. Log messages are for developers and are not translated (ADR 0014).

## Consequences

Pilots can attach a log to a bug report, and the backend can be replaced in one place. The rotation limits live in `logback.xml` and are changed there. Logs can contain file and folder paths and other details of a pilot's setup, which they should know before sending one. Another target will need its own backend and log location.
