package com.anish1050.ledger.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import com.anish1050.ledger.ui.motion.Haptic
import com.anish1050.ledger.ui.motion.Motion
import com.anish1050.ledger.ui.motion.pressable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
}

private val TABS = setOf(Routes.HOME, Routes.REPORT, Routes.SETTINGS)

@Composable
fun LedgerNavHost(nav: NavHostController, startDestination: String) {
    // Tabs cross-fade like an iOS tab bar; pushed screens slide in from the
    // right; Add rises from the bottom like a sheet. All on springs.
    NavHost(
        nav,
        startDestination = startDestination,
        enterTransition = {
            if (initialState.destination.route in TABS && targetState.destination.route in TABS) fadeIn(Motion.screenFade)
            else slideInHorizontally(Motion.screen) { it / 3 } + fadeIn(Motion.screenFade)
        },
        exitTransition = {
            if (initialState.destination.route in TABS && targetState.destination.route in TABS) fadeOut(Motion.screenFade)
            else slideOutHorizontally(Motion.screen) { -it / 8 } + fadeOut(Motion.screenFade)
        },
        popEnterTransition = { slideInHorizontally(Motion.screen) { -it / 8 } + fadeIn(Motion.screenFade) },
        popExitTransition = { slideOutHorizontally(Motion.screen) { it / 3 } + fadeOut(Motion.screenFade) },
    ) {
        composable(Routes.ONBOARDING) { OnboardingFlow(onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }) }
        composable(Routes.HOME) { HomeScreen(onReview = { nav.navigate(Routes.REVIEW) }) }
        composable(
            Routes.ADD,
            deepLinks = listOf(navDeepLink { uriPattern = "ledger://add" }),
            enterTransition = { slideInVertically(Motion.screen) { it } },
            exitTransition = { slideOutVertically(Motion.screen) { it } },
            popExitTransition = { slideOutVertically(Motion.screen) { it } },
        ) { AddExpenseScreen(onDone = { nav.popBackStack() }) }
        composable(Routes.REPORT) { ReportScreen() }
        composable(Routes.REVIEW) { ReviewQueueScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen() }
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
        BottomTab(Icons.AutoMirrored.Outlined.List, current == Routes.HOME, Modifier.weight(1f)) { go(Routes.HOME) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(48.dp).pressable(Haptic.Confirm, pressedScale = 0.88f) { nav.navigate(Routes.ADD) }
                    .shadow(8.dp, CircleShape).background(Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("+", color = Paper, fontSize = 24.sp) }
        }
        BottomTab(Icons.Outlined.PieChart, current == Routes.REPORT, Modifier.weight(1f)) { go(Routes.REPORT) }
        BottomTab(Icons.Outlined.Settings, current == Routes.SETTINGS, Modifier.weight(1f)) { go(Routes.SETTINGS) }
    }
}

@Composable
private fun BottomTab(icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) Ink else Graphite, label = "tab")
    Box(modifier.height(48.dp).pressable(Haptic.Tick, pressedScale = 0.85f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}
