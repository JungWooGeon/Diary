package com.pass.presentation.view.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.Gray
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pass.presentation.state.MainState
import com.pass.presentation.ui.theme.BottomNavigationContentColor
import com.pass.presentation.ui.theme.Fluorescent

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = { BottomNavigationBar(navController, currentRoute) }
    ) {
        Box(Modifier.padding(it).background(Color.White)) {
            NavigationGraph(navController = navController)
        }
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    currentRoute: String?
) {
    val items = listOf(
        MainState.Timeline,
        MainState.Calendar,
        MainState.Analysis,
        MainState.Settings
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
                        tint = if (currentRoute == item.screenRoute) Fluorescent else Color.Black,
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
    NavHost(navController = navController, startDestination = MainState.Timeline.screenRoute) {
        composable(MainState.Timeline.screenRoute) {
            TimelineScreen()
        }
        composable(MainState.Calendar.screenRoute) {
            CalendarScreen()
        }
        composable(MainState.Analysis.screenRoute) {
            AnalysisScreen()
        }
        composable(MainState.Settings.screenRoute) {
            SettingsScreen()
        }
    }
}