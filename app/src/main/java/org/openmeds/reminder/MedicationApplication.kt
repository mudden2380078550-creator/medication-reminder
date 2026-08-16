package org.openmeds.reminder

import android.app.Application

class MedicationApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        const val PACKAGE_ID = "org.openmeds.reminder"
    }
}
