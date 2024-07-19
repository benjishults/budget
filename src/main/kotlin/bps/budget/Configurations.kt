package bps.budget

import bps.budget.persistence.PersistenceConfiguration
import bps.config.ConfigurationHelper
import com.uchuhimo.konf.toValue

interface BudgetConfigurations {
    val persistence: PersistenceConfiguration

    companion object {
        operator fun invoke(filePaths: Sequence<String>): BudgetConfigurations =
            object : BudgetConfigurations {
                override val persistence: PersistenceConfiguration =
                    ConfigurationHelper(filePaths)
                        .config
                        .at("persistence")
                        .toValue()
            }
    }
}

