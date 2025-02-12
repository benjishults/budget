package bps.budget.persistence.jdbc

import bps.budget.persistence.BudgetConfig

data class JdbcConfig(
    override val budgetName: String? = null,
    val database: String = "budget",
    val schema: String = "public",
    val dbProvider: String = "postgresql",
    val port: String = "5432",
    val host: String = "localhost",
    val user: String? = null,
    val password: String? = null,
) : BudgetConfig
