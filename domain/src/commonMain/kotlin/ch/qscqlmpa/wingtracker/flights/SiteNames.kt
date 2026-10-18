package ch.qscqlmpa.wingtracker.flights

/** Where a site's name came from, in order of precedence: the first source with a name is shown (ADR 0030). */
enum class SiteSource { Manual, Syride, Spreadsheet }

/**
 * A take-off or landing site's name as each source gave it. Every source keeps its own name, so editing a flight
 * never loses what an import brought, and the pilot's own name always wins (ADR 0030).
 */
data class SiteNames(
    val bySource: Map<SiteSource, String> = emptyMap(),
    // A source's own id for the site, e.g. the name of its page on Syride; it belongs to that source's name.
    val references: Map<SiteSource, String> = emptyMap(),
) {
    init {
        require(bySource.values.none { it.isBlank() }) { "A site name cannot be blank" }
        require(references.values.none { it.isBlank() }) { "A site reference cannot be blank" }
        require(bySource.keys.containsAll(references.keys)) { "A site reference needs its source's name" }
    }

    /** The source whose name the logbook shows, searches and exports. */
    val shownSource: SiteSource? get() = SiteSource.entries.firstOrNull { it in bySource }

    val shown: String? get() = shownSource?.let(bySource::getValue)

    operator fun get(source: SiteSource): String? = bySource[source]

    fun reference(source: SiteSource): String? = references[source]

    /**
     * Sets the name [source] gave with its own id for the site, replacing both; a blank [name] removes them. A blank
     * [reference] means the source gave none.
     */
    fun with(
        source: SiteSource,
        name: String?,
        reference: String? = null,
    ): SiteNames {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return SiteNames(bySource - source, references - source)
        val id = reference?.trim()?.ifEmpty { null }
        return SiteNames(
            bySource + (source to trimmed),
            if (id == null) references - source else references + (source to id),
        )
    }

    companion object {
        val NONE = SiteNames()

        fun of(
            source: SiteSource,
            name: String?,
            reference: String? = null,
        ) = NONE.with(source, name, reference)
    }
}
