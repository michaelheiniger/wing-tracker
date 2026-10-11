package ch.qscqlmpa.wingtracker.ui.gear

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.GearMaintenanceRoute
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.MaintenanceUseCases
import ch.qscqlmpa.wingtracker.ui.gear.simple.SimpleGearFormPresenter
import ch.qscqlmpa.wingtracker.ui.gear.simple.SimpleGearFormScreen
import ch.qscqlmpa.wingtracker.ui.gear.simple.SimpleGearItem
import ch.qscqlmpa.wingtracker.ui.gear.simple.SimpleGearListPresenter
import ch.qscqlmpa.wingtracker.ui.gear.simple.SimpleGearListScreen
import ch.qscqlmpa.wingtracker.ui.gear.simple.simpleTexts
import ch.qscqlmpa.wingtracker.ui.gear.wing.WingFormPresenter
import ch.qscqlmpa.wingtracker.ui.gear.wing.WingFormScreen
import ch.qscqlmpa.wingtracker.ui.gear.wing.WingListPresenter
import ch.qscqlmpa.wingtracker.ui.gear.wing.WingListScreen
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.tab_harnesses
import ch.qscqlmpa.wingtracker.ui.resources.tab_reserves
import ch.qscqlmpa.wingtracker.ui.resources.tab_wings
import ch.qscqlmpa.wingtracker.ui.today
import org.jetbrains.compose.resources.stringResource

private sealed interface GearScreen {
    data object List : GearScreen

    data class WingForm(
        val editing: Wing?,
    ) : GearScreen

    data class SimpleForm(
        val kind: GearKind,
        val editing: SimpleGearItem?,
    ) : GearScreen

    data class Maintenance(
        val ref: GearRef,
    ) : GearScreen
}

@Composable
fun GearRoute(
    useCases: GearUseCases,
    maintenanceUseCases: MaintenanceUseCases,
    platform: PlatformServices,
    // Opens straight on this item's maintenance, e.g. when coming from the attention panel.
    initialRef: GearRef? = null,
    onInitialConsumed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val wingList =
        remember {
            WingListPresenter(
                scope = scope,
                listWings = useCases.listWings,
                retireWing = useCases.retireWing,
                deleteWing = useCases.deleteWing,
                getMaintenanceStatuses = useCases.getMaintenanceStatuses,
                today = ::today,
            )
        }
    val harnessList =
        remember { SimpleGearListPresenter(scope, useCases.harnesses, useCases.getMaintenanceStatuses, ::today) }
    val reserveList =
        remember { SimpleGearListPresenter(scope, useCases.reserves, useCases.getMaintenanceStatuses, ::today) }
    var tab by remember { mutableStateOf(initialRef?.kind ?: GearKind.Wing) }
    var screen by remember {
        mutableStateOf<GearScreen>(initialRef?.let { GearScreen.Maintenance(it) } ?: GearScreen.List)
    }
    val backToList = {
        wingList.refresh()
        harnessList.refresh()
        reserveList.refresh()
        screen = GearScreen.List
    }

    LaunchedEffect(Unit) {
        wingList.refresh()
        harnessList.refresh()
        reserveList.refresh()
        if (initialRef != null) onInitialConsumed()
    }

    when (val current = screen) {
        GearScreen.List -> {
            Column {
                GearTabs(selected = tab, onSelected = { tab = it })
                when (tab) {
                    GearKind.Wing -> {
                        WingListScreen(
                            presenter = wingList,
                            onAdd = { screen = GearScreen.WingForm(editing = null) },
                            onEdit = { screen = GearScreen.WingForm(editing = it) },
                            onOpenMaintenance = { screen = GearScreen.Maintenance(it.ref) },
                        )
                    }

                    GearKind.Harness -> {
                        SimpleList(GearKind.Harness, harnessList) { screen = it }
                    }

                    GearKind.Reserve -> {
                        SimpleList(GearKind.Reserve, reserveList) { screen = it }
                    }
                }
            }
        }

        is GearScreen.WingForm -> {
            val formPresenter =
                remember(current) {
                    WingFormPresenter(scope, useCases.registerWing, useCases.updateWingDetails, current.editing)
                }
            WingFormScreen(presenter = formPresenter, onClose = backToList)
        }

        is GearScreen.SimpleForm -> {
            val ops = if (current.kind == GearKind.Reserve) useCases.reserves else useCases.harnesses
            val formPresenter = remember(current) { SimpleGearFormPresenter(scope, ops, current.editing) }
            SimpleGearFormScreen(presenter = formPresenter, texts = current.kind.simpleTexts(), onClose = backToList)
        }

        is GearScreen.Maintenance -> {
            GearMaintenanceRoute(
                ref = current.ref,
                useCases = maintenanceUseCases,
                platform = platform,
                onBack = backToList,
            )
        }
    }
}

@Composable
private fun SimpleList(
    kind: GearKind,
    presenter: SimpleGearListPresenter,
    navigate: (GearScreen) -> Unit,
) {
    SimpleGearListScreen(
        presenter = presenter,
        texts = kind.simpleTexts(),
        onAdd = { navigate(GearScreen.SimpleForm(kind, editing = null)) },
        onEdit = { navigate(GearScreen.SimpleForm(kind, editing = it)) },
        onOpenMaintenance = { navigate(GearScreen.Maintenance(it.ref)) },
    )
}

@Composable
private fun GearTabs(
    selected: GearKind,
    onSelected: (GearKind) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = GearKind.entries.indexOf(selected)) {
        GearKind.entries.forEach { kind ->
            Tab(
                selected = kind == selected,
                onClick = { onSelected(kind) },
                text = {
                    Text(
                        stringResource(
                            when (kind) {
                                GearKind.Wing -> Res.string.tab_wings
                                GearKind.Harness -> Res.string.tab_harnesses
                                GearKind.Reserve -> Res.string.tab_reserves
                            },
                        ),
                    )
                },
            )
        }
    }
}
