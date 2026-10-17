package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStoreException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

private val logger = KotlinLogging.logger {}

/** How a run of `secret-tool` ended; the secret comes on [output], so errors are kept apart from it. */
class ToolResult(
    val exitCode: Int,
    val output: String,
    val errors: String,
)

/**
 * The Secret Service of the Linux desktop (GNOME Keyring, KWallet), through libsecret's `secret-tool` (ADR 0029).
 * The package cannot depend on it, so a missing tool is reported like a missing store, and the pilot is told how to
 * install it. A locked keyring is unlocked by the desktop's own prompt.
 */
class SecretToolCredentialStore(
    private val run: suspend (arguments: List<String>, input: String?) -> ToolResult = ::runSecretTool,
) : CredentialStore {
    override suspend fun read(key: String): String? {
        val result = run(listOf("lookup") + attributes(key), null)
        return when {
            // secret-tool ends the secret with a line break only when printing to a terminal; neither is part of it.
            result.exitCode == 0 -> result.output.removeSuffix("\n").ifEmpty { null }

            isNothingKept(result) -> null

            else -> fail("Reading $key", result)
        }
    }

    override suspend fun write(
        key: String,
        secret: String,
    ) {
        // The secret goes through the standard input, never the command line, which other users can list.
        val result = run(listOf("store", "--label=wing-tracker $key") + attributes(key), secret)
        if (result.exitCode != 0) fail("Keeping $key", result)
        logger.info { "Kept $key in the Secret Service" }
    }

    override suspend fun delete(key: String) {
        val result = run(listOf("clear") + attributes(key), null)
        if (result.exitCode != 0 && !isNothingKept(result)) fail("Forgetting $key", result)
    }

    private fun attributes(key: String) = listOf("application", "wing-tracker", "key", key)

    // secret-tool ends with 1 and says nothing when no secret matches; any other failure explains itself.
    private fun isNothingKept(result: ToolResult) = result.exitCode == 1 && result.errors.isBlank()

    private fun fail(
        what: String,
        result: ToolResult,
    ): Nothing {
        logger.warn { "$what failed: secret-tool ended with ${result.exitCode}: ${result.errors.trim()}" }
        throw CredentialStoreException("secret-tool ended with ${result.exitCode}")
    }
}

private suspend fun runSecretTool(
    arguments: List<String>,
    input: String?,
): ToolResult =
    withContext(Dispatchers.IO) {
        val process =
            try {
                ProcessBuilder(listOf("secret-tool") + arguments).start()
            } catch (e: IOException) {
                logger.info { "secret-tool is not available: ${e.message}" }
                throw CredentialStoreException("secret-tool is not available", e)
            }
        process.outputStream.use { stdin -> input?.let { stdin.write(it.toByteArray(Charsets.UTF_8)) } }
        // Both streams are read at once, so neither can fill up and block the tool.
        coroutineScope {
            val output = async { process.inputStream.bufferedReader().readText() }
            val errors = async { process.errorStream.bufferedReader().readText() }
            ToolResult(process.waitFor(), output.await(), errors.await())
        }
    }
