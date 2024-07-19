package bps.budget.model

import bps.budget.persistence.AccountConfig

//sealed interface Account : AccountConfig {
//    val transactions: List<Transaction>
//
//    fun addTransaction(transaction: Transaction)
//}

//abstract class BaseAccount(
//    override val name: String,
//    override val description: String = "",
//    override val id: UUID = UUID.randomUUID(),
//    override val balance: Double = 0.0,
//) : Account {
//    override val transactions: MutableList<Transaction> = mutableListOf()
//
//    override fun addTransaction(transaction: Transaction) {
//        transactions.add(transaction)
////        balanceCents += transaction.amountCents
//        TODO("Not yet implemented")
//    }
//
//}

//class CategoryAccount(
//    name: String,
//    description: String = "",
//    id: UUID = UUID.randomUUID(),
//    balance: Double = 0.0,
//) : CategoryAccountConfig(name, description, id, balance) , Account {
//
//}
//
//class RealAccount(
//    name: String,
//    description: String? = null,
//    id: UUID = UUID.randomUUID(),
//) : BaseAccount(name, description, id) {
//
//    override fun addTransaction(transaction: Transaction) {
//        transactions.add(transaction)
//        TODO("Not yet implemented")
//    }
//
//}
//
//class DraftAccount(
//    name: String,
//    description: String? = null,
//    id: UUID = UUID.randomUUID(),
//) : BaseAccount(name, description, id) {
//
//
//    override fun addTransaction(transaction: Transaction) {
//        transactions.add(transaction)
//        TODO("Not yet implemented")
//    }
//
//}
