package kr.flightalert

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * 스카이스캐너를 눈에 보이게 연다. '사람 확인'이 나오면 여기서 한 번 풀면
 * 쿠키가 남아 뒤에서 하는 확인도 다시 된다. 가격 하락 알림을 눌러도 여기로 온다.
 */
class BrowserActivity : ComponentActivity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        SkyscannerFetcher.configure(web, this)
        web.webViewClient = WebViewClient()
        setContentView(web)
        web.loadUrl(intent.getStringExtra(EXTRA_URL) ?: "https://www.skyscanner.co.kr/")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
