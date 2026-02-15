package bps.budget

interface InitializingBudgetDao : AutoCloseable {

    /**
     * Ensures the persistent store is ready.
     *
     * The default implementation does nothing.
     */
    fun ensureTablesAndIndexes() {}

    /**
     * Ensures that resources held by the DAO are released.
     *
     * The default implementation does nothing.
     */
    override fun close() {}

}
