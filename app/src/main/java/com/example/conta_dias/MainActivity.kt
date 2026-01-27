package com.example.conta_dias

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.example.conta_dias.ui.theme.ContaDiasTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContaDiasTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConfigurationScreen(
                        modifier = Modifier.padding(innerPadding),
                        onUpdateWidget = { updateWidgets() }
                    )
                }
            }
        }
    }

    private fun updateWidgets() {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@MainActivity)
            val ids = manager.getGlanceIds(CounterWidget::class.java)
            ids.forEach { id ->
                CounterWidget().update(this@MainActivity, id)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(modifier: Modifier = Modifier, onUpdateWidget: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isPlural by remember { mutableStateOf(false) }
    var middleText by remember { mutableStateOf("Sem acidentes graves") }
    var useMonths by remember { mutableStateOf(false) }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var record by remember { mutableLongStateOf(0L) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)

    LaunchedEffect(Unit) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(CounterWidget::class.java)
        if (ids.isNotEmpty()) {
            val state = PreferencesGlanceStateDefinition.getDataStore(context, ids.first().toString()).data.first()
            isPlural = state[CounterWidget.Keys.IS_PLURAL] ?: false
            middleText = state[CounterWidget.Keys.MIDDLE_TEXT] ?: "Sem acidentes graves"
            useMonths = state[CounterWidget.Keys.USE_MONTHS] ?: false
            startDateMillis = state[CounterWidget.Keys.START_DATE] ?: System.currentTimeMillis()
            record = state[CounterWidget.Keys.RECORD] ?: 0L
            datePickerState.selectedDateMillis = startDateMillis
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        startDateMillis = it
                    }
                    showDatePicker = false
                }) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configuração do Widget", style = MaterialTheme.typography.headlineMedium)

        Text("Modo de tratamento:")
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !isPlural,
                onClick = { isPlural = false },
                label = { Text("Estou (Eu)") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = isPlural,
                onClick = { isPlural = true },
                label = { Text("Estamos (Nós)") }
            )
        }

        OutlinedTextField(
            value = middleText,
            onValueChange = { middleText = it },
            label = { Text("Texto Central (Ex: Sem acidentes)") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Unidade de tempo:")
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !useMonths,
                onClick = { useMonths = false },
                label = { Text("Dias") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = useMonths,
                onClick = { useMonths = true },
                label = { Text("Meses") }
            )
        }

        Text("Data de Início:")
        val dateDisplay = Instant.ofEpochMilli(startDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        Text("Contando desde: $dateDisplay", style = MaterialTheme.typography.bodyLarge)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    startDateMillis = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                    datePickerState.selectedDateMillis = startDateMillis
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Hoje")
            }
            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Escolher Data")
            }
        }

        HorizontalDivider()

        Text("Record Atual: $record ${if (useMonths) "meses" else "dias"}")
        
        Button(
            onClick = { record = 0L },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zerar Record")
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    val manager = GlanceAppWidgetManager(context)
                    val ids = manager.getGlanceIds(CounterWidget::class.java)
                    ids.forEach { id ->
                        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                            prefs.toMutablePreferences().apply {
                                this[CounterWidget.Keys.IS_PLURAL] = isPlural
                                this[CounterWidget.Keys.MIDDLE_TEXT] = middleText
                                this[CounterWidget.Keys.USE_MONTHS] = useMonths
                                this[CounterWidget.Keys.START_DATE] = startDateMillis
                                this[CounterWidget.Keys.RECORD] = record
                            }
                        }
                    }
                    onUpdateWidget()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Salvar e Atualizar Widget")
        }
    }
}
