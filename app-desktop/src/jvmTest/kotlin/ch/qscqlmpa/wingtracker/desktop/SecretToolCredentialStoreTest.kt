package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.ui.platform.CredentialStoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val TOKEN = "at_7f3c9e21b4d84a6f"
private val ATTRIBUTES = listOf("application", "wing-tracker", "key", "airtime-token")

class SecretToolCredentialStoreTest {
    private val runs = mutableListOf<Pair<List<String>, String?>>()

    private fun store(result: ToolResult) =
        SecretToolCredentialStore("wing-tracker") { arguments, input ->
            runs += arguments to input
            result
        }

    @Nested
    inner class Read {
        @Test
        fun `returns the kept secret without the line break printed after it`() =
            runTest {
                // when
                val secret = store(ToolResult(0, "$TOKEN\n", "")).read("airtime-token")

                // then
                assertThat(secret).isEqualTo(TOKEN)
                assertThat(runs.single()).isEqualTo(listOf("lookup") + ATTRIBUTES to null)
            }

        @Test
        fun `returns null when nothing is kept under the key`() =
            runTest {
                // when / then
                assertThat(store(ToolResult(1, "", "")).read("airtime-token")).isNull()
            }

        @Test
        fun `fails when the Secret Service cannot be reached`() {
            // given
            val store = store(ToolResult(1, "", "secret-tool: Cannot autolaunch D-Bus without X11 \$DISPLAY"))

            // when / then
            assertThatThrownBy { runBlocking { store.read("airtime-token") } }
                .isInstanceOf(CredentialStoreException::class.java)
        }
    }

    @Nested
    inner class Write {
        @Test
        fun `passes the secret through the standard input, never as an argument`() =
            runTest {
                // when
                store(ToolResult(0, "", "")).write("airtime-token", TOKEN)

                // then
                val (arguments, input) = runs.single()
                assertThat(arguments).isEqualTo(listOf("store", "--label=wing-tracker airtime-token") + ATTRIBUTES)
                assertThat(input).isEqualTo(TOKEN)
            }

        @Test
        fun `fails when the secret could not be kept`() {
            // given
            val store = store(ToolResult(1, "", "secret-tool: Cannot create an item in a locked collection"))

            // when / then
            assertThatThrownBy { runBlocking { store.write("airtime-token", TOKEN) } }
                .isInstanceOf(CredentialStoreException::class.java)
        }
    }

    @Nested
    inner class Application {
        @Test
        fun `keeps a development build's secrets apart from the installed application's`() =
            runTest {
                // given
                val store =
                    SecretToolCredentialStore("wing-tracker-dev") { arguments, input ->
                        runs += arguments to input
                        ToolResult(0, "", "")
                    }

                // when
                store.write("airtime-token", TOKEN)

                // then
                assertThat(runs.single().first).isEqualTo(
                    listOf(
                        "store",
                        "--label=wing-tracker-dev airtime-token",
                        "application",
                        "wing-tracker-dev",
                        "key",
                        "airtime-token",
                    ),
                )
            }
    }

    @Nested
    inner class Delete {
        @Test
        fun `does not fail when nothing was kept`() =
            runTest {
                // when
                store(ToolResult(1, "", "")).delete("airtime-token")

                // then
                assertThat(runs.single()).isEqualTo(listOf("clear") + ATTRIBUTES to null)
            }
    }
}
