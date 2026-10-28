package kr.flightalert

import android.content.Context
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.flightalert.core.PricePoint
import kr.flightalert.core.Watch
import org.json.JSONArray
import org.json.JSONObject

/** 확인 상태 (마지막으로 확인한 시각과 결과 한 줄). */
data class CheckStatus(val at: Long, val message: String, val ok: Boolean)

data class AppData(
    val percent: Double = 10.0,
    val watches: List<Watch> = emptyList(),
    val history: List<PricePoint> = emptyList(),
    val status: Map<String, CheckStatus> = emptyMap(),
    val lastCaptchaNotice: Long = 0,
) {
    fun historyOf(id: String) = history.filter { it.watchId == id }.sortedBy { it.at }
}

/** 핸드폰 안 파일 하나(data.json)에 모두 저장한다. */
object Store {
    private const val KEEP_PER_WATCH = 3000 // 1시간마다면 약 4달치
    private lateinit var file: File
    private val state = MutableStateFlow(AppData())
    val data: StateFlow<AppData> get() = state

    fun init(context: Context) {
        file = File(context.filesDir, "data.json")
        state.value = runCatching { decode(JSONObject(file.readText())) }.getOrDefault(AppData())
    }

    @Synchronized
    fun update(change: (AppData) -> AppData) {
        val next = change(state.value)
        val tmp = File(file.parentFile, "data.json.tmp")
        tmp.writeText(encode(next).toString())
        tmp.renameTo(file)
        state.value = next
    }

    fun addPrice(p: PricePoint) = update { d ->
        val mine = (d.historyOf(p.watchId) + p).takeLast(KEEP_PER_WATCH)
        d.copy(history = d.history.filter { it.watchId != p.watchId } + mine)
    }

    fun setStatus(id: String, s: CheckStatus) = update { it.copy(status = it.status + (id to s)) }

    private fun encode(d: AppData) = JSONObject().apply {
        put("percent", d.percent)
        put("lastCaptchaNotice", d.lastCaptchaNotice)
        put("watches", JSONArray().apply {
            d.watches.forEach { w ->
                put(JSONObject().apply {
                    put("id", w.id); put("from", w.from); put("to", w.to)
                    put("depart", w.departDate.toString())
                    put("return", w.returnDate?.toString() ?: "")
                    put("adults", w.adults); put("active", w.active)
                })
            }
        })
        put("history", JSONArray().apply {
            d.history.forEach { put(JSONArray().put(it.watchId).put(it.at).put(it.price)) }
        })
        put("status", JSONObject().apply {
            d.status.forEach { (id, s) ->
                put(id, JSONObject().put("at", s.at).put("message", s.message).put("ok", s.ok))
            }
        })
    }

    private fun decode(o: JSONObject): AppData {
        val ws = o.optJSONArray("watches") ?: JSONArray()
        val watches = (0 until ws.length()).map { i ->
            val w = ws.getJSONObject(i)
            Watch(
                id = w.getString("id"),
                from = w.getString("from"),
                to = w.getString("to"),
                departDate = LocalDate.parse(w.getString("depart")),
                returnDate = w.optString("return").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                adults = w.optInt("adults", 1),
                active = w.optBoolean("active", true),
            )
        }
        val hs = o.optJSONArray("history") ?: JSONArray()
        val history = (0 until hs.length()).map { i ->
            val h = hs.getJSONArray(i)
            PricePoint(h.getString(0), h.getLong(1), h.getLong(2))
        }
        val st = o.optJSONObject("status") ?: JSONObject()
        val status = st.keys().asSequence().associateWith { id ->
            val s = st.getJSONObject(id)
            CheckStatus(s.getLong("at"), s.getString("message"), s.getBoolean("ok"))
        }
        return AppData(
            percent = o.optDouble("percent", 10.0),
            watches = watches,
            history = history,
            status = status,
            lastCaptchaNotice = o.optLong("lastCaptchaNotice", 0),
        )
    }
}
