package com.example.data.model

import com.example.data.local.BrowserProfileEntity
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

enum class ProxyType(val label: String, val defaultPort: Int) {
    AUTO("Auto", 1080),
    DIRECT("Without proxy", 0),
    HTTP("HTTP", 8080),
    HTTPS("HTTPS", 443),
    SOCKS4("SOCKS4", 1080),
    SOCKS5("SOCKS5", 1080)
}

enum class ProfileProxyConnectionType(val label: String) {
    VAULT("Saved Vault Proxy"),
    YOUR_PROXY("Your proxy"),
    WITHOUT_PROXY("Without proxy")
}

enum class ProxyStatus(val label: String) {
    UNCHECKED("Unchecked"),
    CHECKING("Checking"),
    ONLINE("Live"),
    OFFLINE("Dead")
}

enum class OsPlatform(
    val displayName: String,
    val navigatorPlatform: String,
    val isMobile: Boolean
) {
    WINDOWS("Windows", "Win32", false),
    WINDOWS_11("Windows 11", "Win32", false),
    MACOS("Macos", "MacIntel", false),
    MACOS_M1("Macos M1", "MacIntel", false),
    MACOS_M2("Macos M2", "MacIntel", false),
    MACOS_M3("Macos M3", "MacIntel", false),
    LINUX("Linux", "Linux x86_64", false),
    ANDROID("Android", "Linux aarch64", true),
    IOS("iOS", "iPhone", true)
}

enum class BrowserEngine(val displayName: String) {
    CHROME("Chrome"),
    SAFARI("Safari"),
    EDGE("Edge"),
    FIREFOX("Firefox")
}

enum class SpoofMode(val label: String) {
    NOISE("NOISE"),
    OFF("OFF"),
    BLOCK("BLOCK")
}

enum class WebRtcMode(val label: String, val summary: String) {
    BASED_ON_IP("BASED ON IP", "Masks WebRTC ICE IP with active proxy exit IP"),
    OFF("OFF", "Disables RTCPeerConnection completely"),
    REAL("REAL", "Leaves WebRTC unprotected")
}

enum class WebGlMode(val label: String) {
    MASK("MASK"),
    OFF("OFF")
}

enum class GeolocationMode(val label: String) {
    PROMPT("PROMPT"),
    ALLOW("ALLOW"),
    BLOCK("BLOCK")
}

data class DeviceBlueprint(
    val id: String,
    val title: String,
    val subtitle: String,
    val osPlatform: OsPlatform,
    val browserEngine: BrowserEngine,
    val userAgent: String,
    val vendor: String,
    val deviceModel: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val dpr: Float,
    val cpuCores: Int,
    val ramGb: Int,
    val maxTouchPoints: Int,
    val webGlVendor: String,
    val webGlRenderer: String,
    val fontsCsv: String
)

data class GeoTimezonePreset(
    val label: String,
    val countryCode: String,
    val timezone: String,
    val language: String,
    val languagesCsv: String,
    val latitude: Double,
    val longitude: Double
)

object FingerprintPresets {

    val accentColors = listOf(
        "#00E5C3",
        "#6C7CFF",
        "#10B981",
        "#F59E0B",
        "#EC4899",
        "#38BDF8"
    )

    val allFontsCatalog = listOf(
        "Arial", "Courier", "Courier New", "Georgia", "Helvetica", "Helvetica Neue",
        "Monaco", "Palatino", "Tahoma", "Times", "Times New Roman", "Verdana",
        "Baskerville", "Calibri", "Cambria", "Consolas", "Segoe UI", "Trebuchet MS",
        "Roboto", "Noto Sans", "San Francisco", "Menlo", "Ubuntu", "DejaVu Sans"
    )

    val resolutionPresets = listOf(
        "411x914",
        "412x915",
        "430x932",
        "390x844",
        "1920x1080",
        "1536x864",
        "1728x1117",
        "2560x1440"
    )

