package ch.qscqlmpa.wingtracker.spreadsheet

import ch.qscqlmpa.wingtracker.flights.spreadsheet.Sheet
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetException
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetProblem
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetReader
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

private val logger = KotlinLogging.logger {}

/**
 * Reads OpenDocument and Office Open XML spreadsheets (ADR 0022). The format is told from the content, not from the
 * file name, since both are zip files and a renamed file is common.
 */
class FileSpreadsheetReader : SpreadsheetReader {
    override suspend fun read(bytes: ByteArray): List<Sheet> =
        withContext(Dispatchers.IO) {
            try {
                val format = formatOf(bytes)
                when (format) {
                    Format.Ods -> OdsSheets.read(entry(bytes, ODS_CONTENT))
                    Format.Xlsx -> XlsxSheets.read(ByteArrayInputStream(bytes))
                }.also { sheets ->
                    logger.debug { "Read ${sheets.size} sheets from a $format file of ${bytes.size} bytes" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpreadsheetException) {
                logger.info { "Refused a spreadsheet of ${bytes.size} bytes (${e.problem}): ${e.message}" }
                throw e
                // Whatever the parsers stumble on, the pilot gets one message about the file, never a crash.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                logger.warn(e) { "Could not read a spreadsheet of ${bytes.size} bytes" }
                throw SpreadsheetException(SpreadsheetProblem.Unreadable, e.message, e)
            }
        }

    private enum class Format { Ods, Xlsx }

    private fun formatOf(bytes: ByteArray): Format {
        if (bytes.startsWith(OLE2_SIGNATURE)) throw SpreadsheetException(SpreadsheetProblem.OldExcelFormat)
        val names = entryNames(bytes)
        return when {
            ODS_CONTENT in names && String(entryBytes(bytes, MIMETYPE) ?: byteArrayOf()) == ODS_MIMETYPE -> Format.Ods
            XLSX_WORKBOOK in names -> Format.Xlsx
            else -> throw SpreadsheetException(SpreadsheetProblem.Unreadable, "Neither an ODS nor an XLSX file")
        }
    }

    private fun entryNames(bytes: ByteArray): Set<String> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toSet()
        }

    private fun entry(
        bytes: ByteArray,
        name: String,
    ) = ByteArrayInputStream(entryBytes(bytes, name) ?: throw SpreadsheetException(SpreadsheetProblem.Unreadable))

    private fun entryBytes(
        bytes: ByteArray,
        name: String,
    ): ByteArray? =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == name }?.let { zip.readNBytes(MAX_ENTRY_BYTES) }
        }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        const val ODS_CONTENT = "content.xml"
        const val MIMETYPE = "mimetype"
        const val ODS_MIMETYPE = "application/vnd.oasis.opendocument.spreadsheet"
        const val XLSX_WORKBOOK = "xl/workbook.xml"

        // A logbook is small; a cap keeps a crafted file from unpacking into all the memory there is.
        const val MAX_ENTRY_BYTES = 256 * 1024 * 1024

        // How every Excel 97-2003 file starts.
        val OLE2_SIGNATURE = listOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1).map { it.toByte() }.toByteArray()
    }
}
