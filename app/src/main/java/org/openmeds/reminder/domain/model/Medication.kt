package org.openmeds.reminder.domain.model

data class Medication(
    val id: Long,
    val name: String,
    val unit: String,
    val stock: SignedMilliUnits,
    val note: String?,
    val isActive: Boolean
) {
    init {
        require(name.isNotBlank()) { "Medication name must not be blank" }
        require(unit.isNotBlank()) { "Medication unit must not be blank" }
    }
}
