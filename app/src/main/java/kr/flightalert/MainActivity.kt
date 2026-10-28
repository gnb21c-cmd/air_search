package kr.flightalert

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.UUID
import kr.flightalert.core.PricePoint
import kr.flightalert.core.Skyscanner
import kr.flightalert.core.Watch
import kr.flightalert.core.normalizePlace

private val Blue = Color(0xFF0B5CAD)

class MainActivity : ComponentActivity() {
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(this)) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue)) { Home() }
        }
    }
}

private fun batteryFree(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife")
private fun askBatteryFree(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    )
}

private fun openBrowser(context: Context, url: String) {
    context.startActivity(Intent(context, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
}

private fun openDiagnose(context: Context, url: String) {
    context.startActivity(Intent(context, DiagnoseActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
}

private val TIME = SimpleDateFormat("M/d HH:mm", Locale.KOREA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home() {
    val context = LocalContext.current
    val data by Store.data.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var batteryOk by remember { mutableStateOf(batteryFree(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("항공권 가격 알림") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Blue, titleContentColor = Color.White),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, containerColor = Blue, contentColor = Color.White) {
                Text("+", fontSize = 28.sp)
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!batteryOk) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("배터리 절약 때문에 1시간마다 확인이 늦어질 수 있어요.")
                        TextButton(onClick = { askBatteryFree(context); batteryOk = true }) { Text("배터리 제한 풀기") }
                    }
                }
            }
            item { SettingsRow(data.percent) }
            if (data.watches.isEmpty()) item {
                Text(
                    "오른쪽 아래 + 를 눌러 출발지·도착지·날짜를 등록하세요.\n1시간마다 스카이스캐너에서 가격을 확인해, 바로 앞보다 ${"%.0f".format(data.percent)}% 이상 떨어지면 알려 드립니다.",
                    modifier = Modifier.padding(8.dp),
                )
            }
            items(data.watches, key = { it.id }) { w ->
                WatchCard(w, data.historyOf(w.id), data.status[w.id])
            }
            item {
                Text(
                    "스카이스캐너가 '사람 확인'을 요구하면 알림이 옵니다. 그때 카드의 '스카이스캐너 열기'로 한 번 풀어 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
    if (adding) AddDialog(onDismiss = { adding = false }) { w ->
        Store.update { it.copy(watches = it.watches + w) }
        Scheduler.checkNow(context, w.id)
        adding = false
    }
}

@Composable
private fun SettingsRow(percent: Double) {
    var text by remember(percent) { mutableStateOf("%.0f".format(percent)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("알림 기준: 바로 앞 가격보다 ")
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() }.take(2)
                text.toDoubleOrNull()?.takeIf { it in 1.0..90.0 }?.let { p -> Store.update { it.copy(percent = p) } }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(72.dp),
        )
        Text(" % 이상 하락")
    }
}

@Composable
private fun WatchCard(w: Watch, history: List<PricePoint>, status: CheckStatus?) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val expired = w.expired(LocalDate.now())
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${w.from.uppercase()} → ${w.to.uppercase()}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${w.departDate} ${if (w.returnDate != null) "~ ${w.returnDate}" else "(편도)"} · 성인 ${w.adults}명",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = w.active && !expired,
                    enabled = !expired,
                    onCheckedChange = { on ->
                        Store.update { d -> d.copy(watches = d.watches.map { if (it.id == w.id) it.copy(active = on) else it }) }
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            val last = history.lastOrNull()
            Text(
                if (last != null) "최근 ${Notifier.won(last.price)}  ·  최저 ${Notifier.won(history.minOf { it.price })}" else "아직 가격 없음",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Blue,
            )
            when {
                expired -> Text("가는 날이 지나 확인을 멈췄어요.", color = Color.Gray)
                status != null -> Text(
                    "${TIME.format(Date(status.at))} 확인: ${status.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.ok) Color.Gray else Color(0xFFC62828),
                )
            }
            if (history.size >= 2) {
                Spacer(Modifier.height(8.dp))
                PriceChart(history.takeLast(168)) // 최근 1주일
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                history.takeLast(24).reversed().forEach {
                    Text("${TIME.format(Date(it.at))}   ${Notifier.won(it.price)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Scheduler.checkNow(context, w.id) }, enabled = !expired) { Text("지금 확인") }
                OutlinedButton(onClick = { openBrowser(context, Skyscanner.searchUrl(w)) }) { Text("스카이스캐너 열기") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { openDiagnose(context, Skyscanner.searchUrl(w)) }) { Text("점검") }
                TextButton(onClick = { confirmDelete = true }) { Text("삭제", color = Color(0xFFC62828)) }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("삭제할까요?") },
        text = { Text("${w.title}\n쌓인 가격 기록도 함께 지워집니다.") },
        confirmButton = {
            TextButton(onClick = {
                Store.update { d ->
                    d.copy(
                        watches = d.watches.filter { it.id != w.id },
                        history = d.history.filter { it.watchId != w.id },
                        status = d.status - w.id,
                    )
                }
                confirmDelete = false
            }) { Text("삭제") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
    )
}

/** 가격 흐름 꺾은선 (낮을수록 아래). */
@Composable
private fun PriceChart(points: List<PricePoint>) {
    val min = points.minOf { it.price }
    val max = points.maxOf { it.price }
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        val span = (max - min).coerceAtLeast(1)
        fun pos(i: Int, p: Long) = Offset(
            x = size.width * i / (points.size - 1),
            y = size.height * (1f - (p - min).toFloat() / span),
        )
        for (i in 1 until points.size) {
            drawLine(Blue, pos(i - 1, points[i - 1].price), pos(i, points[i].price), strokeWidth = 4f)
        }
    }
}

@Composable
private fun AddDialog(onDismiss: () -> Unit, onAdd: (Watch) -> Unit) {
    val context = LocalContext.current
    var from by remember { mutableStateOf("ICN") }
    var to by remember { mutableStateOf("") }
    var depart by remember { mutableStateOf(LocalDate.now().plusDays(30)) }
    var oneWay by remember { mutableStateOf(false) }
    var ret by remember { mutableStateOf(LocalDate.now().plusDays(35)) }
    var adults by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf<String?>(null) }

    fun pick(start: LocalDate, set: (LocalDate) -> Unit) {
        DatePickerDialog(context, { _, y, m, d -> set(LocalDate.of(y, m + 1, d)) }, start.year, start.monthValue - 1, start.dayOfMonth).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("항공권 등록") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it }, label = { Text("출발지 코드 (예: ICN, GMP, SELA=서울 전체)") }, singleLine = true)
                OutlinedTextField(to, { to = it }, label = { Text("도착지 코드 (예: NRT, KIX, OSAA=오사카 전체)") }, singleLine = true)
                OutlinedButton(onClick = { pick(depart) { depart = it; if (ret.isBefore(it)) ret = it } }) { Text("가는 날: $depart") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(oneWay, { oneWay = it })
                    Text("편도")
                }
                if (!oneWay) OutlinedButton(onClick = { pick(ret) { ret = it } }) { Text("오는 날: $ret") }
                OutlinedTextField(
                    adults, { adults = it.filter(Char::isDigit).take(1) },
                    label = { Text("성인 인원") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                error?.let { Text(it, color = Color(0xFFC62828)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val f = normalizePlace(from)
                val t = normalizePlace(to)
                val n = adults.toIntOrNull() ?: 0
                error = when {
                    f == null || t == null -> "공항·도시 코드는 영문 3~4자로 넣어 주세요."
                    f == t -> "출발지와 도착지가 같아요."
                    depart.isBefore(LocalDate.now()) -> "가는 날이 이미 지났어요."
                    !oneWay && ret.isBefore(depart) -> "오는 날이 가는 날보다 빨라요."
                    n !in 1..9 -> "인원은 1~9명."
                    else -> null
                }
                if (error == null) onAdd(
                    Watch(UUID.randomUUID().toString(), f!!, t!!, depart, if (oneWay) null else ret, n)
                )
            }) { Text("등록") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
