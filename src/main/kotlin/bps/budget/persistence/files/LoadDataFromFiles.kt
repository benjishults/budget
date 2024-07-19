package bps.budget.persistence.files

import bps.budget.data.BudgetData
import bps.budget.persistence.AccountsConfig
import bps.budget.persistence.FileConfig
import bps.config.ConfigurationHelper
import com.uchuhimo.konf.source.LoadException
import com.uchuhimo.konf.toValue

/**
 * Loads data from files to produce an instance of [BudgetData]
 */
class LoadDataFromFiles {

}

/**
 * @throws LoadException if the files aren't set up properly
 */
fun loadAccountsFromFiles(fileConfig: FileConfig): BudgetData =
    ConfigurationHelper(sequenceOf("${fileConfig.dataDirectory}/accounts.yml"))
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
