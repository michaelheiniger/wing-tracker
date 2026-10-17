package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStoreException
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.ptr.PointerByReference
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

// Values from the Windows headers (wincred.h, winerror.h).
private const val CRED_TYPE_GENERIC = 1
private const val CRED_PERSIST_LOCAL_MACHINE = 2
private const val ERROR_NOT_FOUND = 1168

/**
 * The Windows Credential Manager, called through JNA (ADR 0029). Secrets are kept for this user on this computer only,
 * like the other per-installation preferences, and never roam to other computers.
 */
class WindowsCredentialStore : CredentialStore {
    private val advapi by lazy { NativeLibrary.getInstance("Advapi32") }

    override suspend fun read(key: String): String? =
        withContext(Dispatchers.IO) {
            val found = PointerByReference()
            if (!call("CredReadW", WString(target(key)), CRED_TYPE_GENERIC, 0, found)) {
                val error = Native.getLastError()
                if (error == ERROR_NOT_FOUND) return@withContext null
                fail("Reading $key", error)
            }
            try {
                val credential = Credential(found.value)
                credential.blob?.getByteArray(0, credential.blobSize)?.toString(Charsets.UTF_8)
            } finally {
                call("CredFree", found.value)
            }
        }

    override suspend fun write(
        key: String,
        secret: String,
    ) {
        withContext(Dispatchers.IO) {
            val bytes = secret.toByteArray(Charsets.UTF_8)
            val blob = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
            val credential =
                Credential().apply {
                    type = CRED_TYPE_GENERIC
                    targetName = WString(target(key))
                    blobSize = bytes.size
                    this.blob = blob
                    persist = CRED_PERSIST_LOCAL_MACHINE
                }
            try {
                if (!call("CredWriteW", credential, 0)) fail("Keeping $key", Native.getLastError())
            } finally {
                // The secret must not stay in memory longer than needed.
                blob.clear()
            }
            logger.info { "Kept $key in the Credential Manager" }
        }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) {
            if (!call("CredDeleteW", WString(target(key)), CRED_TYPE_GENERIC, 0)) {
                val error = Native.getLastError()
                if (error != ERROR_NOT_FOUND) fail("Forgetting $key", error)
            }
        }
    }

    // The application's name in front, so the entry is recognisable among others in the Credential Manager.
    private fun target(key: String) = "wing-tracker/$key"

    private fun call(
        name: String,
        vararg arguments: Any,
    ): Boolean =
        try {
            advapi.getFunction(name, Function.ALT_CONVENTION).invokeInt(arguments) != 0
        } catch (e: UnsatisfiedLinkError) {
            throw CredentialStoreException("The Credential Manager is not available", e)
        }

    private fun fail(
        what: String,
        error: Int,
    ): Nothing {
        logger.warn { "$what failed: the Credential Manager reported error $error" }
        throw CredentialStoreException("The Credential Manager reported error $error")
    }
}

/** CREDENTIALW; only the order of the fields matters to Windows, so they carry Kotlin names. */
@Structure.FieldOrder(
    "flags",
    "type",
    "targetName",
    "comment",
    "lastWritten",
    "blobSize",
    "blob",
    "persist",
    "attributeCount",
    "attributes",
    "targetAlias",
    "userName",
)
internal class Credential(
    pointer: Pointer? = null,
) : Structure(pointer) {
    @JvmField var flags: Int = 0

    @JvmField var type: Int = 0

    @JvmField var targetName: WString? = null

    @JvmField var comment: WString? = null

    @JvmField var lastWritten: WinBase.FILETIME = WinBase.FILETIME()

    @JvmField var blobSize: Int = 0

    @JvmField var blob: Pointer? = null

    @JvmField var persist: Int = 0

    @JvmField var attributeCount: Int = 0

    @JvmField var attributes: Pointer? = null

    @JvmField var targetAlias: WString? = null

    @JvmField var userName: WString? = null

    init {
        if (pointer != null) read()
    }
}
