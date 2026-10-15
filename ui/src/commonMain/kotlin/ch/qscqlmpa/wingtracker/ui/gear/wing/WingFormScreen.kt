package ch.qscqlmpa.wingtracker.ui.gear.wing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.wing.CertificationClass
import ch.qscqlmpa.wingtracker.ui.components.DateField
import ch.qscqlmpa.wingtracker.ui.components.HelpTooltip
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.acquisition_new
import ch.qscqlmpa.wingtracker.ui.resources.acquisition_second_hand
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_save
import ch.qscqlmpa.wingtracker.ui.resources.certification_unknown
import ch.qscqlmpa.wingtracker.ui.resources.field_currency
import ch.qscqlmpa.wingtracker.ui.resources.field_flights_before_tracking
import ch.qscqlmpa.wingtracker.ui.resources.field_hours_before_tracking
import ch.qscqlmpa.wingtracker.ui.resources.field_in_service_date
import ch.qscqlmpa.wingtracker.ui.resources.field_interval_hours
import ch.qscqlmpa.wingtracker.ui.resources.field_interval_months
import ch.qscqlmpa.wingtracker.ui.resources.field_manufacturer
import ch.qscqlmpa.wingtracker.ui.resources.field_model
import ch.qscqlmpa.wingtracker.ui.resources.field_name
import ch.qscqlmpa.wingtracker.ui.resources.field_price
import ch.qscqlmpa.wingtracker.ui.resources.field_purchase_date
import ch.qscqlmpa.wingtracker.ui.resources.field_serial_number
import ch.qscqlmpa.wingtracker.ui.resources.field_size
import ch.qscqlmpa.wingtracker.ui.resources.form_title_add
import ch.qscqlmpa.wingtracker.ui.resources.form_title_edit
import ch.qscqlmpa.wingtracker.ui.resources.help_in_service_date
import ch.qscqlmpa.wingtracker.ui.resources.interval_hint
import ch.qscqlmpa.wingtracker.ui.resources.section_acquisition
import ch.qscqlmpa.wingtracker.ui.resources.section_certification
import ch.qscqlmpa.wingtracker.ui.resources.section_maintenance
import ch.qscqlmpa.wingtracker.ui.resources.section_usage
import org.jetbrains.compose.resources.stringResource

@Composable
fun WingFormScreen(
    presenter: WingFormPresenter,
    onClose: () -> Unit,
) {
    val input = presenter.input
    val errors = presenter.errors
    val editing = presenter.editing

    @Composable
    fun Field(
        label: String,
        value: String,
        field: WingFormField?,
        modifier: Modifier = Modifier.fillMaxWidth(),
        onChange: (String) -> Unit,
    ) = OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = field != null && field in errors,
        supportingText = { field?.let { errors[it] }?.let { Text(it.message()) } },
        singleLine = true,
        modifier = modifier,
    )

    @Composable
    fun NameField() =
        Field(stringResource(Res.string.field_name), input.name, WingFormField.Name) { v ->
            presenter.change { it.withName(v) }
        }

    @Composable
    fun ManufacturerAndModel() =
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Field(
                stringResource(Res.string.field_manufacturer),
                input.manufacturer,
                WingFormField.Manufacturer,
                Modifier.weight(1f),
            ) { v -> presenter.change { it.withManufacturer(v) } }
            Field(stringResource(Res.string.field_model), input.model, WingFormField.Model, Modifier.weight(1f)) { v ->
                presenter.change { it.withModel(v) }
            }
        }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (editing == null) {
                stringResource(Res.string.form_title_add)
            } else {
                stringResource(Res.string.form_title_edit, editing.details.name)
            },
            style = MaterialTheme.typography.headlineMedium,
        )

        // A new wing starts from manufacturer and model, which fill in the name; an existing one keeps its name first.
        if (editing == null) {
            ManufacturerAndModel()
            NameField()
        } else {
            NameField()
            ManufacturerAndModel()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Field(stringResource(Res.string.field_size), input.size, null, Modifier.weight(1f)) { v ->
                presenter.change { it.copy(size = v) }
            }
            Field(stringResource(Res.string.field_serial_number), input.serialNumber, null, Modifier.weight(2f)) { v ->
                presenter.change { it.copy(serialNumber = v) }
            }
        }

        Text(stringResource(Res.string.section_certification), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = input.certificationClass == null,
                onClick = { presenter.change { it.copy(certificationClass = null) } },
                label = { Text(stringResource(Res.string.certification_unknown)) },
            )
            CertificationClass.entries.forEach { cls ->
                FilterChip(
                    selected = input.certificationClass == cls,
                    onClick = { presenter.change { it.copy(certificationClass = cls) } },
                    label = { Text(cls.name) },
                )
            }
        }

        Text(
            stringResource(Res.string.section_acquisition),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = input.condition == AcquisitionCondition.New,
                onClick = { presenter.change { it.copy(condition = AcquisitionCondition.New) } },
                label = { Text(stringResource(Res.string.acquisition_new)) },
            )
            FilterChip(
                selected = input.condition == AcquisitionCondition.SecondHand,
                onClick = { presenter.change { it.copy(condition = AcquisitionCondition.SecondHand) } },
                label = { Text(stringResource(Res.string.acquisition_second_hand)) },
            )
        }
        DateField(
            label = stringResource(Res.string.field_purchase_date),
            value = input.purchaseDate,
            onValueChange = { v -> presenter.change { it.copy(purchaseDate = v) } },
            clearable = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Field(
                stringResource(Res.string.field_price),
                input.priceAmount,
                WingFormField.PriceAmount,
                Modifier.weight(2f),
            ) { v ->
                presenter.change { it.copy(priceAmount = v) }
            }
            Field(
                stringResource(Res.string.field_currency),
                input.priceCurrency,
                WingFormField.PriceCurrency,
                Modifier.weight(1f),
            ) { v -> presenter.change { it.copy(priceCurrency = v) } }
        }

        Text(
            stringResource(Res.string.section_usage),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DateField(
                label = stringResource(Res.string.field_in_service_date),
                value = input.inServiceDate,
                onValueChange = { v -> presenter.change { it.copy(inServiceDate = v) } },
                clearable = true,
                modifier = Modifier.weight(1f),
            )
            HelpTooltip(stringResource(Res.string.help_in_service_date))
        }
        Field(
            stringResource(Res.string.field_hours_before_tracking),
            input.hoursBeforeTracking,
            WingFormField.HoursBeforeTracking,
        ) { v -> presenter.change { it.copy(hoursBeforeTracking = v) } }
        Field(
            stringResource(Res.string.field_flights_before_tracking),
            input.flightsBeforeTracking,
            WingFormField.FlightsBeforeTracking,
        ) { v -> presenter.change { it.copy(flightsBeforeTracking = v) } }

        Text(
            stringResource(Res.string.section_maintenance),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Field(
                stringResource(Res.string.field_interval_months),
                input.intervalMonths,
                WingFormField.IntervalMonths,
                Modifier.weight(1f),
            ) { v -> presenter.change { it.copy(intervalMonths = v) } }
            Field(
                stringResource(Res.string.field_interval_hours),
                input.intervalHours,
                WingFormField.IntervalHours,
                Modifier.weight(1f),
            ) { v -> presenter.change { it.copy(intervalHours = v) } }
        }
        Text(
            stringResource(Res.string.interval_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Button(onClick = { presenter.submit(onSaved = onClose) }, enabled = !presenter.saving) {
                Text(stringResource(Res.string.action_save))
            }
            TextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
        }
    }
}
