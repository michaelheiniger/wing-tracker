package ch.qscqlmpa.wingtracker.sync

/** Groups the writes of one change to the logbook, so synchronising records them as one step (ADR 0033). */
interface LogbookTransactions {
    /**
     * Runs [block] as the transaction of [change]; inside another transaction, it joins that one. Nothing is rolled
     * back: what [block] wrote before failing is still recorded, marked as interrupted.
     */
    suspend fun <T> run(
        change: LogbookChange,
        block: suspend () -> T,
    ): T
}

/** Until the logbook is synchronised there is nothing to record, so a transaction only runs its block. */
object UnsyncedTransactions : LogbookTransactions {
    override suspend fun <T> run(
        change: LogbookChange,
        block: suspend () -> T,
    ): T = block()
}
