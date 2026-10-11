# 11. Attachments are copied into the data folder

Date: 2026-10-11

## Status

Accepted

## Context

Maintenance reports (PDF) and IGC files are attached to records. Referencing original paths breaks when files move and does not carry to other storage backends.

## Decision

Attached files are copied into the app's data folder and referenced from aggregates by an attachment id, never by the original path. A storage port hides where bytes live, so cloud storage can replace the folder. Imported IGC files are stored unmodified.

## Consequences

The data folder is self-contained, easy to back up and move. Files are duplicated on disk. Deleting a record must also delete or orphan-clean its attachments.
- 2026-10-11: A check can have several attached files. Attachments are stored through the `AttachmentStore` port and referenced by id. A file is stored before the aggregate is saved and removed again if saving fails; files removed from a check are deleted only after the aggregate was saved.
- 2026-10-11: A flight imported from a track keeps the original IGC file as an attachment with a checksum of its content. Deleting the flight deletes the file.

