package io.github.hansfbaier.modpoll.data

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
            val mergedProfiles = missingBuiltIns + parsed.profiles
            val needsScopeGroup = parsed.version < 3
            val migratedProfiles =
                if (needsScopeGroup) {
                    val scopeGroup =
                        BuiltInProfiles
                            .demoPlc()
                            .groups
                            .firstOrNull { it.id == "builtin.demo-plc.scope" }
                    if (scopeGroup != null) {
                        mergedProfiles.map { profile ->
                            if (profile.id == BuiltInProfiles.DEMO_ID &&
                                profile.groups.none { it.id == scopeGroup.id }
                            ) {
                                profile.copy(groups = profile.groups + scopeGroup)
                            } else {
                                profile
                            }
                        }
                    } else {
                        mergedProfiles
                    }
                } else {
                    mergedProfiles
                }
            val active =
                if (parsed.version < 2) {
                    // Version 2 starts with no active profile: nothing is polled until the user
                    // explicitly activates a profile.
                    ""
                } else if (mergedProfiles.any { it.id == parsed.activeProfileId }) {
                    parsed.activeProfileId
                } else {
                    ""
                }
            val result =
                parsed.copy(
                    version = 3,
                    activeProfileId = active,
                    profiles = migratedProfiles,
                )
            if (result != parsed) runCatching { save(result) }
            result
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
        return ProfilesFile(
            version = 3,
            activeProfileId = "",
            profiles = BuiltInProfiles.all(),
        )
    }
}
