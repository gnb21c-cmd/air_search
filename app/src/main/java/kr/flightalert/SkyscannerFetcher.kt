package kr.flightalert

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kr.flightalert.core.Skyscanner
import org.json.JSONArray
import org.json.JSONObject

/** 스카이스캐너 한 번 확인한 결과. */
sealed interface FetchResult {
    data class Price(val price: Long, val source: String) : FetchResult
    /** 스카이스캐너가 '사람인지 확인'을 요구함 → 앱에서 한 번 열어 풀어 줘야 함 */
    data object Captcha : FetchResult
    data class Failed(val reason: String) : FetchResult
}

/**
 * 화면에 보이지 않는 웹 브라우저(WebView)로 스카이스캐너 검색 화면을 열어 최저가를 읽는다.
 * 사람이 보는 것과 같은 화면이라 따로 API 키가 필요 없다.
 */
object SkyscannerFetcher {
    private const val TIMEOUT_MS = 100_000L
    private const val SETTLE_MS = 12_000L // 마지막 가격이 들어온 뒤 이만큼 조용하면 검색이 끝난 것으로 봄
    private const val MIN_WAIT_MS = 20_000L

    private val PROBE = """
        (function(){
          var fa = window.__fa || {};
          var body = document.body ? document.body.innerText : '';
          var href = location.href;
          var captcha = href.indexOf('captcha') >= 0 || !!document.querySelector('#px-captcha') ||
            body.indexOf('로봇이 아닙니다') >= 0 || body.indexOf('사람인지') >= 0 ||
            /are you a person or a robot/i.test(body);
          return JSON.stringify({min: fa.min || 0, last: fa.last || 0, captcha: captcha,
            text: body.slice(0, 30000)});
        })()
    """.trimIndent()

    /** 브라우저 흔적('; wv')을 지워 일반 크롬처럼 보이게 한다. 화면 보기용 창도 같은 값을 쓴다. */
    fun userAgent(context: Context): String =
        WebSettings.getDefaultUserAgent(context)
            .replace("; wv", "")
            .replace(Regex("""Version/\d+\.\d+ """), "")

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(web: WebView, context: Context) {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = userAgent(context)
        val hook = context.assets.open("hook.js").bufferedReader().use { it.readText() }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, hook, setOf("https://*.skyscanner.co.kr"))
        }
        web.tag = hook
    }

    suspend fun fetch(context: Context, url: String): FetchResult = withContext(Dispatchers.Main) {
        val web = WebView(context.applicationContext)
        try {
            configure(web, context)
            // 화면에 붙이지 않고 크기만 정해 둔다 (휴대폰 화면 크기)
            web.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
            )
            web.layout(0, 0, 1080, 2400)
            val needsLateHook = !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            web.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, u: String?, favicon: android.graphics.Bitmap?) {
                    if (needsLateHook) view.evaluateJavascript(view.tag as String, null)
                }
            }
            web.loadUrl(url)
            poll(web)
        } finally {
            web.stopLoading()
            web.destroy()
        }
    }

    private suspend fun poll(web: WebView): FetchResult {
        val start = SystemClock.elapsedRealtime()
        var lastText = ""
        var lastMin = 0L
        var lastChange = 0L
        while (SystemClock.elapsedRealtime() - start < TIMEOUT_MS) {
            delay(2_000)
            val o = probe(web) ?: continue
            if (o.optBoolean("captcha")) return FetchResult.Captcha
            lastText = o.optString("text")
            val min = o.optLong("min")
            val now = SystemClock.elapsedRealtime()
            if (min != lastMin) {
                lastMin = min
                lastChange = now
            }
            if (lastMin > 0 && now - start >= MIN_WAIT_MS && now - lastChange >= SETTLE_MS) {
                return FetchResult.Price(lastMin, "검색 결과")
            }
        }
        if (lastMin > 0) return FetchResult.Price(lastMin, "검색 결과")
        Skyscanner.cheapestFromPageText(lastText)?.let { return FetchResult.Price(it, "화면 '최저가'") }
        return FetchResult.Failed(
            if (lastText.isBlank()) "화면을 열지 못함 (인터넷 확인)" else "가격을 찾지 못함 (공항 코드·날짜 확인)"
        )
    }

    private suspend fun probe(web: WebView): JSONObject? = suspendCancellableCoroutine { cont ->
        web.evaluateJavascript(PROBE) { raw ->
            // 돌려받는 값은 따옴표로 감싼 JSON 글자
            val parsed = runCatching { JSONObject(JSONArray("[$raw]").getString(0)) }.getOrNull()
            if (cont.isActive) cont.resume(parsed)
        }
    }
}
