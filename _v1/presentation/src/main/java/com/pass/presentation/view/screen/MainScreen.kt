package com.pass.presentation.view.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.Gray
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pass.presentation.intent.MainIntent
import com.pass.presentation.state.route.MainRouteState
import com.pass.presentation.state.screen.LockState
import com.pass.presentation.ui.theme.BottomNavigationContentColor
import com.pass.presentation.ui.theme.Fluorescent
import com.pass.presentation.viewmodel.MainViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {

    val mainState = viewModel.collectAsState().value

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    MainScreen(
        navController = navController,
        currentRoute = currentRoute,
        password = mainState.password,
        lockState = mainState.lock,
        onUnLock = { viewModel.processIntent(MainIntent.UnLock) }
    )
}

@Composable
fun MainScreen(
    navController: NavHostController,
    currentRoute: String?,
    password: String,
    lockState: LockState,
    onUnLock: () -> Unit
) {
    when (lockState) {
        LockState.UnLock -> {
            Scaffold(
                bottomBar = { BottomNavigationBar(navController, currentRoute) }
            ) {
                Box(
                    Modifier
                        .padding(it)
                        .background(Color.White)
                ) {
                    NavigationGraph(navController = navController)
                }
            }
        }

        LockState.Lock -> {
            LockScreen(
                password = password,
                onComplete = { onUnLock() }
            )
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    currentRoute: String?
) {
    val items = listOf(
        MainRouteState.Timeline,
        MainRouteState.Calendar,
        MainRouteState.Analysis,
        MainRouteState.Settings
    )

    NavigationBar(
        containerColor = Color.White,
        contentColor = BottomNavigationContentColor
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = stringResource(id = item.title),
                        tint = if (currentRoute == item.screenRoute) Fluorescent else Black,
                        modifier = Modifier
                            .width(26.dp)
                            .height(26.dp)
                    )
                },
                label = { Text(stringResource(id = item.title), fontSize = 9.sp) },
                selected = currentRoute == item.screenRoute,
                alwaysShowLabel = false,
                onClick = {
                    navController.navigate(item.screenRoute) {
                        navController.graph.startDestinationRoute?.let {
                            popUpTo(it) { saveState = true }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = Black,
                    unselectedTextColor = Gray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun NavigationGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = MainRouteState.Timeline.screenRoute) {
        composable(MainRouteState.Timeline.screenRoute) {
            TimelineScreen()
        }
        composable(MainRouteState.Calendar.screenRoute) {
            CalendarScreen()
        }
        composable(MainRouteState.Analysis.screenRoute) {
            AnalysisScreen()
        }
        composable(MainRouteState.Settings.screenRoute) {
            SettingsScreen()
        }
    }
}