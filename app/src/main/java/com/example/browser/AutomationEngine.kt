package com.example.browser

import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import com.example.data.local.AutomationFlowEntity
import com.example.data.local.BrowserProfileEntity
import com.example.data.local.ProxyEntity
import com.example.data.local.RpaActionType
import com.example.data.local.RpaStep
import com.example.data.model.HumanizeBehaviorResolver
import com.example.data.model.HumanizeEasing
import com.example.data.model.HumanizeScrollStyle
import com.example.data.model.ResolvedScrollPersona
import com.example.data.model.ScrollArchetype
import com.example.data.network.CaptchaSolveOutcome
import com.example.data.network.CaptchaSolverConfig
import com.example.data.network.CaptchaSolverEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlin.random.Random

data class AutomationExecutionState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val flowName: String = "",
    val loopIndex: Int = 1,
    val totalLoops: Int = 1,
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    val statusText: String = "",
    val remainingSeconds: Int? = null,
    val scrollStyleLabel: String = "",
    val pagesScrolled: Int = 0,
    val targetPages: Int = 1,
    val captchasSolved: Int = 0,
    val captchaAlertText: String = "",
    val lastCompletedAt: Long? = null
)

object AutomationTiming {
    fun computePageDwellAllocation(totalDwellSeconds: Int, pagesPerProfile: Int): List<Int> {
        val safeTotal = totalDwellSeconds.coerceIn(3, 3600)
        val safePages = pagesPerProfile.coerceIn(1, 50)
        if (safePages == 1) return listOf(safeTotal)
        val base = safeTotal / safePages
        val rem = safeTotal % safePages
        return List(safePages) { idx ->
            (base + if (idx == 0) rem else 0).coerceAtLeast(5)
        }
    }
}

object SerpDomainMatcher {
    fun normalizeHostHint(raw: String): String {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isBlank()) return ""
        val withoutScheme = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("//")
        val hostPart = withoutScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
        return hostPart.removePrefix("www.")
    }

    fun normalizeFullHint(raw: String): String {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isBlank()) return ""
        val withoutScheme = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("//")
            .removePrefix("www.")
        return withoutScheme.trimEnd('/')
    }
}

object AutomationPresets {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeSteps(steps: List<RpaStep>): String = json.encodeToString(steps)

    fun decodeSteps(stepsJson: String): List<RpaStep> {
        return runCatching { json.decodeFromString<List<RpaStep>>(stepsJson) }.getOrDefault(emptyList())
    }

    fun defaultStarterFlows(): List<AutomationFlowEntity> {
        return listOf(
            AutomationFlowEntity(
                name = "Google Keyword Search & Human Read",
                description = "Opens Google, types a keyword naturally, opens the suggested website, and reads it like a human for the configured seconds.",
                stepsJson = encodeSteps(
                    listOf(
                        RpaStep(
                            id = "g1",
                            type = RpaActionType.GOOGLE_SEARCH_AND_BROWSE,
                            target = "",
                            value = "best productivity workflows 2026",
                            durationSeconds = 45,
                            clickInternalLink = true
                        )
                    )
                ),
                loopCount = 1
            ),
            AutomationFlowEntity(
                name = "Proxy IP & Leak Auto-Verifier",
                description = "Automatically verifies active proxy IP, scrolls results naturally, and checks WebRTC/timezone alignment.",
                stepsJson = encodeSteps(
                    listOf(
                        RpaStep("s1", RpaActionType.NAVIGATE, target = "https://myip.link"),
                        RpaStep("s2", RpaActionType.HUMAN_WAIT, minDelayMs = 2000, maxDelayMs = 3500),
                        RpaStep("s3", RpaActionType.HUMAN_SCROLL, value = "DOWN", scrollCycles = 4, minDelayMs = 700, maxDelayMs = 1500),
                        RpaStep("s4", RpaActionType.HUMAN_WAIT, minDelayMs = 1500, maxDelayMs = 2800)
                    )
                ),
                loopCount = 1
            ),
            AutomationFlowEntity(
                name = "Humanized Cookie & Trust Warmup",
                description = "Simulates natural organic reading, scrolling, micro-pauses, and link traversal to warm up a fresh profile.",
                stepsJson = encodeSteps(
                    listOf(
                        RpaStep("w1", RpaActionType.NAVIGATE, target = "https://en.wikipedia.org/wiki/Special:Random"),
                        RpaStep("w2", RpaActionType.HUMAN_READ_DURATION, durationSeconds = 30, clickInternalLink = true)
                    )
                ),
                loopCount = 1
            ),
            AutomationFlowEntity(
                name = "Search & Organic Browse (DuckDuckGo)",
                description = "Opens DuckDuckGo, types a query with realistic human keystroke timing, submits, and scrolls results.",
                stepsJson = encodeSteps(
                    listOf(
                        RpaStep("q1", RpaActionType.NAVIGATE, target = "https://duckduckgo.com"),
                        RpaStep("q2", RpaActionType.HUMAN_WAIT, minDelayMs = 1200, maxDelayMs = 2400),
                        RpaStep("q3", RpaActionType.HUMAN_TYPE, target = "input[name='q']", value = "whats my browser timezone", minDelayMs = 900, maxDelayMs = 1500),
                        RpaStep("q4", RpaActionType.HUMAN_READ_DURATION, durationSeconds = 20, clickInternalLink = false)
                    )
                ),
                loopCount = 1
            )
        )
    }
}

class HumanizedAutomationRunner {
    private val _state = MutableStateFlow(AutomationExecutionState())
    val state: StateFlow<AutomationExecutionState> = _state.asStateFlow()

    @Volatile
    private var paused = false

    @Volatile
    private var pagesScrolledCounter = 0

    @Volatile
    private var captchasSolvedCounter = 0

    @Volatile
    private var currentCaptchaAlert = ""

    fun togglePause() {
        paused = !paused
        _state.value = _state.value.copy(isPaused = paused)
    }

    fun stop() {
        paused = false
        currentCaptchaAlert = ""
        _state.value = AutomationExecutionState(isRunning = false)
    }

    suspend fun runDirectHumanizeTask(
        keyword: String,
        targetDomain: String,
        dwellSeconds: Int,
        clickInternalLink: Boolean,
        profile: BrowserProfileEntity? = null,
        pagesPerProfile: Int = 1,
        captchaConfig: CaptchaSolverConfig = CaptchaSolverConfig(),
        activeProxy: ProxyEntity? = null,
        onRotateProxySession: (suspend (ProxyEntity, String) -> Boolean)? = null,
        onPageScrolled: (suspend () -> Unit)? = null,
        onCaptchaSolved: (suspend (String) -> Unit)? = null,
        onLogEvent: (suspend (String) -> Unit)? = null,
        webViewProvider: () -> WebView?,
        onNavigate: (String) -> Unit
    ) {
        val cleanKeyword = keyword.trim()
        val safeDuration = dwellSeconds.coerceIn(3, 3600)
        val syntheticFlow = AutomationFlowEntity(
            name = if (cleanKeyword.isNotBlank()) "Humanize: $cleanKeyword (${safeDuration}s)" else "Human Read (${safeDuration}s)",
            stepsJson = AutomationPresets.encodeSteps(
                listOf(
                    if (cleanKeyword.isNotBlank()) {
                        RpaStep(
                            id = "direct_google",
                            type = RpaActionType.GOOGLE_SEARCH_AND_BROWSE,
                            target = targetDomain.trim(),
                            value = cleanKeyword,
                            durationSeconds = safeDuration,
                            clickInternalLink = clickInternalLink
                        )
                    } else {
                        RpaStep(
                            id = "direct_read",
                            type = RpaActionType.HUMAN_READ_DURATION,
                            durationSeconds = safeDuration,
                            clickInternalLink = clickInternalLink
                        )
                    }
                )
            )
        )
        runFlow(
            flow = syntheticFlow,
            profile = profile,
            pagesPerProfile = pagesPerProfile,
            captchaConfig = captchaConfig,
            activeProxy = activeProxy,
            onRotateProxySession = onRotateProxySession,
            onPageScrolled = onPageScrolled,
            onCaptchaSolved = onCaptchaSolved,
            onLogEvent = onLogEvent,
            webViewProvider = webViewProvider,
            onNavigate = onNavigate
        )
    }

