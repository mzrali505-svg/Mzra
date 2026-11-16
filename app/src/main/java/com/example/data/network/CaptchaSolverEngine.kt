package com.example.data.network

import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebView
import com.example.data.local.ProxyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

enum class CaptchaSolverProvider(val label: String, val endpointBase: String) {
    TOUCH_ONLY("Built-in Human Touch + Watchdog", ""),
    TWO_CAPTCHA("2Captcha Auto-Token API", "https://api.2captcha.com"),
    CAPSOLVER("CapSolver Auto-Token API", "https://api.capsolver.com"),
    ANTI_CAPTCHA("Anti-Captcha Auto-Token API", "https://api.anti-captcha.com")
}

enum class CaptchaType(val label: String) {
    NONE("None"),
    GOOGLE_SORRY_RECAPTCHA("Google Unusual Traffic reCAPTCHA"),
    RECAPTCHA_V2("Google reCAPTCHA v2"),
    CLOUDFLARE_TURNSTILE("Cloudflare Turnstile / Human Check"),
    HCAPTCHA("hCaptcha Challenge"),
    IMAGE_CHALLENGE("Interactive CAPTCHA Challenge")
}

data class CaptchaSolverConfig(
    val autoSolveEnabled: Boolean = true,
    val provider: CaptchaSolverProvider = CaptchaSolverProvider.TOUCH_ONLY,
    val apiKey: String = "",
    val rotateProxySessionOnGoogleBlock: Boolean = true,
    val interactiveWaitSeconds: Int = 8,
    val directOpenDomainOnCaptcha: Boolean = true
)

data class CaptchaDetectionResult(
    val detected: Boolean = false,
    val type: CaptchaType = CaptchaType.NONE,
    val pageUrl: String = "",
    val siteKey: String = "",
    val dataS: String = "",
    val anchorCssX: Float = -1f,
    val anchorCssY: Float = -1f,
    val viewportWidth: Float = 0f,
    val viewportHeight: Float = 0f,
    val isChallengeGridOpen: Boolean = false
)

sealed class CaptchaSolveOutcome {
    data object NotPresent : CaptchaSolveOutcome()
    data class Solved(val method: String) : CaptchaSolveOutcome()
    data class RotatedProxySession(val newSsid: String) : CaptchaSolveOutcome()
    data class FallbackToDirectTarget(val type: CaptchaType, val reason: String) : CaptchaSolveOutcome()
}

object ProxySessionRotator {
    private val ssidRegex = Regex("(-ssid-)([A-Za-z0-9]{4,24})", RegexOption.IGNORE_CASE)
    private const val ALPHANUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun canRotateSession(proxy: ProxyEntity?): Boolean {
        if (proxy == null) return false
        return ssidRegex.containsMatchIn(proxy.username)
    }

    fun generateRandomSessionToken(length: Int = 10, rng: Random = Random.Default): String {
        return buildString(length) {
            repeat(length) {
                append(ALPHANUM[rng.nextInt(ALPHANUM.length)])
            }
        }
    }

    fun rotateUsernameSession(username: String, newSessionToken: String): String {
        if (!ssidRegex.containsMatchIn(username)) return username
        return ssidRegex.replace(username) { match ->
            "${match.groupValues[1]}$newSessionToken"
        }
    }

    fun withRotatedSession(proxy: ProxyEntity, rng: Random = Random.Default): Pair<ProxyEntity, String> {
        val token = generateRandomSessionToken(10, rng)
        val newUser = rotateUsernameSession(proxy.username, token)
        return proxy.copy(username = newUser) to token
    }
}

object CaptchaTokenClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun solveTokenTask(
        provider: CaptchaSolverProvider,
        apiKey: String,
        detection: CaptchaDetectionResult
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (provider == CaptchaSolverProvider.TOUCH_ONLY || cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Solver API key not configured"))
        }
        if (detection.siteKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing sitekey on CAPTCHA page"))
        }

        val taskJson = JSONObject().apply {
            when (detection.type) {
                CaptchaType.CLOUDFLARE_TURNSTILE -> {
                    val typeName = when (provider) {
                        CaptchaSolverProvider.CAPSOLVER -> "AntiTurnstileTaskProxyLess"
                        else -> "TurnstileTaskProxyless"
                    }
                    put("type", typeName)
                    put("websiteURL", detection.pageUrl)
                    put("websiteKey", detection.siteKey)
                }

                CaptchaType.HCAPTCHA -> {
                    put("type", "HCaptchaTaskProxyless")
                    put("websiteURL", detection.pageUrl)
                    put("websiteKey", detection.siteKey)
                }

                else -> {
                    val typeName = when (provider) {
                        CaptchaSolverProvider.CAPSOLVER -> "ReCaptchaV2TaskProxyLess"
                        else -> "RecaptchaV2TaskProxyless"
                    }
                    put("type", typeName)
                    put("websiteURL", detection.pageUrl)
                    put("websiteKey", detection.siteKey)
                    if (detection.dataS.isNotBlank()) {
                        put("recaptchaDataSValue", detection.dataS)
                    }
                }
            }
        }

        val createPayload = JSONObject().apply {
            put("clientKey", cleanKey)
            put("task", taskJson)
        }

        val createReq = Request.Builder()
            .url("${provider.endpointBase}/createTask")
            .post(createPayload.toString().toRequestBody(jsonMediaType))
            .build()

        val taskId = runCatching {
            httpClient.newCall(createReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("HTTP ${resp.code}: $raw")
                val obj = JSONObject(raw)
                val errId = obj.optInt("errorId", 0)
                if (errId != 0) {
                    error(obj.optString("errorDescription", obj.optString("errorCode", "createTask error $errId")))
                }
                obj.optString("taskId").ifBlank { obj.optLong("taskId", 0L).takeIf { it > 0 }?.toString() ?: "" }
            }
        }.getOrElse { return@withContext Result.failure(it) }

        if (taskId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Empty taskId from ${provider.label}"))
        }

        val pollPayload = JSONObject().apply {
            put("clientKey", cleanKey)
            put("taskId", taskId.toLongOrNull() ?: taskId)
        }

        val maxAttempts = 24
        for (attempt in 1..maxAttempts) {
            if (!coroutineContext.isActive) {
                return@withContext Result.failure(IllegalStateException("Cancelled"))
            }
            delay(3000)

            val pollReq = Request.Builder()
                .url("${provider.endpointBase}/getTaskResult")
                .post(pollPayload.toString().toRequestBody(jsonMediaType))
                .build()

            val pollObj = runCatching {
                httpClient.newCall(pollReq).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Poll HTTP ${resp.code}")
                    JSONObject(raw)
                }
            }.getOrElse {
                if (attempt == maxAttempts) return@withContext Result.failure(it)
                continue
            }

            val errId = pollObj.optInt("errorId", 0)
            if (errId != 0) {
                return@withContext Result.failure(
                    IllegalStateException(pollObj.optString("errorDescription", "Solver error $errId"))
                )
            }

            val status = pollObj.optString("status", "")
            if (status.equals("ready", ignoreCase = true)) {
                val solution = pollObj.optJSONObject("solution")
                val token = solution?.optString("gRecaptchaResponse").orEmpty()
                    .ifBlank { solution?.optString("token").orEmpty() }
                return@withContext if (token.isNotBlank()) {
                    Result.success(token)
                } else {
                    Result.failure(IllegalStateException("Empty token in solver response"))
                }
            }
        }

        Result.failure(IllegalStateException("Solver timed out"))
    }
}

object CaptchaSolverEngine {

