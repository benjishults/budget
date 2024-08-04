package bps.console.inputs

fun interface Prompt<out T> {
    fun getResult(): T
}
