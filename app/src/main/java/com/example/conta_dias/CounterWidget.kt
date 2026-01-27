package com.example.conta_dias

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.currentState
import androidx.glance.unit.ColorProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class CounterWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val startDateMillis = prefs[Keys.START_DATE] ?: System.currentTimeMillis()
            val record = prefs[Keys.RECORD] ?: 0L
            val isPlural = prefs[Keys.IS_PLURAL] ?: false
            val middleText = prefs[Keys.MIDDLE_TEXT] ?: "Sem acidentes graves"
            val useMonths = prefs[Keys.USE_MONTHS] ?: false

            val startDate = Instant.ofEpochMilli(startDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            val today = LocalDate.now()
            
            val rawDiff = if (useMonths) {
                ChronoUnit.MONTHS.between(startDate, today)
            } else {
                ChronoUnit.DAYS.between(startDate, today)
            }
            
            val diff = abs(rawDiff)

            // Update record if current diff is higher
            val currentRecord = if (diff > record) diff else record

            WidgetContent(
                isPlural = isPlural,
                count = diff,
                middleText = middleText,
                record = currentRecord,
                useMonths = useMonths
            )
        }
    }

    @Composable
    private fun WidgetContent(
        isPlural: Boolean,
        count: Long,
        middleText: String,
        record: Long,
        useMonths: Boolean
    ) {
        val unit = if (useMonths) {
            if (count == 1L) "mês" else "meses"
        } else {
            if (count == 1L) "dia" else "dias"
        }
        
        val recordUnit = if (useMonths) {
            if (record == 1L) "mês" else "meses"
        } else {
            if (record == 1L) "dia" else "dias"
        }

        val prefix1 = if (isPlural) "Estamos há" else "Estou há"
        val prefix3 = if (isPlural) "Nosso record é" else "Meu record é"

        // Amarelo claro e Vermelho
        val lightYellow = Color(0xFFFFFFE0)
        val redColor = Color(0xFFFF0000)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(lightYellow)
                .padding(8.dp),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "$prefix1 $count $unit",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color.Black)
                )
            )
            Text(
                text = middleText,
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(redColor)
                ),
                modifier = GlanceModifier.padding(vertical = 4.dp)
            )
            Text(
                text = "$prefix3 $record $recordUnit",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color.Black)
                )
            )
        }
    }

    object Keys {
        val START_DATE = longPreferencesKey("start_date")
        val RECORD = longPreferencesKey("record")
        val IS_PLURAL = booleanPreferencesKey("is_plural")
        val MIDDLE_TEXT = stringPreferencesKey("middle_text")
        val USE_MONTHS = booleanPreferencesKey("use_months")
    }
}

class CounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CounterWidget()
}
