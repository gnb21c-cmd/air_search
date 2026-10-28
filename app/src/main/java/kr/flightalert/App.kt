package kr.flightalert

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.createChannels(this)
        Scheduler.ensurePeriodic(this)
    }
}
