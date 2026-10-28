package kr.flightalert

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val PERIODIC = "hourly_check"
    private const val NOW = "check_now"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** 1시간마다 확인 (앱을 처음 열 때 한 번 걸어 두면 휴대폰을 다시 켜도 이어짐). */
    fun ensurePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<PriceCheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** 지금 바로 확인. watchId 가 있으면 그 항공권만. */
    fun checkNow(context: Context, watchId: String? = null) {
        val b = OneTimeWorkRequestBuilder<PriceCheckWorker>().setConstraints(online)
        if (watchId != null) b.setInputData(workDataOf(PriceCheckWorker.KEY_WATCH to watchId))
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, b.build())
    }
}
