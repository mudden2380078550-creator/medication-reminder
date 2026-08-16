package org.openmeds.reminder

import android.app.Application
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedicationApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(Dispatchers.IO).launch {
            container.lowStockPlanner.reconcile(Instant.now(), container.zoneProvider.current())
        }
    }

    companion object {
        const val PACKAGE_ID = "org.openmeds.reminder"
    }
}
