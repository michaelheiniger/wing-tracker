package ch.qscqlmpa.wingtracker.ui.gear.wing.maintenance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.check_pick_title
import ch.qscqlmpa.wingtracker.ui.today
import org.jetbrains.compose.resources.stringResource

@Composable
fun WingMaintenanceRoute(
    wingId: WingId,
    useCases: MaintenanceUseCases,
    platform: PlatformServices,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pickTitle = stringResource(Res.string.check_pick_title)
    val presenter = remember(wingId) { WingMaintenancePresenter(scope, wingId, useCases, platform.fileOpener) }
    val dialogs =
        MaintenanceDialogs(
            checkPresenter = { mode ->
                val initial =
                    when (mode) {
                        CheckFormMode.Record -> CheckFormInput(date = today())
                        is CheckFormMode.Edit -> CheckFormInput.from(mode.check)
                        is CheckFormMode.Complete -> CheckFormInput.completing(mode.planned, today())
                    }
                CheckFormPresenter(scope, useCases, wingId, mode, initial, platform.filePicker, pickTitle)
            },
            planPresenter = { planned ->
                PlanFormPresenter(
                    scope,
                    useCases,
                    wingId,
                    planned,
                    planned?.let { PlanFormInput.from(it) } ?: PlanFormInput(),
                )
            },
        )
    LaunchedEffect(wingId) { presenter.refresh() }
    WingMaintenanceScreen(presenter, dialogs, onBack)
}
