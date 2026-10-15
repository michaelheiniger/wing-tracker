# 22. Spreadsheet logbooks are imported through a port, matched by content

Date: 2026-10-15

## Status

Accepted

## Context

Many pilots kept a paper logbook or a spreadsheet before using the app. Typing years of flights again is not an option. Spreadsheets have no fixed layout: columns differ in order, language and unit (a duration in hours, in minutes, or split in two), and they carry no flight ids. Pilots use LibreOffice (ODS) or Excel (XLSX). Apache POI reads both formats but is large; ODS is plain zipped XML that the JDK reads alone.

## Decision

The application layer defines a `SpreadsheetReader` port that turns a file into typed cells (text, number, date, time). Everything else is in the application layer and shared with the other imports. Header names suggest a column mapping in English, French and German, and the pilot confirms it while seeing the flights it gives and the rows it cannot read. The pilot then picks a wing for each wing named in the file, through the same wing step as the Syride import (`flights/importing`). The port is implemented in its own JVM-only module, `infrastructure-spreadsheet`. It reads ODS with the JDK's streaming XML reader, with DTDs and external entities off, and XLSX with fastexcel-reader. The format is told from the file content, not the file name. Excel 97-2003 files (`.xls`) are not read, and the pilot is asked to save them in a newer format.

Since a spreadsheet has no ids, a row counts as already imported when the logbook has a flight on the same wing with the same date, sites and duration. Identical flights are matched by count, because the same flight is often flown several times a day. Flights imported from a spreadsheet carry no `ExternalReference`.

## Consequences

Any logbook layout can be imported without code changes, and importing a longer version of the same file adds only the new rows. A flight edited after import (other sites or duration) is no longer recognised and would be imported again, so the pilot sees the "already in the logbook" count before importing. Columns without a matching flight field (for example take-off altitude) are listed as not imported rather than kept. fastexcel-reader brings its own XML and compression libraries. They are confined to `infrastructure-spreadsheet`, like igc4j behind the IGC port (ADR 0018).
