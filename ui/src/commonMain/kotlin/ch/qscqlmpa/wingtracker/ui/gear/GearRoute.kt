package ch.qscqlmpa.wingtracker.ui.gear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.ui.gear.wing.WingFormPresenter
import ch.qscqlmpa.wingtracker.ui.gear.wing.WingFormScreen
import ch.qscqlmpa.wingtracker.ui.gear.wing.WingListPresenter
import ch.qscqlmpa.wingtracker.ui.gear.wing.WingListScreen
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

private sealed interface GearScreen {
    data object List : GearScreen

    data class Form(
        val editing: Wing?,
    ) : GearScreen
}

@Composable
fun GearRoute(useCases: GearUseCases) {
    val scope = rememberCoroutineScope()
    val listPresenter =
        remember {
            WingListPresenter(
                scope = scope,
                listWings = useCases.listWings,
                retireWing = useCases.retireWing,
                deleteWing = useCases.deleteWing,
                today = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
            )
        }
    var screen by remember { mutableStateOf<GearScreen>(GearScreen.List) }

    LaunchedEffect(Unit) { listPresenter.refresh() }

    when (val current = screen) {
        GearScreen.List -> {
            WingListScreen(
                presenter = listPresenter,
                onAdd = { screen = GearScreen.Form(editing = null) },
                onEdit = { screen = GearScreen.Form(editing = it) },
            )
        }

        is GearScreen.Form -> {
            val formPresenter =
                remember(current) {
                    WingFormPresenter(scope, useCases.registerWing, useCases.updateWingDetails, current.editing)
                }
            WingFormScreen(
                presenter = formPresenter,
                onClose = {
                    listPresenter.refresh()
                    screen = GearScreen.List
                },
            )
        }
    }
}
