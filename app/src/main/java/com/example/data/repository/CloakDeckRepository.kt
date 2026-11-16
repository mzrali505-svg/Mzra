package com.example.data.repository

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.example.browser.AutomationPresets
import com.example.data.local.AppPreferences
import com.example.data.local.AppSettings
import com.example.data.local.AutomationFlowEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserHistoryEntity
import com.example.data.local.BrowserProfileEntity
import com.example.data.local.CloakDeckDatabase
import com.example.data.local.ProxyEntity
import com.example.data.local.SearchEngine
import com.example.data.model.FingerprintPresets
import com.example.data.model.ProxyStatus
import com.example.data.model.ProxyType
import com.example.data.network.ProxyCheckResult
import com.example.data.network.ProxyChecker
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

enum class BulkTimingMode(val label: String) {
    PER_LINE_OR_FIXED("Per-line |sec or Fixed"),
    RANDOM_RANGE("Unique Random Range"),
    CUSTOM_SEQUENCE("Cycle Custom Seconds")
}

data class ProxyOrderCreationResult(
    val groupTag: String,
    val createdCount: Int
)

class CloakDeckRepository(context: Context) {
    companion object {
        fun parseCommaDurationsSeconds(raw: String, fallbackSec: Int = 45): List<Int> {
            val safeFallback = fallbackSec.coerceIn(3, 3600)
            val parsed = raw.split(",", ";", "\n", " ")
                .mapNotNull { token -> token.trim().toIntOrNull()?.coerceIn(3, 3600) }
            return parsed.ifEmpty { listOf(safeFallback) }
        }
    }

    private val db = CloakDeckDatabase.getInstance(context)
    private val proxyDao = db.proxyDao()
    private val profileDao = db.profileDao()
    private val historyDao = db.historyDao()
    private val automationDao = db.automationDao()
    private val prefs = AppPreferences(context)

    val profilesFlow: Flow<List<BrowserProfileEntity>> = profileDao.observeAll()
    val proxiesFlow: Flow<List<ProxyEntity>> = proxyDao.observeAll()
    val automationsFlow: Flow<List<AutomationFlowEntity>> = automationDao.observeAll()
    val settingsFlow: Flow<AppSettings> = prefs.settingsFlow

    fun observeProfile(id: Long): Flow<BrowserProfileEntity?> = profileDao.observeById(id)
    fun observeHistory(profileId: Long): Flow<List<BrowserHistoryEntity>> = historyDao.observeHistory(profileId)
    fun observeBookmarks(profileId: Long): Flow<List<BookmarkEntity>> = historyDao.observeBookmarks(profileId)

    suspend fun getProfileOnce(id: Long): BrowserProfileEntity? = profileDao.getById(id)
    suspend fun getProxyOnce(id: Long): ProxyEntity? = proxyDao.getById(id)
    suspend fun getAutomationOnce(id: Long): AutomationFlowEntity? = automationDao.getById(id)

    suspend fun ensureStarterAutomations() = withContext(Dispatchers.IO) {
        val current = automationDao.observeAll().first()
        if (current.isEmpty()) {
            db.withTransaction {
                AutomationPresets.defaultStarterFlows().forEach { flow ->
                    automationDao.insert(flow)
                }
            }
        }
    }

    suspend fun ensureSeededInitialProxies() = withContext(Dispatchers.IO) {
        if (prefs.hasSeededProxiesV1()) return@withContext

        val existingKeys = proxyDao.getAllOnce()
            .map { "${it.host}:${it.port}:${it.username}" }
            .toSet()

        val usSessions = listOf(
            "ro1q7BBEOn" to 20,
            "Ckbo5WniY5" to 35,
            "hzoU7urfwL" to 45,
            "w7Be4KPxzK" to 60,
            "xhmT3hqriu" to 75,
            "L8yL84Njkv" to 90,
            "Juva1kWI2Y" to 120,
            "GD2PxIt8SM" to 150,
            "bLosr3PHuv" to 180,
            "WdjAc2JUzM" to 30
        )

        val baseTime = System.currentTimeMillis()
        val proxiesToInsert = usSessions.mapIndexedNotNull { idx, (ssid, dwellSec) ->
            val username = "goodday_plsK-country-US-ssid-$ssid-sst-30"
            val key = "niceproxy.io:17521:$username"
            if (key in existingKeys) return@mapIndexedNotNull null
            ProxyEntity(
                name = "US Node #${idx + 1} ($ssid)",
                type = ProxyType.AUTO.name,
                host = "niceproxy.io",
                port = 17521,
                username = username,
                password = "mzrali",
                country = "United States",
                countryCode = "US",
                timezone = "America/New_York",
                status = ProxyStatus.UNCHECKED.name,
                notes = "US Sticky Residential (30m SST)",
                createdAt = baseTime - idx * 1000L,
                humanizeEnabled = false,
                humanizeDwellSeconds = dwellSec,
                humanizeClickInternalLink = true,
                geolocationMode = "PROMPT",
                geolocationBasedOnIp = true,
                timezoneFillFromIp = true,
                webRtcMode = "BASED_ON_IP"
            )
        }

        if (proxiesToInsert.isNotEmpty()) {
            db.withTransaction {
                val insertedIds = proxyDao.insertAll(proxiesToInsert)
                val usNewYork = FingerprintPresets.geoPresets.first()
                val profilesToInsert = proxiesToInsert.zip(insertedIds).mapIndexed { idx, (proxy, proxyId) ->
                    val bp = FingerprintPresets.fleetBlueprintForIndex(idx)
                    FingerprintPresets.createRandomProfile(
                        name = proxy.name,
                        groupTag = "US Nodes",
                        proxyId = proxyId,
                        preferredBlueprint = bp,
                        preferredGeo = usNewYork
                    ).copy(
                        proxyConnectionType = com.example.data.model.ProfileProxyConnectionType.VAULT.name,
                        geolocationMode = proxy.geolocationMode,
                        geolocationBasedOnIp = proxy.geolocationBasedOnIp,
                        timezoneFillFromIp = proxy.timezoneFillFromIp,
                        webRtcMode = proxy.webRtcMode,
                        humanizeSearchEnabled = proxy.humanizeEnabled,
                        humanizeKeyword = proxy.humanizeKeyword,
                        humanizeTargetDomain = proxy.humanizeTargetDomain,
                        humanizeDwellSeconds = proxy.humanizeDwellSeconds,
                        humanizeClickInternalLink = proxy.humanizeClickInternalLink
                    )
                }
                profileDao.insertAll(profilesToInsert)
            }
        }

        prefs.markProxiesSeededV1()
    }

