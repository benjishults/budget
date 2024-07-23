package bps.budget.persistence

class DataConfigurationException(
    override val message: String?,
    override val cause: Throwable?,
) : Exception(message, cause) {
    constructor(cause: Throwable?) : this(null, cause)
}
