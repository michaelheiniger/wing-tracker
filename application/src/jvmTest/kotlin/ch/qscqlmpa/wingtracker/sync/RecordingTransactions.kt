package ch.qscqlmpa.wingtracker.sync

/** Runs every transaction and remembers the change of each outermost one, like a synchronised logbook commits it. */
class RecordingTransactions : LogbookTransactions {
    val changes = mutableListOf<LogbookChange>()
    private var depth = 0

    override suspend fun <T> run(
        change: LogbookChange,
        block: suspend () -> T,
    ): T {
        // A nested transaction joins the outer one, so only the outer change is recorded.
        if (depth == 0) changes += change
        depth++
        try {
            return block()
        } finally {
            depth--
        }
    }
}
