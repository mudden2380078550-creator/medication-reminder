package org.openmeds.reminder.backup

import kotlinx.serialization.json.Json

class BackupCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {

    fun encode(document: BackupDocument): ByteArray =
        json.encodeToString(BackupDocument.serializer(), document).encodeToByteArray()

    fun decode(bytes: ByteArray): BackupDocument {
        val document = json.decodeFromString(BackupDocument.serializer(), bytes.decodeToString())
        require(document.schemaVersion == CURRENT_VERSION) {
            "Unsupported backup version ${document.schemaVersion}"
        }
        return document
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}
