package com.pass.diary.presentation.view.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Gray
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pass.diary.presentation.intent.MainIntent
import com.pass.diary.presentation.state.MainState
import com.pass.diary.presentation.viewmodel.MainViewModel
import org.koin.compose.getKoin


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val viewModel: MainViewModel = getKoin().get()
    val state by viewModel.state.collectAsState()

    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(viewModel, state) }
    ) {
        Box(Modifier.padding(it)) {
            NavigationGraph(navController = navController, state = state)
        }
    }
}

@Composable
fun BottomNavigationBar(
    viewModel: MainViewModel,
    state: MainState
) {
    val items = listOf(
        MainIntent.Timeline,
        MainIntent.Calendar,
        MainIntent.Analysis,
        MainIntent.Settings
    )

    androidx.compose.material.BottomNavigation(
        backgroundColor = Color.White,
        contentColor = Color(0xFF3F414E)
    ) {
        items.forEach { item ->
            BottomNavigationItem(
                icon = {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = stringResource(id = item.title),
                        modifier = Modifier
                            .width(26.dp)
                            .height(26.dp)
                    )
                },
                label = { Text(stringResource(id = item.title), fontSize = 9.sp) },
                selectedContentColor = MaterialTheme.colors.primary,
                unselectedContentColor = Gray,
                selected = state.selectedNavItem == item,
                alwaysShowLabel = false,

                onClick= { viewModel.onNavItemSelected(item) }
            )
        }
    }
}

@Composable
fun NavigationGraph(navController: NavHostController, state: MainState) {
    NavHost(navController = navController, startDestination = MainIntent.Timeline.screenRoute) {
        composable(MainIntent.Timeline.screenRoute) {
            TimelineScreen()
        }
        composable(MainIntent.Calendar.screenRoute) {
            CalendarScreen()
        }
        composable(MainIntent.Analysis.screenRoute) {
            AnalysisScreen()
        }
        composable(MainIntent.Settings.screenRoute) {
            SettingsScreen()
        }
    }

    navController.navigate(state.selectedNavItem.screenRoute) {
        navController.graph.startDestinationRoute?.let {
            popUpTo(it) { saveState = true }
        }
        launchSingleTop = true
        restoreState = true
    }
}