package kr.flightalert

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kr.flightalert.core.Skyscanner
import org.json.JSONArray
import org.json.JSONObject

/**
 * 점검 화면: 검색 화면을 눈에 보이게 열고, 페이지가 주고받는 자료(주소 · 키 · 가격 칸)를 모두 적는다.
 * [기록 보내기]로 그 글을 복사 · 공유해 어디서 가격을 읽어야 하는지 확인한다.
 */
class DiagnoseActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var url: String
    private var bgResult = "아직 안 함"
    private var bgJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = intent.getStringExtra(BrowserActivity.EXTRA_URL) ?: "https://www.skyscanner.co.kr/"

        web = WebView(this)
        SkyscannerFetcher.configure(web, this)
        val diag = assets.open("diag.js").bufferedReader().use { it.readText() }
        val docStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (docStart) WebViewCompat.addDocumentStartJavaScript(web, diag, setOf("*"))
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, u: String?, favicon: android.graphics.Bitmap?) {
                if (!docStart) {
                    view.evaluateJavascript(view.tag as String, null)
                    view.evaluateJavascript(diag, null)
                }
            }
        }

        status = TextView(this).apply {
            setPadding(24, 12, 24, 12)
            setBackgroundColor(Color.parseColor("#E8F0FA"))
            text = "검색 화면을 여는 중… (끝까지 뜬 뒤 [기록 보내기])"
        }
        val send = Button(this).apply { text = "기록 보내기"; setOnClickListener { share() } }
        val bg = Button(this).apply { text = "숨은 확인 시험"; setOnClickListener { backgroundTest() } }
        val reload = Button(this).apply { text = "다시 열기"; setOnClickListener { web.loadUrl(url) } }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(send, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(bg, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(reload, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
        web.loadUrl(url)

        // 2초마다 요약 한 줄
        lifecycleScope.launch {
            while (true) {
                delay(2_000)
                val o = probe() ?: continue
                val r = o.optString("report")
                val net = r.lineSequence().firstOrNull { it.startsWith("[주고받은 자료]") } ?: ""
                val fa = r.lineSequence().firstOrNull { it.startsWith("최저:") } ?: ""
                val cap = if (r.contains("사람 확인 화면: 예")) "⚠ 사람 확인 화면" else ""
                status.text = "$net\n엿보기 $fa\n숨은 확인: $bgResult $cap".trim()
            }
        }
    }

    private suspend fun probe(): JSONObject? = suspendCancellableCoroutine { cont ->
        web.evaluateJavascript("window.__diagReport ? window.__diagReport() : null") { raw ->
            val o = runCatching { JSONObject(JSONArray("[$raw]").getString(0)) }.getOrNull()
            if (cont.isActive) cont.resume(o)
        }
    }

    /** 앱이 1시간마다 하는 것과 똑같이, 보이지 않는 창으로 한 번 확인해 본다. */
    private fun backgroundTest() {
        if (bgJob?.isActive == true) return
        bgResult = "확인 중… (최대 100초)"
        bgJob = lifecycleScope.launch {
            val start = SystemClock.elapsedRealtime()
            val r = SkyscannerFetcher.fetch(this@DiagnoseActivity, url)
            val sec = (SystemClock.elapsedRealtime() - start) / 1000
            bgResult = when (r) {
                is FetchResult.Price -> "${Notifier.won(r.price)} (${r.source}, ${sec}초)"
                FetchResult.Captcha -> "사람 확인에 막힘 (${sec}초)"
                is FetchResult.Failed -> "${r.reason} (${sec}초)"
            }
        }
    }

    private fun share() = lifecycleScope.launch {
        val o = probe()
        val text = buildString {
            appendLine("=== 항공권 가격 알림 점검 기록 ===")
            appendLine("시각: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())}")
            val ver = packageManager.getPackageInfo(packageName, 0).versionName
            appendLine("앱: $ver · 안드로이드 ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("WebView: ${WebViewCompat.getCurrentWebViewPackage(this@DiagnoseActivity)?.versionName ?: "?"}")
            appendLine("첫 주소: $url")
            appendLine("숨은 확인 시험: $bgResult")
            if (o == null) {
                appendLine("페이지에서 기록을 못 받음 (점검 스크립트가 안 붙음)")
            } else {
                appendLine("화면 '최저가' 읽기: ${Skyscanner.cheapestFromPageText(o.optString("text"))?.let(Notifier::won) ?: "못 읽음"}")
                appendLine()
                append(o.optString("report"))
            }
        }.take(20_000)
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("점검 기록", text))
        Toast.makeText(this@DiagnoseActivity, "점검 기록을 복사했어요. 대화창에 붙여 넣거나 공유하세요.", Toast.LENGTH_LONG).show()
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                "점검 기록 보내기",
            )
        )
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
