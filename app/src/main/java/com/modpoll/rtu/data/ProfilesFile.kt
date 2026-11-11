package com.modpoll.rtu.data

import kotlinx.serialization.Serializable

@Serializable
data class ProfilesFile(
    val version: Int = 2,
    val activeProfileId: String = "",
    val profiles: List<DeviceProfile> = emptyList(),
    val link: LinkSettings = LinkSettings(),
)
