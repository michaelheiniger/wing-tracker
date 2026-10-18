# 32. Backups are uploaded to S3-compatible storage through a port, signed by the application

Date: 2026-10-18

## Status

Accepted

## Context

A backup on the same computer or a drive next to it does not survive a fire or a theft (ADR 0031). Pilots asked to upload backups to MEGA S4, an object storage that speaks the S3 protocol: Signature Version 4 only, path-style and virtual-hosted addresses, and a subset of S3 without the `x-amz-checksum-*` headers, server-side encryption, versioning or object lock. Other providers (Backblaze B2, Wasabi, AWS S3) speak the same protocol. MEGA's endpoints follow a template with a region code that MEGA has renamed before. Alternatives considered: the AWS SDK for Java (a large dependency whose default checksum headers S4 rejects) and the MinIO client (a second HTTP stack next to the JDK's).

## Decision

- **Port.** The application layer defines a `CloudStorage` port in the `backup` package, with the calls a backup needs: check access, upload a file, list, download and delete objects. Use cases upload a backup, keep the newest ones, list them and download one for the restore of ADR 0031. Failures are typed (no connection, keys refused, no such bucket, access denied, clock off, unexpected) so only composables turn them into text (ADR 0014).
- **Adapter.** A new JVM-only module, `infrastructure-s3`, implements the port with the JDK HTTP client on top of `infrastructure-http`, signing every request with Signature Version 4 itself. It uses path-style addresses and sends only the headers every S3-compatible service accepts. The signing is tested against the examples AWS publishes.
- **Settings.** The pilot chooses MEGA S4, whose endpoint is built from the region code shown in their MEGA account, or another S3-compatible service by its endpoint. Endpoint, region, bucket, access key id and how many backups to keep live in the settings; only HTTPS endpoints are accepted. The secret key is kept by the OS through the `CredentialStore` port, like the air-time token (ADR 0029).
- **Flow.** Per backup, the pilot chooses to save a file or to upload. An upload builds the same archive (ADR 0031) in a temporary file, uploads it under a dated name below an application prefix, and deletes the local copy. Either counts as the last backup. After a successful upload only, the oldest backups under the prefix beyond the number to keep are deleted.
- **No encryption.** Backups are uploaded as they are; protecting them rests on the keys and the provider.

## Consequences

Backups can leave the building without new dependencies, and any S3-compatible provider works. The signing and the XML answers are ours to maintain, and a provider's quirk shows only against the real service, so a connection test is offered before the first upload. Anyone with the bucket's keys can read the logbook. Deleting old backups needs the keys to allow it; a provider that refuses leaves them in place and says so.
