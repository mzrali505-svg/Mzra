package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object ProfilesRoute

@Serializable
object ProxiesRoute

@Serializable
object AutomationRoute

@Serializable
object StealthLabRoute

@Serializable
object SettingsRoute

@Serializable
object MassRunnerRoute

@Serializable
data class ProfileEditorRoute(val profileId: Long? = null)

@Serializable
data class ProxyEditorRoute(val proxyId: Long? = null)

@Serializable
data class BrowserRoute(
    val profileId: Long,
    val initialUrl: String? = null,
    val initialFlowId: Long? = null,
    val batchMode: Boolean = false
)
