package com.anish1050.ledger

import android.content.Context
import com.anish1050.ledger.capture.CaptureDispatcher
import com.anish1050.ledger.capture.parser.RuleStore
import com.anish1050.ledger.data.api.AuthStore
import com.anish1050.ledger.data.api.LedgerApi
import com.anish1050.ledger.data.db.LedgerDatabase
import com.anish1050.ledger.data.repo.BudgetRepository
import com.anish1050.ledger.data.repo.CategoryRepository
import com.anish1050.ledger.data.repo.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Manual wiring instead of a DI framework: one object, no codegen, nothing on
// the launch path until a screen asks for it.
class AppContainer(private val context: Context) {
    // Process-lifetime scope for work that must outlive the screen that started it (SMS backfill).
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: LedgerDatabase by lazy { LedgerDatabase.build(context) }
    val authStore: AuthStore by lazy { AuthStore(context) }
    val api: LedgerApi by lazy { LedgerApi(BuildConfig.API_BASE_URL, authStore) }
    val ruleStore: RuleStore by lazy { RuleStore(context, api) }

    val transactions: TransactionRepository by lazy { TransactionRepository(context, db.transactionDao()) }
    val categories: CategoryRepository by lazy { CategoryRepository(db.categoryDao()) }
    val budgets: BudgetRepository by lazy { BudgetRepository(db.budgetDao()) }

    val capture: CaptureDispatcher by lazy { CaptureDispatcher(ruleStore, transactions) }
}

val Context.container: AppContainer
    get() = (applicationContext as LedgerApp).container
