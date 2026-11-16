package com.example

import com.example.browser.AutomationPresets
import com.example.browser.FingerprintScriptBuilder
import com.example.browser.SerpDomainMatcher
import com.example.data.model.FingerprintPresets
import com.example.data.model.HumanizeBehaviorResolver
import com.example.data.model.ProxyType
import com.example.data.network.CaptchaSolverConfig
import com.example.data.network.CaptchaSolverProvider
import com.example.data.network.ProxyChecker
import com.example.data.network.ProxySessionRotator
import com.example.data.repository.CloakDeckRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun bulkProxyParser_supportsStandardFormats() {
        val raw = """
            # Comment line
            socks5://alice:secret@192.0.2.10:1080
            198.51.100.4:8080:bob:pass123
            203.0.113.9:3128
        """.trimIndent()

        val parsed = ProxyChecker.parseBulkProxies(raw, ProxyType.HTTP)
        assertEquals(3, parsed.size)

        assertEquals(ProxyType.SOCKS5.name, parsed[0].type)
        assertEquals("192.0.2.10", parsed[0].host)
        assertEquals(1080, parsed[0].port)
        assertEquals("alice", parsed[0].username)
        assertEquals("secret", parsed[0].password)

        assertEquals(ProxyType.HTTP.name, parsed[1].type)
        assertEquals("198.51.100.4", parsed[1].host)
        assertEquals(8080, parsed[1].port)
        assertEquals("bob", parsed[1].username)
        assertEquals("pass123", parsed[1].password)

        assertEquals("203.0.113.9", parsed[2].host)
        assertEquals(3128, parsed[2].port)
    }

    @Test
    fun bulkProxyParser_parsesPerProxySecondsAndKeywords() {
        val raw = """
            socks5://user1:pass1@192.0.2.10:1080|120|best laptops|example.com
            198.51.100.4:8080:user2:pass2|20|cloud tools
        """.trimIndent()

        val parsed = ProxyChecker.parseBulkProxies(raw, ProxyType.HTTP)
        assertEquals(2, parsed.size)

        assertTrue(parsed[0].humanizeEnabled)
        assertEquals(120, parsed[0].humanizeDwellSeconds)
        assertEquals("best laptops", parsed[0].humanizeKeyword)
        assertEquals("example.com", parsed[0].humanizeTargetDomain)

        assertTrue(parsed[1].humanizeEnabled)
        assertEquals(20, parsed[1].humanizeDwellSeconds)
        assertEquals("cloud tools", parsed[1].humanizeKeyword)
    }

    @Test
    fun profileGenerator_createsUniqueIsolatedFingerprints() {
        val p1 = FingerprintPresets.createRandomProfile(name = "Alpha")
        val p2 = FingerprintPresets.createRandomProfile(name = "Beta")

        assertNotEquals(p1.webViewProfileName, p2.webViewProfileName)
        assertNotEquals(p1.canvasSeed, p2.canvasSeed)
        assertNotEquals(p1.audioSeed, p2.audioSeed)
    }

    @Test
    fun fingerprintScriptBuilder_embedsAllSpoofedVectors() {
        val profile = FingerprintPresets.createRandomProfile(name = "TestNode").copy(
            videoInputsCount = 1,
            audioInputsCount = 2,
            audioOutputsCount = 1,
            geolocationMode = "BLOCK"
        )
        val js = FingerprintScriptBuilder.buildDocumentStartScript(profile, activeExitIp = "198.51.100.55")

        assertTrue(js.contains(profile.userAgent))
        assertTrue(js.contains(profile.timezone))
        assertTrue(js.contains("198.51.100.55"))
        assertTrue(js.contains("RTCPeerConnection"))
        assertTrue(js.contains("toDataURL"))
        assertTrue(js.contains("37445"))
        assertTrue(js.contains("videoIn: 1"))
        assertTrue(js.contains("audioIn: 2"))
        assertTrue(js.contains("permissions.query"))
        assertTrue(js.contains("'BLOCK'"))
        assertFalse(js.contains("undefined"))
    }

    @Test
    fun automationPresets_roundTripEncodeDecode() {
        val starters = AutomationPresets.defaultStarterFlows()
        assertTrue(starters.size >= 4)
        starters.forEach { flow ->
            val decoded = AutomationPresets.decodeSteps(flow.stepsJson)
            assertTrue(decoded.isNotEmpty())
        }
    }

    @Test
    fun niceproxy_tenNodeBatch_parsesAllSessionCredentials() {
        val raw = """
            niceproxy.io:17521:goodday_plsK-country-US-ssid-ro1q7BBEOn-sst-30:mzrali
            niceproxy.io:17521:goodday_plsK-country-US-ssid-Ckbo5WniY5-sst-30:mzrali
            niceproxy.io:17521:goodday_plsK-country-US-ssid-hzoU7urfwL-sst-30:mzrali
            niceproxy.io:17521:goodday_plsK-country-US-ssid-w7Be4KPxzK-sst-30:mzrali
            niceproxy.io:17521:goodday_plsK-country-US-ssid-xhmT3hqriu-sst-30:mzrali
            niceproxy.io:17521:goodday_plsK-country-US-ssid-L8yL84Njkv-sst-30:mzrali
            niceproxy.io:17521:goodday_plsK-country-US-ssid-Juva1kWI2Y-sst-30:mzrali
            niceproxy.io:17521:goodday_plsK-country-US-ssid-GD2PxIt8SM-sst-30:mzrali
            niceproxy.io:17521:goodday_plsK-country-US-ssid-bLosr3PHuv-sst-30:mzrali
            niceproxy.io:17521:goodday_plsK-country-US-ssid-WdjAc2JUzM-sst-30:mzrali
        """.trimIndent()

        val parsed = ProxyChecker.parseBulkProxies(raw, ProxyType.AUTO)
        assertEquals(10, parsed.size)
        assertEquals(10, parsed.map { it.username }.distinct().size)
        parsed.forEach { node ->
            assertEquals("niceproxy.io", node.host)
            assertEquals(17521, node.port)
            assertEquals("mzrali", node.password)
            assertEquals(ProxyType.AUTO.name, node.type)
        }
    }

    @Test
    fun serpDomainMatcher_normalizesFullUrlsAndWwwPrefixes() {
        assertEquals("example.com", SerpDomainMatcher.normalizeHostHint("https://www.Example.com/blog/post?x=1"))
        assertEquals("sub.myshop.com", SerpDomainMatcher.normalizeHostHint("http://sub.myshop.com/"))
        assertEquals("example.com/blog", SerpDomainMatcher.normalizeFullHint("https://www.example.com/blog/"))
    }

    @Test
    fun humanizeBehaviorResolver_generatesDistinctScrollPersonasAcrossProfiles() {
        val personas = (1L..10L).map { idx ->
            val profile = FingerprintPresets.createRandomProfile("Node $idx").copy(id = idx)
            HumanizeBehaviorResolver.resolve(profile)
        }
        assertTrue(personas.map { it.style }.distinct().size >= 5)
        assertEquals(10, personas.map { "${it.style}:${it.paceMultiplier}:${it.amplitudeMultiplier}" }.distinct().size)
        personas.forEach { persona ->
            assertTrue("Expected calm human pace >= 0.90x, was ${persona.paceMultiplier}", persona.paceMultiplier >= 0.90f)
            assertTrue("Expected natural reading pause >= 2200ms, was ${persona.pauseMinMs}", persona.pauseMinMs >= 2200L)
        }
    }

    @Test
    fun proxySessionRotator_rotatesStickyNiceProxySsidCleanly() {
        val original = "goodday_plsK-country-US-ssid-ro1q7BBEOn-sst-30"
        val proxy = ProxyChecker.parseBulkProxies("niceproxy.io:17521:$original:mzrali", ProxyType.AUTO).first()
        assertTrue(ProxySessionRotator.canRotateSession(proxy))

        val (rotated, newSsid) = ProxySessionRotator.withRotatedSession(proxy)
        assertEquals(10, newSsid.length)
        assertNotEquals(original, rotated.username)
        assertTrue(rotated.username.startsWith("goodday_plsK-country-US-ssid-"))
        assertTrue(rotated.username.endsWith("-sst-30"))
        assertTrue(rotated.username.contains(newSsid))
    }

    @Test
    fun captchaSolverConfig_defaultsAndProvidersAreValid() {
        val cfg = CaptchaSolverConfig()
        assertTrue(cfg.autoSolveEnabled)
        assertTrue(cfg.rotateProxySessionOnGoogleBlock)
        assertEquals(CaptchaSolverProvider.TOUCH_ONLY, cfg.provider)
        assertTrue(CaptchaSolverProvider.TWO_CAPTCHA.endpointBase.startsWith("https://"))
        assertTrue(CaptchaSolverProvider.CAPSOLVER.endpointBase.startsWith("https://"))
    }

    @Test
    fun profileWebViewManager_extractsVersionAndClientHintSpecs() {
        val winProfile = FingerprintPresets.createRandomProfile(
            name = "WinNode",
            preferredBlueprint = FingerprintPresets.deviceBlueprints.first { it.id == "win11_rtx4070" }
        )
        assertEquals("132", com.example.browser.ProfileWebViewManager.extractMajorChromeVersion(winProfile.userAgent))
        val winHint = com.example.browser.ProfileWebViewManager.resolveOsClientHintSpec(winProfile)
        assertEquals("Windows", winHint.platform)
        assertEquals("15.0.0", winHint.platformVersion)
        assertFalse(winHint.isMobile)

        val androidProfile = FingerprintPresets.createRandomProfile(
            name = "AndroidNode",
            preferredBlueprint = FingerprintPresets.deviceBlueprints.first { it.id == "android_s25" }
        )
        val androidHint = com.example.browser.ProfileWebViewManager.resolveOsClientHintSpec(androidProfile)
        assertEquals("Android", androidHint.platform)
        assertTrue(androidHint.isMobile)

        val headers = com.example.browser.ProfileWebViewManager.buildRequestHeaders(winProfile)
        assertTrue(headers.containsKey("Accept-Language"))
    }

    @Test
    fun fingerprintPresets_defaultsAndFleetsArePredominantlyMobile() {
        val randomProfiles = (1..25).map { FingerprintPresets.createRandomProfile("MobileCheck $it") }
        assertTrue(randomProfiles.all { prof ->
            com.example.data.model.OsPlatform.valueOf(prof.osPlatform).isMobile
        })

        val fleet = (0 until 100).map { FingerprintPresets.fleetBlueprintForIndex(it) }
        val mobileCount = fleet.count { it.osPlatform.isMobile }
        val desktopCount = fleet.size - mobileCount
        assertEquals(90, mobileCount)
        assertEquals(10, desktopCount)
    }

    @Test
    fun localProxyBridge_bindsAndReleasesLoopbackPortCleanly() {
        val bridge = com.example.data.network.LocalProxyBridge()
        val dummyProxy = com.example.data.local.ProxyEntity(
            name = "AutoTest",
            type = ProxyType.AUTO.name,
            host = "127.0.0.1",
            port = 18080
        )
        val boundPort = bridge.start(dummyProxy)
        assertTrue("Expected valid loopback port > 0, got $boundPort", boundPort in 1..65535)
        bridge.shutdown()
        assertEquals(-1, bridge.localPort)
    }

    @Test
    fun uniqueFleetVariance_diversifiesUserAgentRamRomAndAntiLeakScript() {
        val fleetProfiles = (0 until 30).map { idx ->
            FingerprintPresets.createRandomProfile(
                name = "Order #$idx",
                preferredBlueprint = FingerprintPresets.fleetBlueprintForIndex(idx),
                uniqueHardwareVariance = true
            )
        }
        assertTrue("Expected multiple distinct User-Agents", fleetProfiles.map { it.userAgent }.distinct().size >= 5)
        assertTrue("Expected multiple RAM configurations", fleetProfiles.map { it.deviceMemory }.distinct().size >= 2)
        assertTrue("Expected multiple CPU configurations", fleetProfiles.map { it.hardwareConcurrency }.distinct().size >= 2)
        val roms = fleetProfiles.map { FingerprintPresets.computeSpoofedRomGb(it.audioSeed, it.webGlSeed) }.distinct()
        assertTrue("Expected multiple ROM storage tiers, got $roms", roms.size >= 3)

        val script = FingerprintScriptBuilder.buildDocumentStartScript(fleetProfiles.first(), activeExitIp = "203.0.113.77")
        assertTrue(script.contains("navigator.storage.estimate"))
        assertTrue(script.contains("onicecandidate"))
        assertTrue(script.contains("203.0.113.77"))
    }

    @Test
    fun commaDurationsParser_parsesAndClampsSequenceForBulkProfiles() {
        val parsed = CloakDeckRepository.parseCommaDurationsSeconds("20, 45; 60\n90 9999 1", fallbackSec = 30)
        assertEquals(listOf(20, 45, 60, 90, 3600, 3), parsed)

        val fallback = CloakDeckRepository.parseCommaDurationsSeconds("   ", fallbackSec = 55)
        assertEquals(listOf(55), fallback)

        // Verify directOpenDomainOnCaptcha default is enabled
        val cfg = CaptchaSolverConfig()
        assertTrue(cfg.directOpenDomainOnCaptcha)
    }

    @Test
    fun hundredPlusProfiles_maintainUniqueFingerprintsAndSeeds() {
        val count = 120
        val profiles = (0 until count).map { idx ->
            FingerprintPresets.createRandomProfile(
                name = "Bulk #$idx",
                preferredBlueprint = FingerprintPresets.fleetBlueprintForIndex(idx),
                uniqueHardwareVariance = true
            )
        }
        assertEquals(count, profiles.map { it.webViewProfileName }.distinct().size)
        assertEquals(count, profiles.map { it.canvasSeed }.distinct().size)
        assertEquals(count, profiles.map { it.webGlSeed }.distinct().size)
        assertEquals(count, profiles.map { it.audioSeed }.distinct().size)
        assertTrue(profiles.map { it.userAgent }.distinct().size >= 15)
        assertTrue(profiles.map { it.deviceModel }.distinct().size >= 15)
    }

    @Test
    fun localProxyBridge_startPoolHandlesRestartAndEmptyCleanly() {
        val bridge = com.example.data.network.LocalProxyBridge()
        assertEquals(-1, bridge.startPool(emptyList()))

        val nodes = (1..12).map { idx ->
            com.example.data.local.ProxyEntity(
                name = "PoolNode $idx",
                type = ProxyType.HTTP.name,
                host = "127.0.0.1",
                port = 19000 + idx
            )
        }
        val firstPort = bridge.startPool(nodes)
        assertTrue(firstPort in 1..65535)
        val secondPort = bridge.startPool(nodes.take(4))
        assertTrue(secondPort in 1..65535)

        bridge.shutdown()
        assertEquals(-1, bridge.localPort)
    }

    @Test
    fun bulkProxyParser_handlesSocks5hAndSpecialCharsInPasswords() {
        val raw = """
            socks5h://alice:p@ss:word@192.0.2.10:1080/
            198.51.100.4:8080:bob_user:pass:with:colons
        """.trimIndent()
        val parsed = ProxyChecker.parseBulkProxies(raw, ProxyType.AUTO)
        assertEquals(2, parsed.size)

        assertEquals(ProxyType.SOCKS5.name, parsed[0].type)
        assertEquals("192.0.2.10", parsed[0].host)
        assertEquals(1080, parsed[0].port)
        assertEquals("alice", parsed[0].username)
        assertEquals("p@ss:word", parsed[0].password)

        assertEquals("198.51.100.4", parsed[1].host)
        assertEquals(8080, parsed[1].port)
        assertEquals("bob_user", parsed[1].username)
        assertEquals("pass:with:colons", parsed[1].password)
    }

    @Test
    fun localProxyBridge_poolFailoverSkipsDeadNodeAndConnectsViaLiveNode() {
        // Live mock HTTP CONNECT proxy on loopback
        val mockUpstream = java.net.ServerSocket(0, 10, java.net.InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        val echoTarget = java.net.ServerSocket(0, 10, java.net.InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))

        val upstreamThread = Thread {
            try {
                val upClient = mockUpstream.accept()
                val reader = upClient.getInputStream()
                val out = upClient.getOutputStream()
                val buf = ByteArray(1024)
                reader.read(buf)
                out.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                out.flush()
                val targetSock = java.net.Socket("127.0.0.1", echoTarget.localPort)
                val t1 = Thread { runCatching { upClient.getInputStream().copyTo(targetSock.getOutputStream()) } }
                t1.start()
                runCatching { targetSock.getInputStream().copyTo(upClient.getOutputStream()) }
                upClient.close()
                targetSock.close()
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val targetThread = Thread {
            try {
                val s = echoTarget.accept()
                s.getOutputStream().write("PONG".toByteArray())
                s.getOutputStream().flush()
                s.close()
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val bridge = com.example.data.network.LocalProxyBridge()
        try {
            // Node 0 points to closed port 1 (dead), Node 1 points to live mock HTTP CONNECT proxy
            val deadNode = com.example.data.local.ProxyEntity(
                name = "Dead",
                type = ProxyType.HTTP.name,
                host = "127.0.0.1",
                port = 1
            )
            val liveNode = com.example.data.local.ProxyEntity(
                name = "Live",
                type = ProxyType.AUTO.name,
                host = "127.0.0.1",
                port = mockUpstream.localPort
            )
            val port = bridge.startPool(listOf(deadNode, liveNode))
            assertTrue(port in 1..65535)

            // Same port is reused on in-place pool update
            val reusedPort = bridge.startPool(listOf(deadNode, liveNode))
            assertEquals(port, reusedPort)

            val client = java.net.Socket("127.0.0.1", port)
            client.soTimeout = 5000
            client.getOutputStream().write("CONNECT 127.0.0.1:${echoTarget.localPort} HTTP/1.1\r\nHost: 127.0.0.1:${echoTarget.localPort}\r\n\r\n".toByteArray())
            client.getOutputStream().flush()

            val resp = client.getInputStream().bufferedReader().readLine()
            assertTrue("Expected 200 Connection Established via pool failover, got: $resp", resp.contains("200"))
            client.close()
        } finally {
            bridge.shutdown()
            runCatching { mockUpstream.close() }
            runCatching { echoTarget.close() }
            upstreamThread.interrupt()
            targetThread.interrupt()
        }
    }

    @Test
    fun humanizeScrollStyle_hasOverOneHundredTwentyConcreteOptionsAndBackwardCompatFromRaw() {
        assertTrue(
            "Expected at least 110 concrete humanized options, got ${com.example.data.model.HumanizeScrollStyle.concreteStyles.size}",
            com.example.data.model.HumanizeScrollStyle.concreteStyles.size >= 110
        )
        assertEquals(120, com.example.data.model.HumanizeScrollStyle.concreteStyles.size)
        assertEquals(121, com.example.data.model.HumanizeScrollStyle.entries.size)

        assertEquals(
            com.example.data.model.HumanizeScrollStyle.CAREFUL_READER,
            com.example.data.model.HumanizeScrollStyle.fromRaw("CAREFUL_READER")
        )
        assertEquals(
            com.example.data.model.HumanizeScrollStyle.AUTO,
            com.example.data.model.HumanizeScrollStyle.fromRaw("auto")
        )
        assertEquals(
            com.example.data.model.HumanizeScrollStyle.DEEP_DWELL_ZEN,
            com.example.data.model.HumanizeScrollStyle.fromRaw("DEEP_DWELL_ZEN")
        )
        assertEquals(
            com.example.data.model.HumanizeScrollStyle.AUTO,
            com.example.data.model.HumanizeScrollStyle.fromRaw("NON_EXISTENT_PRESET")
        )
    }

    @Test
    fun humanizeBehaviorResolver_assignsOverHundredDistinctPresetsAndCalmPacingAcrossProfiles() {
        val personas = (1L..120L).map { idx ->
            val profile = FingerprintPresets.createRandomProfile("PresetNode $idx").copy(id = idx)
            HumanizeBehaviorResolver.resolve(profile)
        }
        assertEquals(120, personas.map { it.style }.distinct().size)
        personas.forEach { persona ->
            assertTrue("Expected calm pace >= 0.90x, got ${persona.paceMultiplier}", persona.paceMultiplier >= 0.90f)
            assertTrue("Expected pauseMinMs >= 2200L, got ${persona.pauseMinMs}", persona.pauseMinMs >= 2200L)
            assertTrue("Expected pauseMaxMs > pauseMinMs", persona.pauseMaxMs > persona.pauseMinMs)
        }
    }

    @Test
    fun automationTiming_allocatesFullDwellForSingleAndMultiPageSessions() {
        assertEquals(listOf(120), com.example.browser.AutomationTiming.computePageDwellAllocation(120, 1))
        assertEquals(listOf(60, 60), com.example.browser.AutomationTiming.computePageDwellAllocation(120, 2))
        assertEquals(listOf(45), com.example.browser.AutomationTiming.computePageDwellAllocation(45, 1))
        assertEquals(listOf(185), com.example.browser.AutomationTiming.computePageDwellAllocation(185, 1))
    }

    @Test
    fun localProxyBridge_sequentialStartSwitchesUpstreamCleanly() {
        val bridge = com.example.data.network.LocalProxyBridge()
        try {
            val proxy1 = com.example.data.local.ProxyEntity(
                name = "Proxy1",
                type = ProxyType.HTTP.name,
                host = "192.0.2.1",
                port = 8080,
                username = "user1"
            )
            val proxy2 = com.example.data.local.ProxyEntity(
                name = "Proxy2",
                type = ProxyType.HTTP.name,
                host = "192.0.2.2",
                port = 8081,
                username = "user2"
            )
            val port1 = bridge.start(proxy1)
            val port2 = bridge.start(proxy2)
            assertTrue(port1 in 1..65535)
            assertEquals(port1, port2)
        } finally {
            bridge.shutdown()
        }
    }
}

