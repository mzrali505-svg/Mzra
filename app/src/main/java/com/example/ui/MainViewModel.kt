package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppSettings
import com.example.data.local.AutomationFlowEntity
import com.example.data.local.BrowserProfileEntity
import com.example.data.local.ProxyEntity
import com.example.data.local.SearchEngine
import com.example.data.model.DeviceBlueprint
import com.example.data.model.FingerprintPresets
import com.example.data.model.GeoTimezonePreset
import com.example.data.model.ProfileProxyConnectionType
import com.example.data.model.ProxyType
import com.example.data.network.ProxyCheckResult
import com.example.data.network.ProxyChecker
import com.example.data.repository.CloakDeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileWithProxy(
    val profile: BrowserProfileEntity,
    val proxy: ProxyEntity?
)

data class MassLaunchRequest(
    val groupTag: String = "All",
    val concurrency: Int = 1,
    val pagesPerProfile: Int = 1,
    val keywordPool: String = "",
    val targetDomain: String = "",
    val dwellSecondsSpec: String = "",
    val syncGeoBeforeEachProfile: Boolean = true,
    val autoStart: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = CloakDeckRepository(application)

    init {
        viewModelScope.launch {
            repository.ensureStarterAutomations()
            repository.ensureSeededInitialProxies()
            repository.ensureMostlyMobileDefaultsV2()
        }
    }

    val profiles: StateFlow<List<BrowserProfileEntity>> = repository.profilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val proxies: StateFlow<List<ProxyEntity>> = repository.proxiesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automations: StateFlow<List<AutomationFlowEntity>> = repository.automationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profilesWithProxies: StateFlow<List<ProfileWithProxy>> =
        combine(repository.profilesFlow, repository.proxiesFlow) { profList, proxyList ->
            val proxyMap = proxyList.associateBy { it.id }
            profList.map { prof -> ProfileWithProxy(prof, prof.proxyId?.let { proxyMap[it] }) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _checkingProxyIds = MutableStateFlow<Set<Long>>(emptySet())
    val checkingProxyIds: StateFlow<Set<Long>> = _checkingProxyIds.asStateFlow()

    private val _directIpResult = MutableStateFlow<ProxyCheckResult?>(null)
    val directIpResult: StateFlow<ProxyCheckResult?> = _directIpResult.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _batchQueueProfileIds = MutableStateFlow<List<Long>>(emptyList())
    val batchQueueProfileIds: StateFlow<List<Long>> = _batchQueueProfileIds.asStateFlow()

    private val _batchQueueIndex = MutableStateFlow(0)
    val batchQueueIndex: StateFlow<Int> = _batchQueueIndex.asStateFlow()

    private val _pendingMassLaunch = MutableStateFlow<MassLaunchRequest?>(null)
    val pendingMassLaunch: StateFlow<MassLaunchRequest?> = _pendingMassLaunch.asStateFlow()

    private val _isBulkBusy = MutableStateFlow(false)
    val isBulkBusy: StateFlow<Boolean> = _isBulkBusy.asStateFlow()

    fun requestMassLaunch(request: MassLaunchRequest) {
        _pendingMassLaunch.value = request
    }

    fun consumePendingMassLaunch(): MassLaunchRequest? {
        val current = _pendingMassLaunch.value
        _pendingMassLaunch.value = null
        return current
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun setBatchQueue(profileIds: List<Long>) {
        _batchQueueProfileIds.value = profileIds
        _batchQueueIndex.value = 0
    }

    fun advanceBatchQueue(): Long? {
        val next = _batchQueueIndex.value + 1
        val list = _batchQueueProfileIds.value
        return if (next < list.size) {
            _batchQueueIndex.value = next
            list[next]
        } else {
            _batchQueueProfileIds.value = emptyList()
            _batchQueueIndex.value = 0
            null
        }
    }

    fun clearBatchQueue() {
        _batchQueueProfileIds.value = emptyList()
        _batchQueueIndex.value = 0
    }

    fun checkDirectIp() {
        viewModelScope.launch {
            _directIpResult.value = ProxyChecker.checkProxy(null)
        }
    }

    fun createQuickProfile(
        name: String,
        groupTag: String,
        proxyId: Long?,
        blueprint: DeviceBlueprint? = null,
        geoPreset: GeoTimezonePreset? = null,
        automationFlowId: Long? = null,
        onCreated: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            var entity = FingerprintPresets.createRandomProfile(
                name = name,
                groupTag = groupTag,
                proxyId = proxyId,
                preferredBlueprint = blueprint,
                preferredGeo = geoPreset
            ).copy(
                proxyConnectionType = if (proxyId != null) {
                    ProfileProxyConnectionType.VAULT.name
                } else {
                    ProfileProxyConnectionType.WITHOUT_PROXY.name
                },
                automationFlowId = automationFlowId,
                autoRunAutomationOnLaunch = automationFlowId != null
            )
            if (proxyId != null) {
                val proxy = repository.getProxyOnce(proxyId)
                if (proxy != null) {
                    entity = entity.copy(
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
                        entity = repository.applyProxyGeoToProfile(entity, proxy)
                    }
                }
            }
            val id = repository.saveProfile(entity)
            _statusBanner.value = "Created isolated profile '${entity.name}'"
            onCreated(id)
        }
    }

    fun saveProfile(profile: BrowserProfileEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.saveProfile(profile)
            _statusBanner.value = "Saved profile '${profile.name}'"
            onSaved(id)
        }
    }

    fun duplicateProfile(profile: BrowserProfileEntity) {
        viewModelScope.launch {
            repository.duplicateProfile(profile)
            _statusBanner.value = "Cloned '${profile.name}' with fresh Canvas/WebGL/Audio seeds"
        }
    }

    fun deleteProfile(profile: BrowserProfileEntity) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            _statusBanner.value = "Deleted profile '${profile.name}'"
        }
    }

    fun saveProxy(proxy: ProxyEntity, checkImmediately: Boolean = true) {
        viewModelScope.launch {
            val id = repository.saveProxy(proxy)
            _statusBanner.value = "Saved proxy ${proxy.host}:${proxy.port}"
            if (checkImmediately) {
                checkProxy(id)
            }
        }
    }

    fun saveProxyWithProfileTemplate(
        proxy: ProxyEntity,
        profileTemplate: BrowserProfileEntity,
        syncToProfile: Boolean,
        checkImmediately: Boolean = true,
        onSaved: (proxyId: Long, profileId: Long?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val (proxyId, profileId) = repository.saveProxyWithProfileTemplate(proxy, profileTemplate, syncToProfile)
            _statusBanner.value = "Saved proxy ${proxy.host}:${proxy.port}" +
                if (syncToProfile) " & synced profile settings" else ""
            if (checkImmediately) {
                checkProxy(proxyId)
            }
            onSaved(proxyId, profileId)
        }
    }

    fun launchProxySession(proxyId: Long, onReadyProfile: (Long) -> Unit) {
        viewModelScope.launch {
            clearBatchQueue()
            val profId = repository.ensureProfileForProxy(proxyId)
            if (profId != null) {
                onReadyProfile(profId)
            } else {
                _statusBanner.value = "Unable to create session for proxy"
            }
        }
    }

    fun startBatchHumanizeQueue(onlyHumanized: Boolean = true, onReadyFirstProfile: (Long) -> Unit) {
        viewModelScope.launch {
            val queue = repository.prepareBatchQueueProfileIds(onlyHumanized)
            if (queue.isEmpty()) {
                _statusBanner.value = "Add at least one proxy first to run batch humanization"
                return@launch
            }
            setBatchQueue(queue)
            _statusBanner.value = "Queued ${queue.size} proxy session(s) with per-proxy dwell timers"
            onReadyFirstProfile(queue.first())
        }
    }

    fun bulkImportProxiesAdvanced(
        rawText: String,
        defaultType: ProxyType,
        humanizeEnabled: Boolean,
        keywordsPoolRaw: String,
        targetDomain: String,
        timingMode: com.example.data.repository.BulkTimingMode,
        fixedSeconds: Int,
        randomMinSeconds: Int,
        randomMaxSeconds: Int,
        customSequenceCsv: String,
        geolocationMode: String,
        webRtcMode: String,
        createIsolatedProfiles: Boolean
    ) {
        viewModelScope.launch {
            val count = repository.bulkImportProxies(
                rawText = rawText,
                defaultType = defaultType,
                humanizeEnabled = humanizeEnabled,
                keywordsPoolRaw = keywordsPoolRaw,
                targetDomain = targetDomain,
                timingMode = timingMode,
                fixedSeconds = fixedSeconds,
                randomMinSeconds = randomMinSeconds,
                randomMaxSeconds = randomMaxSeconds,
                customSequenceCsv = customSequenceCsv,
                geolocationMode = geolocationMode,
                webRtcMode = webRtcMode,
                createIsolatedProfiles = createIsolatedProfiles
            )
            _statusBanner.value = if (count > 0) {
                "Imported $count proxy node(s)" +
                    if (createIsolatedProfiles) " with isolated profiles & timers" else ""
            } else {
                "No valid proxy lines detected (use host:port or host:port:user:pass)"
            }
        }
    }

    fun checkProxy(proxyId: Long) {
        viewModelScope.launch {
            _checkingProxyIds.value = _checkingProxyIds.value + proxyId
            val result = repository.checkAndUpdateProxy(proxyId)
            _checkingProxyIds.value = _checkingProxyIds.value - proxyId
            _statusBanner.value = if (result.success) {
                "Proxy live: ${result.exitIp} (${result.city.ifBlank { result.country }}, ${result.latencyMs}ms)"
            } else {
                "Proxy check failed: ${result.errorMessage}"
            }
        }
    }

    fun checkAllProxies() {
        viewModelScope.launch {
            val ids = proxies.value.map { it.id }.toSet()
            _checkingProxyIds.value = ids
            repository.checkAllProxies()
            _checkingProxyIds.value = emptySet()
            _statusBanner.value = "Completed proxy verification sweep"
        }
    }

    fun deleteProxy(proxy: ProxyEntity) {
        viewModelScope.launch {
            repository.deleteProxy(proxy.id)
            _statusBanner.value = "Removed proxy ${proxy.host}:${proxy.port}"
        }
    }

    fun saveAutomationFlow(flow: AutomationFlowEntity) {
        viewModelScope.launch {
            repository.saveAutomation(flow)
            _statusBanner.value = "Saved RPA flow '${flow.name}'"
        }
    }

    fun deleteAutomationFlow(id: Long) {
        viewModelScope.launch {
            repository.deleteAutomation(id)
            _statusBanner.value = "Deleted automation flow"
        }
    }

    fun generateProfileFleet(
        count: Int,
        namePrefix: String,
        groupTag: String,
        keywordsPoolRaw: String,
        targetDomain: String,
        minSeconds: Int,
        maxSeconds: Int,
        bindVaultProxies: Boolean = true,
        rotateStickySsidPerProfile: Boolean = true,
        onComplete: (Int) -> Unit = {}
    ) {
        if (_isBulkBusy.value) return
        viewModelScope.launch {
            _isBulkBusy.value = true
            try {
                val created = repository.generateProfileFleet(
                    count = count,
                    namePrefix = namePrefix,
                    groupTag = groupTag,
                    keywordsPoolRaw = keywordsPoolRaw,
                    targetDomain = targetDomain,
                    minSeconds = minSeconds,
                    maxSeconds = maxSeconds,
                    bindVaultProxies = bindVaultProxies,
                    rotateStickySsidPerProfile = rotateStickySsidPerProfile
                )
                _statusBanner.value = "Generated $created isolated profiles with distinct scroll styles"
                onComplete(created)
            } finally {
                _isBulkBusy.value = false
            }
        }
    }

    fun bulkApplyHumanizeToAllProxies(
        keyword: String,
        targetDomain: String,
        dwellSeconds: Int = 45,
        dwellSecondsSpec: String = dwellSeconds.toString(),
        clickInternalLink: Boolean,
        onComplete: (proxiesUpdated: Int, profilesUpdated: Int) -> Unit = { _, _ -> }
    ) {
        if (_isBulkBusy.value) return
        viewModelScope.launch {
            _isBulkBusy.value = true
            try {
                val (proxyCount, profileCount) = repository.bulkApplyHumanizeToAllProxies(
                    keyword = keyword,
                    targetDomain = targetDomain,
                    dwellSeconds = dwellSeconds,
                    dwellSecondsSpec = dwellSecondsSpec,
                    clickInternalLink = clickInternalLink
                )
                val kwLabel = keyword.trim().ifBlank { "saved keywords" }
                val domLabel = targetDomain.trim().ifBlank { "saved domains" }
                _statusBanner.value = "Applied '$kwLabel' -> '$domLabel' to $proxyCount proxies & $profileCount unique profiles"
                onComplete(proxyCount, profileCount)
            } finally {
                _isBulkBusy.value = false
            }
        }
    }

    fun submitProxyOrderCampaign(
        rawProxiesText: String,
        defaultType: ProxyType,
        keywordsPoolRaw: String,
        targetDomain: String,
        dwellSeconds: Int = 45,
        dwellSecondsSpec: String = dwellSeconds.toString(),
        groupTag: String,
        onComplete: (groupTag: String, createdCount: Int) -> Unit = { _, _ -> }
    ) {
        if (_isBulkBusy.value) return
        viewModelScope.launch {
            _isBulkBusy.value = true
            try {
                val res = repository.createProxyOrderCampaign(
                    rawProxiesText = rawProxiesText,
                    defaultType = defaultType,
                    keywordsPoolRaw = keywordsPoolRaw,
                    targetDomain = targetDomain,
                    dwellSeconds = dwellSeconds,
                    dwellSecondsSpec = dwellSecondsSpec,
                    groupTag = groupTag
                )
                _statusBanner.value = if (res.createdCount > 0) {
                    "Created ${res.createdCount} distinct profiles in '${res.groupTag}' ready for automation"
                } else {
                    "No valid proxies found (use ip:port:user:pass or socks5://...)"
                }
                onComplete(res.groupTag, res.createdCount)
            } finally {
                _isBulkBusy.value = false
            }
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            repository.clearAllWebSessions()
            _statusBanner.value = "Cleared all isolated WebView cookies & storage"
        }
    }

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { repository.setDarkMode(enabled) }
    fun setAutoSyncGeo(enabled: Boolean) = viewModelScope.launch { repository.setAutoSyncGeo(enabled) }
    fun setAutoVerifyProxy(enabled: Boolean) = viewModelScope.launch { repository.setAutoVerifyProxy(enabled) }
    fun setSearchEngine(engine: SearchEngine) = viewModelScope.launch { repository.setSearchEngine(engine) }
    fun setBlockThirdPartyCookies(enabled: Boolean) = viewModelScope.launch { repository.setBlockThirdPartyCookies(enabled) }
    fun setAutoSolveCaptcha(enabled: Boolean) = viewModelScope.launch { repository.setAutoSolveCaptcha(enabled) }
    fun setCaptchaProvider(provider: com.example.data.network.CaptchaSolverProvider) = viewModelScope.launch { repository.setCaptchaProvider(provider) }
    fun setCaptchaApiKey(apiKey: String) = viewModelScope.launch { repository.setCaptchaApiKey(apiKey) }
    fun setRotateProxyOnCaptcha(enabled: Boolean) = viewModelScope.launch { repository.setRotateProxyOnCaptcha(enabled) }
    fun setCaptchaWaitSeconds(seconds: Int) = viewModelScope.launch { repository.setCaptchaWaitSeconds(seconds) }
    fun setDirectOpenDomainOnCaptcha(enabled: Boolean) = viewModelScope.launch { repository.setDirectOpenDomainOnCaptcha(enabled) }
    fun setAutoCloseProfileAfterHumanize(enabled: Boolean) = viewModelScope.launch { repository.setAutoCloseProfileAfterHumanize(enabled) }
    fun setHumanizePagesPerProfile(pages: Int) = viewModelScope.launch { repository.setHumanizePagesPerProfile(pages) }
}
