package ch.qscqlmpa.wingtracker.airtime

import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeException
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeGlider
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeProblem
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeTakeOff
import ch.qscqlmpa.wingtracker.flights.airtime.ManualAirtimeFlight
import ch.qscqlmpa.wingtracker.flights.airtime.NewAirtimeGlider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.time.Instant

private const val BASE_URL = "https://air-time.net"
private const val USER_AGENT = "wing-tracker/0.3.0"
private const val TOKEN = "at_7f3c9e21b4d84a6f"

private fun sample(name: String) = checkNotNull(AirtimeWebAccountTest::class.java.getResource("/$name")).readText()

class AirtimeWebAccountTest {
    private val sent = mutableListOf<AirtimeRequest>()

    private fun account(vararg answers: AirtimeAnswer): AirtimeWebAccount {
        val queue = ArrayDeque(answers.toList())
        return AirtimeWebAccount(BASE_URL, USER_AGENT) { request, userAgent ->
            assertThat(userAgent).isEqualTo(USER_AGENT)
            sent += request
            queue.removeFirst()
        }
    }

    private fun AirtimeRequest.json() = Json.parseToJsonElement((body as AirtimeBody.Json).text) as JsonObject

    @Nested
    inner class Gliders {
        @Test
        fun `reads the pilot's gliders, retired ones included, with the token`() =
            runTest {
                // when
                val gliders = account(AirtimeAnswer(200, sample("gliders.json"))).gliders(TOKEN)

                // then
                assertThat(gliders).containsExactly(
                    AirtimeGlider("0b6f2c1e-93a4-4d7b-8e55-1c2f7a9d3e60", "Niviuk", "Birdy", "S"),
                    AirtimeGlider("5d2a9e41-7c3b-4f86-b0d1-8e6a2c4f9b17", "Ozone", "Ultralite 4", ""),
                )
                assertThat(sent.single().url).isEqualTo("$BASE_URL/v0/gliders")
                assertThat(sent.single().token).isEqualTo(TOKEN)
            }

        @Test
        fun `reports a refused token`() =
            runTest {
                // given
                val account = account(AirtimeAnswer(401, """{"title":"Unauthorized","status":401}"""))

                // when / then
                assertThatThrownBy { kotlinx.coroutines.runBlocking { account.gliders(TOKEN) } }
                    .isInstanceOfSatisfying(AirtimeException::class.java) {
                        assertThat(it.problem).isEqualTo(AirtimeProblem.TokenRefused)
                    }
            }
    }

    @Nested
    inner class CreateGlider {
        @Test
        fun `posts every field air-time requires, with the colour under its own name`() =
            runTest {
                // given
                val account = account(AirtimeAnswer(204, ""))

                // when
                account.createGlider(
                    TOKEN,
                    NewAirtimeGlider(
                        "c4e8a2d6-5f17-4b39-a0c2-7e9d1b3f6a84",
                        "Sky Dancer",
                        "Advance",
                        "Iota 3",
                        "25",
                        "Red",
                    ),
                )

                // then
                val request = sent.single()
                assertThat(request.method).isEqualTo("POST")
                assertThat(request.url).isEqualTo("$BASE_URL/v0/gliders")
                assertThat(request.json()).containsAllEntriesOf(
                    mapOf(
                        "id" to JsonPrimitive("c4e8a2d6-5f17-4b39-a0c2-7e9d1b3f6a84"),
                        "name" to JsonPrimitive("Sky Dancer"),
                        "brand" to JsonPrimitive("Advance"),
                        "model" to JsonPrimitive("Iota 3"),
                        "size" to JsonPrimitive("25"),
                        "color" to JsonPrimitive("Red"),
                    ),
                )
            }
    }

    @Nested
    inner class FlightStarts {
        @Test
        fun `reads the start of each public flight of the pilot`() =
            runTest {
                // when
                val starts = account(AirtimeAnswer(200, sample("flights-page.json"))).flightStarts("QSCQLMPA")

                // then
                assertThat(starts).containsExactly(
                    Instant.parse("2026-04-24T13:08:42Z"),
                    Instant.parse("2026-04-18T15:13:06Z"),
                )
                assertThat(sent.single().url).isEqualTo("$BASE_URL/v0/pilots/QSCQLMPA/flights?limit=200&offset=0")
                assertThat(sent.single().token).isNull()
            }

        @Test
        fun `follows the pages while air-time says there are more`() =
            runTest {
                // given
                val firstPage = sample("flights-page.json").replace("\"has_more\": false", "\"has_more\": true")

                // when
                val starts =
                    account(AirtimeAnswer(200, firstPage), AirtimeAnswer(200, sample("flights-page.json")))
                        .flightStarts("QSCQLMPA")

                // then
                assertThat(starts).hasSize(4)
                assertThat(sent.map { it.url }).containsExactly(
                    "$BASE_URL/v0/pilots/QSCQLMPA/flights?limit=200&offset=0",
                    "$BASE_URL/v0/pilots/QSCQLMPA/flights?limit=200&offset=2",
                )
            }

        @Test
        fun `encodes a nick that is not a plain word`() =
            runTest {
                // when
                account(AirtimeAnswer(200, sample("flights-page.json"))).flightStarts("Jean Dé")

                // then
                assertThat(sent.single().url).startsWith("$BASE_URL/v0/pilots/Jean%20D%C3%A9/flights")
            }

        @Test
        fun `reports an unknown pilot`() =
            runTest {
                // given
                val account = account(AirtimeAnswer(404, """{"title":"Not Found","status":404}"""))

                // when / then
                assertThatThrownBy { kotlinx.coroutines.runBlocking { account.flightStarts("nobody") } }
                    .isInstanceOfSatisfying(AirtimeException::class.java) {
                        assertThat(it.problem).isEqualTo(AirtimeProblem.PilotNotFound)
                    }
            }

        @Test
        fun `reports an answer whose shape changed`() =
            runTest {
                // given
                val account = account(AirtimeAnswer(200, """{"items":[]}"""))

                // when / then
                assertThatThrownBy { kotlinx.coroutines.runBlocking { account.flightStarts("QSCQLMPA") } }
                    .isInstanceOfSatisfying(AirtimeException::class.java) {
                        assertThat(it.problem).isEqualTo(AirtimeProblem.UnexpectedAnswer)
                    }
            }
    }

