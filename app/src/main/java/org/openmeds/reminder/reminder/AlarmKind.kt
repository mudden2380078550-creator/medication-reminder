package org.openmeds.reminder.reminder

enum class AlarmKind(val link: Int) {
    DOSE(0),
    RETRY_1(1),
    RETRY_2(2),
    RETRY_3(3),
    FINALIZE(4),
    LOW_STOCK(5);

    companion object {
        fun retryOrFinalize(link: Int): AlarmKind = when (link) {
            0 -> DOSE
            1 -> RETRY_1
            2 -> RETRY_2
            3 -> RETRY_3
            else -> FINALIZE
        }

        fun forRetry(attempt: Int): AlarmKind = when (attempt) {
            1 -> RETRY_1
            2 -> RETRY_2
            else -> RETRY_3
        }
    }
}
