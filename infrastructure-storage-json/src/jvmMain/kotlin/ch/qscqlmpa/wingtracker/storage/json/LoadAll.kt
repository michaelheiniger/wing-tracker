package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord

/**
 * Decodes every stored document, setting aside the ones that cannot be read (ADR 0015) so a single
 * bad file never hides the rest.
 */
internal suspend fun <T> JsonDocumentStore.loadAll(decode: (String) -> T): LoadResult<T> {
    val items = mutableListOf<T>()
    val unreadable = mutableListOf<UnreadableRecord>()
    for (document in readAll()) {
        try {
            items += decode(document.text)
        } catch (e: UnsupportedSchemaException) {
            // Valid data from a newer app version: never move it, an upgrade will read it again.
            unreadable += UnreadableRecord(document.name, e.message.orEmpty(), quarantined = false)
        } catch (e: IllegalArgumentException) {
            quarantine(document.name)
            unreadable += UnreadableRecord(document.name, e.message.orEmpty(), quarantined = true)
        } catch (e: IllegalStateException) {
            quarantine(document.name)
            unreadable += UnreadableRecord(document.name, e.message.orEmpty(), quarantined = true)
        }
    }
    return LoadResult(items, unreadable)
}