    @Nested
    inner class TakeOffs {
        @Test
        fun `reads the id and name of each site`() =
            runTest {
                // when
                val sites = account(AirtimeAnswer(200, sample("takeoffs.json"))).takeOffs()

                // then
                assertThat(sites).containsExactly(
                    AirtimeTakeOff(2, "Le Suchet"),
                    AirtimeTakeOff(3, "La Palette"),
                    AirtimeTakeOff(4, "Sonchaux"),
                )
            }
    }

    @Nested
    inner class UploadTrack {
        @Test
        fun `sends the track as the file of a form, with the glider`() =
            runTest {
                // given
                val igc = "AXCT7LX\r\nHFDTE200926\r\n".encodeToByteArray()

                // when
                account(AirtimeAnswer(204, "")).uploadTrack(TOKEN, igc, "2026-09-20-niederhorn.igc", "0b6f2c1e")

                // then
                val request = sent.single()
                val body = request.body as AirtimeBody.Multipart
                val form = body.bytes.decodeToString()
                assertThat(request.url).isEqualTo("$BASE_URL/v0/flights")
                assertThat(request.token).isEqualTo(TOKEN)
                assertThat(form).contains(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"2026-09-20-niederhorn.igc\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n" +
                        "AXCT7LX\r\nHFDTE200926\r\n\r\n--${body.boundary}",
                    "Content-Disposition: form-data; name=\"glider_id\"\r\n\r\n0b6f2c1e\r\n--${body.boundary}--",
                )
            }

        @Test
        fun `keeps quotes and line breaks of the file name out of the form's header`() {
            // when
            val form = multipart("b", byteArrayOf(), "vol \"1\"\r\n.igc", "g").decodeToString()

            // then
            assertThat(form).contains("filename=\"vol _1___.igc\"")
        }
    }

    @Nested
    inner class RegisterManualFlight {
        @Test
        fun `posts the start, end, glider, site and air-time's default type`() =
            runTest {
                // when
                account(AirtimeAnswer(204, "")).registerManualFlight(
                    TOKEN,
                    ManualAirtimeFlight(
                        Instant.parse("2026-09-21T11:05:00Z"),
                        Instant.parse("2026-09-21T12:40:00Z"),
                        "0b6f2c1e",
                        4,
                    ),
                )

                // then
                assertThat(sent.single().url).isEqualTo("$BASE_URL/v0/flights/manual")
                assertThat(sent.single().json()).isEqualTo(
                    JsonObject(
                        mapOf(
                            "start_time" to JsonPrimitive("2026-09-21T11:05:00Z"),
                            "end_time" to JsonPrimitive("2026-09-21T12:40:00Z"),
                            "glider_id" to JsonPrimitive("0b6f2c1e"),
                            "takeoff_id" to JsonPrimitive(4),
                            "type" to JsonPrimitive("site flight"),
                        ),
                    ),
                )
            }

        @Test
        fun `leaves the site out when there is none`() =
            runTest {
                // when
                account(AirtimeAnswer(204, "")).registerManualFlight(
                    TOKEN,
                    ManualAirtimeFlight(
                        Instant.parse("2026-09-21T11:05:00Z"),
                        Instant.parse("2026-09-21T12:40:00Z"),
                        "0b6f2c1e",
                        null,
                    ),
                )

                // then
                assertThat(sent.single().json()).doesNotContainKey("takeoff_id")
            }
    }

    @Nested
    inner class Answers {
        @ParameterizedTest
        @CsvSource("403, TokenRefused", "500, Unreachable", "502, Unreachable", "422, UnexpectedAnswer")
        fun `turns a status into what it means for the pilot`(
            status: Int,
            problem: AirtimeProblem,
        ) {
            // given
            val account = account(AirtimeAnswer(status, ""))

            // when / then
            assertThatThrownBy { kotlinx.coroutines.runBlocking { account.takeOffs() } }
                .isInstanceOfSatisfying(AirtimeException::class.java) { assertThat(it.problem).isEqualTo(problem) }
        }
    }
}
