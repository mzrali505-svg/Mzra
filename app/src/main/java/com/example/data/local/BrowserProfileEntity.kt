package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class BrowserProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val groupTag: String = "General",
    val notes: String = "",
    val colorHex: String = "#00E5C3",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLaunchedAt: Long? = null,
    val launchCount: Int = 0,
    val startUrl: String = "https://myip.link",

    // Proxy binding (Vault, Your inline proxy, or Without proxy)
    val proxyConnectionType: String = "WITHOUT_PROXY",
    val proxyId: Long? = null,
    val inlineProxyType: String = "AUTO",
    val inlineProxyHost: String = "",
    val inlineProxyPort: Int = 1080,
    val inlineProxyUsername: String = "",
    val inlineProxyPassword: String = "",

    // OS / Browser / User-Agent
    val osPlatform: String,
    val browserEngine: String,
    val userAgent: String,
    val platformString: String,
    val vendorString: String,
    val deviceModel: String,
    val doNotTrack: Boolean = false,

    // Timezone, Locale & Geolocation
    val syncLocationWithProxy: Boolean = true,
    val timezoneFillFromIp: Boolean = true,
    val timezone: String = "America/New_York",
    val languagesFillFromIp: Boolean = true,
    val language: String = "en-US",
    val languagesCsv: String = "en-US,en",
    val geolocationMode: String = "PROMPT",
    val geolocationBasedOnIp: Boolean = true,
    val latitude: Double = 40.7128,
    val longitude: Double = -74.0060,
    val geoAccuracy: Int = 25,

    // Hardware & Display
    val screenWidth: Int = 411,
    val screenHeight: Int = 914,
    val devicePixelRatio: Float = 3.5f,
    val colorDepth: Int = 24,
    val hardwareConcurrency: Int = 8,
    val deviceMemory: Int = 8,
    val maxTouchPoints: Int = 5,
    val batterySpoofEnabled: Boolean = true,
    val batteryLevel: Float = 0.84f,
    val batteryCharging: Boolean = true,

    // WebRTC & Hardware Fingerprints
    val webRtcMode: String = "BASED_ON_IP",
    val webRtcFakeIp: String = "10.8.42.19",
    val canvasMode: String = "NOISE",
    val canvasSeed: Long = 123456789L,
    val audioMode: String = "NOISE",
    val audioSeed: Long = 456789123L,
    val webGlImageMode: String = "NOISE",
    val webGlMetadataMode: String = "MASK",
    val webGlVendor: String = "Qualcomm",
    val webGlRenderer: String = "Adreno (TM) 830",
    val webGlSeed: Long = 987654321L,
    val webGlMaxTextureSize: Int = 16384,
    val webGlMaxVertexAttribs: Int = 16,
    val webGlShadingLang: String = "WebGL GLSL ES 1.0 (OpenGL ES GLSL ES 1.0 Chromium)",

    // Fonts & DOMRect
    val clientRectsSeed: Long = 321654987L,
    val fontMasking: Boolean = true,
    val enableUnicodeGlyphsAndDomRect: Boolean = true,
    val fontsListCsv: String = "Arial,Courier,Courier New,Georgia,Helvetica,Monaco,Palatino,Tahoma,Times,Times New Roman,Verdana,Baskerville",

    // Media Devices
    val maskMediaDevices: Boolean = true,
    val videoInputsCount: Int = 0,
    val audioInputsCount: Int = 0,
    val audioOutputsCount: Int = 0,

    // Storage, Plugins & Session Behavior
    val localStorageAndIndexedDb: Boolean = true,
    val enableExtensionStorage: Boolean = false,
    val allowVulnerablePlugins: Boolean = false,
    val activeSessionLock: Boolean = true,
    val enableGoogleServices: Boolean = false,
    val bookmarkSaving: Boolean = true,
    val historySaving: Boolean = true,
    val passwordSaving: Boolean = true,
    val sessionSaving: Boolean = true,
    val debugMode: Boolean = false,
    val customDns: String = "",

    // RPA / Humanized Automation Binding
    val automationFlowId: Long? = null,
    val autoRunAutomationOnLaunch: Boolean = false,
    val humanizeSearchEnabled: Boolean = false,
    val humanizeKeyword: String = "",
    val humanizeTargetDomain: String = "",
    val humanizeDwellSeconds: Int = 30,
    val humanizeClickInternalLink: Boolean = true,
    val humanizeScrollStyle: String = "AUTO",

    // Session Isolation
    val webViewProfileName: String
)
