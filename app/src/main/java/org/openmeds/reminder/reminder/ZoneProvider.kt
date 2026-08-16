package org.openmeds.reminder.reminder

import java.time.ZoneId

interface ZoneProvider {
    fun current(): ZoneId
}

class SystemZoneProvider : ZoneProvider {
    override fun current(): ZoneId = ZoneId.systemDefault()
}
