package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.network.CaptchaSolverConfig
import com.example.data.network.CaptchaSolverProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cloakdeck_settings")

enum class SearchEngine(val label: String, val queryUrlTemplate: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q=%s")
}

data class AppSettings(
    val darkMode: Boolean = true,
    val autoSyncGeoWithProxy: Boolean = true,
    val autoVerifyProxyOnLaunch: Boolean = true,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val blockThirdPartyCookies: Boolean = false,
    val autoSolveCaptcha: Boolean = true,
    val captchaSolverProvider: CaptchaSolverProvider = CaptchaSolverProvider.TOUCH_ONLY,
    val captchaApiKey: String = "",
    val rotateProxySessionOnCaptcha: Boolean = true,
    val captchaInteractiveWaitSeconds: Int = 8,
    val directOpenDomainOnCaptcha: Boolean = true,
    val autoCloseProfileAfterHumanize: Boolean = true,
    val humanizePagesPerProfile: Int = 1
) {
    fun toCaptchaConfig(): CaptchaSolverConfig = CaptchaSolverConfig(
        autoSolveEnabled = autoSolveCaptcha,
        provider = captchaSolverProvider,
        apiKey = captchaApiKey,
        rotateProxySessionOnGoogleBlock = rotateProxySessionOnCaptcha,
        interactiveWaitSeconds = captchaInteractiveWaitSeconds,
        directOpenDomainOnCaptcha = directOpenDomainOnCaptcha
    )
}

class AppPreferences(private val context: Context) {
    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AUTO_SYNC_GEO = booleanPreferencesKey("auto_sync_geo")
        val AUTO_VERIFY_PROXY = booleanPreferencesKey("auto_verify_proxy")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val BLOCK_THIRD_PARTY_COOKIES = booleanPreferencesKey("block_third_party_cookies")
        val HAS_SEEDED_PROXIES_V1 = booleanPreferencesKey("has_seeded_proxies_v1")
        val HAS_MIGRATED_MOBILE_DEFAULTS_V2 = booleanPreferencesKey("has_migrated_mobile_defaults_v2")
        val AUTO_SOLVE_CAPTCHA = booleanPreferencesKey("auto_solve_captcha")
        val CAPTCHA_PROVIDER = stringPreferencesKey("captcha_provider")
        val CAPTCHA_API_KEY = stringPreferencesKey("captcha_api_key")
        val ROTATE_PROXY_ON_CAPTCHA = booleanPreferencesKey("rotate_proxy_on_captcha")
        val CAPTCHA_WAIT_SECONDS = intPreferencesKey("captcha_wait_seconds")
        val DIRECT_OPEN_DOMAIN_ON_CAPTCHA = booleanPreferencesKey("direct_open_domain_on_captcha")
        val AUTO_CLOSE_PROFILE_AFTER_HUMANIZE = booleanPreferencesKey("auto_close_profile_after_humanize")
        val HUMANIZE_PAGES_PER_PROFILE = intPreferencesKey("humanize_pages_per_profile")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val engineName = prefs[Keys.SEARCH_ENGINE] ?: SearchEngine.DUCKDUCKGO.name
        val engine = runCatching { SearchEngine.valueOf(engineName) }.getOrDefault(SearchEngine.DUCKDUCKGO)
        val providerName = prefs[Keys.CAPTCHA_PROVIDER] ?: CaptchaSolverProvider.TOUCH_ONLY.name
        val provider = runCatching { CaptchaSolverProvider.valueOf(providerName) }.getOrDefault(CaptchaSolverProvider.TOUCH_ONLY)
        AppSettings(
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            autoSyncGeoWithProxy = prefs[Keys.AUTO_SYNC_GEO] ?: true,
            autoVerifyProxyOnLaunch = prefs[Keys.AUTO_VERIFY_PROXY] ?: true,
            searchEngine = engine,
            blockThirdPartyCookies = prefs[Keys.BLOCK_THIRD_PARTY_COOKIES] ?: false,
            autoSolveCaptcha = prefs[Keys.AUTO_SOLVE_CAPTCHA] ?: true,
            captchaSolverProvider = provider,
            captchaApiKey = prefs[Keys.CAPTCHA_API_KEY] ?: "",
            rotateProxySessionOnCaptcha = prefs[Keys.ROTATE_PROXY_ON_CAPTCHA] ?: true,
            captchaInteractiveWaitSeconds = prefs[Keys.CAPTCHA_WAIT_SECONDS] ?: 8,
            directOpenDomainOnCaptcha = prefs[Keys.DIRECT_OPEN_DOMAIN_ON_CAPTCHA] ?: true,
            autoCloseProfileAfterHumanize = prefs[Keys.AUTO_CLOSE_PROFILE_AFTER_HUMANIZE] ?: true,
            humanizePagesPerProfile = (prefs[Keys.HUMANIZE_PAGES_PER_PROFILE] ?: 1).coerceIn(1, 50)
        )
    }

    suspend fun hasSeededProxiesV1(): Boolean {
        return context.dataStore.data.map { it[Keys.HAS_SEEDED_PROXIES_V1] ?: false }.first()
    }

    suspend fun markProxiesSeededV1() {
        context.dataStore.edit { it[Keys.HAS_SEEDED_PROXIES_V1] = true }
    }

    suspend fun hasMigratedMobileDefaultsV2(): Boolean {
        return context.dataStore.data.map { it[Keys.HAS_MIGRATED_MOBILE_DEFAULTS_V2] ?: false }.first()
    }

    suspend fun markMobileDefaultsMigratedV2() {
        context.dataStore.edit { it[Keys.HAS_MIGRATED_MOBILE_DEFAULTS_V2] = true }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setAutoSyncGeo(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SYNC_GEO] = enabled }
    }

    suspend fun setAutoVerifyProxy(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_VERIFY_PROXY] = enabled }
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.dataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun setBlockThirdPartyCookies(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BLOCK_THIRD_PARTY_COOKIES] = enabled }
    }

    suspend fun setAutoSolveCaptcha(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SOLVE_CAPTCHA] = enabled }
    }

    suspend fun setCaptchaProvider(provider: CaptchaSolverProvider) {
        context.dataStore.edit { it[Keys.CAPTCHA_PROVIDER] = provider.name }
    }

    suspend fun setCaptchaApiKey(apiKey: String) {
        context.dataStore.edit { it[Keys.CAPTCHA_API_KEY] = apiKey.trim() }
    }

    suspend fun setRotateProxyOnCaptcha(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ROTATE_PROXY_ON_CAPTCHA] = enabled }
    }

    suspend fun setCaptchaWaitSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.CAPTCHA_WAIT_SECONDS] = seconds.coerceIn(5, 120) }
    }

    suspend fun setDirectOpenDomainOnCaptcha(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DIRECT_OPEN_DOMAIN_ON_CAPTCHA] = enabled }
    }

    suspend fun setAutoCloseProfileAfterHumanize(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CLOSE_PROFILE_AFTER_HUMANIZE] = enabled }
    }

    suspend fun setHumanizePagesPerProfile(pages: Int) {
        context.dataStore.edit { it[Keys.HUMANIZE_PAGES_PER_PROFILE] = pages.coerceIn(1, 50) }
    }
}
