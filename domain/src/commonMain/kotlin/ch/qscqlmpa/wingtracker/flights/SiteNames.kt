package ch.qscqlmpa.wingtracker.flights

/** Where a site's name came from, in order of precedence: the first source with a name is shown (ADR 0030). */
enum class SiteSource { Manual, Syride, Spreadsheet }

/**
 * A take-off or landing site's name as each source gave it. Every source keeps its own name, so editing a flight
 * never loses what an import brought, and the pilot's own name always wins (ADR 0030).
 */
data class SiteNames(
    val bySource: Map<SiteSource, String> = emptyMap(),
) {
    init {
        require(bySource.values.none { it.isBlank() }) { "A site name cannot be blank" }
    }

    /** The source whose name the logbook shows, searches and exports. */
    val shownSource: SiteSource? get() = SiteSource.entries.firstOrNull { it in bySource }

    val shown: String? get() = shownSource?.let(bySource::getValue)

    operator fun get(source: SiteSource): String? = bySource[source]

    /** Sets the name [source] gave, or removes it when [name] is blank. */
    fun with(
        source: SiteSource,
        name: String?,
    ): SiteNames {
        val trimmed = name?.trim().orEmpty()
        return SiteNames(if (trimmed.isEmpty()) bySource - source else bySource + (source to trimmed))
    }

    companion object {
        val NONE = SiteNames()

        fun of(
            source: SiteSource,
            name: String?,
        ) = NONE.with(source, name)
    }
}
