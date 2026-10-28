package kr.flightalert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.util.Locale
import kr.flightalert.core.DropRule
import kr.flightalert.core.Skyscanner
import kr.flightalert.core.Watch

object Notifier {
    private const val DROP = "price_drop"
    private const val NOTICE = "notice"

    fun won(v: Long): String = NumberFormat.getNumberInstance(Locale.KOREA).format(v) + "원"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(DROP, "가격 하락", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "항공권 가격이 갑자기 떨어졌을 때"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(NOTICE, "확인 필요", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "스카이스캐너가 사람 확인을 요구할 때"
            }
        )
    }

    fun canNotify(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33

    @Suppress("MissingPermission")
    fun priceDrop(context: Context, w: Watch, d: DropRule.Drop) {
        if (!canNotify(context)) return
        // 알림을 누르면 바로 스카이스캐너 검색 화면
        val open = Intent(context, BrowserActivity::class.java)
            .putExtra(BrowserActivity.EXTRA_URL, Skyscanner.searchUrl(w))
        val pi = PendingIntent.getActivity(
            context, w.id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, DROP)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("✈ ${w.from.uppercase()}→${w.to.uppercase()} ${"%.0f".format(d.percent)}% 하락")
            .setContentText("${won(d.previous)} → ${won(d.current)}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${w.title}\n${won(d.previous)} → ${won(d.current)} (${"%.1f".format(d.percent)}% 내림)\n누르면 스카이스캐너가 열립니다."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(w.id.hashCode(), n)
    }

    @Suppress("MissingPermission")
    fun captcha(context: Context) {
        if (!canNotify(context)) return
        val pi = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, NOTICE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("스카이스캐너 확인이 필요해요")
            .setContentText("앱에서 '스카이스캐너 열기'를 눌러 사람 확인을 한 번 풀어 주세요.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, n)
    }
}
