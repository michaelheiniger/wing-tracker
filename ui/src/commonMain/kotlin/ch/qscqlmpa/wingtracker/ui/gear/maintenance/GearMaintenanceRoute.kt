package ch.qscqlmpa.wingtracker.ui.gear.maintenance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.check_pick_title
import ch.qscqlmpa.wingtracker.ui.today
import org.jetbrains.compose.resources.stringResource

@Composable
fun GearMaintenanceRoute(
    ref: GearRef,
    useCases: MaintenanceUseCases,
    platform: PlatformServices,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pickTitle = stringResource(Res.string.check_pick_title)
    val presenter = remember(ref) { GearMaintenancePresenter(scope, ref, useCases, platform.fileOpener) }
    val dialogs =
        MaintenanceDialogs(
            checkPresenter = { mode ->
                val initial =
                    when (mode) {
                        CheckFormMode.Record -> {
                            CheckFormInput(
                                date = today(),
                                type = ref.kind.allowedChecks.first(),
                                restartsInterval = true,
                            )
                        }

                        is CheckFormMode.Edit -> {
                            CheckFormInput.from(mode.check)
                        }

                        is CheckFormMode.Complete -> {
                            CheckFormInput.completing(mode.planned, today())
                        }
                    }
                CheckFormPresenter(scope, useCases, ref, mode, initial, platform.filePicker, pickTitle)
            },
            planPresenter = { planned ->
                PlanFormPresenter(
                    scope,
                    useCases,
                    ref,
                    planned,
                    planned?.let { PlanFormInput.from(it) } ?: PlanFormInput(type = ref.kind.allowedChecks.first()),
                )
            },
        )
    LaunchedEffect(ref) { presenter.refresh() }
    GearMaintenanceScreen(presenter, dialogs, onBack)
}
