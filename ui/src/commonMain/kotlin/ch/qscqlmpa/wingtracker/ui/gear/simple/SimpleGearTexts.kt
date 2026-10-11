package ch.qscqlmpa.wingtracker.ui.gear.simple

import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.field_harness_interval_months
import ch.qscqlmpa.wingtracker.ui.resources.field_reserve_interval_months
import ch.qscqlmpa.wingtracker.ui.resources.harness_add
import ch.qscqlmpa.wingtracker.ui.resources.harness_empty_action
import ch.qscqlmpa.wingtracker.ui.resources.harness_empty_body
import ch.qscqlmpa.wingtracker.ui.resources.harness_empty_title
import ch.qscqlmpa.wingtracker.ui.resources.harness_form_title_add
import ch.qscqlmpa.wingtracker.ui.resources.harness_form_title_edit
import ch.qscqlmpa.wingtracker.ui.resources.harness_title
import ch.qscqlmpa.wingtracker.ui.resources.reserve_add
import ch.qscqlmpa.wingtracker.ui.resources.reserve_empty_action
import ch.qscqlmpa.wingtracker.ui.resources.reserve_empty_body
import ch.qscqlmpa.wingtracker.ui.resources.reserve_empty_title
import ch.qscqlmpa.wingtracker.ui.resources.reserve_form_title_add
import ch.qscqlmpa.wingtracker.ui.resources.reserve_form_title_edit
import ch.qscqlmpa.wingtracker.ui.resources.reserve_title
import org.jetbrains.compose.resources.StringResource

/** The wording that differs between harnesses and reserves; everything else is shared. */
class SimpleGearTexts(
    val title: StringResource,
    val add: StringResource,
    val emptyTitle: StringResource,
    val emptyBody: StringResource,
    val emptyAction: StringResource,
    val formAdd: StringResource,
    val formEdit: StringResource,
    val intervalLabel: StringResource,
)

fun GearKind.simpleTexts(): SimpleGearTexts =
    when (this) {
        GearKind.Reserve -> {
            SimpleGearTexts(
                Res.string.reserve_title,
                Res.string.reserve_add,
                Res.string.reserve_empty_title,
                Res.string.reserve_empty_body,
                Res.string.reserve_empty_action,
                Res.string.reserve_form_title_add,
                Res.string.reserve_form_title_edit,
                Res.string.field_reserve_interval_months,
            )
        }

        else -> {
            SimpleGearTexts(
                Res.string.harness_title,
                Res.string.harness_add,
                Res.string.harness_empty_title,
                Res.string.harness_empty_body,
                Res.string.harness_empty_action,
                Res.string.harness_form_title_add,
                Res.string.harness_form_title_edit,
                Res.string.field_harness_interval_months,
            )
        }
    }
