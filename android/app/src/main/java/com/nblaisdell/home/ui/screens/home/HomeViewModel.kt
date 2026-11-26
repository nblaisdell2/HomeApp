package com.nblaisdell.home.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nblaisdell.home.data.repository.ShoppingRepository
import com.nblaisdell.home.data.sync.SyncStatus
import com.nblaisdell.home.data.sync.SyncStatusTracker
import com.nblaisdell.home.ui.navigation.TopLevelDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime
import javax.inject.Inject

data class DashboardTile(val destination: TopLevelDestination, val summary: String)

data class HomeUiState(
    val greeting: String = "Home",
    val syncStatus: SyncStatus = SyncStatus.Syncing,
    val tiles: List<DashboardTile> = dashboardTiles(shoppingCount = 0),
) {
    val needsSetup: Boolean get() = syncStatus == SyncStatus.NotSetUp
}

fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

fun dashboardTiles(shoppingCount: Int) = listOf(
    DashboardTile(TopLevelDestination.Todo, "Today's to-dos and the weekly plan"),
    DashboardTile(TopLevelDestination.Calendar, "Upcoming dated to-dos"),
    DashboardTile(TopLevelDestination.Weather, "Today's forecast"),
    DashboardTile(
        TopLevelDestination.Shopping,
        when (shoppingCount) {
            0 -> "Nothing on the list"
            1 -> "1 item on the list"
            else -> "$shoppingCount items on the list"
        },
    ),
    DashboardTile(TopLevelDestination.Evercent, "Next automation run"),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    shoppingRepository: ShoppingRepository,
    tracker: SyncStatusTracker,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(tracker.status, shoppingRepository.activeCount) { status, count ->
            HomeUiState(
                greeting = greetingFor(LocalTime.now()),
                syncStatus = status,
                tiles = dashboardTiles(count),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
