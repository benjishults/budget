package bps.budget.persistence.files

import bps.budget.data.BudgetData
import bps.budget.persistence.AccountsConfig
import bps.budget.persistence.FileConfig
import bps.config.ConfigurationHelper
import bps.config.convertToPath
import io.github.nhubbard.konf.source.LoadException
import io.github.nhubbard.konf.toValue

/**
 * Loads data from files to produce an instance of [BudgetData]
 */
class LoadDataFromFiles {

}

/**
 * @throws LoadException if the files aren't set up properly
 */
fun loadAccountsFromFiles(fileConfig: FileConfig): BudgetData =
    ConfigurationHelper(sequenceOf("${convertToPath(fileConfig.dataDirectory)}/accounts.yml"))
        .config
        .at("accounts")
        .toValue<AccountsConfig>()
        .let { accountsConfig: AccountsConfig ->
            accountsConfig.toBudgetData()
        }

//fun loadAccountFromCsv(): Account {
//
//}

fun loadAccounts() {

}
