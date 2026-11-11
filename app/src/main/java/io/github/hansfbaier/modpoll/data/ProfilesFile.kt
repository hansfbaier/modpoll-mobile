package io.github.hansfbaier.modpoll.data

import kotlinx.serialization.Serializable

@Serializable
data class ProfilesFile(
    val version: Int = 3,
    val activeProfileId: String = "",
    val profiles: List<DeviceProfile> = emptyList(),
    val link: LinkSettings = LinkSettings(),
)
