package bps.budget.persistence

class DataConfigurationException(
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Exception(message, cause) {
    constructor(cause: Throwable?) : this(null, cause)
}
