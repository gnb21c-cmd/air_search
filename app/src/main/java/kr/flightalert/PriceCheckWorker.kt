package kr.flightalert

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import kr.flightalert.core.DropRule
import kr.flightalert.core.PricePoint
import kr.flightalert.core.Skyscanner

/** 1시간마다 (또는 '지금 확인') 켜 둔 항공권을 하나씩 검색해 가격을 쌓고, 갑자기 떨어지면 알린다. */
class PriceCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val only = inputData.getString(KEY_WATCH)
        val today = LocalDate.now()
        val targets = Store.data.value.watches.filter {
            if (only != null) it.id == only else it.active && !it.expired(today)
        }
        for (w in targets) {
            if (isStopped) break
            val now = System.currentTimeMillis()
            when (val r = SkyscannerFetcher.fetch(applicationContext, Skyscanner.searchUrl(w))) {
                is FetchResult.Price -> {
                    val before = Store.data.value.historyOf(w.id)
                    DropRule.check(before, r.price, Store.data.value.percent)?.let {
                        Notifier.priceDrop(applicationContext, w, it)
                    }
                    Store.addPrice(PricePoint(w.id, now, r.price))
                    Store.setStatus(w.id, CheckStatus(now, "${Notifier.won(r.price)} (${r.source})", true))
                }
                FetchResult.Captcha -> {
                    Store.setStatus(w.id, CheckStatus(now, "스카이스캐너 사람 확인 필요", false))
                    // 같은 안내를 매시간 보내지 않도록 6시간에 한 번만
                    if (now - Store.data.value.lastCaptchaNotice > 6 * 3600_000L) {
                        Store.update { it.copy(lastCaptchaNotice = now) }
                        Notifier.captcha(applicationContext)
                    }
                    break // 다른 항공권도 같은 이유로 막힘
                }
                is FetchResult.Failed -> Store.setStatus(w.id, CheckStatus(now, r.reason, false))
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_WATCH = "watch"
    }
}
