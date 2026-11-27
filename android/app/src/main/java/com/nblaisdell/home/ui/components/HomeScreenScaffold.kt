package com.nblaisdell.home.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Standard screen frame: a top app bar with [title] and optional [actions], an optional FAB
 * and snackbar host, and the screen content. Every top-level screen uses this so they look and behave alike.
 * The navigation bar/rail around it is drawn by HomeNavHost, not here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = navigationIcon,
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            content()
        }
    }
}
