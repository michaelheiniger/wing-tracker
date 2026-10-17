package ch.qscqlmpa.wingtracker.ui.airtime

import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStoreException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val TOKEN = "at_7f3c9e21b4d84a6f"

class AirtimeTokenTest {
    private val store = mockk<CredentialStore>(relaxUnitFun = true)
    private val token = AirtimeToken(store)

    @Nested
    inner class Keep {
        @Test
        fun `keeps the token in the system store without surrounding spaces`() =
            runTest {
                // when
                val keeping = token.keep("  $TOKEN\n")

                // then
                assertThat(keeping).isEqualTo(TokenKeeping.System)
                coVerify(exactly = 1) { store.write("airtime-token", TOKEN) }
            }

        @Test
        fun `keeps the token for the session when the system has no store`() =
            runTest {
                // given
                coEvery { store.write(any(), any()) } throws CredentialStoreException("secret-tool is not installed")
                coEvery { store.read(any()) } throws CredentialStoreException("secret-tool is not installed")

                // when
                val keeping = token.keep(TOKEN)

                // then
                assertThat(keeping).isEqualTo(TokenKeeping.Session)
                assertThat(token.current()).isEqualTo(TOKEN)
            }
    }

    @Nested
    inner class Current {
        @Test
        fun `reads the token the system kept from an earlier session`() =
            runTest {
                // given
                coEvery { store.read("airtime-token") } returns TOKEN

                // when / then
                assertThat(token.current()).isEqualTo(TOKEN)
            }

        @Test
        fun `has none when the system has no store and none was given in this session`() =
            runTest {
                // given
                coEvery { store.read(any()) } throws CredentialStoreException("secret-tool is not installed")

                // when / then
                assertThat(token.current()).isNull()
            }
    }

    @Nested
    inner class Forget {
        @Test
        fun `forgets the token in the session and in the system`() =
            runTest {
                // given
                token.keep(TOKEN)
                coEvery { store.read("airtime-token") } returns null

                // when
                token.forget()

                // then
                assertThat(token.current()).isNull()
                coVerify(exactly = 1) { store.delete("airtime-token") }
            }
    }
}
