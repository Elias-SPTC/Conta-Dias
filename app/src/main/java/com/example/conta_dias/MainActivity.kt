package com.example.conta_dias

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.example.conta_dias.ui.theme.ContaDiasTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val startDate: Long,
    val endDate: Long,
    val count: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContaDiasTheme {
                var currentScreen by remember { mutableStateOf("config") }
                
                if (currentScreen == "history") {
                    HistoryScreen(onBack = { currentScreen = "config" })
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        ConfigurationScreen(
                            modifier = Modifier.padding(innerPadding),
                            onUpdateWidget = { updateWidgets() },
                            onOpenHistory = { currentScreen = "history" }
                        )
                    }
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
fun ConfigurationScreen(
    modifier: Modifier = Modifier, 
    onUpdateWidget: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val todayMillis = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    var isPlural by remember { mutableStateOf(false) }
    var middleText by remember { mutableStateOf("Sem acidentes graves") }
    var useMonths by remember { mutableStateOf(false) }
    var startDateMillis by remember { mutableLongStateOf(todayMillis) }
    var endDateMillis by remember { mutableStateOf<Long?>(null) }
    var record by remember { mutableLongStateOf(0L) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    
    val startDatePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
    val endDatePickerState = rememberDatePickerState(initialSelectedDateMillis = endDateMillis ?: todayMillis)

    LaunchedEffect(Unit) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(CounterWidget::class.java)
        if (ids.isNotEmpty()) {
            val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, ids.first())
            isPlural = state[CounterWidget.Keys.IS_PLURAL] ?: false
            middleText = state[CounterWidget.Keys.MIDDLE_TEXT] ?: "Sem acidentes graves"
            useMonths = state[CounterWidget.Keys.USE_MONTHS] ?: false
            startDateMillis = state[CounterWidget.Keys.START_DATE] ?: todayMillis
            endDateMillis = state[CounterWidget.Keys.END_DATE]
            val savedRecord = state[CounterWidget.Keys.RECORD] ?: 0L
            
            // Calcula se a contagem atual supera o recorde salvo para exibição
            val s = Instant.ofEpochMilli(startDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val today = LocalDate.now()
            val liveDiff = abs(if (useMonths) ChronoUnit.MONTHS.between(s, today) else ChronoUnit.DAYS.between(s, today))
            record = if (liveDiff > savedRecord && endDateMillis == null) liveDiff else savedRecord
            
            startDatePickerState.selectedDateMillis = startDateMillis
            endDatePickerState.selectedDateMillis = endDateMillis ?: todayMillis
        }
    }

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDatePickerState.selectedDateMillis?.let { startDateMillis = it }
                    showStartDatePicker = false
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancelar") }
            }
        ) { DatePicker(state = startDatePickerState) }
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDateMillis = endDatePickerState.selectedDateMillis
                    showEndDatePicker = false
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancelar") }
            }
        ) { DatePicker(state = endDatePickerState) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Configuração", style = MaterialTheme.typography.headlineMedium)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Modo:", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            FilterChip(
                selected = !isPlural, 
                onClick = { isPlural = false }, 
                label = { Text("Eu", style = MaterialTheme.typography.bodyLarge) },
                modifier = Modifier.height(48.dp)
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = isPlural, 
                onClick = { isPlural = true }, 
                label = { Text("Nós", style = MaterialTheme.typography.bodyLarge) },
                modifier = Modifier.height(48.dp)
            )
        }

        OutlinedTextField(
            value = middleText,
            onValueChange = { middleText = it },
            label = { Text("Texto Central") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleLarge
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Unidade:", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            FilterChip(
                selected = !useMonths, 
                onClick = { useMonths = false }, 
                label = { Text("Dias", style = MaterialTheme.typography.bodyLarge) },
                modifier = Modifier.height(48.dp)
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = useMonths, 
                onClick = { useMonths = true }, 
                label = { Text("Meses", style = MaterialTheme.typography.bodyLarge) },
                modifier = Modifier.height(48.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Início:", style = MaterialTheme.typography.titleLarge)
                val startDisplay = Instant.ofEpochMilli(startDateMillis).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                Text(startDisplay, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            Button(onClick = { startDateMillis = todayMillis; startDatePickerState.selectedDateMillis = todayMillis }, modifier = Modifier.height(48.dp)) { Text("Hoje") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { showStartDatePicker = true }, modifier = Modifier.height(48.dp)) { Text("Mudar") }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Final:", style = MaterialTheme.typography.titleLarge)
                val endDisplay = endDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) } ?: "Hoje"
                Text(endDisplay, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            Button(onClick = { endDateMillis = null }, modifier = Modifier.height(48.dp)) { Text("Limpar") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { showEndDatePicker = true }, modifier = Modifier.height(48.dp)) { Text("Definir") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Record: $record", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(
                onClick = { record = 0L },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.height(48.dp)
            ) { Text("Zerar") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onOpenHistory, modifier = Modifier.height(48.dp)) { Text("Histórico") }
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        val manager = GlanceAppWidgetManager(context)
                        val ids = manager.getGlanceIds(CounterWidget::class.java)
                        
                        val s = Instant.ofEpochMilli(startDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        val today = LocalDate.now()
                        val diff = if (useMonths) ChronoUnit.MONTHS.between(s, today) else ChronoUnit.DAYS.between(s, today)
                        val finalCount = abs(diff)

                        ids.forEach { id ->
                            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                                val currentHistoryJson = prefs[CounterWidget.Keys.HISTORY_JSON] ?: "[]"
                                val historyArray = JSONArray(currentHistoryJson)
                                historyArray.put(JSONObject().apply {
                                    put("id", UUID.randomUUID().toString())
                                    put("start", startDateMillis)
                                    put("end", todayMillis)
                                    put("count", finalCount)
                                })
                                
                                val newRecord = if (finalCount > record) finalCount else record
                                
                                prefs.toMutablePreferences().apply {
                                    this[CounterWidget.Keys.START_DATE] = todayMillis
                                    this[CounterWidget.Keys.RECORD] = newRecord
                                    this[CounterWidget.Keys.HISTORY_JSON] = historyArray.toString()
                                    remove(CounterWidget.Keys.END_DATE)
                                }
                            }
                        }
                        startDateMillis = todayMillis
                        endDateMillis = null
                        record = if (finalCount > record) finalCount else record
                        onUpdateWidget()
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) { Text("Novo", fontSize = 18.sp) }

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
                                    if (endDateMillis != null) this[CounterWidget.Keys.END_DATE] = endDateMillis!! else remove(CounterWidget.Keys.END_DATE)
                                    this[CounterWidget.Keys.RECORD] = record
                                }
                            }
                        }
                        onUpdateWidget()
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("Salvar", fontSize = 18.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var historyItems by remember { mutableStateOf(listOf<HistoryItem>()) }
    var isLoading by remember { mutableStateOf(true) }
    var useMonths by remember { mutableStateOf(false) }

    var editingItem by remember { mutableStateOf<HistoryItem?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(CounterWidget::class.java)
        if (ids.isNotEmpty()) {
            val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, ids.first())
            val json = state[CounterWidget.Keys.HISTORY_JSON] ?: "[]"
            useMonths = state[CounterWidget.Keys.USE_MONTHS] ?: false
            val list = mutableListOf<HistoryItem>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(HistoryItem(
                    obj.getString("id"),
                    obj.getLong("start"),
                    obj.getLong("end"),
                    obj.getLong("count")
                ))
            }
            historyItems = list
        }
        isLoading = false
    }

    fun saveHistory(newList: List<HistoryItem>) {
        scope.launch {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(CounterWidget::class.java)
            val jsonArray = JSONArray()
            newList.forEach { item ->
                jsonArray.put(JSONObject().apply {
                    put("id", item.id)
                    put("start", item.startDate)
                    put("end", item.endDate)
                    put("count", item.count)
                })
            }
            val jsonString = jsonArray.toString()
            
            val maxHistory = if (newList.isEmpty()) 0L else newList.maxOf { it.count }

            ids.forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[CounterWidget.Keys.HISTORY_JSON] = jsonString
                        val currentRecord = this[CounterWidget.Keys.RECORD] ?: 0L
                        if (maxHistory > currentRecord) {
                            this[CounterWidget.Keys.RECORD] = maxHistory
                        }
                    }
                }
                CounterWidget().update(context, id)
            }
            historyItems = newList
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Voltar") }
                },
                actions = {
                    IconButton(onClick = {
                        val normalizedNow = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                        editingItem = HistoryItem(startDate = normalizedNow, endDate = normalizedNow, count = 0L)
                        showEditDialog = true
                    }) { Icon(Icons.Default.Edit, "Add") }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(historyItems) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                                val s = Instant.ofEpochMilli(item.startDate).atZone(ZoneOffset.UTC).toLocalDate().format(fmt)
                                val e = Instant.ofEpochMilli(item.endDate).atZone(ZoneOffset.UTC).toLocalDate().format(fmt)
                                Text("$s - $e", style = MaterialTheme.typography.bodySmall)
                                Text("${item.count} ${if (useMonths) "meses" else "dias"}", style = MaterialTheme.typography.titleSmall)
                            }
                            IconButton(onClick = { editingItem = item; showEditDialog = true }) { Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { saveHistory(historyItems.filter { it.id != item.id }) }) { Icon(Icons.Default.Delete, "Deletar", tint = Color.Red, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && editingItem != null) {
        EditHistoryDialog(
            item = editingItem!!,
            useMonths = useMonths,
            onDismiss = { showEditDialog = false },
            onConfirm = { updatedItem ->
                val exists = historyItems.any { it.id == updatedItem.id }
                val newList = if (exists) {
                    historyItems.map { if (it.id == updatedItem.id) updatedItem else it }
                } else {
                    historyItems + updatedItem
                }
                saveHistory(newList)
                showEditDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHistoryDialog(item: HistoryItem, useMonths: Boolean, onDismiss: () -> Unit, onConfirm: (HistoryItem) -> Unit) {
    var startDate by remember { mutableLongStateOf(item.startDate) }
    var endDate by remember { mutableLongStateOf(item.endDate) }
    
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(onDismissRequest = { showStartPicker = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { startDate = it }; showStartPicker = false }) { Text("OK") } }) { DatePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(onDismissRequest = { showEndPicker = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { endDate = it }; showEndPicker = false }) { Text("OK") } }) { DatePicker(state = state) }
    }

    val calculatedCount = remember(startDate, endDate, useMonths) {
        val s = Instant.ofEpochMilli(startDate).atZone(ZoneOffset.UTC).toLocalDate()
        val e = Instant.ofEpochMilli(endDate).atZone(ZoneOffset.UTC).toLocalDate()
        if (useMonths) {
            ChronoUnit.MONTHS.between(s, e)
        } else {
            ChronoUnit.DAYS.between(s, e)
        }
    }.let { abs(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Intervalo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                Column {
                    Text("Início", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(Instant.ofEpochMilli(startDate).atZone(ZoneOffset.UTC).toLocalDate().format(fmt))
                    }
                }
                Column {
                    Text("Fim", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(Instant.ofEpochMilli(endDate).atZone(ZoneOffset.UTC).toLocalDate().format(fmt))
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Contagem calculada:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "$calculatedCount ${if (useMonths) "meses" else "dias"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(item.copy(startDate = startDate, endDate = endDate, count = calculatedCount)) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
