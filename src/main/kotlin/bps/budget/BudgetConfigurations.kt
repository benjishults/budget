package bps.budget

import bps.budget.persistence.PersistenceConfiguration
import bps.config.ConfigurationHelper
import io.github.nhubbard.konf.Config
import io.github.nhubbard.konf.toValue

interface BudgetConfigurations {
    val persistence: PersistenceConfiguration
    val config: Config

    companion object {
        /**
         * Load configuration from the given files with the later files overriding the later.  Highest
         * precedence goes to env vars and system properties.
         */
        operator fun invoke(filePaths: Sequence<String>): BudgetConfigurations =
            object : BudgetConfigurations {
                override val config: Config =
                    ConfigurationHelper(filePaths)
                        .config
                override val persistence: PersistenceConfiguration =
                    config
                        .at("persistence")
                        .toValue()
            }
    }
}

