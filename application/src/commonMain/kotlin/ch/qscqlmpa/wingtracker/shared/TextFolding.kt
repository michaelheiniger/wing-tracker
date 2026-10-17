package ch.qscqlmpa.wingtracker.shared

/**
 * Lower case without accents, so that what a pilot types matches however the text was entered or imported
 * (e.g. "chateau" finds "Château-d'Oex").
 */
fun String.folded(): String = lowercase().map { ACCENTS[it] ?: it }.joinToString("")

// Common code has no Unicode normalizer; these are the letters of the languages the pilots write in.
private val ACCENTS =
    mapOf(
        'á' to 'a',
        'à' to 'a',
        'â' to 'a',
        'ä' to 'a',
        'é' to 'e',
        'è' to 'e',
        'ê' to 'e',
        'ë' to 'e',
        'í' to 'i',
        'î' to 'i',
        'ï' to 'i',
        'ó' to 'o',
        'ô' to 'o',
        'ö' to 'o',
        'ú' to 'u',
        'ù' to 'u',
        'û' to 'u',
        'ü' to 'u',
        'ç' to 'c',
        'ñ' to 'n',
    )
