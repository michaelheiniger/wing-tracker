package ch.qscqlmpa.wingtracker.storage.json

/** The file is intact but newer than this application understands. */
internal class UnsupportedSchemaException(
    message: String,
) : IllegalStateException(message)