    /**
     * Pure non-intrusive DOM inspection. NEVER scrolls or mutates the page so polling never yanks the viewport.
     */
    suspend fun detectCaptcha(webView: WebView?): CaptchaDetectionResult {
        if (webView == null) return CaptchaDetectionResult()
        val js = """
            (function(){
              const url = (window.location.href || '');
              const urlLow = url.toLowerCase();
              const bodyTxt = (document.body ? (document.body.innerText || '') : '').toLowerCase();

              const isGoogleSorry = urlLow.includes('google.com/sorry') || urlLow.includes('/sorry/index') ||
                !!document.querySelector('#captcha-form, form[action*="/sorry/index"]') ||
                bodyTxt.includes('unusual traffic from your computer network');

              const recaptchaAnchor = document.querySelector('iframe[src*="recaptcha/api2/anchor"], iframe[src*="recaptcha/enterprise/anchor"]');
              const recaptchaBframe = document.querySelector('iframe[src*="recaptcha/api2/bframe"], iframe[src*="recaptcha/enterprise/bframe"]');
              const recaptchaGateBox = document.querySelector('#captcha-form .g-recaptcha[data-sitekey], #recaptcha[data-sitekey], form[action*="/sorry"] [data-sitekey]');

              const cfFrame = document.querySelector('iframe[src*="challenges.cloudflare.com"]');
              const cfGate = document.querySelector('#challenge-form, #cf-please-wait, .cf-turnstile');
              const isCfInterstitial = (urlLow.includes('/cdn-cgi/challenge') || !!document.querySelector('#challenge-form, #cf-please-wait')) &&
                (bodyTxt.includes('verify you are human') || bodyTxt.includes('checking your browser') || bodyTxt.includes('just a moment'));

              const hFrame = document.querySelector('iframe[src*="hcaptcha.com/captcha"]');
              const hBox = document.querySelector('.h-captcha[data-sitekey]');

              function isVisibleRect(el) {
                if (!el) return false;
                const st = window.getComputedStyle(el);
                if (st.display === 'none' || st.visibility === 'hidden' || st.opacity === '0') return false;
                const r = el.getBoundingClientRect();
                return r.width > 60 && r.height > 30;
              }

              let type = 'NONE';
              let targetEl = null;
              let siteKey = '';
              let dataS = '';
              let gridOpen = false;

              if (isGoogleSorry) {
                type = 'GOOGLE_SORRY_RECAPTCHA';
                targetEl = recaptchaAnchor || recaptchaGateBox || document.querySelector('.g-recaptcha');
              } else if (isCfInterstitial || (cfFrame && isVisibleRect(cfFrame))) {
                type = 'CLOUDFLARE_TURNSTILE';
                targetEl = cfFrame || cfGate;
              } else if (hFrame && isVisibleRect(hFrame)) {
                type = 'HCAPTCHA';
                targetEl = hFrame || hBox;
              } else if (recaptchaAnchor && isVisibleRect(recaptchaAnchor)) {
                // Only treat inline reCAPTCHA as a blocking gate if page has minimal organic body content or an active puzzle
                const words = bodyTxt.trim().split(/\s+/).length;
                if (words < 120 || !!document.querySelector('#captcha-form, [action*="captcha" i]')) {
                  type = 'RECAPTCHA_V2';
                  targetEl = recaptchaAnchor;
                }
              }

              if (type === 'NONE') return JSON.stringify({ detected: false });

              if (recaptchaBframe) {
                const br = recaptchaBframe.getBoundingClientRect();
                const bst = window.getComputedStyle(recaptchaBframe);
                if (bst.display !== 'none' && bst.visibility !== 'hidden' && br.width > 140 && br.height > 140) {
                  gridOpen = true;
                }
              }

              const anyKeyBox = recaptchaGateBox || document.querySelector('.g-recaptcha[data-sitekey], .cf-turnstile[data-sitekey], .h-captcha[data-sitekey]');
              if (anyKeyBox) {
                siteKey = anyKeyBox.getAttribute('data-sitekey') || '';
                dataS = anyKeyBox.getAttribute('data-s') || '';
              }
              if (!siteKey) {
                const ifr = recaptchaAnchor || cfFrame || hFrame;
                if (ifr && ifr.src) {
                  try {
                    const u = new URL(ifr.src);
                    siteKey = u.searchParams.get('k') || u.searchParams.get('sitekey') || '';
                    if (!dataS) dataS = u.searchParams.get('s') || '';
                  } catch(_) {}
                }
              }

              let cx = -1, cy = -1;
              if (targetEl) {
                const r = targetEl.getBoundingClientRect();
                if (r.width > 20 && r.height > 20) {
                  cx = r.left + Math.min(34, Math.max(18, r.width * 0.12));
                  cy = r.top + (r.height * 0.5);
                }
                window.__cdCaptchaTarget = targetEl;
              }

              return JSON.stringify({
                detected: true,
                type: type,
                url: url,
                siteKey: siteKey,
                dataS: dataS,
                cx: cx,
                cy: cy,
                vw: window.innerWidth || 0,
                vh: window.innerHeight || 0,
                gridOpen: gridOpen
              });
            })();
        """.trimIndent()

        val rawJson = decodeJsString(evalJsResult(webView, js))
        if (rawJson.isBlank()) return CaptchaDetectionResult()
        return runCatching {
            val obj = JSONObject(rawJson)
            if (!obj.optBoolean("detected", false)) {
                CaptchaDetectionResult(detected = false)
            } else {
                val typeStr = obj.optString("type", "NONE")
                val cType = runCatching { CaptchaType.valueOf(typeStr) }.getOrDefault(CaptchaType.IMAGE_CHALLENGE)
                CaptchaDetectionResult(
                    detected = true,
                    type = cType,
                    pageUrl = obj.optString("url", ""),
                    siteKey = obj.optString("siteKey", ""),
                    dataS = obj.optString("dataS", ""),
                    anchorCssX = obj.optDouble("cx", -1.0).toFloat(),
                    anchorCssY = obj.optDouble("cy", -1.0).toFloat(),
                    viewportWidth = obj.optDouble("vw", 0.0).toFloat(),
                    viewportHeight = obj.optDouble("vh", 0.0).toFloat(),
                    isChallengeGridOpen = obj.optBoolean("gridOpen", false)
                )
            }
        }.getOrDefault(CaptchaDetectionResult())
    }

