package ch.qscqlmpa.wingtracker.flights.spreadsheet

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ColumnMappingTest {
    @Nested
    inner class Suggest {
        @Test
        fun `matches every column of an English logbook and leaves the altitudes and numbers out`() {
            // when
            val mapping = ColumnMapping.suggest(LOGBOOK_HEADERS)

            // then
            assertThat(mapping).isEqualTo(LOGBOOK_MAPPING)
        }

        @Test
        fun `matches French headers, accents included`() {
            // given
            val headers = listOf("N°", "Date", "Voile", "Décollage", "Atterrissage", "Durée (min)", "Remarques")

            // when
            val mapping = ColumnMapping.suggest(headers)

            // then
            assertThat(mapping.columns).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                    FlightField.Date to 1,
                    FlightField.Wing to 2,
                    FlightField.TakeOff to 3,
                    FlightField.Landing to 4,
                    FlightField.DurationMinutes to 5,
                    FlightField.Notes to 6,
                ),
            )
        }

        @Test
        fun `matches German headers and reads a duration in hours from its unit`() {
            // given
            val headers = listOf("Datum", "Schirm", "Startplatz", "Landeplatz", "Flugzeit (Std)", "Bemerkungen")

            // when
            val mapping = ColumnMapping.suggest(headers)

            // then
            assertThat(mapping.columnOf(FlightField.DurationHours)).isEqualTo(4)
            assertThat(mapping.columnOf(FlightField.TakeOff)).isEqualTo(2)
            assertThat(mapping.isComplete).isTrue()
        }

        @Test
        fun `takes a duration without a unit as minutes`() {
            // when
            val mapping = ColumnMapping.suggest(listOf("Date", "Wing", "Duration"))

            // then
            assertThat(mapping.columnOf(FlightField.DurationMinutes)).isEqualTo(2)
        }

        @Test
        fun `keeps the first column when two headers mean the same field`() {
            // when
            val mapping = ColumnMapping.suggest(listOf("Notes", "Comments"))

            // then
            assertThat(mapping.columnOf(FlightField.Notes)).isZero()
        }
    }

    @Nested
    inner class With {
        @Test
        fun `moves a column to the field it is given to`() {
            // when
            val mapping = LOGBOOK_MAPPING.with(FlightField.Notes, 4)

            // then
            assertThat(mapping.columnOf(FlightField.Notes)).isEqualTo(4)
            assertThat(mapping.columnOf(FlightField.TakeOff)).isNull()
        }

        @Test
        fun `leaves a field out when given no column`() {
            // when
            val mapping = LOGBOOK_MAPPING.with(FlightField.Distance, null)

            // then
            assertThat(mapping.columnOf(FlightField.Distance)).isNull()
        }
    }

    @Nested
    inner class IsComplete {
        @Test
        fun `needs a date, a wing and one duration column`() {
            // given
            val minimal =
                ColumnMapping(mapOf(FlightField.Date to 0, FlightField.Wing to 1, FlightField.DurationHours to 2))

            // when / then
            assertThat(minimal.isComplete).isTrue()
            assertThat(minimal.with(FlightField.DurationHours, null).isComplete).isFalse()
            assertThat(minimal.with(FlightField.Wing, null).isComplete).isFalse()
        }
    }
}