    suspend fun runFlow(
        flow: AutomationFlowEntity,
        profile: BrowserProfileEntity? = null,
        pagesPerProfile: Int = 1,
        captchaConfig: CaptchaSolverConfig = CaptchaSolverConfig(),
        activeProxy: ProxyEntity? = null,
        onRotateProxySession: (suspend (ProxyEntity, String) -> Boolean)? = null,
        onPageScrolled: (suspend () -> Unit)? = null,
        onCaptchaSolved: (suspend (String) -> Unit)? = null,
        onLogEvent: (suspend (String) -> Unit)? = null,
        webViewProvider: () -> WebView?,
        onNavigate: (String) -> Unit
    ) {
        val steps = AutomationPresets.decodeSteps(flow.stepsJson)
        if (steps.isEmpty()) return

        paused = false
        pagesScrolledCounter = 0
        captchasSolvedCounter = 0
        currentCaptchaAlert = ""
        val totalLoops = flow.loopCount.coerceAtLeast(1)
        val safePages = pagesPerProfile.coerceIn(1, 50)
        val persona = HumanizeBehaviorResolver.resolve(profile)

        try {
            for (loop in 1..totalLoops) {
                for ((idx, step) in steps.withIndex()) {
                    if (!coroutineContext.isActive) return
                    waitWhilePaused()

                    _state.value = AutomationExecutionState(
                        isRunning = true,
                        isPaused = paused,
                        flowName = flow.name,
                        loopIndex = loop,
                        totalLoops = totalLoops,
                        stepIndex = idx + 1,
                        totalSteps = steps.size,
                        statusText = describeStep(step),
                        scrollStyleLabel = persona.label,
                        pagesScrolled = pagesScrolledCounter,
                        targetPages = safePages,
                        captchasSolved = captchasSolvedCounter,
                        captchaAlertText = currentCaptchaAlert
                    )

                    executeStep(
                        step = step,
                        flow = flow,
                        persona = persona,
                        pagesPerProfile = safePages,
                        captchaConfig = captchaConfig,
                        activeProxy = activeProxy,
                        onRotateProxySession = onRotateProxySession,
                        onPageScrolled = onPageScrolled,
                        onCaptchaSolved = onCaptchaSolved,
                        onLogEvent = onLogEvent,
                        loop = loop,
                        totalLoops = totalLoops,
                        stepIndex = idx + 1,
                        totalSteps = steps.size,
                        webViewProvider = webViewProvider,
                        onNavigate = onNavigate
                    )
                }
            }
            _state.value = AutomationExecutionState(
                isRunning = false,
                flowName = flow.name,
                stepIndex = steps.size,
                totalSteps = steps.size,
                statusText = "Completed '${flow.name}'",
                scrollStyleLabel = persona.label,
                pagesScrolled = pagesScrolledCounter,
                targetPages = safePages,
                captchasSolved = captchasSolvedCounter,
                captchaAlertText = "",
                lastCompletedAt = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            _state.value = AutomationExecutionState(isRunning = false)
        }
    }

    private suspend fun waitWhilePaused() {
        while (paused && coroutineContext.isActive) {
            delay(250)
        }
    }

    private suspend fun executeStep(
        step: RpaStep,
        flow: AutomationFlowEntity,
        persona: ResolvedScrollPersona,
        pagesPerProfile: Int,
        captchaConfig: CaptchaSolverConfig,
        activeProxy: ProxyEntity?,
        onRotateProxySession: (suspend (ProxyEntity, String) -> Boolean)?,
        onPageScrolled: (suspend () -> Unit)?,
        onCaptchaSolved: (suspend (String) -> Unit)?,
        onLogEvent: (suspend (String) -> Unit)?,
        loop: Int,
        totalLoops: Int,
        stepIndex: Int,
        totalSteps: Int,
        webViewProvider: () -> WebView?,
        onNavigate: (String) -> Unit
    ) {
        val rng = Random(System.nanoTime())
        when (step.type) {
            RpaActionType.GOOGLE_SEARCH_AND_BROWSE -> {
                executeGoogleSearchAndBrowse(
                    keyword = step.value.ifBlank { "technology news" },
                    targetDomain = step.target,
                    dwellSeconds = step.durationSeconds.coerceIn(3, 3600),
                    clickInternalLink = step.clickInternalLink,
                    pagesPerProfile = pagesPerProfile,
                    captchaConfig = captchaConfig,
                    activeProxy = activeProxy,
                    onRotateProxySession = onRotateProxySession,
                    onPageScrolled = onPageScrolled,
                    onCaptchaSolved = onCaptchaSolved,
                    onLogEvent = onLogEvent,
                    flow = flow,
                    persona = persona,
                    loop = loop,
                    totalLoops = totalLoops,
                    stepIndex = stepIndex,
                    totalSteps = totalSteps,
                    webViewProvider = webViewProvider,
                    onNavigate = onNavigate
                )
            }

            RpaActionType.HUMAN_READ_DURATION -> {
                val safePages = pagesPerProfile.coerceIn(1, 50)
                val allocations = AutomationTiming.computePageDwellAllocation(
                    totalDwellSeconds = step.durationSeconds.coerceIn(3, 3600),
                    pagesPerProfile = safePages
                )
                for ((idxZero, perPageDwell) in allocations.withIndex()) {
                    val pageIdx = idxZero + 1
                    if (!coroutineContext.isActive) return
                    handleCaptchaGateIfPresent(
                        webView = webViewProvider(),
                        captchaConfig = captchaConfig,
                        activeProxy = activeProxy,
                        allowSessionRotation = false,
                        onRotateProxySession = onRotateProxySession,
                        onCaptchaSolved = onCaptchaSolved,
                        onLogEvent = onLogEvent,
                        flow = flow,
                        persona = persona,
                        loop = loop,
                        totalLoops = totalLoops,
                        stepIndex = stepIndex,
                        totalSteps = totalSteps
                    )
                    currentCaptchaAlert = ""
                    // Keep exact page count: outer loop handles page transitions only when safePages > 1
                    executeHumanReadDuration(
                        dwellSeconds = perPageDwell,
                        clickInternalLink = false,
                        flow = flow,
                        persona = persona,
                        loop = loop,
                        totalLoops = totalLoops,
                        stepIndex = stepIndex,
                        totalSteps = totalSteps,
                        webViewProvider = webViewProvider
                    )
                    pagesScrolledCounter++
                    onPageScrolled?.invoke()
                    if (pageIdx < safePages) {
                        clickRandomInternalLink(webViewProvider())
                        delay(rng.nextLong(1800, 2900))
                    }
                }
            }

            RpaActionType.NAVIGATE -> {
                withContext(Dispatchers.Main) { onNavigate(step.target) }
                delay(rng.nextLong(2200, 3600))
            }

            RpaActionType.HUMAN_WAIT -> {
                val minMs = step.minDelayMs.coerceAtLeast(250).toLong()
                val maxMs = step.maxDelayMs.coerceAtLeast(minMs.toInt() + 100).toLong()
                delay(rng.nextLong(minMs, maxMs))
            }

            RpaActionType.HUMAN_SCROLL -> {
                val cycles = step.scrollCycles.coerceIn(1, 15)
                val isUp = step.value.equals("UP", ignoreCase = true)
                for (c in 1..cycles) {
                    if (!coroutineContext.isActive) return
                    waitWhilePaused()
                    val baseDelta = (rng.nextInt(160, 320) * persona.amplitudeMultiplier).roundToInt() * if (isUp) -1 else 1
                    val delta = if (flow.humanScrollJitter && c > 2 && rng.nextInt(100) < persona.backtrackChancePct) {
                        -(baseDelta * 0.45f).roundToInt()
                    } else {
                        baseDelta
                    }
                    performScrollJs(webViewProvider(), persona, delta)
                    val animMs = rng.nextLong(750L, 1100L)
                    delay(animMs)
                    val pauseMin = (maxOf(step.minDelayMs, 1400) * persona.paceMultiplier).toLong().coerceAtLeast(1200L)
                    val pauseMax = (maxOf(step.maxDelayMs, pauseMin.toInt() + 900) * persona.paceMultiplier).toLong().coerceAtLeast(pauseMin + 800L)
                    delay(rng.nextLong(pauseMin, pauseMax))
                }
            }

            RpaActionType.HUMAN_CLICK -> {
                val targetEsc = escapeJs(step.target)
                val js = """
                    (function(){
                      const sel = '$targetEsc';
                      let el = null;
                      try { el = document.querySelector(sel); } catch(_) {}
                      if (!el) {
                        const all = Array.from(document.querySelectorAll('a,button,input[type=submit],[role=button]'));
                        el = all.find(x => (x.innerText || x.value || '').toLowerCase().includes(sel.toLowerCase()));
                      }
                      if (!el) return false;
                      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                      setTimeout(() => {
                        try { el.removeAttribute('target'); } catch(_) {}
                        el.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
                        el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
                        el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
                        el.click();
                      }, 320);
                      return true;
                    })();
                """.trimIndent()
                evalJs(webViewProvider(), js)
                delay(rng.nextLong(1200, 2400))
            }

            RpaActionType.HUMAN_TYPE -> {
                humanTypeIntoSelector(
                    selector = step.target.ifBlank { "input[type=text],input[type=search],input:not([type=hidden]),textarea" },
                    text = step.value,
                    flow = flow,
                    webViewProvider = webViewProvider,
                    submitAfter = true
                )
            }

            RpaActionType.CLICK_RANDOM_LINK -> {
                clickRandomInternalLink(webViewProvider())
                delay(rng.nextLong(2200, 3800))
            }

            RpaActionType.CLEAR_COOKIES -> {
                withContext(Dispatchers.Main) {
                    runCatching {
                        val cm = ProfileWebViewManager.getProfileCookieManager(webViewProvider())
                        cm.removeAllCookies(null)
                        cm.flush()
                    }
                }
                delay(600)
            }
        }
    }

    private enum class CaptchaGateOutcome {
        CLEAR,
        RETRY_SEARCH,
        FALLBACK_DIRECT
    }

    private suspend fun handleCaptchaGateIfPresent(
        webView: WebView?,
        captchaConfig: CaptchaSolverConfig,
        activeProxy: ProxyEntity?,
        allowSessionRotation: Boolean,
        onRotateProxySession: (suspend (ProxyEntity, String) -> Boolean)?,
        onCaptchaSolved: (suspend (String) -> Unit)?,
        onLogEvent: (suspend (String) -> Unit)?,
        flow: AutomationFlowEntity,
        persona: ResolvedScrollPersona,
        loop: Int,
        totalLoops: Int,
        stepIndex: Int,
        totalSteps: Int
    ): CaptchaGateOutcome {
        val outcome = CaptchaSolverEngine.attemptSolveUpTo3Tries(
            webView = webView,
            config = captchaConfig,
            activeProxy = activeProxy,
            allowSessionRotation = allowSessionRotation,
            onStatus = { msg ->
                currentCaptchaAlert = msg
                updateStatus(flow.name, loop, totalLoops, stepIndex, totalSteps, msg, persona.label)
            },
            onRotateProxyRequested = onRotateProxySession
        )
        return when (outcome) {
            CaptchaSolveOutcome.NotPresent -> {
                currentCaptchaAlert = ""
                CaptchaGateOutcome.CLEAR
            }
            is CaptchaSolveOutcome.Solved -> {
                currentCaptchaAlert = ""
                captchasSolvedCounter++
                onCaptchaSolved?.invoke(outcome.method)
                onLogEvent?.invoke("CAPTCHA solved (${outcome.method})")
                delay(1200)
                CaptchaGateOutcome.CLEAR
            }
            is CaptchaSolveOutcome.RotatedProxySession -> {
                currentCaptchaAlert = "Switched proxy session -> ${outcome.newSsid}"
                onLogEvent?.invoke("Rotated sticky session -> ${outcome.newSsid}")
                delay(1100)
                CaptchaGateOutcome.RETRY_SEARCH
            }
            is CaptchaSolveOutcome.FallbackToDirectTarget -> {
                currentCaptchaAlert = outcome.reason
                onLogEvent?.invoke("CAPTCHA unresolved (${outcome.type.label}) after 3 tries: ${outcome.reason}")
                CaptchaGateOutcome.FALLBACK_DIRECT
            }
        }
    }

    private suspend fun executeGoogleSearchAndBrowse(
        keyword: String,
        targetDomain: String,
        dwellSeconds: Int,
        clickInternalLink: Boolean,
        pagesPerProfile: Int,
        captchaConfig: CaptchaSolverConfig,
        activeProxy: ProxyEntity?,
        onRotateProxySession: (suspend (ProxyEntity, String) -> Boolean)?,
        onPageScrolled: (suspend () -> Unit)?,
        onCaptchaSolved: (suspend (String) -> Unit)?,
        onLogEvent: (suspend (String) -> Unit)?,
        flow: AutomationFlowEntity,
        persona: ResolvedScrollPersona,
        loop: Int,
        totalLoops: Int,
        stepIndex: Int,
        totalSteps: Int,
        webViewProvider: () -> WebView?,
        onNavigate: (String) -> Unit
    ) {
        val rng = Random(System.nanoTime())
        val hostHint = SerpDomainMatcher.normalizeHostHint(targetDomain)
        val fullHint = SerpDomainMatcher.normalizeFullHint(targetDomain)

        suspend fun openDirectTargetAndRead(reasonLabel: String) {
            if (hostHint.isBlank()) {
                currentCaptchaAlert = ""
                updateStatus(
                    flow.name,
                    loop,
                    totalLoops,
                    stepIndex,
                    totalSteps,
                    "$reasonLabel — no target domain set to open directly",
                    persona.label
                )
                delay(1500)
                return
            }
            val cleanDirect = fullHint.ifBlank { hostHint }
            val directUrl = if (cleanDirect.startsWith("http://") || cleanDirect.startsWith("https://")) {
                cleanDirect
            } else {
                "https://$cleanDirect"
            }
            currentCaptchaAlert = ""
            updateStatus(
                flow.name,
                loop,
                totalLoops,
                stepIndex,
                totalSteps,
                "$reasonLabel — opening $hostHint directly...",
                persona.label
            )
            onLogEvent?.invoke("$reasonLabel -> opening $directUrl directly")
            withContext(Dispatchers.Main) { onNavigate(directUrl) }
            waitForPageCondition(maxWaitMs = 9500L) {
                evalJsResult(webViewProvider(), "document.readyState !== 'loading'") == "true"
            }
            delay(rng.nextLong(1400, 2300))
            runTargetWebsiteHumanReadLoop(
                pagesPerProfile = pagesPerProfile,
                dwellSeconds = dwellSeconds,
                clickInternalLink = clickInternalLink,
                captchaConfig = captchaConfig,
                activeProxy = activeProxy,
                onRotateProxySession = onRotateProxySession,
                onPageScrolled = onPageScrolled,
                onCaptchaSolved = onCaptchaSolved,
                onLogEvent = onLogEvent,
                flow = flow,
                persona = persona,
                loop = loop,
                totalLoops = totalLoops,
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                webViewProvider = webViewProvider,
                rng = rng
            )
        }

        var didRotateSession = false
        var fallbackDirectDueToCaptcha = false

        for (searchAttempt in 1..2) {
            if (!coroutineContext.isActive) return
            // 1. Open Google homepage naturally and wait until ready
            updateStatus(flow.name, loop, totalLoops, stepIndex, totalSteps, "Opening Google for '$keyword'...", persona.label)
            withContext(Dispatchers.Main) { onNavigate("https://www.google.com/ncr") }
            waitForPageCondition(maxWaitMs = 9000L) {
                evalJsResult(webViewProvider(), "document.readyState !== 'loading' && (!!document.querySelector('textarea[name=q], input[name=q], #L2AGLb, #captcha-form, iframe[src*=recaptcha]'))") == "true"
            }
            if (!coroutineContext.isActive) return
            waitWhilePaused()

            // Solve CAPTCHA if Google challenged homepage immediately (up to 3 tries)
            when (
                handleCaptchaGateIfPresent(
                    webView = webViewProvider(),
                    captchaConfig = captchaConfig,
                    activeProxy = activeProxy,
                    allowSessionRotation = !didRotateSession,
                    onRotateProxySession = onRotateProxySession,
                    onCaptchaSolved = onCaptchaSolved,
                    onLogEvent = onLogEvent,
                    flow = flow,
                    persona = persona,
                    loop = loop,
                    totalLoops = totalLoops,
                    stepIndex = stepIndex,
                    totalSteps = totalSteps
                )
            ) {
                CaptchaGateOutcome.RETRY_SEARCH -> {
                    didRotateSession = true
                    continue
                }
                CaptchaGateOutcome.FALLBACK_DIRECT -> {
                    fallbackDirectDueToCaptcha = true
                }
                CaptchaGateOutcome.CLEAR -> Unit
            }
            if (fallbackDirectDueToCaptcha) break

            // 2. Dismiss cookie consent banner if Google displays one + settle naturally to reduce /sorry rate
            dismissGoogleConsent(webViewProvider())
            delay(rng.nextLong(1150, 1950))

            // 3. Type keyword character-by-character into Google search box, submit, then wait for SERP
            updateStatus(flow.name, loop, totalLoops, stepIndex, totalSteps, "Typing '$keyword' like a human...", persona.label)
            val typed = humanTypeIntoSelector(
                selector = "textarea[name='q'], input[name='q']",
                text = keyword,
                flow = flow,
                webViewProvider = webViewProvider,
                submitAfter = true
            )

            var serpLoaded = false
            if (typed) {
                serpLoaded = waitForPageCondition(maxWaitMs = 7500L) {
                    val url = decodeJsString(evalJsResult(webViewProvider(), "window.location.href")).lowercase()
                    val isCaptcha = url.contains("/sorry")
                    val hasResults = evalJsResult(
                        webViewProvider(),
                        "(document.readyState !== 'loading') && (document.querySelectorAll('#search a[href], #rso a[href], a h3, a [role=heading], cite').length > 1)"
                    ) == "true"
                    isCaptcha || ((url.contains("/search") && url.contains("q=")) && hasResults)
                }
            }
            if (!serpLoaded) {
                val encoded = Uri.encode(keyword)
                withContext(Dispatchers.Main) { onNavigate("https://www.google.com/search?q=$encoded") }
                waitForPageCondition(maxWaitMs = 10000L) {
                    val url = decodeJsString(evalJsResult(webViewProvider(), "window.location.href")).lowercase()
                    val isCaptcha = url.contains("/sorry")
                    val hasResults = evalJsResult(
                        webViewProvider(),
                        "(document.readyState !== 'loading') && (document.querySelectorAll('#search a[href], #rso a[href], a h3, a [role=heading], cite').length > 1)"
                    ) == "true"
                    isCaptcha || ((url.contains("/search") && url.contains("q=")) && hasResults)
                }
            }
            if (!coroutineContext.isActive) return
            waitWhilePaused()

            // Check & solve Google /sorry CAPTCHA if triggered right after submitting keyword (up to 3 tries)
            val serpGate = handleCaptchaGateIfPresent(
                webView = webViewProvider(),
                captchaConfig = captchaConfig,
                activeProxy = activeProxy,
                allowSessionRotation = !didRotateSession,
                onRotateProxySession = onRotateProxySession,
                onCaptchaSolved = onCaptchaSolved,
                onLogEvent = onLogEvent,
                flow = flow,
                persona = persona,
                loop = loop,
                totalLoops = totalLoops,
                stepIndex = stepIndex,
                totalSteps = totalSteps
            )
            when (serpGate) {
                CaptchaGateOutcome.RETRY_SEARCH -> {
                    didRotateSession = true
                    continue
                }
                CaptchaGateOutcome.FALLBACK_DIRECT -> {
                    fallbackDirectDueToCaptcha = true
                    break
                }
                CaptchaGateOutcome.CLEAR -> {
                    val postCaptchaUrl = decodeJsString(evalJsResult(webViewProvider(), "window.location.href")).lowercase()
                    if (!postCaptchaUrl.contains("/sorry")) break
                    if (didRotateSession || searchAttempt >= 2) {
                        fallbackDirectDueToCaptcha = true
                        break
                    }
                }
            }
        }

        if (fallbackDirectDueToCaptcha) {
            openDirectTargetAndRead("CAPTCHA unsolved after 3 tries")
            return
        }

        // Dismiss consent banner again if Google showed it on the SERP and let user naturally read top results
        dismissGoogleConsent(webViewProvider())
        delay(rng.nextLong(1800, 2900))

        // 4. Locate and open the matching/suggested website on Google SERP:
        //    Check current page FIRST; ONLY scroll down & click "More results" / "Next" if NOT found on current page!
        var matchedUrl: String? = null
        val maxPagesToScan = if (hostHint.isNotBlank()) 5 else 1

        pageLoop@ for (pageAttempt in 1..maxPagesToScan) {
            updateStatus(
                flow.name,
                loop,
                totalLoops,
                stepIndex,
                totalSteps,
                if (hostHint.isNotBlank()) "Reading Google Page $pageAttempt for '$hostHint'..." else "Reading Google results...",
                persona.label
            )

            // Check the full currently-loaded DOM first before doing any downward scroll
            matchedUrl = findAndPrepareGoogleResult(
                webView = webViewProvider(),
                hostHint = hostHint,
                fullHint = fullHint,
                allowRandomFallback = hostHint.isBlank()
            )
            if (matchedUrl != null) break@pageLoop

            // Not found in initial view — scroll gently like a human reading SERP snippets, pausing between stops
            for (scanStep in 1..7) {
                if (!coroutineContext.isActive) return
                waitWhilePaused()

                val scrollStep = rng.nextInt(220, 360)
                val glideDuration = rng.nextInt(750, 1150)
                performGentleDeltaGlideJs(webViewProvider(), scrollStep, glideDuration)
                delay(glideDuration.toLong() + rng.nextLong(1650, 3000))

                matchedUrl = findAndPrepareGoogleResult(
                    webView = webViewProvider(),
                    hostHint = hostHint,
                    fullHint = fullHint,
                    allowRandomFallback = (hostHint.isBlank() && scanStep >= 2)
                )
                if (matchedUrl != null) break@pageLoop

                val atBottom = evalJsResult(
                    webViewProvider(),
                    "(window.scrollY + window.innerHeight) >= (document.documentElement.scrollHeight - 50)"
                ) == "true"
                if (atBottom) break
            }

            // Reached bottom of current results and domain STILL not found -> tap "More results" / "Next"
            if (pageAttempt < maxPagesToScan && hostHint.isNotBlank()) {
                updateStatus(
                    flow.name,
                    loop,
                    totalLoops,
                    stepIndex,
                    totalSteps,
                    "Not on Page $pageAttempt — opening More results (Page ${pageAttempt + 1})...",
                    persona.label
                )
                val loadedMore = loadMoreGoogleResults(webViewProvider(), rng)
                if (!loadedMore) break
                delay(rng.nextLong(1000, 1500))
                val moreGate = handleCaptchaGateIfPresent(
                    webView = webViewProvider(),
                    captchaConfig = captchaConfig,
                    activeProxy = activeProxy,
                    allowSessionRotation = false,
                    onRotateProxySession = onRotateProxySession,
                    onCaptchaSolved = onCaptchaSolved,
                    onLogEvent = onLogEvent,
                    flow = flow,
                    persona = persona,
                    loop = loop,
                    totalLoops = totalLoops,
                    stepIndex = stepIndex,
                    totalSteps = totalSteps
                )
                if (moreGate == CaptchaGateOutcome.FALLBACK_DIRECT) {
                    openDirectTargetAndRead("CAPTCHA unsolved after 3 tries")
                    return
                }
            }
        }

        // If target domain was left blank by user, pick a top organic result on Page 1
        if (matchedUrl == null && hostHint.isBlank()) {
            matchedUrl = findAndPrepareGoogleResult(
                webView = webViewProvider(),
                hostHint = "",
                fullHint = "",
                allowRandomFallback = true
            )
        }

        if (matchedUrl == null) {
            if (hostHint.isNotBlank()) {
                openDirectTargetAndRead("Domain not on top SERP pages")
                return
            }
            updateStatus(
                flow.name,
                loop,
                totalLoops,
                stepIndex,
                totalSteps,
                "No clickable Google result found",
                persona.label
            )
            onLogEvent?.invoke("No clickable SERP result found")
            delay(1500)
            return
        }

        // Pause briefly to read snippet/title smoothly scrolled into view, then tap like a real human
        updateStatus(
            flow.name,
            loop,
            totalLoops,
            stepIndex,
            totalSteps,
            "Found ${if (hostHint.isNotBlank()) hostHint else "result"} — reading snippet & opening...",
            persona.label
        )
        delay(rng.nextLong(1250, 2200))

        tapPreparedTargetInWebView(webViewProvider(), "__cdTargetEl", rng)
        var navigatedAway = waitForPageCondition(maxWaitMs = 2800L) {
            val cur = decodeJsString(evalJsResult(webViewProvider(), "window.location.href")).lowercase()
            cur.startsWith("http") && !isGoogleSerpUrl(cur)
        }

        if (!navigatedAway) {
            clickPreparedGoogleResult(webViewProvider())
            navigatedAway = waitForPageCondition(maxWaitMs = 4500L) {
                val cur = decodeJsString(evalJsResult(webViewProvider(), "window.location.href")).lowercase()
                cur.startsWith("http") && !isGoogleSerpUrl(cur)
            }
        }

        if (!navigatedAway) {
            evalJs(webViewProvider(), "if (window.__cdTargetEl) window.__cdTargetEl.scrollIntoView({ behavior: 'smooth', block: 'center' });")
            delay(750)
            tapPreparedTargetInWebView(webViewProvider(), "__cdTargetEl", rng)
            navigatedAway = waitForPageCondition(maxWaitMs = 4000L) {
                val cur = decodeJsString(evalJsResult(webViewProvider(), "window.location.href")).lowercase()
                cur.startsWith("http") && !isGoogleSerpUrl(cur)
            }
        }

        if (!navigatedAway) {
            if (hostHint.isNotBlank()) {
                openDirectTargetAndRead("SERP click blocked")
                return
            }
            updateStatus(
                flow.name,
                loop,
                totalLoops,
                stepIndex,
                totalSteps,
                "Could not open result from Google SERP",
                persona.label
            )
            delay(1500)
            return
        }

        waitForPageCondition(maxWaitMs = 7000L) {
            evalJsResult(webViewProvider(), "document.readyState !== 'loading'") == "true"
        }
        delay(rng.nextLong(1400, 2300))

        runTargetWebsiteHumanReadLoop(
            pagesPerProfile = pagesPerProfile,
            dwellSeconds = dwellSeconds,
            clickInternalLink = clickInternalLink,
            captchaConfig = captchaConfig,
            activeProxy = activeProxy,
            onRotateProxySession = onRotateProxySession,
            onPageScrolled = onPageScrolled,
            onCaptchaSolved = onCaptchaSolved,
            onLogEvent = onLogEvent,
            flow = flow,
            persona = persona,
            loop = loop,
            totalLoops = totalLoops,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            webViewProvider = webViewProvider,
            rng = rng
        )
    }

    private suspend fun runTargetWebsiteHumanReadLoop(
        pagesPerProfile: Int,
        dwellSeconds: Int,
        clickInternalLink: Boolean,
        captchaConfig: CaptchaSolverConfig,
        activeProxy: ProxyEntity?,
        onRotateProxySession: (suspend (ProxyEntity, String) -> Boolean)?,
        onPageScrolled: (suspend () -> Unit)?,
        onCaptchaSolved: (suspend (String) -> Unit)?,
        onLogEvent: (suspend (String) -> Unit)?,
        flow: AutomationFlowEntity,
        persona: ResolvedScrollPersona,
        loop: Int,
        totalLoops: Int,
        stepIndex: Int,
        totalSteps: Int,
        webViewProvider: () -> WebView?,
        rng: Random
    ) {
        // Attempt up to 3 bounded tries if target site shows Cloudflare/Turnstile/hCaptcha; never hang if unresolved
        handleCaptchaGateIfPresent(
            webView = webViewProvider(),
            captchaConfig = captchaConfig,
            activeProxy = activeProxy,
            allowSessionRotation = false,
            onRotateProxySession = onRotateProxySession,
            onCaptchaSolved = onCaptchaSolved,
            onLogEvent = onLogEvent,
            flow = flow,
            persona = persona,
            loop = loop,
            totalLoops = totalLoops,
            stepIndex = stepIndex,
            totalSteps = totalSteps
        )
        currentCaptchaAlert = ""

        val safePages = pagesPerProfile.coerceIn(1, 50)
        val allocations = AutomationTiming.computePageDwellAllocation(
            totalDwellSeconds = dwellSeconds,
            pagesPerProfile = safePages
        )
        for ((idxZero, perPageDwell) in allocations.withIndex()) {
            val pageIdx = idxZero + 1
            if (!coroutineContext.isActive) return
            // Exact page control: when safePages == 1 never click away to a 2nd page;
            // when safePages > 1 the outer loop below performs each page transition cleanly.
            executeHumanReadDuration(
                dwellSeconds = perPageDwell,
                clickInternalLink = false,
                flow = flow,
                persona = persona,
                loop = loop,
                totalLoops = totalLoops,
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                webViewProvider = webViewProvider
            )
            pagesScrolledCounter++
            onPageScrolled?.invoke()
            onLogEvent?.invoke("Visited page $pageIdx/$safePages (${perPageDwell}s • ${persona.style.displayName})")

            if (pageIdx < safePages) {
                val prevUrl = decodeJsString(evalJsResult(webViewProvider(), "window.location.href"))
                updateStatus(
                    flow.name,
                    loop,
                    totalLoops,
                    stepIndex,
                    totalSteps,
                    "Opening next site page (${pageIdx + 1}/$safePages)...",
                    persona.label
                )
                val clicked = evalJsResult(webViewProvider(), buildClickRandomInternalLinkJs()) == "true"
                if (clicked) {
                    delay(1450)
                    waitForPageCondition(maxWaitMs = 6500L) {
                        val nowUrl = decodeJsString(evalJsResult(webViewProvider(), "window.location.href"))
                        val ready = evalJsResult(webViewProvider(), "document.readyState !== 'loading'") == "true"
                        ready && (nowUrl != prevUrl || System.currentTimeMillis() % 2 == 0L)
                    }
                    delay(rng.nextLong(1200, 2100))
                } else {
                    delay(rng.nextLong(1200, 1900))
                }
                handleCaptchaGateIfPresent(
                    webView = webViewProvider(),
                    captchaConfig = captchaConfig,
                    activeProxy = activeProxy,
                    allowSessionRotation = false,
                    onRotateProxySession = onRotateProxySession,
                    onCaptchaSolved = onCaptchaSolved,
                    onLogEvent = onLogEvent,
                    flow = flow,
                    persona = persona,
                    loop = loop,
                    totalLoops = totalLoops,
                    stepIndex = stepIndex,
                    totalSteps = totalSteps
                )
                currentCaptchaAlert = ""
            }
        }
    }

    private fun buildClickRandomInternalLinkJs(): String = """
        (function(){
          const curHost = location.hostname;
          const curPath = location.pathname;
          const links = Array.from(document.querySelectorAll('a[href]')).filter(a => {
            const rect = a.getBoundingClientRect();
            const href = a.getAttribute('href') || '';
            if (rect.width < 20 || rect.height < 10) return false;
            if (href.startsWith('#') || href.startsWith('javascript:')) return false;
            try {
              const u = new URL(a.href, location.href);
              return u.hostname === curHost && u.pathname !== curPath;
            } catch(_) { return false; }
          });
          if (links.length === 0) return false;
          const pick = links[Math.floor(Math.random() * Math.min(links.length, 15))];
          try { pick.removeAttribute('target'); } catch(_) {}
          const r = pick.getBoundingClientRect();
          const startY = window.scrollY || 0;
          const desiredTop = Math.max(0, startY + r.top - (window.innerHeight * 0.42));
          const dist = desiredTop - startY;
          const dur = 750;
          const t0 = performance.now();
          (function anim(now){
            const p = Math.min(1, (now - t0) / dur);
            const e = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
            window.scrollTo(0, startY + dist * e);
            if (p < 1) requestAnimationFrame(anim);
          })(t0);
          setTimeout(() => pick.click(), 1250);
          return true;
        })();
    """.trimIndent()

    private suspend fun findAndPrepareGoogleResult(
        webView: WebView?,
        hostHint: String,
        fullHint: String,
        allowRandomFallback: Boolean
    ): String? {
        val hostEsc = escapeJs(hostHint)
        val fullEsc = escapeJs(fullHint)
        val allowFallbackJs = if (allowRandomFallback) "true" else "false"

        val js = """
            (function(){
              const hostHint = '$hostEsc';
              const fullHint = '$fullEsc';
              const allowRandom = $allowFallbackJs;

              function unwrapGoogleUrl(raw) {
                if (!raw || raw.startsWith('#') || raw.startsWith('javascript:')) return '';
                try {
                  const u = new URL(raw, window.location.origin);
                  const h = u.hostname.toLowerCase();
                  if ((h === window.location.hostname.toLowerCase() || h.includes('google.') || h.includes('googleadservices.com')) &&
                      (u.pathname === '/url' || u.pathname.startsWith('/url') || u.pathname.includes('/aclk'))) {
                    const urlParam = u.searchParams.get('url') || u.searchParams.get('adurl') || u.searchParams.get('u') || u.searchParams.get('dest_url');
                    const qParam = u.searchParams.get('q');
                    const inner = (urlParam && urlParam.startsWith('http')) ? urlParam
                                : (qParam && qParam.startsWith('http')) ? qParam : '';
                    return inner || '';
                  }
                  if (u.protocol === 'http:' || u.protocol === 'https:') {
                    return u.href;
                  }
                } catch(_) {}
                return '';
              }

              function isGoogleUiUrl(resolvedUrl) {
                try {
                  const h = new URL(resolvedUrl).hostname.toLowerCase();
                  if (hostHint && (h === hostHint || h.endsWith('.' + hostHint))) return false;
                  const blocked = ['google.', 'gstatic.com', 'googleusercontent.com', 'youtube.com/redirect'];
                  return blocked.some(b => h.includes(b));
                } catch(_) { return true; }
              }

              const anchors = Array.from(document.querySelectorAll('a[href], a[data-href], a[data-url], [data-sb-url]'));
              const scored = [];
              const fallbackOrganic = [];

              for (const a of anchors) {
                const style = window.getComputedStyle(a);
                if (style.display === 'none' || style.visibility === 'hidden') continue;

                const rawCandidates = [
                  a.getAttribute('href') || '',
                  a.href || '',
                  a.getAttribute('data-href') || '',
                  a.getAttribute('data-url') || '',
                  a.getAttribute('data-sb-url') || '',
                  a.getAttribute('ping') || ''
                ];
                let resolved = '';
                for (const r of rawCandidates) {
                  const unwrapped = unwrapGoogleUrl(r);
                  if (unwrapped) { resolved = unwrapped; break; }
                }

                const card = a.closest('[data-hveid], [data-sokoban-container], .MjjYud, .g, .xpd, .ezO2md, article');
                const citeEl = (card || a).querySelector('cite, [role="text"], .VuuXrf, .qLRx3b, .tjvcx');
                const citeText = (citeEl ? citeEl.innerText : '').toLowerCase();
                const anchorText = (a.innerText || a.textContent || '').toLowerCase();
                const cardText = (card ? (card.innerText || '') : '').toLowerCase();

                if (!resolved) continue;
                if (isGoogleUiUrl(resolved)) {
                  // Only keep Google-hosted URL if the card itself visibly shows target hostHint (e.g. AMP/snippet link)
                  if (!(hostHint && (citeText.includes(hostHint) || anchorText.includes(hostHint)))) continue;
                }
                if (resolved.includes('translate.google.') || resolved.includes('webcache.googleusercontent.')) continue;

                let destHost = '';
                let destFull = '';
                try {
                  const parsed = new URL(resolved);
                  destHost = parsed.hostname.toLowerCase().replace(/^www\./, '');
                  destFull = (destHost + parsed.pathname + parsed.search).toLowerCase();
                } catch(_) { continue; }

                const hasHeading = a.querySelector('h3, [role="heading"], .LC20lb, .DKV0Md, .MBeuO, .vvjwJb') !== null;
                const inMainSearch = a.closest('#search, #rso, #main') !== null;
                const rect = a.getBoundingClientRect();
                const hasLayout = (rect.width > 8 && rect.height > 8) || a.getClientRects().length > 0;

                if (hostHint.length > 0) {
                  let score = 0;
                  if (destHost === hostHint || destHost.endsWith('.' + hostHint)) score += 240;
                  else if (destHost.includes(hostHint)) score += 140;
                  if (fullHint.length > 0 && destFull.includes(fullHint)) score += 90;
                  if (resolved.toLowerCase().includes(hostHint)) score += 60;
                  if (citeText.includes(hostHint)) score += 50;
                  if (anchorText.includes(hostHint)) score += 40;
                  else if (cardText.includes(hostHint) && hasHeading) score += 35;

                  if (score > 0) {
                    if (hasHeading) score += 80;
                    if (inMainSearch) score += 20;
                    if (hasLayout) score += 15;
                    scored.push({ el: a, url: resolved, score: score });
                  }
                } else if (allowRandom && inMainSearch && (hasHeading || anchorText.trim().length > 10) && hasLayout) {
                  fallbackOrganic.push({ el: a, url: resolved, score: (hasHeading ? 50 : 20) });
                }
              }

              // Plan B: If hostHint is specified and still no anchor matched, inspect visible <cite> / domain text directly
              if (hostHint.length > 0 && scored.length === 0) {
                const cites = Array.from(document.querySelectorAll('cite, .VuuXrf, .qLRx3b, .tjvcx'));
                for (const c of cites) {
                  const ct = (c.innerText || c.textContent || '').toLowerCase();
                  if (!ct.includes(hostHint)) continue;
                  const card = c.closest('[data-hveid], [data-sokoban-container], .MjjYud, .g, .xpd, .ezO2md, article') || c.parentElement;
                  const link = c.closest('a[href]') || (card ? card.querySelector('a[href]') : null);
                  if (link) {
                    const fallbackUrl = unwrapGoogleUrl(link.getAttribute('href') || link.href || '') || ('https://' + hostHint);
                    scored.push({ el: link, url: fallbackUrl, score: 150 });
                    break;
                  }
                }
              }

              let chosen = null;
              if (scored.length > 0) {
                scored.sort((x, y) => y.score - x.score);
                chosen = scored[0];
              } else if (allowRandom && fallbackOrganic.length > 0) {
                const topN = Math.min(3, fallbackOrganic.length);
                chosen = fallbackOrganic[Math.floor(Math.random() * topN)];
              }

              if (!chosen) return '';
              window.__cdTargetEl = chosen.el;
              window.__cdTargetUrl = chosen.url;
              try { chosen.el.removeAttribute('target'); } catch(_) {}
              try { chosen.el.removeAttribute('ping'); } catch(_) {}
              const focusEl = chosen.el.querySelector('h3, [role="heading"]') || chosen.el;
              try {
                const r = focusEl.getBoundingClientRect();
                const curY = window.scrollY || 0;
                const desiredTop = Math.max(0, curY + r.top - (window.innerHeight * 0.38));
                const dist = desiredTop - curY;
                if (Math.abs(dist) > 25) {
                  const dur = Math.min(1050, Math.max(550, Math.abs(dist) * 0.9));
                  const t0 = performance.now();
                  (function anim(now){
                    const p = Math.min(1, (now - t0) / dur);
                    const e = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
                    window.scrollTo(0, curY + dist * e);
                    if (p < 1) requestAnimationFrame(anim);
                  })(t0);
                }
              } catch(_) {}
              return 'FOUND|' + chosen.url;
            })();
        """.trimIndent()

        val raw = decodeJsString(evalJsResult(webView, js))
        return if (raw.startsWith("FOUND|")) raw.removePrefix("FOUND|").takeIf { it.isNotBlank() } else null
    }

    private suspend fun clickPreparedGoogleResult(webView: WebView?) {
        val js = """
            (function(){
              const el = window.__cdTargetEl;
              if (!el) return false;
              try { el.removeAttribute('target'); } catch(_) {}
              try { el.removeAttribute('ping'); } catch(_) {}
              try { el.focus(); } catch(_) {}
              const target = el.querySelector('h3, [role="heading"]') || el;
              const rect = target.getBoundingClientRect();
              const cx = Math.max(10, Math.min(window.innerWidth - 10, rect.left + Math.max(20, rect.width) / 2));
              const cy = Math.max(10, Math.min(window.innerHeight - 10, rect.top + Math.max(16, rect.height) / 2));
              const opts = { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy };
              ['pointerover','mouseover','pointerdown','mousedown','pointerup','mouseup','click'].forEach(t => {
                try { target.dispatchEvent(new MouseEvent(t, opts)); } catch(_) {}
              });
              try { el.click(); } catch(_) {}
              return true;
            })();
        """.trimIndent()
        evalJs(webView, js)
    }

    private suspend fun loadMoreGoogleResults(webView: WebView?, rng: Random): Boolean {
        val snapshotRaw = decodeJsString(
            evalJsResult(
                webView,
                "(document.querySelectorAll('a[href]').length) + '|' + (document.documentElement.scrollHeight) + '|' + window.location.href"
            )
        )
        val snapParts = snapshotRaw.split("|", limit = 3)
        val prevAnchors = snapParts.getOrNull(0)?.toIntOrNull() ?: 0
        val prevHeight = snapParts.getOrNull(1)?.toIntOrNull() ?: 0
        val prevUrl = snapParts.getOrNull(2).orEmpty()

        val findMoreJs = """
            (function(){
              const minBottomDocY = Math.max(200, document.documentElement.scrollHeight * 0.45);
              function isNearBottom(el) {
                const r = el.getBoundingClientRect();
                const docY = (window.scrollY || 0) + r.top;
                return docY >= minBottomDocY || el.closest('#botstuff, #bres, #foot, [role="navigation"], td.d6cvqb') !== null;
              }

              const directSelectors = [
                '#pnnext',
                'a[id="pnnext"]',
                'td.d6cvqb a',
                'a.T7sFge',
                'a[aria-label*="More results" i]',
                'button[aria-label*="More results" i]',
                '[role="button"][aria-label*="More results" i]',
                '[role="navigation"] a[aria-label*="Next" i]'
              ];
              let btn = null;
              for (const s of directSelectors) {
                const candidates = Array.from(document.querySelectorAll(s));
                for (const candidate of candidates) {
                  const st = window.getComputedStyle(candidate);
                  if (st.display !== 'none' && st.visibility !== 'hidden' && isNearBottom(candidate)) {
                    btn = candidate;
                    break;
                  }
                }
                if (btn) break;
              }
              if (!btn) {
                const phrases = ['more results', 'see more results', 'show more results', 'more search results', 'search more', 'next page', 'مزید نتائج'];
                const all = Array.from(document.querySelectorAll('a, button, [role="button"], span.RVQdVd'));
                for (const el of all) {
                  const txt = (el.innerText || el.textContent || '').trim().toLowerCase().replace(/\s+/g, ' ');
                  if (!txt || txt.includes('learn more') || txt.includes('feedback') || txt.includes('help') || txt.includes('search settings') || txt.includes('search tools')) continue;
                  if (phrases.some(p => txt === p || txt.includes('more results') || txt.includes('search more') || txt.includes('مزید نتائج'))) {
                    const clickable = el.closest('a, button, [role="button"]') || el;
                    const st = window.getComputedStyle(clickable);
                    if (st.display !== 'none' && st.visibility !== 'hidden' && isNearBottom(clickable)) {
                      btn = clickable;
                      break;
                    }
                  }
                }
              }
              if (!btn) return false;
              window.__cdMoreBtn = btn;
              try { btn.removeAttribute('target'); } catch(_) {}
              try {
                const r = btn.getBoundingClientRect();
                const curY = window.scrollY || 0;
                const desiredTop = Math.max(0, curY + r.top - (window.innerHeight * 0.55));
                const dist = desiredTop - curY;
                if (Math.abs(dist) > 20) {
                  const dur = 750;
                  const t0 = performance.now();
                  (function anim(now){
                    const p = Math.min(1, (now - t0) / dur);
                    const e = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
                    window.scrollTo(0, curY + dist * e);
                    if (p < 1) requestAnimationFrame(anim);
                  })(t0);
                }
              } catch(_) {}
              return true;
            })();
        """.trimIndent()

        val foundBtn = evalJsResult(webView, findMoreJs) == "true"
        if (!foundBtn) return false

        delay(rng.nextLong(1100, 1850))
        // Perform real human touch tap on "More results" / "Next", with DOM click fallback
        tapPreparedTargetInWebView(webView, "__cdMoreBtn", rng)
        evalJs(webView, "if (window.__cdMoreBtn) { try { window.__cdMoreBtn.click(); } catch(_) {} }")

        return waitForPageCondition(maxWaitMs = 8000L) {
            val curRaw = decodeJsString(
                evalJsResult(
                    webView,
                    "(document.querySelectorAll('a[href]').length) + '|' + (document.documentElement.scrollHeight) + '|' + window.location.href + '|' + document.readyState"
                )
            )
            val parts = curRaw.split("|", limit = 4)
            val curAnchors = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val curHeight = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val curUrl = parts.getOrNull(2).orEmpty()
            val ready = parts.getOrNull(3).orEmpty()
            (curAnchors > prevAnchors + 2) ||
                (curHeight > prevHeight + 220) ||
                (curUrl != prevUrl && ready != "loading")
        }
    }

    private suspend fun tapPreparedTargetInWebView(
        webView: WebView?,
        globalVarName: String,
        rng: Random
    ): Boolean {
        if (webView == null) return false
        val rectJs = """
            (function(){
              const el = window['$globalVarName'];
              if (!el) return '';
              const focusEl = el.querySelector ? (el.querySelector('h3, [role="heading"]') || el) : el;
              const r = focusEl.getBoundingClientRect();
              if (!r || (r.width <= 0 && r.height <= 0)) return '';
              const cx = r.left + r.width * (0.35 + Math.random() * 0.30);
              const cy = r.top + r.height * (0.35 + Math.random() * 0.30);
              return cx.toFixed(1) + '|' + cy.toFixed(1) + '|' + window.innerWidth + '|' + window.innerHeight;
            })();
        """.trimIndent()
        val raw = decodeJsString(evalJsResult(webView, rectJs))
        val parts = raw.split("|")
        if (parts.size != 4) return false
        val cssX = parts[0].toFloatOrNull() ?: return false
        val cssY = parts[1].toFloatOrNull() ?: return false
        val vw = parts[2].toFloatOrNull()?.takeIf { it > 0f } ?: return false
        val vh = parts[3].toFloatOrNull()?.takeIf { it > 0f } ?: return false
        if (cssX < 0f || cssY < 0f || cssX > vw || cssY > vh) return false

        val holdMs = rng.nextLong(48L, 115L)
        return withContext(Dispatchers.Main) {
            if (webView.width <= 0 || webView.height <= 0) return@withContext false
            val viewX = ((cssX / vw) * webView.width).coerceIn(4f, (webView.width - 4).toFloat())
            val viewY = ((cssY / vh) * webView.height).coerceIn(4f, (webView.height - 4).toFloat())
            val downTime = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, viewX, viewY, 0)
            webView.dispatchTouchEvent(downEvent)
            downEvent.recycle()
            delay(holdMs)
            val upTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, viewX, viewY, 0)
            webView.dispatchTouchEvent(upEvent)
            upEvent.recycle()
            true
        }
    }

    private suspend fun dismissGoogleConsent(webView: WebView?) {
        val consentJs = """
            (function(){
              const selectors = [
                '#L2AGLb',
                'button[aria-label*="Accept"]',
                'button[aria-label*="Reject"]',
                'form[action*="consent"] button[type="submit"]'
              ];
              for (const s of selectors) {
                const btn = document.querySelector(s);
                if (btn) { btn.click(); return true; }
              }
              const buttons = Array.from(document.querySelectorAll('button, [role="button"]'));
              const match = buttons.find(b => {
                const t = (b.innerText || '').trim().toLowerCase();
                return t === 'accept all' || t === 'i agree' || t === 'reject all';
              });
              if (match) { match.click(); return true; }
              return false;
            })();
        """.trimIndent()
        evalJs(webView, consentJs)
    }

    private fun isGoogleSerpUrl(urlLower: String): Boolean {
        return urlLower.contains("google.") &&
            (urlLower.contains("/search") || urlLower.contains("consent.google") || urlLower.endsWith("google.com/") || urlLower.contains("/ncr"))
    }

    private data class ScrollBounds(
        val scrollY: Int,
        val maxScroll: Int
    )

    private suspend fun readScrollBounds(webView: WebView?): ScrollBounds {
        val raw = decodeJsString(
            evalJsResult(
                webView,
                "Math.round(window.scrollY || 0) + '|' + Math.max(0, Math.round(document.documentElement.scrollHeight - window.innerHeight))"
            )
        )
        val parts = raw.split("|")
        return ScrollBounds(
            scrollY = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            maxScroll = parts.getOrNull(1)?.toIntOrNull() ?: 0
        )
    }

    private suspend fun executeHumanReadDuration(
        dwellSeconds: Int,
        clickInternalLink: Boolean,
        flow: AutomationFlowEntity,
        persona: ResolvedScrollPersona,
        loop: Int,
        totalLoops: Int,
        stepIndex: Int,
        totalSteps: Int,
        webViewProvider: () -> WebView?
    ) {
        val rng = Random(System.nanoTime())
        val safeDwellSec = dwellSeconds.coerceIn(3, 3600)
        val totalMs = safeDwellSec * 1000L
        var activeElapsedMs = 0L
        var lastTickMs = SystemClock.elapsedRealtime()
        var clickedDeepLink = false
        var cycle = 0
        var movingDown = true
        val deepLinkThresholdMs = (totalMs * persona.clickRatioPercent) / 100L

        // Relax stride subtly for 1-minute+ and 2-minute+ single-page stays so scrolling feels calm & unhurried
        val sessionStrideScale = when {
            safeDwellSec >= 120 -> 0.72f
            safeDwellSec >= 60 -> 0.84f
            else -> 1.0f
        }
        val sessionPauseScale = when {
            safeDwellSec >= 120 -> 1.24f
            safeDwellSec >= 60 -> 1.12f
            else -> 1.0f
        }

        fun advanceWallClock() {
            val now = SystemClock.elapsedRealtime()
            activeElapsedMs += (now - lastTickMs).coerceAtLeast(0L)
            lastTickMs = now
        }

        suspend fun waitWhilePausedPreservingClock() {
            if (!paused) return
            advanceWallClock()
            waitWhilePaused()
            lastTickMs = SystemClock.elapsedRealtime()
        }

        suspend fun sleepWithCountdown(durationMs: Long, statusPrefix: String) {
            val targetElapsed = (activeElapsedMs + durationMs).coerceAtMost(totalMs)
            while (coroutineContext.isActive) {
                waitWhilePausedPreservingClock()
                advanceWallClock()
                val sliceLeft = targetElapsed - activeElapsedMs
                if (sliceLeft <= 0L) break

                val remSec = (((totalMs - activeElapsedMs).coerceAtLeast(0L) + 500L) / 1000L).toInt().coerceAtLeast(1)
                updateStatus(
                    flow.name,
                    loop,
                    totalLoops,
                    stepIndex,
                    totalSteps,
                    "$statusPrefix • ${remSec}s left",
                    persona.label,
                    remainingSeconds = remSec
                )
                delay(minOf(950L, sliceLeft))
            }
        }

        // Initial above-the-fold reading pause — a real person reads the headline/hero before touching scroll
        if (totalMs >= 5000L && coroutineContext.isActive) {
            waitWhilePausedPreservingClock()
            val initialWords = measureVisibleReadableWords(webViewProvider())
            val baseIntroMs = rng.nextLong(2600L, 4500L) + if (initialWords >= 30) rng.nextLong(800L, 1700L) else 0L
            val introPauseMs = minOf(baseIntroMs, (totalMs * 0.35).toLong()).coerceAtLeast(1600L)
            sleepWithCountdown(introPauseMs, "${persona.label} • Reading top of page")
        }

        while (coroutineContext.isActive) {
            waitWhilePausedPreservingClock()
            advanceWallClock()
            if (activeElapsedMs >= totalMs) break

            // Occasional internal link visit ONLY when explicitly enabled (unused when page count is exact)
            if (clickInternalLink && !clickedDeepLink && safeDwellSec >= 25 &&
                activeElapsedMs >= deepLinkThresholdMs && (totalMs - activeElapsedMs) > 8500L
            ) {
                clickedDeepLink = true
                clickRandomInternalLink(webViewProvider())
                sleepWithCountdown(minOf(rng.nextLong(2800L, 4200L), totalMs - activeElapsedMs), "${persona.label} • Loading section")
                continue
            }

            cycle++

            // Check scroll position so we reverse direction smoothly at bottom/top instead of bouncing at footer
            val bounds = readScrollBounds(webViewProvider())
            var hitTurnaroundBoundary = false
            if (bounds.maxScroll > 140) {
                if (movingDown && bounds.scrollY >= bounds.maxScroll - 75) {
                    movingDown = false
                    hitTurnaroundBoundary = true
                } else if (!movingDown && bounds.scrollY <= 75) {
                    movingDown = true
                    hitTurnaroundBoundary = true
                }
            }

            if (hitTurnaroundBoundary) {
                // Natural pause when finishing footer or returning to top before smoothly reversing scroll direction
                val boundaryPauseMs = rng.nextLong(2400L, 4400L)
                sleepWithCountdown(minOf(boundaryPauseMs, totalMs - activeElapsedMs), "${persona.label} • Reviewing section")
                advanceWallClock()
                if (activeElapsedMs >= totalMs) break
            }

            val burstCount = if (persona.burstCountMax > persona.burstCountMin) {
                rng.nextInt(persona.burstCountMin, persona.burstCountMax + 1)
            } else {
                persona.burstCountMin
            }

            for (b in 1..burstCount) {
                waitWhilePausedPreservingClock()
                advanceWallClock()
                if (!coroutineContext.isActive || activeElapsedMs >= totalMs) break

                val animMs = performProfileScrollStep(
                    webView = webViewProvider(),
                    persona = persona,
                    flowJitter = flow.humanScrollJitter,
                    cycle = cycle,
                    movingDown = movingDown,
                    strideScale = sessionStrideScale,
                    rng = rng
                )
                val waitAnim = minOf(animMs, totalMs - activeElapsedMs).coerceAtLeast(250L)
                sleepWithCountdown(waitAnim, "${persona.label} • Smooth reading")

                if (b < burstCount) {
                    advanceWallClock()
                    if (activeElapsedMs < totalMs) {
                        val intraBurstDelay = minOf(rng.nextLong(950L, 1750L), totalMs - activeElapsedMs).coerceAtLeast(350L)
                        sleepWithCountdown(intraBurstDelay, "${persona.label} • Reading")
                    }
                }
            }

            advanceWallClock()
            if (activeElapsedMs >= totalMs) break

            // Content-aware reading pause: look at what's visible on screen and actually pause to read it
            val visibleWords = measureVisibleReadableWords(webViewProvider())
            val contentExtraMs = when {
                visibleWords >= 90 -> rng.nextLong(1700L, 3200L)
                visibleWords >= 35 -> rng.nextLong(900L, 1900L)
                else -> rng.nextLong(200L, 650L)
            }
            val rawMin = (persona.pauseMinMs * persona.paceMultiplier * sessionPauseScale).toLong().coerceAtLeast(2200L)
            val rawMax = (persona.pauseMaxMs * persona.paceMultiplier * sessionPauseScale).toLong().coerceAtLeast(rawMin + 1000L)
            var pauseMs = rng.nextLong(rawMin, rawMax) + contentExtraMs

            if (persona.archetype == ScrollArchetype.SKIMMER_SCANNER && cycle == 3) {
                pauseMs = (pauseMs * 1.65f).toLong()
            }

            val remainingForPause = (totalMs - activeElapsedMs).coerceAtLeast(0L)
            val totalPauseSlice = minOf(pauseMs, remainingForPause)
            if (totalPauseSlice <= 0L) break

            if (totalPauseSlice > 4200L && rng.nextInt(100) < persona.eyeNudgeChancePct) {
                // Split long reading pause with a tiny calm eye-follow nudge (~35-75px) in active direction
                val firstHalf = totalPauseSlice / 2L
                sleepWithCountdown(firstHalf, "${persona.label} • Reading")
                advanceWallClock()
                if (coroutineContext.isActive && activeElapsedMs < totalMs) {
                    val nudgeAbs = rng.nextInt(35, 75)
                    val nudgePx = if (movingDown) nudgeAbs else -nudgeAbs
                    performGentleDeltaGlideJs(webViewProvider(), nudgePx, 780, persona.easing)
                    val secondHalf = minOf(totalPauseSlice - firstHalf, totalMs - activeElapsedMs)
                    if (secondHalf > 0L) {
                        sleepWithCountdown(secondHalf, "${persona.label} • Reading")
                    }
                }
            } else {
                sleepWithCountdown(totalPauseSlice, "${persona.label} • Reading")
            }
        }
    }

    private suspend fun measureVisibleReadableWords(webView: WebView?): Int {
        val js = """
            (function(){
              const els = document.querySelectorAll('p, h1, h2, h3, article li, blockquote');
              let words = 0;
              for (const el of els) {
                const r = el.getBoundingClientRect();
                if (r.bottom > 20 && r.top < window.innerHeight - 20 && r.height > 8) {
                  const t = (el.innerText || '').trim();
                  if (t) words += t.split(/\s+/).length;
                  if (words > 220) break;
                }
              }
              return words;
            })();
        """.trimIndent()
        return evalJsResult(webView, js)?.trim()?.toIntOrNull() ?: 0
    }

    private suspend fun performProfileScrollStep(
        webView: WebView?,
        persona: ResolvedScrollPersona,
        flowJitter: Boolean,
        cycle: Int,
        movingDown: Boolean,
        strideScale: Float,
        rng: Random
    ): Long {
        val shouldBacktrack = flowJitter && cycle > 1 && rng.nextInt(100) < persona.backtrackChancePct
        val dirSign = if (movingDown) 1 else -1

        return when (persona.archetype) {
            ScrollArchetype.SECTION_SNAPPER -> {
                val stepAdvance = if (shouldBacktrack) -dirSign else dirSign
                val durMs = rng.nextInt(980, 1520)
                val maxStepPx = (290 * persona.amplitudeMultiplier * strideScale).roundToInt().coerceIn(140, 340)
                val easeIdx = persona.easing.jsCurveIndex
                val js = """
                    (function(){
                      const blocks = Array.from(document.querySelectorAll('h1, h2, h3, p, article li, blockquote'))
                        .filter(el => {
                          const r = el.getBoundingClientRect();
                          return r.height > 16 && r.width > 80;
                        });
                      const easeFn = function(t){
                        if ($easeIdx === 1) return t < 0.5 ? 16*t*t*t*t*t : 1 - Math.pow(-2*t + 2, 5)/2;
                        if ($easeIdx === 2) return -(Math.cos(Math.PI * t) - 1) / 2;
                        if ($easeIdx === 3) return 1 - Math.pow(1 - t, 3);
                        return t < 0.5 ? 4*t*t*t : 1 - Math.pow(-2*t + 2, 3)/2;
                      };
                      const startY = window.scrollY || 0;
                      const maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
                      if (blocks.length > 2) {
                        if (typeof window.__cdSecIdx !== 'number') window.__cdSecIdx = 0;
                        let nextIdx = window.__cdSecIdx + ($stepAdvance);
                        if (nextIdx >= blocks.length) nextIdx = blocks.length - 1;
                        if (nextIdx < 0) nextIdx = 0;
                        window.__cdSecIdx = nextIdx;
                        const target = blocks[window.__cdSecIdx];
                        const rect = target.getBoundingClientRect();
                        let desiredTop = Math.max(0, Math.min(maxScroll, startY + rect.top - (window.innerHeight * 0.${rng.nextInt(24, 38)})));
                        let dist = desiredTop - startY;
                        // Never snap across 1500px in one burst — cap per-step travel so motion stays calm & human
                        if (Math.abs(dist) > $maxStepPx) {
                          dist = (dist > 0 ? 1 : -1) * $maxStepPx;
                        }
                        if (Math.abs(dist) < 20 && maxScroll > 60) {
                          dist = ($dirSign) * ${rng.nextInt(85, 165)};
                        }
                        const clampedDist = Math.max(-startY, Math.min(maxScroll - startY, dist));
                        const dur = $durMs;
                        const t0 = performance.now();
                        (function anim(now){
                          const p = Math.min(1, (now - t0) / dur);
                          window.scrollTo(0, startY + clampedDist * easeFn(p));
                          if (p < 1) requestAnimationFrame(anim);
                        })(t0);
                        const cx = Math.max(20, Math.min(window.innerWidth - 20, rect.left + rect.width * 0.4));
                        const cy = Math.max(20, Math.min(window.innerHeight - 20, window.innerHeight * 0.35));
                        target.dispatchEvent(new MouseEvent('mouseover', { clientX: cx, clientY: cy, bubbles: true }));
                        document.dispatchEvent(new MouseEvent('mousemove', { clientX: cx, clientY: cy, bubbles: true }));
                      } else {
                        const dist = ($dirSign) * ${rng.nextInt(110, 210)};
                        const clampedDist = Math.max(-startY, Math.min(maxScroll - startY, dist));
                        const dur = $durMs;
                        const t0 = performance.now();
                        (function anim(now){
                          const p = Math.min(1, (now - t0) / dur);
                          window.scrollTo(0, startY + clampedDist * easeFn(p));
                          if (p < 1) requestAnimationFrame(anim);
                        })(t0);
                      }
                    })();
                """.trimIndent()
                evalJs(webView, js)
                durMs.toLong()
            }

            ScrollArchetype.TOUCH_MOMENTUM -> {
                val base = (rng.nextInt(125, 245) * persona.amplitudeMultiplier * strideScale).roundToInt().coerceAtLeast(85)
                val delta = (if (shouldBacktrack) -(base * 0.50f).roundToInt() else base) * dirSign
                performScrollJs(webView, persona, delta)
            }

            ScrollArchetype.CAREFUL_READER -> {
                val base = (rng.nextInt(85, 165) * persona.amplitudeMultiplier * strideScale).roundToInt().coerceAtLeast(65)
                val delta = (if (shouldBacktrack) -(base * 0.50f).roundToInt() else base) * dirSign
                performScrollJs(webView, persona, delta)
            }

            ScrollArchetype.SKIMMER_SCANNER -> {
                val base = (rng.nextInt(175, 310) * persona.amplitudeMultiplier * strideScale).roundToInt().coerceAtLeast(130)
                val delta = (if (shouldBacktrack) -(base * 0.55f).roundToInt() else base) * dirSign
                performScrollJs(webView, persona, delta)
            }

            ScrollArchetype.BURST_AND_PAUSE -> {
                val base = (rng.nextInt(100, 195) * persona.amplitudeMultiplier * strideScale).roundToInt().coerceAtLeast(75)
                val delta = (if (shouldBacktrack) -(base * 0.45f).roundToInt() else base) * dirSign
                performScrollJs(webView, persona, delta)
            }

            ScrollArchetype.ZIGZAG_EXPLORER -> {
                val base = (rng.nextInt(120, 230) * persona.amplitudeMultiplier * strideScale).roundToInt().coerceAtLeast(85)
                val delta = (if (shouldBacktrack) -(base * 0.58f).roundToInt() else base) * dirSign
                performScrollJs(webView, persona, delta)
            }
        }
    }

    private suspend fun performGentleDeltaGlideJs(
        webView: WebView?,
        deltaPx: Int,
        durationMs: Int,
        easing: HumanizeEasing = HumanizeEasing.SINE_IN_OUT
    ) {
        val easeIdx = easing.jsCurveIndex
        val js = """
            (function(){
              const maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
              const startY = window.scrollY || 0;
              const targetY = Math.max(0, Math.min(maxScroll, startY + $deltaPx));
              const dist = targetY - startY;
              if (Math.abs(dist) < 4) return;
              const easeFn = function(t){
                if ($easeIdx === 1) return t < 0.5 ? 16*t*t*t*t*t : 1 - Math.pow(-2*t + 2, 5)/2;
                if ($easeIdx === 2) return -(Math.cos(Math.PI * t) - 1) / 2;
                if ($easeIdx === 3) return 1 - Math.pow(1 - t, 3);
                return t < 0.5 ? 4*t*t*t : 1 - Math.pow(-2*t + 2, 3)/2;
              };
              const dur = $durationMs;
              const t0 = performance.now();
              function anim(now) {
                const p = Math.min(1, (now - t0) / dur);
                window.scrollTo(0, startY + dist * easeFn(p));
                if (p < 1) requestAnimationFrame(anim);
              }
              requestAnimationFrame(anim);
            })();
        """.trimIndent()
        evalJs(webView, js)
    }

    private suspend fun performScrollJs(webView: WebView?, persona: ResolvedScrollPersona, deltaPx: Int): Long {
        val rng = Random(System.nanoTime())
        val hoverReal = if (persona.hoverRealElements) "true" else "false"
        // Scale animation duration smoothly (~880ms to ~1,720ms) so every scroll looks like a calm human thumb swipe
        val distAbs = kotlin.math.abs(deltaPx)
        val durationMs = ((840 + (distAbs * 2.15f).roundToInt() + rng.nextInt(100, 290)) * persona.paceMultiplier)
            .roundToInt()
            .coerceIn(880, 1750)
        val reverseStep = rng.nextInt(110, 210)
        val easeIdx = persona.easing.jsCurveIndex

        val js = """
            (function(){
              const maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
              const startY = window.scrollY || 0;
              let delta = $deltaPx;
              if (maxScroll > 0 && delta > 0 && startY >= maxScroll - 30) {
                delta = -Math.min(startY, $reverseStep);
              } else if (delta < 0 && startY <= 30) {
                delta = Math.min(maxScroll, Math.abs(delta));
              }

              const targetY = Math.max(0, Math.min(maxScroll, startY + delta));
              const dist = targetY - startY;
              const dur = $durationMs;
              const easeFn = function(t){
                if ($easeIdx === 1) return t < 0.5 ? 16*t*t*t*t*t : 1 - Math.pow(-2*t + 2, 5)/2;
                if ($easeIdx === 2) return -(Math.cos(Math.PI * t) - 1) / 2;
                if ($easeIdx === 3) return 1 - Math.pow(1 - t, 3);
                return t < 0.5 ? 4*t*t*t : 1 - Math.pow(-2*t + 2, 3)/2;
              };
              const startT = performance.now();
              if (Math.abs(dist) >= 2) {
                function step(now) {
                  const t = Math.min(1, (now - startT) / dur);
                  window.scrollTo(0, startY + dist * easeFn(t));
                  if (t < 1) requestAnimationFrame(step);
                }
                requestAnimationFrame(step);
              }

              if ($hoverReal) {
                const vis = Array.from(document.querySelectorAll('h2, h3, p, a')).filter(el => {
                  const r = el.getBoundingClientRect();
                  return r.top > 40 && r.bottom < window.innerHeight - 40 && r.width > 40;
                });
                if (vis.length > 0) {
                  const pick = vis[Math.floor(Math.random() * vis.length)];
                  const r = pick.getBoundingClientRect();
                  const rx = Math.floor(r.left + Math.random() * Math.max(20, r.width));
                  const ry = Math.floor(r.top + Math.random() * Math.max(12, r.height));
                  pick.dispatchEvent(new MouseEvent('mouseover', { clientX: rx, clientY: ry, bubbles: true }));
                  document.dispatchEvent(new MouseEvent('mousemove', { clientX: rx, clientY: ry, bubbles: true }));
                  return;
                }
              }
              const rx = Math.floor(Math.random() * Math.max(100, window.innerWidth - 40)) + 20;
              const ry = Math.floor(Math.random() * Math.max(100, window.innerHeight - 60)) + 30;
              document.dispatchEvent(new MouseEvent('mousemove', { clientX: rx, clientY: ry, bubbles: true }));
            })();
        """.trimIndent()
        evalJs(webView, js)
        return durationMs.toLong()
    }

    private suspend fun waitForPageCondition(
        maxWaitMs: Long,
        predicate: suspend () -> Boolean
    ): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs && coroutineContext.isActive) {
            waitWhilePaused()
            if (predicate()) return true
            delay(420)
        }
        return false
    }

    private suspend fun humanTypeIntoSelector(
        selector: String,
        text: String,
        flow: AutomationFlowEntity,
        webViewProvider: () -> WebView?,
        submitAfter: Boolean
    ): Boolean {
        val rng = Random(System.nanoTime())
        val selectorEsc = escapeJs(selector)
        val focusJs = """
            (function(){
              const el = document.querySelector('$selectorEsc');
              if (!el) return false;
              el.scrollIntoView({ behavior: 'smooth', block: 'center' });
              el.focus();
              el.value = '';
              el.dispatchEvent(new Event('input', { bubbles: true }));
              return true;
            })();
        """.trimIndent()
        val found = evalJsResult(webViewProvider(), focusJs) == "true"
        if (!found) return false
        delay(rng.nextLong(650, 1150))

        val minKey = flow.humanTypingMinMs.coerceIn(70, 320).toLong()
        val maxKey = flow.humanTypingMaxMs.coerceAtLeast(minKey.toInt() + 45).toLong()
        for (ch in text) {
            if (!coroutineContext.isActive) return true
            waitWhilePaused()
            val chEsc = escapeJs(ch.toString())
            val typeCharJs = """
                (function(){
                  const el = document.querySelector('$selectorEsc') || document.activeElement;
                  if (!el) return;
                  el.value = (el.value || '') + '$chEsc';
                  el.dispatchEvent(new Event('input', { bubbles: true }));
                })();
            """.trimIndent()
            evalJs(webViewProvider(), typeCharJs)
            val charDelay = when {
                ch == ' ' -> rng.nextLong(190, 410)
                rng.nextInt(100) < 12 -> rng.nextLong(maxKey, maxKey + 180)
                else -> rng.nextLong(minKey, maxKey)
            }
            delay(charDelay)
        }

        if (submitAfter) {
            delay(rng.nextLong(750, 1350))
            val submitJs = """
                (function(){
                  const el = document.querySelector('$selectorEsc') || document.activeElement;
                  if (!el) return;
                  el.dispatchEvent(new Event('change', { bubbles: true }));
                  const btn = document.querySelector('button[type="submit"], button[aria-label="Search"], button[aria-label="Google Search"], input[type="submit"]');
                  if (el.form) { el.form.submit(); }
                  else if (btn) { btn.click(); }
                  else {
                    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true }));
                  }
                })();
            """.trimIndent()
            evalJs(webViewProvider(), submitJs)
        }
        return true
    }

    private suspend fun clickRandomInternalLink(webView: WebView?) {
        val js = """
            (function(){
              const curHost = location.hostname;
              const links = Array.from(document.querySelectorAll('a[href]')).filter(a => {
                const rect = a.getBoundingClientRect();
                const href = a.getAttribute('href') || '';
                if (rect.width < 20 || rect.height < 10) return false;
                if (href.startsWith('#') || href.startsWith('javascript:')) return false;
                try {
                  const u = new URL(a.href, location.href);
                  return u.hostname === curHost;
                } catch(_) { return false; }
              });
              if (links.length === 0) return false;
              const pick = links[Math.floor(Math.random() * Math.min(links.length, 12))];
              try { pick.removeAttribute('target'); } catch(_) {}
              const r = pick.getBoundingClientRect();
              const startY = window.scrollY || 0;
              const desiredTop = Math.max(0, startY + r.top - (window.innerHeight * 0.42));
              const dist = desiredTop - startY;
              const dur = 750;
              const t0 = performance.now();
              (function anim(now){
                const p = Math.min(1, (now - t0) / dur);
                const e = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
                window.scrollTo(0, startY + dist * e);
                if (p < 1) requestAnimationFrame(anim);
              })(t0);
              setTimeout(() => pick.click(), 1350);
              return true;
            })();
        """.trimIndent()
        evalJs(webView, js)
    }

    private fun updateStatus(
        flowName: String,
        loop: Int,
        totalLoops: Int,
        stepIndex: Int,
        totalSteps: Int,
        status: String,
        scrollStyleLabel: String = "",
        remainingSeconds: Int? = null
    ) {
        _state.value = _state.value.copy(
            isRunning = true,
            isPaused = paused,
            flowName = flowName,
            loopIndex = loop,
            totalLoops = totalLoops,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            statusText = status,
            remainingSeconds = remainingSeconds ?: _state.value.remainingSeconds,
            scrollStyleLabel = scrollStyleLabel,
            pagesScrolled = pagesScrolledCounter,
            captchasSolved = captchasSolvedCounter,
            captchaAlertText = currentCaptchaAlert
        )
    }

    private suspend fun evalJs(webView: WebView?, js: String) {
        if (webView == null) return
        withContext(Dispatchers.Main) {
            runCatching { webView.evaluateJavascript(js, null) }
        }
    }

    private suspend fun evalJsResult(webView: WebView?, js: String): String? {
        if (webView == null) return null
        return withTimeoutOrNull(3_500L) {
            withContext(Dispatchers.Main) {
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    runCatching {
                        webView.evaluateJavascript(js) { res ->
                            if (cont.isActive) cont.resumeWith(Result.success(res))
                        }
                    }.onFailure {
                        if (cont.isActive) cont.resumeWith(Result.success(null))
                    }
                }
            }
        }
    }

    private fun decodeJsString(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return raw.removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun describeStep(step: RpaStep): String {
        return when (step.type) {
            RpaActionType.GOOGLE_SEARCH_AND_BROWSE -> "Google search '${step.value}' (${step.durationSeconds}s)..."
            RpaActionType.HUMAN_READ_DURATION -> "Human reading page (${step.durationSeconds}s)..."
            RpaActionType.NAVIGATE -> "Opening ${step.target}"
            RpaActionType.HUMAN_WAIT -> "Human reading pause..."
            RpaActionType.HUMAN_SCROLL -> "Human scrolling (${step.scrollCycles}x)..."
            RpaActionType.HUMAN_CLICK -> "Human tapping '${step.target}'"
            RpaActionType.HUMAN_TYPE -> "Typing '${step.value.take(18)}'..."
            RpaActionType.CLICK_RANDOM_LINK -> "Exploring page link..."
            RpaActionType.CLEAR_COOKIES -> "Clearing session cookies..."
        }
    }

    private fun escapeJs(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
    }
}