    /**
     * Makes up to 3 bounded, non-blocking tries to clear a blocking CAPTCHA:
     *   Try 1: Scroll widget into view once & perform realistic MotionEvent checkbox tap.
     *   Try 2: Use configured token solver API (2Captcha/CapSolver/Anti-Captcha) OR retry touch / sticky IP rotation.
     *   Try 3: Brief interactive watchdog window in case user taps the challenge directly.
     * If still blocked after 3 tries, returns FallbackToDirectTarget so caller can open target site directly.
     */
    suspend fun attemptSolveUpTo3Tries(
        webView: WebView?,
        config: CaptchaSolverConfig,
        activeProxy: ProxyEntity?,
        allowSessionRotation: Boolean,
        onStatus: suspend (String) -> Unit,
        onRotateProxyRequested: (suspend (ProxyEntity, String) -> Boolean)? = null
    ): CaptchaSolveOutcome {
        val initial = detectCaptcha(webView)
        if (!initial.detected) return CaptchaSolveOutcome.NotPresent
        if (!config.autoSolveEnabled) {
            return CaptchaSolveOutcome.FallbackToDirectTarget(initial.type, "Auto-solver disabled")
        }

        val hasTokenApi = config.provider != CaptchaSolverProvider.TOUCH_ONLY && config.apiKey.isNotBlank()
        if (config.directOpenDomainOnCaptcha && !hasTokenApi) {
            onStatus("CAPTCHA detected — opening target domain directly...")
            return CaptchaSolveOutcome.FallbackToDirectTarget(
                type = initial.type,
                reason = "CAPTCHA detected — opened target domain directly"
            )
        }

        val rng = Random(System.nanoTime())

        // TRY 1: Center widget once & perform humanized MotionEvent checkbox tap
        onStatus("CAPTCHA try 1/3 (${initial.type.label}): human checkbox tap...")
        scrollCaptchaTargetOnce(webView)
        delay(rng.nextLong(400L, 700L))
        val centered = detectCaptcha(webView)
        if (!centered.detected) return CaptchaSolveOutcome.Solved("Challenge Cleared")

        if (!centered.isChallengeGridOpen && centered.anchorCssX > 0f && centered.anchorCssY > 0f) {
            performHumanTouchTap(
                webView = webView,
                cssX = centered.anchorCssX,
                cssY = centered.anchorCssY,
                viewportWidth = centered.viewportWidth,
                viewportHeight = centered.viewportHeight,
                rng = rng
            )
            delay(rng.nextLong(2400L, 3200L))
            val afterTry1 = detectCaptcha(webView)
            if (!afterTry1.detected) {
                return CaptchaSolveOutcome.Solved("Human Touch Pass")
            }
        }

        // TRY 2: Solver API token injection (if configured) OR secondary touch / sticky proxy session switch
        val stateForTry2 = detectCaptcha(webView)
        if (!stateForTry2.detected) return CaptchaSolveOutcome.Solved("Challenge Cleared")

        if (config.provider != CaptchaSolverProvider.TOUCH_ONLY &&
            config.apiKey.isNotBlank() &&
            stateForTry2.siteKey.isNotBlank()
        ) {
            onStatus("CAPTCHA try 2/3: solving via ${config.provider.label}...")
            val tokenResult = CaptchaTokenClient.solveTokenTask(config.provider, config.apiKey, stateForTry2)
            val token = tokenResult.getOrNull()
            if (!token.isNullOrBlank()) {
                injectSolvedToken(webView, token)
                delay(2800L)
                val afterToken = detectCaptcha(webView)
                if (!afterToken.detected) {
                    return CaptchaSolveOutcome.Solved(config.provider.label)
                }
            }
        } else if (!stateForTry2.isChallengeGridOpen && stateForTry2.anchorCssX > 0f && stateForTry2.anchorCssY > 0f) {
            onStatus("CAPTCHA try 2/3: retrying checkbox tap...")
            performHumanTouchTap(
                webView = webView,
                cssX = stateForTry2.anchorCssX,
                cssY = stateForTry2.anchorCssY,
                viewportWidth = stateForTry2.viewportWidth,
                viewportHeight = stateForTry2.viewportHeight,
                rng = rng
            )
            delay(2200L)
            if (!detectCaptcha(webView).detected) {
                return CaptchaSolveOutcome.Solved("Human Touch Retry Pass")
            }
        }

        // TRY 3: Rotate sticky proxy -ssid- once (only if caller allows a session retry) OR brief manual tap grace window
        val stateForTry3 = detectCaptcha(webView)
        if (!stateForTry3.detected) return CaptchaSolveOutcome.Solved("Challenge Cleared")

        if (allowSessionRotation &&
            config.rotateProxySessionOnGoogleBlock &&
            stateForTry3.type == CaptchaType.GOOGLE_SORRY_RECAPTCHA &&
            ProxySessionRotator.canRotateSession(activeProxy) &&
            onRotateProxyRequested != null
        ) {
            val (rotatedProxy, newSsid) = ProxySessionRotator.withRotatedSession(activeProxy!!, rng)
            onStatus("CAPTCHA try 3/3: rotating sticky session to $newSsid...")
            val switched = onRotateProxyRequested(rotatedProxy, newSsid)
            if (switched) {
                return CaptchaSolveOutcome.RotatedProxySession(newSsid)
            }
        }

        val graceSec = config.interactiveWaitSeconds.coerceIn(4, 15)
        onStatus("CAPTCHA try 3/3: waiting ${graceSec}s for tap before fallback...")
        val deadline = System.currentTimeMillis() + graceSec * 1000L
        while (System.currentTimeMillis() < deadline && coroutineContext.isActive) {
            delay(1000L)
            if (!detectCaptcha(webView).detected) {
                return CaptchaSolveOutcome.Solved("Manual/Interactive Pass")
            }
        }

        return CaptchaSolveOutcome.FallbackToDirectTarget(
            type = stateForTry3.type,
            reason = "CAPTCHA persisted after 3 tries"
        )
    }

