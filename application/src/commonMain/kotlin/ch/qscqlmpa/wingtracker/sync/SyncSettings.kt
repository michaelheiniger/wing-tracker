package ch.qscqlmpa.wingtracker.sync

/** Where this device synchronises the logbook to and who its changes are by (ADR 0033); the OS keeps the token. */
data class SyncSettings(
    val repository: String,
    // Some hosts need a username next to the token, others ignore it.
    val username: String?,
    val authorName: String,
    val authorEmail: String,
) {
    init {
        require(repository.startsWith("https://")) { "Only HTTPS repositories are supported" }
        require(authorName.isNotBlank()) { "The author needs a name" }
        require(authorEmail.isNotBlank()) { "The author needs an email address" }
    }

    fun remote(token: String) = SyncRemote(repository, username, token)

    val author: SyncAuthor get() = SyncAuthor(authorName, authorEmail)
}
