package com.modpoll.rtu.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/** Persists profiles + link settings as JSON in the app's private storage. */
class ProfileStore(
    context: Context,
    private val json: Json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
            isLenient = true
        },
) {
    private val file = File(context.filesDir, "profiles.json")

    @Synchronized
    fun load(): ProfilesFile {
        if (!file.exists()) {
            return defaultFile().also { save(it) }
        }
        return try {
            val parsed = json.decodeFromString<ProfilesFile>(file.readText())
            val existingIds = parsed.profiles.map { it.id }.toSet()
            val missingBuiltIns = BuiltInProfiles.all().filter { it.id !in existingIds }
            val merged = parsed.copy(profiles = parsed.profiles + missingBuiltIns)
            val active =
                if (merged.profiles.any { it.id == merged.activeProfileId }) {
                    merged.activeProfileId
                } else {
                    merged.profiles
                        .firstOrNull()
                        ?.id
                        .orEmpty()
                }
            merged.copy(activeProfileId = active)
        } catch (failure: Exception) {
            // Keep the unreadable file for recovery, then start from defaults.
            runCatching { file.renameTo(File(file.parentFile, "profiles.corrupt.json")) }
            defaultFile().also { save(it) }
        }
    }

    @Synchronized
    fun save(state: ProfilesFile) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            tmp.writeText(json.encodeToString(ProfilesFile.serializer(), state))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (io: IOException) {
            runCatching { tmp.delete() }
            throw io
        }
    }

    fun importJson(text: String): List<DeviceProfile> {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("[") -> {
                json.decodeFromString<List<DeviceProfile>>(trimmed)
            }

            trimmed.startsWith("{") && trimmed.contains("\"profiles\"") -> {
                json.decodeFromString<ProfilesFile>(trimmed).profiles
            }

            else -> {
                listOf(json.decodeFromString<DeviceProfile>(trimmed))
            }
        }.map { it.copy(id = if (it.id.isBlank()) UUID.randomUUID().toString() else it.id) }
    }

    fun exportJson(profiles: List<DeviceProfile>): String =
        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DeviceProfile.serializer()), profiles)

    private fun defaultFile(): ProfilesFile {
        val profiles = BuiltInProfiles.all()
        return ProfilesFile(
            activeProfileId = BuiltInProfiles.DEMO_ID,
            profiles = profiles,
        )
    }
}
