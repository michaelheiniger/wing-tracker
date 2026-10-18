package ch.qscqlmpa.wingtracker.s3

import ch.qscqlmpa.wingtracker.backup.CloudObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Instant

/** One page of a ListObjectsV2 answer. */
internal class ListingPage(
    val objects: List<CloudObject>,
    // Set while more pages follow.
    val nextToken: String?,
)

/** @throws IllegalArgumentException when [xml] is no listing. */
internal fun parseListing(xml: String): ListingPage {
    val root = requireNotNull(parse(xml)?.documentElement) { "Not XML" }
    require(root.localName() == "ListBucketResult") { "Not a listing but ${root.localName()}" }
    val objects =
        root.children("Contents").map { entry ->
            CloudObject(
                key = requireNotNull(entry.text("Key")) { "An object without a key" },
                size = requireNotNull(entry.text("Size")?.toLongOrNull()) { "An object without a size" },
                lastModified = Instant.parse(requireNotNull(entry.text("LastModified")) { "An object without a date" }),
            )
        }
    val truncated = root.text("IsTruncated") == "true"
    return ListingPage(objects, root.text("NextContinuationToken").takeIf { truncated })
}

/** The `Code` of an S3 error answer, or null when the answer is no such error. */
internal fun errorCodeOf(xml: String): String? =
    parse(xml)?.documentElement?.takeIf { it.localName() == "Error" }?.text("Code")

private fun parse(xml: String): Document? {
    if (xml.isBlank()) return null
    val factory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // The answer comes from the network: no document type, so no entity can reach files or other hosts.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
    return try {
        factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    } catch (_: SAXException) {
        null
    }
}

private fun Element.localName(): String = localName ?: tagName

private fun Element.children(name: String): List<Element> =
    (0 until childNodes.length)
        .map {
            childNodes.item(
                it,
            )
        }.filterIsInstance<Element>()
        .filter { it.localName() == name }

private fun Element.text(name: String): String? = children(name).firstOrNull()?.textContent?.trim()
