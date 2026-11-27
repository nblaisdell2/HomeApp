package com.nblaisdell.home.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nblaisdell.home.MainActivity
import com.nblaisdell.home.R
import com.nblaisdell.home.data.repository.ShoppingRepository
import com.nblaisdell.home.di.ApplicationScope
import com.nblaisdell.home.ui.quickadd.QuickAddActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Home-screen widget (HOME-68): how many items are on the shopping list, and a "+" that opens
 * the quick-add dialog. Tapping the rest of the widget opens the app.
 */
class ShoppingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .shoppingRepository()
        val initial = repository.activeCount.first()
        provideContent {
            // Collected inside the composition: while the widget session is alive, updates
            // recompose this content rather than calling provideGlance again.
            val count by repository.activeCount.collectAsState(initial)
            GlanceTheme { WidgetContent(count) }
        }
    }

    @Composable
    private fun WidgetContent(count: Int) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "Shopping",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
                Text(
                    countLabel(count),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
            Box(
                modifier = GlanceModifier
                    .size(44.dp)
                    .cornerRadius(22.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .clickable(actionStartActivity<QuickAddActivity>())
                    .semantics { contentDescription = "Add shopping item" },
                contentAlignment = Alignment.Center,
            ) {
                Image(ImageProvider(R.drawable.ic_add_shopping), contentDescription = null)
            }
        }
    }
}

internal fun countLabel(count: Int) = when (count) {
    0 -> "Nothing on the list"
    1 -> "1 item"
    else -> "$count items"
}

class ShoppingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun shoppingRepository(): ShoppingRepository
}

/**
 * Redraws the widget whenever the number of items on the list changes, whether the change was
 * made here, in quick add, or pulled from the other device by sync.
 */
@Singleton
class ShoppingWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ShoppingRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            // The first value is what the widget already shows when the process starts.
            repository.activeCount.distinctUntilChanged().drop(1).collect {
                ShoppingWidget().updateAll(context)
            }
        }
    }
}
