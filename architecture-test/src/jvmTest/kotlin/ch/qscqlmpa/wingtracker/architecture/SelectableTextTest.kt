package ch.qscqlmpa.wingtracker.architecture

import com.lemonappdev.konsist.api.Konsist
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Every text can be selected. A popup is laid out apart from the window and fails when its text joins the window's
 * selection, so popups come from the wrappers in `Popups.kt`, which give each its own.
 */
class SelectableTextTest {
    private val materialPopups =
        setOf(
            "androidx.compose.material3.AlertDialog",
            "androidx.compose.material3.DatePickerDialog",
            "androidx.compose.material3.DropdownMenu",
            "androidx.compose.ui.window.Dialog",
        )

    // A member of its box's scope, so it needs no import and is found by its call.
    private val exposedMenuCall = Regex("""(?<![A-Za-z])ExposedDropdownMenu\(""")

    @Nested
    inner class Popups {
        @Test
        fun `the shared ui opens popups only through the selectable wrappers`() {
            // given
            val files =
                Konsist
                    .scopeFromDirectory(
                        "ui/src",
                    ).files
                    .filterNot { it.path.endsWith("components/Popups.kt") }

            // when
            val violations =
                files.flatMap { file ->
                    file.imports
                        .map { it.name }
                        .filter { it in materialPopups }
                        .map { "${file.path}: $it" }
                } + files.filter { exposedMenuCall.containsMatchIn(it.text) }.map { "${it.path}: ExposedDropdownMenu" }

            // then
            assertThat(violations).isEmpty()
        }
    }
}
