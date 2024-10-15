package bps.console.inputs

fun interface Prompt<out T : Any> {
    fun getResult(): T?
}
