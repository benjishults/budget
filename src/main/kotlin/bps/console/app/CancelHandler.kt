package bps.console.app

open class CancelException(
    message: String = "Canceling work in progress",
    val handler: CancelHandler,
    cause: Throwable? = null,
) : Exception(message, cause)

class TryAgainAtMostRecentMenuException(
    message: String = "Canceling work in progress",
    cause: Throwable? = null,
) : CancelException(message, TryAgainAtMostRecentMenu, cause)

class PopMostRecentMenuCancelException(
    message: String = "Canceling work in progress",
    cause: Throwable? = null,
) : CancelException(message, PopMostRecentMenu, cause)

class PopMenusCancelException(
    menusToPop: Int,
    message: String = "Canceling work in progress",
    cause: Throwable? = null,
) : CancelException(message, PopMenus(menusToPop), cause)

interface CancelHandler : (MenuSession) -> Unit

data object TryAgainAtMostRecentMenu : CancelHandler {
    override fun invoke(menuSession: MenuSession) {
    }
}

data object PopMostRecentMenu : CancelHandler {
    override fun invoke(menuSession: MenuSession) {
        menuSession.pop()
    }
}

data class PopMenus(val menusToPop: Int) : CancelHandler {
    override fun invoke(menuSession: MenuSession) {
        repeat(menusToPop) { menuSession.pop() }
    }
}