    suspend fun ensureMostlyMobileDefaultsV2() = withContext(Dispatchers.IO) {
        if (prefs.hasMigratedMobileDefaultsV2()) return@withContext
        val existing = profileDao.observeAll().first()
        val mobiles = FingerprintPresets.mobileBlueprints
        if (existing.isNotEmpty() && mobiles.isNotEmpty()) {
            val toUpdate = mutableListOf<BrowserProfileEntity>()
            existing.forEachIndexed { idx, prof ->
                val isCurrentlyMobile = runCatching {
                    com.example.data.model.OsPlatform.valueOf(prof.osPlatform).isMobile
                }.getOrDefault(false)
                if (!isCurrentlyMobile && idx % 10 != 9) {
                    val bp = mobiles[idx % mobiles.size]
                    toUpdate.add(
                        prof.copy(
                            osPlatform = bp.osPlatform.name,
                            browserEngine = bp.browserEngine.name,
                            userAgent = bp.userAgent,
                            platformString = bp.osPlatform.navigatorPlatform,
                            vendorString = bp.vendor,
                            deviceModel = bp.deviceModel,
                            screenWidth = bp.screenWidth,
                            screenHeight = bp.screenHeight,
                            devicePixelRatio = bp.dpr,
                            hardwareConcurrency = bp.cpuCores,
                            deviceMemory = bp.ramGb,
                            maxTouchPoints = bp.maxTouchPoints,
                            batterySpoofEnabled = true,
                            webGlVendor = bp.webGlVendor,
                            webGlRenderer = bp.webGlRenderer,
                            fontsListCsv = bp.fontsCsv
                        )
                    )
                }
            }
            if (toUpdate.isNotEmpty()) {
                db.withTransaction {
                    toUpdate.chunked(200).forEach { chunk ->
                        profileDao.updateAll(chunk)
                    }
                }
            }
        }
        prefs.markMobileDefaultsMigratedV2()
    }

    suspend fun saveAutomation(flow: AutomationFlowEntity): Long {
        return if (flow.id == 0L) {
            automationDao.insert(flow)
        } else {
            automationDao.update(flow)
            flow.id
        }
    }

    suspend fun deleteAutomation(id: Long) = automationDao.deleteById(id)

    suspend fun saveProfile(profile: BrowserProfileEntity): Long {
        return if (profile.id == 0L) {
            profileDao.insert(profile)
        } else {
            profileDao.update(profile)
            profile.id
        }
    }

    suspend fun duplicateProfile(source: BrowserProfileEntity): Long {
        val rng = Random(System.nanoTime())
        val shortId = UUID.randomUUID().toString().take(6)
        val clone = source.copy(
            id = 0L,
            name = "${source.name} (Copy)",
            createdAt = System.currentTimeMillis(),
            lastLaunchedAt = null,
            launchCount = 0,
            canvasSeed = abs(rng.nextLong(100_000L, 999_999_999L)),
            webGlSeed = abs(rng.nextLong(100_000L, 999_999_999L)),
            audioSeed = abs(rng.nextLong(100_000L, 999_999_999L)),
            clientRectsSeed = abs(rng.nextLong(100_000L, 999_999_999L)),
            webViewProfileName = "cd_prof_$shortId"
        )
        return profileDao.insert(clone)
    }

    suspend fun markProfileLaunched(profileId: Long) {
        val current = profileDao.getById(profileId) ?: return
        profileDao.update(
            current.copy(
                lastLaunchedAt = System.currentTimeMillis(),
                launchCount = current.launchCount + 1
            )
        )
    }

