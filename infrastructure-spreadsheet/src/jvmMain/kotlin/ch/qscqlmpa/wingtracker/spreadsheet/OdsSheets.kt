package ch.qscqlmpa.wingtracker.spreadsheet

import ch.qscqlmpa.wingtracker.flights.spreadsheet.Cell
import ch.qscqlmpa.wingtracker.flights.spreadsheet.Sheet
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetException
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetProblem
import kotlinx.datetime.LocalDate
import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import kotlin.time.toKotlinDuration
import java.time.Duration as JavaDuration

/**
 * Reads the content of an OpenDocument spreadsheet (what LibreOffice saves). It streams the XML, since a sheet repeats
 * empty rows and cells up to the full size of the grid, and only keeps what lies before the last value.
 */
internal object OdsSheets {
    fun read(content: InputStream): List<Sheet> {
        val xml = secureFactory().createXMLStreamReader(content)
        val sheets = mutableListOf<Sheet>()
        try {
            while (xml.hasNext()) {
                if (xml.next() == XMLStreamConstants.START_ELEMENT && xml.isTable("table")) {
                    sheets += readTable(xml)
                }
            }
        } finally {
            xml.close()
        }
        return sheets
    }

    private fun readTable(xml: XMLStreamReader): Sheet {
        val name = xml.tableAttribute("name").orEmpty()
        val rows = Grid<List<Cell>>(empty = { emptyList() }, isEmpty = { it.isEmpty() })
        while (xml.hasNext()) {
            when (xml.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (xml.isTable("table-row")) {
                        val repeat = xml.tableAttribute("number-rows-repeated")?.toIntOrNull() ?: 1
                        rows.add(readRow(xml), repeat)
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (xml.isTable("table")) return Sheet(name, rows.toList())
                }
            }
        }
        throw SpreadsheetException(SpreadsheetProblem.Unreadable, "Sheet $name ends early")
    }

    private fun readRow(xml: XMLStreamReader): List<Cell> {
        val cells = Grid<Cell>(empty = { Cell.Empty }, isEmpty = { it == Cell.Empty })
        while (xml.hasNext()) {
            when (xml.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (xml.isTable("table-cell") || xml.isTable("covered-table-cell")) {
                        val repeat = xml.tableAttribute("number-columns-repeated")?.toIntOrNull() ?: 1
                        cells.add(readCell(xml), repeat)
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (xml.isTable("table-row")) return cells.toList()
                }
            }
        }
        return cells.toList()
    }

    private fun readCell(xml: XMLStreamReader): Cell {
        val type = xml.officeAttribute("value-type")
        val value = xml.officeAttribute("value")
        val date = xml.officeAttribute("date-value")
        val time = xml.officeAttribute("time-value")
        val text = readText(xml, elementName = xml.localName)
        return when (type) {
            null -> Cell.Empty

            "float", "percentage", "currency" -> value?.toDoubleOrNull()?.let(Cell::Number) ?: Cell.Empty

            // A date cell may also carry a time of day, which a flight does not need.
            "date" -> date?.let { LocalDate.parse(it.substringBefore('T')) }?.let(Cell::Date) ?: Cell.Empty

            "time" -> time?.let { Cell.Time(JavaDuration.parse(it).toKotlinDuration()) } ?: Cell.Empty

            else -> text.ifEmpty { null }?.let(Cell::Text) ?: Cell.Empty
        }
    }

    /** The text of a cell, paragraphs on their own lines; comments attached to the cell are not part of it. */
    private fun readText(
        xml: XMLStreamReader,
        elementName: String,
    ): String {
        val text = CellText()
        while (xml.hasNext()) {
            when (xml.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    text.start(xml)
                }

                XMLStreamConstants.CHARACTERS -> {
                    text.characters(xml.text)
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (xml.localName == elementName && xml.namespaceURI == TABLE_NS) return text.toString()
                    text.end(xml)
                }
            }
        }
        return text.toString()
    }

    private class CellText {
        private val paragraphs = mutableListOf<StringBuilder>()
        private var annotationDepth = 0

        fun start(xml: XMLStreamReader) {
            val current = paragraphs.lastOrNull()
            when {
                xml.localName == "annotation" -> {
                    annotationDepth++
                }

                annotationDepth > 0 -> {}

                xml.isText("p") -> {
                    paragraphs += StringBuilder()
                }

                xml.isText("s") -> {
                    current?.append(" ".repeat(spaces(xml)))
                }

                xml.isText("tab") -> {
                    current?.append('\t')
                }

                xml.isText("line-break") -> {
                    current?.append('\n')
                }
            }
        }

        fun characters(value: String) {
            if (annotationDepth == 0) paragraphs.lastOrNull()?.append(value)
        }

        fun end(xml: XMLStreamReader) {
            if (xml.localName == "annotation") annotationDepth--
        }

        override fun toString() = paragraphs.joinToString("\n")
    }

    private fun spaces(xml: XMLStreamReader) = xml.getAttributeValue(TEXT_NS, "c")?.toIntOrNull() ?: 1

    private fun XMLStreamReader.isTable(name: String) = localName == name && namespaceURI == TABLE_NS

    private fun XMLStreamReader.isText(name: String) = localName == name && namespaceURI == TEXT_NS

    private fun XMLStreamReader.tableAttribute(name: String): String? = getAttributeValue(TABLE_NS, name)

    private fun XMLStreamReader.officeAttribute(name: String): String? = getAttributeValue(OFFICE_NS, name)

    private const val TABLE_NS = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private const val TEXT_NS = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private const val OFFICE_NS = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
}

/**
 * Collects repeated entries without expanding the empty ones until a value follows them, so the grid never grows to
 * the million empty rows a sheet declares after its last value.
 */
private class Grid<T>(
    private val empty: () -> T,
    private val isEmpty: (T) -> Boolean,
) {
    private val items = mutableListOf<T>()
    private var pendingEmpty = 0

    fun add(
        item: T,
        repeat: Int,
    ) {
        if (isEmpty(item)) {
            pendingEmpty += repeat
            return
        }
        repeat(pendingEmpty) { items += empty() }
        pendingEmpty = 0
        repeat(repeat) { items += item }
    }

    fun toList(): List<T> = items.toList()
}

/** Untrusted files must not make the parser fetch anything or expand entities. */
internal fun secureFactory(): XMLInputFactory =
    XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }
