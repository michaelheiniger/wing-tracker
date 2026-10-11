package ch.qscqlmpa.wingtracker.ui.undo

val TEXT = UndoText("Deleted", "Undo")

/** The pilot lets the notification go away. */
fun UndoCoordinator.expire() {
    snackbarHost.currentSnackbarData?.dismiss()
}

/** The pilot presses Undo. */
fun UndoCoordinator.pressUndo() {
    snackbarHost.currentSnackbarData?.performAction()
}
