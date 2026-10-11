package ch.qscqlmpa.wingtracker.ui.resources

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

// Reads the resource files as they are in the source tree; Gradle runs tests from the module directory.
private val RESOURCES = File("src/commonMain/composeResources")
private val LOCALES = listOf("values", "values-de", "values-fr")
private val NAME = Regex("""<(?:string|plurals|string-array) name="([^"]+)"""")

private fun textOf(locale: String) = File(RESOURCES, "$locale/strings.xml").readText()

private fun keysOf(locale: String) = NAME.findAll(textOf(locale)).map { it.groupValues[1] }.toSet()

class StringResourcesTest {
    @Nested
    inner class Languages {
        @Test
        fun `every language defines exactly the same strings as the base language`() {
            // given
            val base = keysOf("values")

            // when / then
            LOCALES.drop(1).forEach { locale ->
                assertThat(base - keysOf(locale)).describedAs("missing in $locale").isEmpty()
                assertThat(keysOf(locale) - base).describedAs("only in $locale").isEmpty()
            }
        }

        @Test
        fun `every language has the same number of placeholders in each string`() {
            // given
            val placeholder = Regex("""%\d\${'$'}[sd]""")
            val entry = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

            fun placeholders(locale: String) =
                entry.findAll(textOf(locale)).associate {
                    it.groupValues[1] to
                        placeholder
                            .findAll(it.groupValues[2])
                            .map { m -> m.value }
                            .sorted()
                            .toList()
                }

            val base = placeholders("values")

            // when / then
            LOCALES.drop(1).forEach { locale ->
                val other = placeholders(locale)
                val different = base.filter { (key, found) -> other[key] != null && other[key] != found }.keys
                assertThat(different).describedAs("placeholders differ in $locale").isEmpty()
            }
        }
    }

    @Nested
    inner class Escaping {
        @Test
        fun `no string contains a backslash escape that would be shown literally`() {
            // given / when
            val offenders = LOCALES.filter { Regex("""\\['"]""").containsMatchIn(textOf(it)) }

            // then
            assertThat(offenders).describedAs("Compose resources show \\' as written; use a plain apostrophe").isEmpty()
        }
    }
}
