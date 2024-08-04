package bps.console.inputs

interface RecursivePrompt<T> : Prompt<T> {
    val prompts: List<Prompt<*>>
    val transformer: (List<*>) -> T
    val onError: (Throwable) -> T
        //        get() = { getResult() }
        get() = {
            throw it
        }

    /**
     * calls [onError] on exception
     */
    override fun getResult(): T =
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
        operator fun <T> invoke(
            prompts: List<Prompt<*>>,
            transformer: (List<*>) -> T,
        ): RecursivePrompt<T> =
            object : RecursivePrompt<T> {
                override val prompts: List<Prompt<*>> = prompts
                override val transformer: (List<*>) -> T = transformer
            }
    }
}