    suspend fun deleteProfile(profile: BrowserProfileEntity) {
        profileDao.deleteById(profile.id)
        historyDao.clearHistoryForProfile(profile.id)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { ProfileStore.getInstance().deleteProfile(profile.webViewProfileName) }
        }
    }

    suspend fun saveProxy(proxy: ProxyEntity): Long {
        return if (proxy.id == 0L) {
            proxyDao.insert(proxy)
        } else {
            proxyDao.update(proxy)
            proxy.id
        }
    }

    suspend fun getFirstProfileForProxy(proxyId: Long): BrowserProfileEntity? {
        return profileDao.getFirstByProxyId(proxyId)
    }

    suspend fun saveProxyWithProfileTemplate(
        proxy: ProxyEntity,
        profileTemplate: BrowserProfileEntity,
        syncToProfile: Boolean = true
    ): Pair<Long, Long?> {
        val savedProxyId = saveProxy(proxy)
        if (!syncToProfile) return savedProxyId to null

        val existing = profileDao.getFirstByProxyId(savedProxyId)
        val mergedProfile = (existing ?: profileTemplate).copy(
            id = existing?.id ?: 0L,
            name = existing?.name ?: (proxy.name.ifBlank { "${proxy.host}:${proxy.port}" }),
            proxyConnectionType = com.example.data.model.ProfileProxyConnectionType.VAULT.name,
            proxyId = savedProxyId,
            osPlatform = profileTemplate.osPlatform,
            browserEngine = profileTemplate.browserEngine,
            userAgent = profileTemplate.userAgent,
            platformString = profileTemplate.platformString,
            vendorString = profileTemplate.vendorString,
            deviceModel = profileTemplate.deviceModel,
            doNotTrack = profileTemplate.doNotTrack,
            timezoneFillFromIp = proxy.timezoneFillFromIp,
            timezone = if (proxy.timezone.isNotBlank() && proxy.timezoneFillFromIp) proxy.timezone else profileTemplate.timezone,
            languagesFillFromIp = profileTemplate.languagesFillFromIp,
            language = profileTemplate.language,
            languagesCsv = profileTemplate.languagesCsv,
            geolocationMode = proxy.geolocationMode,
            geolocationBasedOnIp = proxy.geolocationBasedOnIp,
            latitude = if (proxy.latitude != null && proxy.geolocationBasedOnIp) proxy.latitude else profileTemplate.latitude,
            longitude = if (proxy.longitude != null && proxy.geolocationBasedOnIp) proxy.longitude else profileTemplate.longitude,
            screenWidth = profileTemplate.screenWidth,
            screenHeight = profileTemplate.screenHeight,
            devicePixelRatio = profileTemplate.devicePixelRatio,
            hardwareConcurrency = profileTemplate.hardwareConcurrency,
            deviceMemory = profileTemplate.deviceMemory,
            maxTouchPoints = profileTemplate.maxTouchPoints,
            webRtcMode = proxy.webRtcMode,
            webRtcFakeIp = proxy.lastExitIp.ifBlank { profileTemplate.webRtcFakeIp },
            canvasMode = profileTemplate.canvasMode,
            canvasSeed = profileTemplate.canvasSeed,
            audioMode = profileTemplate.audioMode,
            audioSeed = profileTemplate.audioSeed,
            webGlImageMode = profileTemplate.webGlImageMode,
            webGlMetadataMode = profileTemplate.webGlMetadataMode,
            webGlVendor = profileTemplate.webGlVendor,
            webGlRenderer = profileTemplate.webGlRenderer,
            webGlSeed = profileTemplate.webGlSeed,
            webGlMaxTextureSize = profileTemplate.webGlMaxTextureSize,
            webGlMaxVertexAttribs = profileTemplate.webGlMaxVertexAttribs,
            webGlShadingLang = profileTemplate.webGlShadingLang,
            clientRectsSeed = profileTemplate.clientRectsSeed,
            fontMasking = profileTemplate.fontMasking,
            enableUnicodeGlyphsAndDomRect = profileTemplate.enableUnicodeGlyphsAndDomRect,
            fontsListCsv = profileTemplate.fontsListCsv,
            maskMediaDevices = profileTemplate.maskMediaDevices,
            videoInputsCount = profileTemplate.videoInputsCount,
            audioInputsCount = profileTemplate.audioInputsCount,
            audioOutputsCount = profileTemplate.audioOutputsCount,
            localStorageAndIndexedDb = profileTemplate.localStorageAndIndexedDb,
            enableExtensionStorage = profileTemplate.enableExtensionStorage,
            allowVulnerablePlugins = profileTemplate.allowVulnerablePlugins,
            activeSessionLock = profileTemplate.activeSessionLock,
            enableGoogleServices = profileTemplate.enableGoogleServices,
            bookmarkSaving = profileTemplate.bookmarkSaving,
            historySaving = profileTemplate.historySaving,
            passwordSaving = profileTemplate.passwordSaving,
            sessionSaving = profileTemplate.sessionSaving,
            debugMode = profileTemplate.debugMode,
            startUrl = profileTemplate.startUrl,
            customDns = profileTemplate.customDns,
            humanizeSearchEnabled = proxy.humanizeEnabled,
            humanizeKeyword = proxy.humanizeKeyword,
            humanizeTargetDomain = proxy.humanizeTargetDomain,
            humanizeDwellSeconds = proxy.humanizeDwellSeconds,
            humanizeClickInternalLink = proxy.humanizeClickInternalLink,
            humanizeScrollStyle = proxy.humanizeScrollStyle
        )
        val savedProfileId = saveProfile(mergedProfile)
        return savedProxyId to savedProfileId
    }

    suspend fun ensureProfileForProxy(proxyId: Long): Long? {
        val proxy = proxyDao.getById(proxyId) ?: return null
        val existing = profileDao.getFirstByProxyId(proxyId)
        if (existing != null) {
            val synced = existing.copy(
                geolocationMode = proxy.geolocationMode,
                geolocationBasedOnIp = proxy.geolocationBasedOnIp,
                timezoneFillFromIp = proxy.timezoneFillFromIp,
                webRtcMode = proxy.webRtcMode,
                humanizeSearchEnabled = proxy.humanizeEnabled,
                humanizeKeyword = proxy.humanizeKeyword,
                humanizeTargetDomain = proxy.humanizeTargetDomain,
                humanizeDwellSeconds = proxy.humanizeDwellSeconds,
                humanizeClickInternalLink = proxy.humanizeClickInternalLink,
                humanizeScrollStyle = proxy.humanizeScrollStyle
            )
            profileDao.update(synced)
            return existing.id
        }
        var created = FingerprintPresets.createRandomProfile(
            name = proxy.name.ifBlank { "${proxy.host}:${proxy.port}" },
            groupTag = "Proxy Pool",
            proxyId = proxyId
        ).copy(
            proxyConnectionType = com.example.data.model.ProfileProxyConnectionType.VAULT.name,
            geolocationMode = proxy.geolocationMode,
            geolocationBasedOnIp = proxy.geolocationBasedOnIp,
            timezoneFillFromIp = proxy.timezoneFillFromIp,
            webRtcMode = proxy.webRtcMode,
            humanizeSearchEnabled = proxy.humanizeEnabled,
            humanizeKeyword = proxy.humanizeKeyword,
            humanizeTargetDomain = proxy.humanizeTargetDomain,
            humanizeDwellSeconds = proxy.humanizeDwellSeconds,
            humanizeClickInternalLink = proxy.humanizeClickInternalLink,
            humanizeScrollStyle = proxy.humanizeScrollStyle
        )
        if (proxy.lastExitIp.isNotBlank()) {
            created = applyProxyGeoToProfile(created, proxy)
        }
        return profileDao.insert(created)
    }

    suspend fun bulkImportProxies(
        rawText: String,
        defaultType: ProxyType,
        humanizeEnabled: Boolean = false,
        keywordsPoolRaw: String = "",
        targetDomain: String = "",
        timingMode: BulkTimingMode = BulkTimingMode.PER_LINE_OR_FIXED,
        fixedSeconds: Int = 30,
        randomMinSeconds: Int = 10,
        randomMaxSeconds: Int = 120,
        customSequenceCsv: String = "5,20,45,120,180",
        geolocationMode: String = "PROMPT",
        webRtcMode: String = "BASED_ON_IP",
        createIsolatedProfiles: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        val parsed = ProxyChecker.parseBulkProxies(rawText, defaultType)
        if (parsed.isEmpty()) return@withContext 0

        val keywords = keywordsPoolRaw
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val customDurations = customSequenceCsv
            .split(",", " ", ";")
            .mapNotNull { it.trim().toIntOrNull()?.coerceIn(3, 3600) }
            .ifEmpty { listOf(fixedSeconds.coerceIn(3, 3600)) }

        val rng = Random(System.nanoTime())
        val safeMin = randomMinSeconds.coerceIn(3, 3600)
        val safeMax = randomMaxSeconds.coerceAtLeast(safeMin).coerceAtMost(3600)

        val enriched = parsed.mapIndexed { idx, p ->
            val hasLineDwell = p.humanizeEnabled && p.humanizeDwellSeconds != 30
            val assignedSeconds = when {
                hasLineDwell -> p.humanizeDwellSeconds
                timingMode == BulkTimingMode.RANDOM_RANGE -> {
                    if (safeMin == safeMax) safeMin else rng.nextInt(safeMin, safeMax + 1)
                }
                timingMode == BulkTimingMode.CUSTOM_SEQUENCE -> {
                    customDurations[idx % customDurations.size]
                }
                else -> fixedSeconds.coerceIn(3, 3600)
            }
            val assignedKeyword = p.humanizeKeyword.ifBlank {
                if (keywords.isNotEmpty()) keywords[idx % keywords.size] else ""
            }
            val assignedDomain = p.humanizeTargetDomain.ifBlank { targetDomain.trim() }

            p.copy(
                humanizeEnabled = humanizeEnabled || p.humanizeEnabled || assignedKeyword.isNotBlank(),
                humanizeKeyword = assignedKeyword,
                humanizeTargetDomain = assignedDomain,
                humanizeDwellSeconds = assignedSeconds,
                geolocationMode = geolocationMode,
                webRtcMode = webRtcMode
            )
        }

        db.withTransaction {
            val insertedIds = mutableListOf<Long>()
            enriched.chunked(200).forEach { chunk ->
                insertedIds.addAll(proxyDao.insertAll(chunk))
            }
            if (createIsolatedProfiles) {
                val concreteStyles = com.example.data.model.HumanizeScrollStyle.concreteStyles
                val profilesToInsert = enriched.zip(insertedIds).mapIndexed { idx, (proxy, proxyId) ->
                    FingerprintPresets.createRandomProfile(
                        name = proxy.name,
                        groupTag = "Batch Pool",
                        proxyId = proxyId,
                        preferredBlueprint = FingerprintPresets.fleetBlueprintForIndex(idx),
                        uniqueHardwareVariance = true
                    ).copy(
                        proxyConnectionType = com.example.data.model.ProfileProxyConnectionType.VAULT.name,
                        geolocationMode = proxy.geolocationMode,
                        geolocationBasedOnIp = proxy.geolocationBasedOnIp,
                        timezoneFillFromIp = proxy.timezoneFillFromIp,
                        webRtcMode = proxy.webRtcMode,
                        humanizeSearchEnabled = proxy.humanizeEnabled,
                        humanizeKeyword = proxy.humanizeKeyword,
                        humanizeTargetDomain = proxy.humanizeTargetDomain,
                        humanizeDwellSeconds = proxy.humanizeDwellSeconds,
                        humanizeClickInternalLink = proxy.humanizeClickInternalLink,
                        humanizeScrollStyle = concreteStyles[idx % concreteStyles.size].name
                    )
                }
                profilesToInsert.chunked(200).forEach { chunk ->
                    profileDao.insertAll(chunk)
                }
            }
        }
        enriched.size
    }

    suspend fun createProxyOrderCampaign(
        rawProxiesText: String,
        defaultType: ProxyType,
        keywordsPoolRaw: String,
        targetDomain: String,
        dwellSeconds: Int = 45,
        dwellSecondsSpec: String = dwellSeconds.toString(),
        groupTag: String
    ): ProxyOrderCreationResult = withContext(Dispatchers.IO) {
        val parsed = ProxyChecker.parseBulkProxies(rawProxiesText, defaultType).take(2000)
        if (parsed.isEmpty()) return@withContext ProxyOrderCreationResult(groupTag, 0)

        val cleanGroup = groupTag.trim().ifBlank { "Order" }
        val cleanDomain = targetDomain.trim()
        val keywords = keywordsPoolRaw
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val durationSequence = parseCommaDurationsSeconds(dwellSecondsSpec, fallbackSec = dwellSeconds)
        val now = System.currentTimeMillis()
        val concreteStyles = com.example.data.model.HumanizeScrollStyle.concreteStyles

        val enrichedProxies = parsed.mapIndexed { idx, p ->
            val kw = p.humanizeKeyword.ifBlank {
                if (keywords.isNotEmpty()) keywords[idx % keywords.size] else ""
            }
            val dom = p.humanizeTargetDomain.ifBlank { cleanDomain }
            val assignedFromSeq = durationSequence[idx % durationSequence.size]
            val sec = if (p.humanizeEnabled && p.humanizeDwellSeconds != 30) p.humanizeDwellSeconds else assignedFromSeq
            p.copy(
                name = "$cleanGroup Proxy #${idx + 1} (${p.host})",
                createdAt = now + idx,
                humanizeEnabled = true,
                humanizeKeyword = kw,
                humanizeTargetDomain = dom,
                humanizeDwellSeconds = sec,
                humanizeClickInternalLink = true,
                geolocationMode = "PROMPT",
                geolocationBasedOnIp = true,
                timezoneFillFromIp = true,
                webRtcMode = "BASED_ON_IP"
            )
        }

        var createdCount = 0
        db.withTransaction {
            val insertedProxyIds = mutableListOf<Long>()
            enrichedProxies.chunked(200).forEach { chunk ->
                insertedProxyIds.addAll(proxyDao.insertAll(chunk))
            }

            val orderProfiles = enrichedProxies.zip(insertedProxyIds).mapIndexed { idx, (proxy, proxyId) ->
                FingerprintPresets.createRandomProfile(
                    name = "$cleanGroup #${idx + 1}",
                    groupTag = cleanGroup,
                    proxyId = proxyId,
                    preferredBlueprint = FingerprintPresets.fleetBlueprintForIndex(idx),
                    uniqueHardwareVariance = true
                ).copy(
                    createdAt = now + idx,
                    proxyConnectionType = com.example.data.model.ProfileProxyConnectionType.VAULT.name,
                    geolocationMode = proxy.geolocationMode,
                    geolocationBasedOnIp = true,
                    timezoneFillFromIp = true,
                    languagesFillFromIp = true,
                    webRtcMode = "BASED_ON_IP",
                    humanizeSearchEnabled = true,
                    humanizeKeyword = proxy.humanizeKeyword,
                    humanizeTargetDomain = proxy.humanizeTargetDomain,
                    humanizeDwellSeconds = proxy.humanizeDwellSeconds,
                    humanizeClickInternalLink = true,
                    humanizeScrollStyle = concreteStyles[idx % concreteStyles.size].name
                )
            }

            orderProfiles.chunked(200).forEach { chunk ->
                profileDao.insertAll(chunk)
            }
            createdCount = orderProfiles.size
        }
        ProxyOrderCreationResult(cleanGroup, createdCount)
    }

    suspend fun persistSyncedProxyAndProfile(proxy: ProxyEntity, profile: BrowserProfileEntity) = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (proxy.id > 0L) {
                proxyDao.update(proxy)
            }
            if (profile.id > 0L) {
                profileDao.update(profile)
            }
        }
    }

    suspend fun prepareBatchQueueProfileIds(onlyHumanized: Boolean): List<Long> = withContext(Dispatchers.IO) {
        val allProxies = proxyDao.getAllOnce()
        val targets = if (onlyHumanized) {
            allProxies.filter { it.humanizeEnabled }.ifEmpty { allProxies }
        } else {
            allProxies
        }
        if (targets.isEmpty()) return@withContext emptyList()

        val existingByProxy = profileDao.observeAll().first()
            .filter { it.proxyId != null }
            .associateBy { it.proxyId!! }

        val toUpdate = mutableListOf<BrowserProfileEntity>()
        val toCreate = mutableListOf<BrowserProfileEntity>()
        val resultIds = ArrayList<Long>(targets.size)

        db.withTransaction {
            targets.forEachIndexed { idx, proxy ->
                val existing = existingByProxy[proxy.id]
                if (existing != null) {
                    toUpdate.add(
                        existing.copy(
                            geolocationMode = proxy.geolocationMode,
                            geolocationBasedOnIp = proxy.geolocationBasedOnIp,
                            timezoneFillFromIp = proxy.timezoneFillFromIp,
                            webRtcMode = proxy.webRtcMode,
                            humanizeSearchEnabled = proxy.humanizeEnabled,
                            humanizeKeyword = proxy.humanizeKeyword,
                            humanizeTargetDomain = proxy.humanizeTargetDomain,
                            humanizeDwellSeconds = proxy.humanizeDwellSeconds,
                            humanizeClickInternalLink = proxy.humanizeClickInternalLink,
                            humanizeScrollStyle = proxy.humanizeScrollStyle
                        )
                    )
                    resultIds.add(existing.id)
                } else {
                    var created = FingerprintPresets.createRandomProfile(
                        name = proxy.name.ifBlank { "${proxy.host}:${proxy.port}" },
                        groupTag = "Proxy Pool",
                        proxyId = proxy.id,
                        preferredBlueprint = FingerprintPresets.fleetBlueprintForIndex(idx),
                        uniqueHardwareVariance = true
                    ).copy(
                        proxyConnectionType = com.example.data.model.ProfileProxyConnectionType.VAULT.name,
                        geolocationMode = proxy.geolocationMode,
                        geolocationBasedOnIp = proxy.geolocationBasedOnIp,
                        timezoneFillFromIp = proxy.timezoneFillFromIp,
                        webRtcMode = proxy.webRtcMode,
                        humanizeSearchEnabled = proxy.humanizeEnabled,
                        humanizeKeyword = proxy.humanizeKeyword,
                        humanizeTargetDomain = proxy.humanizeTargetDomain,
                        humanizeDwellSeconds = proxy.humanizeDwellSeconds,
                        humanizeClickInternalLink = proxy.humanizeClickInternalLink,
                        humanizeScrollStyle = proxy.humanizeScrollStyle
                    )
                    if (proxy.lastExitIp.isNotBlank()) {
                        created = applyProxyGeoToProfile(created, proxy)
                    }
                    toCreate.add(created)
                }
            }
            if (toUpdate.isNotEmpty()) {
                toUpdate.chunked(200).forEach { profileDao.updateAll(it) }
            }
            if (toCreate.isNotEmpty()) {
                toCreate.chunked(200).forEach { chunk ->
                    resultIds.addAll(profileDao.insertAll(chunk))
                }
            }
        }
        resultIds
    }

    suspend fun deleteProxy(proxyId: Long) {
        profileDao.unbindProxy(proxyId)
        proxyDao.deleteById(proxyId)
    }

    suspend fun checkInlineProxy(proxy: ProxyEntity): ProxyCheckResult {
        return ProxyChecker.checkProxy(proxy)
    }

    suspend fun checkAndUpdateProxy(proxyId: Long): ProxyCheckResult {
        val proxy = proxyDao.getById(proxyId) ?: return ProxyCheckResult(false, errorMessage = "Proxy not found")
        proxyDao.update(proxy.copy(status = ProxyStatus.CHECKING.name, lastError = ""))

        val result = ProxyChecker.checkProxy(proxy)
        val updated = if (result.success) {
            proxy.copy(
                type = result.detectedType?.name ?: proxy.type,
                lastExitIp = result.exitIp,
                country = result.country,
                countryCode = result.countryCode,
                city = result.city,
                isp = result.isp,
                timezone = result.timezone,
                latitude = result.latitude,
                longitude = result.longitude,
                latencyMs = result.latencyMs,
                status = ProxyStatus.ONLINE.name,
                lastError = "",
                lastCheckedAt = System.currentTimeMillis()
            )
        } else {
            proxy.copy(
                status = ProxyStatus.OFFLINE.name,
                lastError = result.errorMessage,
                lastCheckedAt = System.currentTimeMillis()
            )
        }
        proxyDao.update(updated)

        if (result.success && prefs.settingsFlow.first().autoSyncGeoWithProxy) {
            val boundProfiles = profileDao.getByProxyId(proxyId)
            boundProfiles.forEach { prof ->
                if (prof.syncLocationWithProxy) {
                    val synced = applyProxyGeoToProfile(prof, updated)
                    profileDao.update(synced)
                }
            }
        }
        return result
    }

    fun applyProxyGeoToProfile(profile: BrowserProfileEntity, proxy: ProxyEntity): BrowserProfileEntity {
        val (lang, langsCsv) = if (proxy.countryCode.isNotBlank() && profile.languagesFillFromIp) {
            FingerprintPresets.inferLocaleForCountry(proxy.countryCode)
        } else {
            profile.language to profile.languagesCsv
        }
        return profile.copy(
            timezone = if (profile.timezoneFillFromIp) proxy.timezone.ifBlank { profile.timezone } else profile.timezone,
            language = lang,
            languagesCsv = langsCsv,
            latitude = if (profile.geolocationBasedOnIp) (proxy.latitude ?: profile.latitude) else profile.latitude,
            longitude = if (profile.geolocationBasedOnIp) (proxy.longitude ?: profile.longitude) else profile.longitude,
            webRtcFakeIp = proxy.lastExitIp.ifBlank { profile.webRtcFakeIp }
        )
    }

    fun applyProxyCheckResultToProfile(profile: BrowserProfileEntity, result: ProxyCheckResult): BrowserProfileEntity {
        val (lang, langsCsv) = if (result.countryCode.isNotBlank() && profile.languagesFillFromIp) {
            FingerprintPresets.inferLocaleForCountry(result.countryCode)
        } else {
            profile.language to profile.languagesCsv
        }
        return profile.copy(
            inlineProxyType = result.detectedType?.name ?: profile.inlineProxyType,
            timezone = if (profile.timezoneFillFromIp) result.timezone.ifBlank { profile.timezone } else profile.timezone,
            language = lang,
            languagesCsv = langsCsv,
            latitude = if (profile.geolocationBasedOnIp) (result.latitude ?: profile.latitude) else profile.latitude,
            longitude = if (profile.geolocationBasedOnIp) (result.longitude ?: profile.longitude) else profile.longitude,
            webRtcFakeIp = result.exitIp.ifBlank { profile.webRtcFakeIp }
        )
    }

    suspend fun checkAllProxies() {
        val all = proxyDao.getAllOnce()
        all.forEach { checkAndUpdateProxy(it.id) }
    }

    suspend fun addHistory(profileId: Long, title: String, url: String) {
        if (url.isBlank() || url.startsWith("cloakdeck://")) return
        historyDao.insertHistory(
            BrowserHistoryEntity(
                profileId = profileId,
                title = title.ifBlank { url },
                url = url
            )
        )
    }

    suspend fun clearHistory(profileId: Long) = historyDao.clearHistoryForProfile(profileId)

    suspend fun addBookmark(profileId: Long, title: String, url: String) {
        if (url.isBlank()) return
        historyDao.insertBookmark(
            BookmarkEntity(
                profileId = profileId,
                title = title.ifBlank { url },
                url = url
            )
        )
    }

    suspend fun deleteBookmark(id: Long) = historyDao.deleteBookmarkById(id)

    suspend fun getAllProfilesOnce(): List<BrowserProfileEntity> = profileDao.observeAll().first()
    suspend fun getAllProxiesOnce(): List<ProxyEntity> = proxyDao.getAllOnce()

    suspend fun generateProfileFleet(
        count: Int,
        namePrefix: String = "Fleet",
        groupTag: String = "Mass Fleet",
        keywordsPoolRaw: String = "",
        targetDomain: String = "",
        minSeconds: Int = 15,
        maxSeconds: Int = 60,
        bindVaultProxies: Boolean = true,
        rotateStickySsidPerProfile: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        val safeCount = count.coerceIn(1, 2000)
        val existingProxies = proxyDao.getAllOnce()
        val keywords = keywordsPoolRaw
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val safeMin = minSeconds.coerceIn(5, 3600)
        val safeMax = maxSeconds.coerceAtLeast(safeMin).coerceAtMost(3600)
        val rng = Random(System.nanoTime())
        val geoPresets = FingerprintPresets.geoPresets
        val concreteStyles = com.example.data.model.HumanizeScrollStyle.concreteStyles

        val generatedProfiles = ArrayList<BrowserProfileEntity>(safeCount)
        for (i in 0 until safeCount) {
            val baseProxy = if (bindVaultProxies && existingProxies.isNotEmpty()) {
                existingProxies[i % existingProxies.size]
            } else null

            val dwell = if (safeMin == safeMax) safeMin else rng.nextInt(safeMin, safeMax + 1)
            val kw = if (keywords.isNotEmpty()) keywords[i % keywords.size] else baseProxy?.humanizeKeyword.orEmpty()
            val domain = targetDomain.trim().ifBlank { baseProxy?.humanizeTargetDomain.orEmpty() }
            val style = concreteStyles[i % concreteStyles.size]
            val bp = FingerprintPresets.fleetBlueprintForIndex(i)
            val geo = geoPresets.firstOrNull()

            var profile = FingerprintPresets.createRandomProfile(
                name = "${namePrefix.ifBlank { "Fleet" }} #${i + 1}",
                groupTag = groupTag.ifBlank { "Mass Fleet" },
                proxyId = baseProxy?.id,
                preferredBlueprint = bp,
                preferredGeo = geo,
                uniqueHardwareVariance = true
            ).copy(
                humanizeSearchEnabled = kw.isNotBlank() || (baseProxy?.humanizeEnabled == true),
                humanizeKeyword = kw,
                humanizeTargetDomain = domain,
                humanizeDwellSeconds = dwell,
                humanizeClickInternalLink = true,
                humanizeScrollStyle = style.name
            )

            if (baseProxy != null) {
                val canRotateSsid = rotateStickySsidPerProfile &&
                    com.example.data.network.ProxySessionRotator.canRotateSession(baseProxy)
                if (canRotateSsid && i >= existingProxies.size) {
                    val (rotated, ssid) = com.example.data.network.ProxySessionRotator.withRotatedSession(baseProxy, rng)
                    profile = profile.copy(
                        name = "${namePrefix.ifBlank { "Fleet" }} #${i + 1} ($ssid)",
                        proxyConnectionType = com.example.data.model.ProfileProxyConnectionType.YOUR_PROXY.name,
                        proxyId = null,
                        inlineProxyType = rotated.type,
                        inlineProxyHost = rotated.host,
                        inlineProxyPort = rotated.port,
                        inlineProxyUsername = rotated.username,
                        inlineProxyPassword = rotated.password,
                        geolocationMode = rotated.geolocationMode,
                        geolocationBasedOnIp = rotated.geolocationBasedOnIp,
                        timezoneFillFromIp = rotated.timezoneFillFromIp,
                        webRtcMode = rotated.webRtcMode
                    )
                } else {
                    profile = profile.copy(
                        proxyConnectionType = com.example.data.model.ProfileProxyConnectionType.VAULT.name,
                        proxyId = baseProxy.id,
                        geolocationMode = baseProxy.geolocationMode,
                        geolocationBasedOnIp = baseProxy.geolocationBasedOnIp,
                        timezoneFillFromIp = baseProxy.timezoneFillFromIp,
                        webRtcMode = baseProxy.webRtcMode
                    )
                    if (baseProxy.lastExitIp.isNotBlank()) {
                        profile = applyProxyGeoToProfile(profile, baseProxy)
                    }
                }
            }
            generatedProfiles.add(profile)
        }

        db.withTransaction {
            generatedProfiles.chunked(200).forEach { chunk ->
                profileDao.insertAll(chunk)
            }
        }
        generatedProfiles.size
    }

    suspend fun bulkApplyHumanizeToAllProxies(
        keyword: String,
        targetDomain: String,
        dwellSeconds: Int = 45,
        dwellSecondsSpec: String = dwellSeconds.toString(),
        clickInternalLink: Boolean
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val keywords = keyword
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val cleanDomain = targetDomain.trim()
        val durations = parseCommaDurationsSeconds(dwellSecondsSpec, fallbackSec = dwellSeconds)

        val allProxies = proxyDao.getAllOnce()
        val proxyDurationMap = HashMap<Long, Int>(allProxies.size)
        val proxyKeywordMap = HashMap<Long, String>(allProxies.size)

        val updatedProxies = allProxies.mapIndexed { idx, proxy ->
            val sec = durations[idx % durations.size]
            val kw = if (keywords.isNotEmpty()) keywords[idx % keywords.size] else proxy.humanizeKeyword
            proxyDurationMap[proxy.id] = sec
            proxyKeywordMap[proxy.id] = kw
            proxy.copy(
                humanizeEnabled = true,
                humanizeKeyword = kw,
                humanizeTargetDomain = cleanDomain.ifBlank { proxy.humanizeTargetDomain },
                humanizeDwellSeconds = sec,
                humanizeClickInternalLink = clickInternalLink
            )
        }

        val allProfiles = profileDao.observeAll().first()
        val existingProxyIds = allProfiles.mapNotNull { it.proxyId }.toSet()
        val updatedProfiles = allProfiles.mapIndexed { idx, profile ->
            val sec = profile.proxyId?.let { proxyDurationMap[it] } ?: durations[idx % durations.size]
            val kw = profile.proxyId?.let { proxyKeywordMap[it] }
                ?: if (keywords.isNotEmpty()) keywords[idx % keywords.size] else profile.humanizeKeyword
            profile.copy(
                humanizeSearchEnabled = true,
                humanizeKeyword = kw,
                humanizeTargetDomain = cleanDomain.ifBlank { profile.humanizeTargetDomain },
                humanizeDwellSeconds = sec,
                humanizeClickInternalLink = clickInternalLink
            )
        }

        val unboundProxies = updatedProxies.filter { it.id !in existingProxyIds }
        val concreteStyles = com.example.data.model.HumanizeScrollStyle.concreteStyles
        val baseIdx = updatedProfiles.size
        val newProfiles = unboundProxies.mapIndexed { offset, proxy ->
            val idx = baseIdx + offset
            var prof = FingerprintPresets.createRandomProfile(
                name = proxy.name.ifBlank { "Auto Profile #${idx + 1}" },
                groupTag = "Auto Fleet",
                proxyId = proxy.id,
                preferredBlueprint = FingerprintPresets.fleetBlueprintForIndex(idx),
                uniqueHardwareVariance = true
            ).copy(
                proxyConnectionType = com.example.data.model.ProfileProxyConnectionType.VAULT.name,
                geolocationMode = proxy.geolocationMode,
                geolocationBasedOnIp = proxy.geolocationBasedOnIp,
                timezoneFillFromIp = proxy.timezoneFillFromIp,
                languagesFillFromIp = true,
                webRtcMode = proxy.webRtcMode,
                humanizeSearchEnabled = true,
                humanizeKeyword = proxy.humanizeKeyword,
                humanizeTargetDomain = proxy.humanizeTargetDomain,
                humanizeDwellSeconds = proxy.humanizeDwellSeconds,
                humanizeClickInternalLink = clickInternalLink,
                humanizeScrollStyle = concreteStyles[idx % concreteStyles.size].name
            )
            if (proxy.lastExitIp.isNotBlank()) {
                prof = applyProxyGeoToProfile(prof, proxy)
            }
            prof
        }

        db.withTransaction {
            if (updatedProxies.isNotEmpty()) {
                updatedProxies.chunked(200).forEach { chunk ->
                    proxyDao.updateAll(chunk)
                }
            }
            if (updatedProfiles.isNotEmpty()) {
                updatedProfiles.chunked(200).forEach { chunk ->
                    profileDao.updateAll(chunk)
                }
            }
            if (newProfiles.isNotEmpty()) {
                newProfiles.chunked(200).forEach { chunk ->
                    profileDao.insertAll(chunk)
                }
            }
        }

        updatedProxies.size to (updatedProfiles.size + newProfiles.size)
    }

    suspend fun clearAllWebSessions() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val profiles = profileDao.observeAll().first()
            profiles.forEach { prof ->
                runCatching { ProfileStore.getInstance().deleteProfile(prof.webViewProfileName) }
            }
        }
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    suspend fun setDarkMode(enabled: Boolean) = prefs.setDarkMode(enabled)
    suspend fun setAutoSyncGeo(enabled: Boolean) = prefs.setAutoSyncGeo(enabled)
    suspend fun setAutoVerifyProxy(enabled: Boolean) = prefs.setAutoVerifyProxy(enabled)
    suspend fun setSearchEngine(engine: SearchEngine) = prefs.setSearchEngine(engine)
    suspend fun setBlockThirdPartyCookies(enabled: Boolean) = prefs.setBlockThirdPartyCookies(enabled)
    suspend fun setAutoSolveCaptcha(enabled: Boolean) = prefs.setAutoSolveCaptcha(enabled)
    suspend fun setCaptchaProvider(provider: com.example.data.network.CaptchaSolverProvider) = prefs.setCaptchaProvider(provider)
    suspend fun setCaptchaApiKey(apiKey: String) = prefs.setCaptchaApiKey(apiKey)
    suspend fun setRotateProxyOnCaptcha(enabled: Boolean) = prefs.setRotateProxyOnCaptcha(enabled)
    suspend fun setCaptchaWaitSeconds(seconds: Int) = prefs.setCaptchaWaitSeconds(seconds)
    suspend fun setDirectOpenDomainOnCaptcha(enabled: Boolean) = prefs.setDirectOpenDomainOnCaptcha(enabled)
    suspend fun setAutoCloseProfileAfterHumanize(enabled: Boolean) = prefs.setAutoCloseProfileAfterHumanize(enabled)
    suspend fun setHumanizePagesPerProfile(pages: Int) = prefs.setHumanizePagesPerProfile(pages)
}
