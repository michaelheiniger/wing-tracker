package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.shared.LoadResult

class ListWings(
    private val wings: WingRepository,
) {
    /** Active wings come first, then retired ones; each group sorted for a stable, scannable list. */
    suspend operator fun invoke(): LoadResult<Wing> {
        val loaded = wings.findAll()
        val sorted =
            loaded.items.sortedWith(
                compareBy<Wing> { !it.isActive }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.details.name },
            )
        return loaded.copy(items = sorted)
    }
}