    val deviceBlueprints = listOf(
        DeviceBlueprint(
            id = "android_s25",
            title = "Android 16 • Chrome 132",
            subtitle = "Samsung SM-S938B • Adreno (TM) 830",
            osPlatform = OsPlatform.ANDROID,
            browserEngine = BrowserEngine.CHROME,
            userAgent = "Mozilla/5.0 (Linux; Android 16; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.6834.122 Mobile Safari/537.36",
            vendor = "Google Inc.",
            deviceModel = "SM-S938B",
            screenWidth = 411,
            screenHeight = 914,
            dpr = 3.5f,
            cpuCores = 8,
            ramGb = 8,
            maxTouchPoints = 5,
            webGlVendor = "Qualcomm",
            webGlRenderer = "Adreno (TM) 830",
            fontsCsv = "Arial,Courier,Courier New,Georgia,Helvetica,Monaco,Palatino,Tahoma,Times,Times New Roman,Verdana,Baskerville"
        ),
        DeviceBlueprint(
            id = "android_pixel9",
            title = "Android 15 • Pixel 9 Pro",
            subtitle = "Google Pixel 9 Pro • Mali-G715 MC7",
            osPlatform = OsPlatform.ANDROID,
            browserEngine = BrowserEngine.CHROME,
            userAgent = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.6834.90 Mobile Safari/537.36",
            vendor = "Google Inc.",
            deviceModel = "Pixel 9 Pro",
            screenWidth = 412,
            screenHeight = 915,
            dpr = 3.0f,
            cpuCores = 8,
            ramGb = 12,
            maxTouchPoints = 5,
            webGlVendor = "ARM",
            webGlRenderer = "Mali-G715-Immortalis MC7",
            fontsCsv = "Roboto,Noto Sans,Arial,Georgia,Helvetica,Times New Roman,Verdana"
        ),
        DeviceBlueprint(
            id = "android_oneplus13",
            title = "Android 15 • OnePlus 13",
            subtitle = "OnePlus CPH2653 • Adreno (TM) 830",
            osPlatform = OsPlatform.ANDROID,
            browserEngine = BrowserEngine.CHROME,
            userAgent = "Mozilla/5.0 (Linux; Android 15; CPH2653) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36",
            vendor = "Google Inc.",
            deviceModel = "CPH2653",
            screenWidth = 412,
            screenHeight = 919,
            dpr = 3.5f,
            cpuCores = 8,
            ramGb = 12,
            maxTouchPoints = 5,
            webGlVendor = "Qualcomm",
            webGlRenderer = "Adreno (TM) 830",
            fontsCsv = "Roboto,Noto Sans,Arial,Helvetica,Times New Roman,Verdana"
        ),
        DeviceBlueprint(
            id = "android_xiaomi15",
            title = "Android 15 • Xiaomi 15 Pro",
            subtitle = "Xiaomi 24129PN74G • Adreno (TM) 830",
            osPlatform = OsPlatform.ANDROID,
            browserEngine = BrowserEngine.CHROME,
            userAgent = "Mozilla/5.0 (Linux; Android 15; 24129PN74G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.6834.79 Mobile Safari/537.36",
            vendor = "Google Inc.",
            deviceModel = "24129PN74G",
            screenWidth = 400,
            screenHeight = 890,
            dpr = 3.0f,
            cpuCores = 8,
            ramGb = 12,
            maxTouchPoints = 5,
            webGlVendor = "Qualcomm",
            webGlRenderer = "Adreno (TM) 830",
            fontsCsv = "Roboto,Noto Sans,Arial,Georgia,Times New Roman,Verdana"
        ),
        DeviceBlueprint(
            id = "android_s24",
            title = "Android 15 • Galaxy S24",
            subtitle = "Samsung SM-S921B • Samsung Xclipse 940",
            osPlatform = OsPlatform.ANDROID,
            browserEngine = BrowserEngine.CHROME,
            userAgent = "Mozilla/5.0 (Linux; Android 15; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36",
            vendor = "Google Inc.",
            deviceModel = "SM-S921B",
            screenWidth = 384,
            screenHeight = 854,
            dpr = 3.0f,
            cpuCores = 8,
            ramGb = 8,
            maxTouchPoints = 5,
            webGlVendor = "Samsung Electronics Co., Ltd.",
            webGlRenderer = "ANGLE (Samsung Xclipse 940) on Vulkan 1.3",
            fontsCsv = "Roboto,Noto Sans,Arial,Courier New,Georgia,Helvetica,Verdana"
        ),
        DeviceBlueprint(
            id = "ios_iphone15",
            title = "iOS 18 • iPhone 15 Pro",
            subtitle = "Mobile Safari 18 • Apple A17 Pro GPU",
            osPlatform = OsPlatform.IOS,
            browserEngine = BrowserEngine.SAFARI,
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1",
            vendor = "Apple Computer, Inc.",
            deviceModel = "iPhone16,1",
            screenWidth = 393,
            screenHeight = 852,
            dpr = 3.0f,
            cpuCores = 6,
            ramGb = 8,
            maxTouchPoints = 5,
            webGlVendor = "Apple Inc.",
            webGlRenderer = "Apple GPU",
            fontsCsv = "Helvetica,Helvetica Neue,San Francisco,Arial,Courier,Georgia,Times New Roman"
        ),
        DeviceBlueprint(
            id = "win11_rtx4070",
            title = "Windows 11 • Chrome 132",
            subtitle = "Desktop Workstation • NVIDIA RTX 4070",
            osPlatform = OsPlatform.WINDOWS_11,
            browserEngine = BrowserEngine.CHROME,
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
            vendor = "Google Inc.",
            deviceModel = "PC-Win11-x64",
            screenWidth = 1920,
            screenHeight = 1080,
            dpr = 1.0f,
            cpuCores = 8,
            ramGb = 16,
            maxTouchPoints = 0,
            webGlVendor = "Google Inc. (NVIDIA)",
            webGlRenderer = "ANGLE (NVIDIA, NVIDIA GeForce RTX 4070 (0x00002786) Direct3D11 vs_5_0 ps_5_0, D3D11)",
            fontsCsv = "Arial,Calibri,Cambria,Consolas,Courier New,Georgia,Segoe UI,Tahoma,Times New Roman,Trebuchet MS,Verdana"
        ),
        DeviceBlueprint(
            id = "win10_edge_iris",
            title = "Windows • Edge 131",
            subtitle = "Business Ultrabook • Intel Iris Xe",
            osPlatform = OsPlatform.WINDOWS,
            browserEngine = BrowserEngine.EDGE,
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
            vendor = "Google Inc.",
            deviceModel = "ThinkPad-X1",
            screenWidth = 1536,
            screenHeight = 864,
            dpr = 1.25f,
            cpuCores = 12,
            ramGb = 16,
            maxTouchPoints = 0,
            webGlVendor = "Google Inc. (Intel)",
            webGlRenderer = "ANGLE (Intel, Intel(R) Iris(R) Xe Graphics Direct3D11 vs_5_0 ps_5_0, D3D11)",
            fontsCsv = "Arial,Calibri,Cambria,Consolas,Segoe UI,Tahoma,Times New Roman,Verdana"
        ),
        DeviceBlueprint(
            id = "mac_intel_chrome",
            title = "Macos • Chrome 132",
            subtitle = "MacBook Pro Intel • AMD Radeon Pro",
            osPlatform = OsPlatform.MACOS,
            browserEngine = BrowserEngine.CHROME,
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
            vendor = "Google Inc.",
            deviceModel = "MacBookPro16,1",
            screenWidth = 1728,
            screenHeight = 1117,
            dpr = 2.0f,
            cpuCores = 8,
            ramGb = 16,
            maxTouchPoints = 0,
            webGlVendor = "Google Inc. (ATI Technologies Inc.)",
            webGlRenderer = "ANGLE (ATI Technologies Inc., AMD Radeon Pro 5500M OpenGL Engine, OpenGL 4.1)",
            fontsCsv = "Arial,Courier,Courier New,Georgia,Helvetica,Helvetica Neue,Monaco,Palatino,Times,Times New Roman"
        ),
        DeviceBlueprint(
            id = "mac_m1_chrome",
            title = "Macos M1 • Chrome 132",
            subtitle = "Apple M1 Pro • Metal GPU",
            osPlatform = OsPlatform.MACOS_M1,
            browserEngine = BrowserEngine.CHROME,
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
            vendor = "Google Inc.",
            deviceModel = "MacBookPro18,3",
            screenWidth = 1512,
            screenHeight = 982,
            dpr = 2.0f,
            cpuCores = 8,
            ramGb = 16,
            maxTouchPoints = 0,
            webGlVendor = "Google Inc. (Apple)",
            webGlRenderer = "ANGLE (Apple, ANGLE Metal Renderer: Apple M1 Pro, Unspecified Version)",
            fontsCsv = "Arial,Courier New,Georgia,Helvetica,Helvetica Neue,Menlo,Monaco,San Francisco,Times New Roman"
        ),
        DeviceBlueprint(
            id = "mac_m2_safari",
            title = "Macos M2 • Safari 18",
            subtitle = "Apple M2 Air • Apple GPU",
            osPlatform = OsPlatform.MACOS_M2,
            browserEngine = BrowserEngine.SAFARI,
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Safari/605.1.15",
            vendor = "Apple Computer, Inc.",
            deviceModel = "Mac14,2",
            screenWidth = 1470,
            screenHeight = 956,
            dpr = 2.0f,
            cpuCores = 8,
            ramGb = 8,
            maxTouchPoints = 0,
            webGlVendor = "Apple Inc.",
            webGlRenderer = "Apple GPU",
            fontsCsv = "Arial,Baskerville,Georgia,Helvetica,Helvetica Neue,Monaco,Palatino,San Francisco,Times"
        ),
        DeviceBlueprint(
            id = "mac_m3_chrome",
            title = "Macos M3 • Chrome 132",
            subtitle = "MacBook Pro M3 Max • Metal GPU",
            osPlatform = OsPlatform.MACOS_M3,
            browserEngine = BrowserEngine.CHROME,
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
            vendor = "Google Inc.",
            deviceModel = "Mac15,9",
            screenWidth = 1728,
            screenHeight = 1117,
            dpr = 2.0f,
            cpuCores = 12,
            ramGb = 32,
            maxTouchPoints = 0,
            webGlVendor = "Google Inc. (Apple)",
            webGlRenderer = "ANGLE (Apple, ANGLE Metal Renderer: Apple M3 Max, Unspecified Version)",
            fontsCsv = "Arial,Courier New,Georgia,Helvetica,Helvetica Neue,Menlo,Monaco,San Francisco,Times New Roman"
        ),
        DeviceBlueprint(
            id = "linux_firefox",
            title = "Linux • Firefox 134",
            subtitle = "Ubuntu 24.04 x64 • AMD Radeon RX 7800 XT",
            osPlatform = OsPlatform.LINUX,
            browserEngine = BrowserEngine.FIREFOX,
            userAgent = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:134.0) Gecko/20100101 Firefox/134.0",
            vendor = "",
            deviceModel = "Ubuntu-x86_64",
            screenWidth = 2560,
            screenHeight = 1440,
            dpr = 1.0f,
            cpuCores = 16,
            ramGb = 32,
            maxTouchPoints = 0,
            webGlVendor = "AMD",
            webGlRenderer = "AMD Radeon RX 7800 XT (radeonsi, navi32, LLVM 18.1.3, DRM 3.57)",
            fontsCsv = "DejaVu Sans,Liberation Sans,Noto Sans,Ubuntu,Ubuntu Mono"
        ),
        DeviceBlueprint(
            id = "ios_iphone16",
            title = "iOS 18 • iPhone 16 Pro Max",
            subtitle = "Mobile Safari 18 • Apple A18 Pro GPU",
            osPlatform = OsPlatform.IOS,
            browserEngine = BrowserEngine.SAFARI,
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Mobile/15E148 Safari/604.1",
            vendor = "Apple Computer, Inc.",
            deviceModel = "iPhone17,2",
            screenWidth = 430,
            screenHeight = 932,
            dpr = 3.0f,
            cpuCores = 6,
            ramGb = 8,
            maxTouchPoints = 5,
            webGlVendor = "Apple Inc.",
            webGlRenderer = "Apple GPU",
            fontsCsv = "Helvetica,Helvetica Neue,San Francisco,Arial,Courier,Georgia,Times New Roman"
        )
    )

    val mobileBlueprints: List<DeviceBlueprint>
        get() = deviceBlueprints.filter { it.osPlatform.isMobile }

    val desktopBlueprints: List<DeviceBlueprint>
        get() = deviceBlueprints.filter { !it.osPlatform.isMobile }

    /**
     * Picks mobile blueprints for 90% of profiles (indices not ending in 9) and desktop only
     * for 10%, so fleets open predominantly in mobile view rather than desktop mode.
     */
    fun fleetBlueprintForIndex(index: Int): DeviceBlueprint {
        val mobiles = mobileBlueprints
        val desktops = desktopBlueprints
        if (index % 10 == 9 && desktops.isNotEmpty()) {
            return desktops[(index / 10) % desktops.size]
        }
        return mobiles[abs(index) % mobiles.size]
    }

    private val extraAndroidModels = listOf(
        "SM-S938B" to "Qualcomm|Adreno (TM) 830",
        "SM-S936B" to "Qualcomm|Adreno (TM) 830",
        "SM-S928B" to "Qualcomm|Adreno (TM) 750",
        "SM-S921B" to "Samsung Electronics Co., Ltd.|ANGLE (Samsung Xclipse 940) on Vulkan 1.3",
        "SM-A556B" to "Samsung Electronics Co., Ltd.|ANGLE (Samsung Xclipse 530) on Vulkan 1.3",
        "SM-F741B" to "Qualcomm|Adreno (TM) 750",
        "Pixel 9 Pro" to "ARM|Mali-G715-Immortalis MC7",
        "Pixel 9" to "ARM|Mali-G715 MC7",
        "Pixel 8 Pro" to "ARM|Mali-G715-Immortalis MC10",
        "Pixel 8a" to "ARM|Mali-G715 MC7",
        "CPH2653" to "Qualcomm|Adreno (TM) 830",
        "CPH2581" to "Qualcomm|Adreno (TM) 750",
        "24129PN74G" to "Qualcomm|Adreno (TM) 830",
        "23116PN5BC" to "Qualcomm|Adreno (TM) 750",
        "V2405A" to "ARM|Immortalis-G925",
        "XT2403-2" to "Qualcomm|Adreno (TM) 735",
        "A142" to "ARM|Mali-G610 MC4"
    )

    private val chromeMobileBuilds = listOf(
        "130.0.6723.107",
        "131.0.6778.135",
        "131.0.6778.200",
        "132.0.6834.79",
        "132.0.6834.122",
        "132.0.6834.163",
        "133.0.6943.49",
        "133.0.6943.89",
        "134.0.6998.36"
    )

    private val iosVersions = listOf(
        "17_6" to "17.6",
        "18_0" to "18.0",
        "18_1" to "18.1",
        "18_2" to "18.2",
        "18_3" to "18.3"
    )

    private val iphoneModels = listOf(
        "iPhone17,2", "iPhone17,1", "iPhone16,2", "iPhone16,1", "iPhone15,4", "iPhone15,2"
    )

    private val mobileScreens = listOf(
        Triple(360, 800, 3.0f),
        Triple(384, 854, 3.0f),
        Triple(390, 844, 3.0f),
        Triple(393, 852, 3.0f),
        Triple(400, 890, 3.0f),
        Triple(411, 914, 3.5f),
        Triple(412, 915, 2.625f),
        Triple(412, 919, 3.5f),
        Triple(428, 926, 3.0f),
        Triple(430, 932, 3.0f)
    )

    fun computeSpoofedRomGb(audioSeed: Long, webGlSeed: Long): Int {
        val tiers = listOf(64, 128, 256, 512, 1024)
        val idx = abs((audioSeed xor webGlSeed).toInt()) % tiers.size
        return tiers[idx]
    }

    fun computeSpoofedRomUsedMb(canvasSeed: Long): Int {
        return 6200 + (abs(canvasSeed % 42_000L)).toInt()
    }

    fun blueprintForOs(os: OsPlatform): DeviceBlueprint {
        return deviceBlueprints.firstOrNull { it.osPlatform == os } ?: deviceBlueprints.first()
    }

    val geoPresets = listOf(
        GeoTimezonePreset("New York, US", "US", "America/New_York", "en-US", "en-US,en", 40.7128, -74.0060),
        GeoTimezonePreset("Los Angeles, US", "US", "America/Los_Angeles", "en-US", "en-US,en", 34.0522, -118.2437),
        GeoTimezonePreset("London, UK", "GB", "Europe/London", "en-GB", "en-GB,en", 51.5074, -0.1278),
        GeoTimezonePreset("Frankfurt, DE", "DE", "Europe/Berlin", "de-DE", "de-DE,de,en-US,en", 50.1109, 8.6821),
        GeoTimezonePreset("Paris, FR", "FR", "Europe/Paris", "fr-FR", "fr-FR,fr,en", 48.8566, 2.3522),
        GeoTimezonePreset("Toronto, CA", "CA", "America/Toronto", "en-CA", "en-CA,en,fr", 43.6532, -79.3832),
        GeoTimezonePreset("Tokyo, JP", "JP", "Asia/Tokyo", "ja-JP", "ja-JP,ja,en", 35.6762, 139.6503),
        GeoTimezonePreset("Singapore, SG", "SG", "Asia/Singapore", "en-SG", "en-SG,en,zh", 1.3521, 103.8198),
        GeoTimezonePreset("Dubai, AE", "AE", "Asia/Dubai", "ar-AE", "ar-AE,en-US,en", 25.2048, 55.2708),
        GeoTimezonePreset("Sydney, AU", "AU", "Australia/Sydney", "en-AU", "en-AU,en", -33.8688, 151.2093)
    )

    fun createRandomProfile(
        name: String,
        groupTag: String = "General",
        proxyId: Long? = null,
        preferredBlueprint: DeviceBlueprint? = null,
        preferredGeo: GeoTimezonePreset? = null,
        uniqueHardwareVariance: Boolean = false
    ): BrowserProfileEntity {
        val rng = Random(System.nanoTime())
        val bp = preferredBlueprint ?: mobileBlueprints.random(rng)
        val geo = preferredGeo ?: geoPresets.first()
        val uuidShort = UUID.randomUUID().toString().take(8)
        val color = accentColors[abs(rng.nextInt()) % accentColors.size]

        var finalUa = bp.userAgent
        var finalModel = bp.deviceModel
        var finalGlVendor = bp.webGlVendor
        var finalGlRenderer = bp.webGlRenderer
        var finalWidth = bp.screenWidth
        var finalHeight = bp.screenHeight
        var finalDpr = bp.dpr
        var finalCores = bp.cpuCores
        var finalRam = bp.ramGb
        var finalFonts = bp.fontsCsv

        if (uniqueHardwareVariance) {
            if (bp.osPlatform == OsPlatform.ANDROID) {
                val (mModel, gpuPair) = extraAndroidModels.random(rng)
                val gpuSplit = gpuPair.split("|")
                val androidVer = listOf("13", "14", "15", "16").random(rng)
                val chromeVer = chromeMobileBuilds.random(rng)
                val scr = mobileScreens.random(rng)
                finalModel = mModel
                finalGlVendor = gpuSplit.getOrElse(0) { bp.webGlVendor }
                finalGlRenderer = gpuSplit.getOrElse(1) { bp.webGlRenderer }
                finalUa = "Mozilla/5.0 (Linux; Android $androidVer; $mModel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVer Mobile Safari/537.36"
                finalWidth = scr.first
                finalHeight = scr.second
                finalDpr = scr.third
                finalCores = listOf(4, 6, 8, 12).random(rng)
                finalRam = listOf(4, 6, 8, 12, 16).random(rng)
            } else if (bp.osPlatform == OsPlatform.IOS) {
                val (iosUaVer, safVer) = iosVersions.random(rng)
                finalModel = iphoneModels.random(rng)
                finalUa = "Mozilla/5.0 (iPhone; CPU iPhone OS $iosUaVer like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/$safVer Mobile/15E148 Safari/604.1"
                val scr = listOf(Triple(390, 844, 3.0f), Triple(393, 852, 3.0f), Triple(430, 932, 3.0f)).random(rng)
                finalWidth = scr.first
                finalHeight = scr.second
                finalDpr = scr.third
                finalCores = listOf(6, 8).random(rng)
                finalRam = listOf(6, 8).random(rng)
            } else {
                val chromeVer = chromeMobileBuilds.random(rng)
                finalUa = finalUa.replace(Regex("""Chrome/\d+\.\d+\.\d+\.\d+"""), "Chrome/$chromeVer")
                finalCores = listOf(6, 8, 12, 16).random(rng)
                finalRam = listOf(8, 16, 32).random(rng)
            }
            val baseFonts = bp.fontsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (baseFonts.size > 4) {
                val dropCount = rng.nextInt(0, 2)
                finalFonts = baseFonts.shuffled(rng).drop(dropCount).joinToString(",")
            }
        }

        return BrowserProfileEntity(
            name = name.ifBlank { "Profile-$uuidShort" },
            groupTag = groupTag.ifBlank { "General" },
            notes = "",
            colorHex = color,
            createdAt = System.currentTimeMillis(),
            lastLaunchedAt = null,
            launchCount = 0,
            startUrl = "https://myip.link",
            proxyConnectionType = if (proxyId != null) {
                ProfileProxyConnectionType.VAULT.name
            } else {
                ProfileProxyConnectionType.WITHOUT_PROXY.name
            },
            proxyId = proxyId,
            osPlatform = bp.osPlatform.name,
            browserEngine = bp.browserEngine.name,
            userAgent = finalUa,
            platformString = bp.osPlatform.navigatorPlatform,
            vendorString = bp.vendor,
            deviceModel = finalModel,
            doNotTrack = false,
            syncLocationWithProxy = true,
            timezoneFillFromIp = true,
            timezone = geo.timezone,
            languagesFillFromIp = true,
            language = geo.language,
            languagesCsv = geo.languagesCsv,
            geolocationMode = GeolocationMode.PROMPT.name,
            geolocationBasedOnIp = true,
            latitude = geo.latitude + (rng.nextDouble(-0.015, 0.015)),
            longitude = geo.longitude + (rng.nextDouble(-0.015, 0.015)),
            geoAccuracy = rng.nextInt(12, 48),
            screenWidth = finalWidth,
            screenHeight = finalHeight,
            devicePixelRatio = finalDpr,
            colorDepth = 24,
            hardwareConcurrency = finalCores,
            deviceMemory = finalRam,
            maxTouchPoints = bp.maxTouchPoints,
            batterySpoofEnabled = bp.osPlatform.isMobile,
            batteryLevel = rng.nextInt(38, 96) / 100f,
            batteryCharging = rng.nextBoolean(),
            webRtcMode = WebRtcMode.BASED_ON_IP.name,
            webRtcFakeIp = "10.8.${rng.nextInt(1, 240)}.${rng.nextInt(2, 250)}",
            canvasMode = SpoofMode.NOISE.name,
            canvasSeed = abs(rng.nextLong(100_000L, 999_999_999L)),
            audioMode = SpoofMode.NOISE.name,
            audioSeed = abs(rng.nextLong(100_000L, 999_999_999L)),
            webGlImageMode = SpoofMode.NOISE.name,
            webGlMetadataMode = WebGlMode.MASK.name,
            webGlVendor = finalGlVendor,
            webGlRenderer = finalGlRenderer,
            webGlSeed = abs(rng.nextLong(100_000L, 999_999_999L)),
            webGlMaxTextureSize = listOf(8192, 16384).random(rng),
            webGlMaxVertexAttribs = 16,
            webGlShadingLang = "WebGL GLSL ES 1.0 (OpenGL ES GLSL ES 1.0 Chromium)",
            clientRectsSeed = abs(rng.nextLong(100_000L, 999_999_999L)),
            fontMasking = true,
            enableUnicodeGlyphsAndDomRect = true,
            fontsListCsv = finalFonts,
            maskMediaDevices = true,
            videoInputsCount = 0,
            audioInputsCount = 0,
            audioOutputsCount = 0,
            localStorageAndIndexedDb = true,
            enableExtensionStorage = false,
            allowVulnerablePlugins = false,
            activeSessionLock = true,
            enableGoogleServices = false,
            bookmarkSaving = true,
            historySaving = true,
            passwordSaving = true,
            sessionSaving = true,
            debugMode = false,
            customDns = "",
            webViewProfileName = "cd_prof_$uuidShort"
        )
    }

    fun inferLocaleForCountry(countryCode: String): Pair<String, String> {
        return when (countryCode.uppercase()) {
            "US" -> "en-US" to "en-US,en"
            "GB", "UK" -> "en-GB" to "en-GB,en"
            "CA" -> "en-CA" to "en-CA,en,fr"
            "AU" -> "en-AU" to "en-AU,en"
            "DE" -> "de-DE" to "de-DE,de,en-US,en"
            "FR" -> "fr-FR" to "fr-FR,fr,en"
            "ES" -> "es-ES" to "es-ES,es,en"
            "IT" -> "it-IT" to "it-IT,it,en"
            "NL" -> "nl-NL" to "nl-NL,nl,en"
            "JP" -> "ja-JP" to "ja-JP,ja,en"
            "KR" -> "ko-KR" to "ko-KR,ko,en"
            "SG" -> "en-SG" to "en-SG,en"
            "AE" -> "ar-AE" to "ar-AE,en-US,en"
            "PK" -> "en-PK" to "en-PK,ur-PK,en"
            "IN" -> "en-IN" to "en-IN,hi-IN,en"
            "BR" -> "pt-BR" to "pt-BR,pt,en"
            "TR" -> "tr-TR" to "tr-TR,tr,en"
            "PL" -> "pl-PL" to "pl-PL,pl,en"
            else -> "en-US" to "en-US,en"
        }
    }

    fun countryCodeToFlagEmoji(countryCode: String): String {
        val code = countryCode.trim().uppercase()
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) return "🌐"
        val first = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
}
