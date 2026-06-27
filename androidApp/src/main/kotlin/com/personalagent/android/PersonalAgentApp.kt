package com.personalagent.android

import android.app.Application
import com.personalagent.android.notification.ReminderNotifier

/** Application: owns the [AppContainer] singleton and the notification channel. */
class PersonalAgentApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderNotifier.ensureChannel(this)
    }
}
