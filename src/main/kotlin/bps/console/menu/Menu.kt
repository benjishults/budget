package bps.console.menu

@DslMarker
annotation class MenuDslElement

@MenuDslElement
interface Menu {

    val header: String? get() = null
    val prompt: String? get() = null
    val items: List<MenuItem> get() = emptyList()
}

fun menuBuilder(
    header: String? = null,
    prompt: String? = null,
    items: MutableList<MenuItem>.() -> Unit,
): Menu =
    object : Menu {
        override val header: String? =
            header
        override val prompt: String? =
            prompt
        override val items: List<MenuItem> =
            mutableListOf<MenuItem>()
                .apply { items() }
                .toList()
    }
