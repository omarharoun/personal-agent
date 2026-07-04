package com.personalagent.android

import android.app.Application
import com.personalagent.android.notification.ReflectionNotifier
import com.personalagent.android.notification.ReflectionScheduling
import com.personalagent.android.notification.ReminderNotifier
import com.personalagent.android.notification.ReminderScheduling
import com.personalagent.shared.hermes.ReflectionCadence

/** Application: owns the [AppContainer] singleton and the notification channel. */
class PersonalAgentApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderNotifier.ensureChannel(this)
        ReflectionNotifier.ensureChannel(this)
        // Deliver reminders by polling the user's Hermes (/api/jobs) and raising
        // local notifications. Only run the safety-net poll once connected.
        if (container.isHermesConfigured) {
            ReminderScheduling.ensurePeriodic(this)
            ReminderScheduling.pollNow(this)
            // Keep the gentle reflection check running only if the user opted in.
            if (container.reflectionStore.load().cadence != ReflectionCadence.OFF) {
                ReflectionScheduling.ensureDaily(this)
            }
        }
    }
}
