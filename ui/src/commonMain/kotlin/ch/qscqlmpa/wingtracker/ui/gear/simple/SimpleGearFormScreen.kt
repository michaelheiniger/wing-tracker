package ch.qscqlmpa.wingtracker.ui.gear.simple

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
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.ui.components.DateField
import ch.qscqlmpa.wingtracker.ui.components.HelpTooltip
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.acquisition_new
import ch.qscqlmpa.wingtracker.ui.resources.acquisition_second_hand
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.action_save
import ch.qscqlmpa.wingtracker.ui.resources.field_currency
import ch.qscqlmpa.wingtracker.ui.resources.field_in_service_date
import ch.qscqlmpa.wingtracker.ui.resources.field_manufacture_date
import ch.qscqlmpa.wingtracker.ui.resources.field_manufacturer
import ch.qscqlmpa.wingtracker.ui.resources.field_model
import ch.qscqlmpa.wingtracker.ui.resources.field_name
import ch.qscqlmpa.wingtracker.ui.resources.field_price
import ch.qscqlmpa.wingtracker.ui.resources.field_purchase_date
import ch.qscqlmpa.wingtracker.ui.resources.field_serial_number
import ch.qscqlmpa.wingtracker.ui.resources.field_size
import ch.qscqlmpa.wingtracker.ui.resources.help_in_service_date
import ch.qscqlmpa.wingtracker.ui.resources.interval_hint_months
import ch.qscqlmpa.wingtracker.ui.resources.section_acquisition
import ch.qscqlmpa.wingtracker.ui.resources.section_maintenance
import ch.qscqlmpa.wingtracker.ui.resources.section_usage
import org.jetbrains.compose.resources.stringResource

@Composable
fun SimpleGearFormScreen(
    presenter: SimpleGearFormPresenter,
    texts: SimpleGearTexts,
    onClose: () -> Unit,
) {
    val input = presenter.input
    val errors = presenter.errors
    val editing = presenter.editing

    @Composable
    fun Field(
        label: String,
        value: String,
        field: SimpleGearField?,
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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (editing ==
                null
            ) {
                stringResource(texts.formAdd)
            } else {
                stringResource(texts.formEdit, editing.details.name)
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Field(stringResource(Res.string.field_name), input.name, SimpleGearField.Name) { v ->
            presenter.change { it.copy(name = v) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Field(
                stringResource(Res.string.field_manufacturer),
                input.manufacturer,
                SimpleGearField.Manufacturer,
                Modifier.weight(1f),
            ) { v -> presenter.change { it.copy(manufacturer = v) } }
            Field(
                stringResource(Res.string.field_model),
                input.model,
                SimpleGearField.Model,
                Modifier.weight(1f),
            ) { v ->
                presenter.change { it.copy(model = v) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Field(stringResource(Res.string.field_size), input.size, null, Modifier.weight(1f)) { v ->
                presenter.change { it.copy(size = v) }
            }
            Field(stringResource(Res.string.field_serial_number), input.serialNumber, null, Modifier.weight(2f)) { v ->
                presenter.change { it.copy(serialNumber = v) }
            }
        }
        if (presenter.kind == GearKind.Reserve) {
            DateField(
                label = stringResource(Res.string.field_manufacture_date),
                value = input.manufactureDate,
                onValueChange = { v -> presenter.change { it.copy(manufactureDate = v) } },
                errorText = errors[SimpleGearField.ManufactureDate]?.message(),
                clearable = true,
                modifier = Modifier.fillMaxWidth(),
            )
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
            errorText = errors[SimpleGearField.PurchaseDate]?.message(),
            clearable = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Field(
                stringResource(Res.string.field_price),
                input.priceAmount,
                SimpleGearField.PriceAmount,
                Modifier.weight(2f),
            ) { v -> presenter.change { it.copy(priceAmount = v) } }
            Field(
                stringResource(Res.string.field_currency),
                input.priceCurrency,
                SimpleGearField.PriceCurrency,
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
                errorText = errors[SimpleGearField.InServiceDate]?.message(),
                clearable = true,
                modifier = Modifier.weight(1f),
            )
            HelpTooltip(stringResource(Res.string.help_in_service_date))
        }
        Text(
            stringResource(Res.string.section_maintenance),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Field(stringResource(texts.intervalLabel), input.intervalMonths, SimpleGearField.IntervalMonths) { v ->
            presenter.change { it.copy(intervalMonths = v) }
        }
        Text(
            stringResource(Res.string.interval_hint_months),
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