    private suspend fun scrollCaptchaTargetOnce(webView: WebView?) {
        if (webView == null) return
        val js = """
            (function(){
              if (window.__cdCaptchaTarget) {
                try { window.__cdCaptchaTarget.scrollIntoView({ behavior: 'auto', block: 'center' }); } catch(_) {}
              }
            })();
        """.trimIndent()
        withContext(Dispatchers.Main) {
            runCatching { webView.evaluateJavascript(js, null) }
        }
    }

    private suspend fun performHumanTouchTap(
        webView: WebView?,
        cssX: Float,
        cssY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        rng: Random
    ) {
        if (webView == null || viewportWidth <= 0f || viewportHeight <= 0f) return
        val jitterX = (rng.nextFloat() - 0.5f) * 5f
        val jitterY = (rng.nextFloat() - 0.5f) * 5f
        val clampedCssX = (cssX + jitterX).coerceIn(8f, viewportWidth - 8f)
        val clampedCssY = (cssY + jitterY).coerceIn(8f, viewportHeight - 8f)

        withContext(Dispatchers.Main) {
            if (webView.width <= 0 || webView.height <= 0) return@withContext
            val targetX = ((clampedCssX / viewportWidth) * webView.width).coerceIn(4f, (webView.width - 4).toFloat())
            val targetY = ((clampedCssY / viewportHeight) * webView.height).coerceIn(4f, (webView.height - 4).toFloat())

            val downTime = SystemClock.uptimeMillis()
            val downEv = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, targetX, targetY, 0)
            webView.dispatchTouchEvent(downEv)
            downEv.recycle()

            delay(rng.nextLong(60, 115))
            val upTime = SystemClock.uptimeMillis()
            val upEv = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, targetX, targetY, 0)
            webView.dispatchTouchEvent(upEv)
            upEv.recycle()
        }
    }

    private suspend fun injectSolvedToken(webView: WebView?, token: String) {
        if (webView == null) return
        val escToken = token
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "")
            .replace("\r", "")

        val js = """
            (function(){
              const token = '$escToken';
              const fields = document.querySelectorAll('textarea#g-recaptcha-response, textarea[name="g-recaptcha-response"], input[name="cf-turnstile-response"], textarea[name="h-captcha-response"], input[name="h-captcha-response"]');
              fields.forEach(f => {
                f.value = token;
                f.innerHTML = token;
                f.dispatchEvent(new Event('input', { bubbles: true }));
                f.dispatchEvent(new Event('change', { bubbles: true }));
              });

              try {
                if (window.___grecaptcha_cfg && window.___grecaptcha_cfg.clients) {
                  Object.values(window.___grecaptcha_cfg.clients).forEach(client => {
                    const stack = [client];
                    const visited = new Set();
                    while (stack.length > 0) {
                      const obj = stack.pop();
                      if (!obj || typeof obj !== 'object' || visited.has(obj)) continue;
                      visited.add(obj);
                      if (typeof obj.callback === 'function') {
                        try { obj.callback(token); } catch(_) {}
                      } else if (typeof obj.callback === 'string' && typeof window[obj.callback] === 'function') {
                        try { window[obj.callback](token); } catch(_) {}
                      }
                      Object.values(obj).forEach(v => {
                        if (v && typeof v === 'object') stack.push(v);
                      });
                    }
                  });
                }
              } catch(_) {}

              const captchaForm = document.querySelector('#captcha-form, form[action*="/sorry"], #challenge-form');
              if (captchaForm) {
                try { captchaForm.submit(); } catch(_) {}
              }
              return true;
            })();
        """.trimIndent()

        withContext(Dispatchers.Main) {
            runCatching { webView.evaluateJavascript(js, null) }
        }
    }

    private suspend fun evalJsResult(webView: WebView?, js: String): String? {
        if (webView == null) return null
        return withTimeoutOrNull(3_500L) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
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
}
