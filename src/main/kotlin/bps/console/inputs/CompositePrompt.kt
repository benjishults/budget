package bps.console.inputs

@Deprecated(message = "Just give separate prompts")
interface CompositePrompt<T : Any> : Prompt<T> {
    val prompts: List<Prompt<*>>
    val transformer: (List<*>) -> T
    val onError: (Throwable) -> T?
        //        get() = { getResult() }
        get() = {
            throw it
        }

    /**
     * calls [onError] on exception
     */
    override fun getResult(): T? =
        try {
            transformer(
                prompts.map { innerPrompt: Prompt<*> ->
                    innerPrompt.getResult()
                },
            )
        } catch (ex: Exception) {
            onError(ex)
        }

    companion object {
        operator fun <T : Any> invoke(
            prompts: List<Prompt<*>>,
            transformer: (List<*>) -> T,
        ): CompositePrompt<T> =
            object : CompositePrompt<T> {
                override val prompts: List<Prompt<*>> = prompts
                override val transformer: (List<*>) -> T = transformer
            }
    }
}
