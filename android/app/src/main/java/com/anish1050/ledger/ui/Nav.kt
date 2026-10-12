package com.anish1050.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navDeepLink
import com.anish1050.ledger.ui.add.AddExpenseScreen
import com.anish1050.ledger.ui.home.HomeScreen
import com.anish1050.ledger.ui.onboarding.OnboardingFlow
import com.anish1050.ledger.ui.report.ReportScreen
import com.anish1050.ledger.ui.review.ReviewQueueScreen
import com.anish1050.ledger.ui.settings.SettingsScreen
import com.anish1050.ledger.ui.theme.Graphite
import com.anish1050.ledger.ui.theme.Ink
import com.anish1050.ledger.ui.theme.Paper

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ADD = "add"
    const val REPORT = "report"
    const val REVIEW = "review"
    const val SETTINGS = "settings"
    const val ACCOUNTS = "accounts"
}

@Composable
fun LedgerNavHost(nav: NavHostController, startDestination: String) {
    NavHost(nav, startDestination = startDestination) {
        composable(Routes.ONBOARDING) { OnboardingFlow(onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }) }
        composable(Routes.HOME) { HomeScreen(onReview = { nav.navigate(Routes.REVIEW) }) }
        composable(Routes.ADD, deepLinks = listOf(navDeepLink { uriPattern = "ledger://add" })) { AddExpenseScreen(onDone = { nav.popBackStack() }) }
        composable(Routes.REPORT) { ReportScreen() }
        composable(Routes.REVIEW) { ReviewQueueScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.ACCOUNTS) { ReportScreen() }
    }
}

@Composable
fun BottomBar(nav: NavHostController) {
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    if (current == Routes.ONBOARDING || current == Routes.ADD) return

    fun go(route: String) = nav.navigate(route) { launchSingleTop = true; popUpTo(Routes.HOME) }

    Row(
        Modifier.fillMaxWidth().background(Paper).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab(Icons.AutoMirrored.Outlined.List, current == Routes.HOME, Modifier.weight(1f)) { go(Routes.HOME) }
        Tab(Icons.Outlined.AccountBalanceWallet, current == Routes.ACCOUNTS, Modifier.weight(1f)) { go(Routes.ACCOUNTS) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) {
                IconButton(onClick = { nav.navigate(Routes.ADD) }) { Text("+", color = Paper, fontSize = 22.sp) }
            }
        }
        Tab(Icons.Outlined.PieChart, current == Routes.REPORT, Modifier.weight(1f)) { go(Routes.REPORT) }
        Tab(Icons.Outlined.Settings, current == Routes.SETTINGS, Modifier.weight(1f)) { go(Routes.SETTINGS) }
    }
}

@Composable
private fun Tab(icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = null, tint = if (selected) Ink else Graphite) }
    }
}
