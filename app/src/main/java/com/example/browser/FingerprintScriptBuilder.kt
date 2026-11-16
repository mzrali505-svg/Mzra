package com.example.browser

import com.example.data.local.BrowserProfileEntity
import com.example.data.model.GeolocationMode
import com.example.data.model.OsPlatform
import com.example.data.model.SpoofMode
import com.example.data.model.WebGlMode
import com.example.data.model.WebRtcMode

object FingerprintScriptBuilder {

    fun buildDocumentStartScript(profile: BrowserProfileEntity, activeExitIp: String? = null): String {
        val os = runCatching { OsPlatform.valueOf(profile.osPlatform) }.getOrDefault(OsPlatform.ANDROID)
        val languagesArray = profile.languagesCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(profile.language) }
            .joinToString(",") { "'${escapeJs(it)}'" }

        val fontsArray = profile.fontsListCsv
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .joinToString(",") { "'${escapeJs(it)}'" }

        val uaEsc = escapeJs(profile.userAgent)
        val appVersionEsc = escapeJs(profile.userAgent.removePrefix("Mozilla/"))
        val platformEsc = escapeJs(profile.platformString)
        val vendorEsc = escapeJs(profile.vendorString)
        val langEsc = escapeJs(profile.language)
        val tzEsc = escapeJs(profile.timezone)
        val dntVal = if (profile.doNotTrack) "'1'" else "null"
        val glVendorEsc = escapeJs(profile.webGlVendor)
        val glRendererEsc = escapeJs(profile.webGlRenderer)
        val glShadingEsc = escapeJs(profile.webGlShadingLang)
        val fakeIpEsc = escapeJs(activeExitIp?.takeIf { it.isNotBlank() } ?: profile.webRtcFakeIp)

        val chPlatform = when (os) {
            OsPlatform.WINDOWS, OsPlatform.WINDOWS_11 -> "Windows"
            OsPlatform.MACOS, OsPlatform.MACOS_M1, OsPlatform.MACOS_M2, OsPlatform.MACOS_M3 -> "macOS"
            OsPlatform.ANDROID -> "Android"
            OsPlatform.IOS -> "iOS"
            OsPlatform.LINUX -> "Linux"
        }

        val fullUaVer = Regex("""(?:Chrome|Edg|CriOS)/(\d+\.\d+\.\d+\.\d+)""")
            .find(profile.userAgent)?.groupValues?.getOrNull(1) ?: "132.0.6834.122"
        val majorUaVer = fullUaVer.substringBefore(".")
        val romGb = com.example.data.model.FingerprintPresets.computeSpoofedRomGb(profile.audioSeed, profile.webGlSeed)
        val romUsedMb = com.example.data.model.FingerprintPresets.computeSpoofedRomUsedMb(profile.canvasSeed)
        val romBytes = romGb.toLong() * 1024L * 1024L * 1024L
        val romUsedBytes = romUsedMb.toLong() * 1024L * 1024L

        return """
(function() {
  'use strict';
  if (window.__cloakDeckInjected) return;
  window.__cloakDeckInjected = true;

  const cfg = {
    ua: '$uaEsc',
    appVersion: '$appVersionEsc',
    platform: '$platformEsc',
    vendor: '$vendorEsc',
    language: '$langEsc',
    languages: [$languagesArray],
    dnt: $dntVal,
    cores: ${profile.hardwareConcurrency},
    memory: ${profile.deviceMemory},
    romQuota: $romBytes,
    romUsage: $romUsedBytes,
    touchPoints: ${profile.maxTouchPoints},
    isMobile: ${os.isMobile},
    chPlatform: '$chPlatform',
    uaMajor: '$majorUaVer',
    uaFull: '$fullUaVer',
    deviceModel: '${escapeJs(profile.deviceModel)}',
    screenW: ${profile.screenWidth},
    screenH: ${profile.screenHeight},
    dpr: ${profile.devicePixelRatio},
    colorDepth: ${profile.colorDepth},
    tz: '$tzEsc',
    geoMode: '${profile.geolocationMode}',
    lat: ${profile.latitude},
    lon: ${profile.longitude},
    geoAcc: ${profile.geoAccuracy},
    batterySpoof: ${profile.batterySpoofEnabled},
    batteryLevel: ${profile.batteryLevel},
    batteryCharging: ${profile.batteryCharging},
    webRtcMode: '${profile.webRtcMode}',
    fakeIp: '$fakeIpEsc',
    canvasMode: '${profile.canvasMode}',
    canvasSeed: ${profile.canvasSeed},
    webGlImageMode: '${profile.webGlImageMode}',
    webGlMetaMode: '${profile.webGlMetadataMode}',
    glVendor: '$glVendorEsc',
    glRenderer: '$glRendererEsc',
    glMaxTex: ${profile.webGlMaxTextureSize},
    glMaxAttr: ${profile.webGlMaxVertexAttribs},
    glShading: '$glShadingEsc',
    webGlSeed: ${profile.webGlSeed},
    audioMode: '${profile.audioMode}',
    audioSeed: ${profile.audioSeed},
    domRectJitter: ${profile.enableUnicodeGlyphsAndDomRect},
    rectsSeed: ${profile.clientRectsSeed},
    fontMasking: ${profile.fontMasking},
    fonts: new Set([$fontsArray]),
    maskMedia: ${profile.maskMediaDevices},
    videoIn: ${profile.videoInputsCount},
    audioIn: ${profile.audioInputsCount},
    audioOut: ${profile.audioOutputsCount},
    allowPlugins: ${profile.allowVulnerablePlugins}
  };

  function mulberry32(seed) {
    let t = seed >>> 0;
    return function() {
      t += 0x6D2B79F5;
      let r = Math.imul(t ^ (t >>> 15), 1 | t);
      r ^= r + Math.imul(r ^ (r >>> 7), 61 | r);
      return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
    };
  }

  function defGetter(obj, prop, getter) {
    try {
      Object.defineProperty(obj, prop, {
        get: getter,
        configurable: true,
        enumerable: true
      });
    } catch (_) {}
  }

  // 1. Navigator & Client Hints Spoofing
  const navProto = Navigator.prototype;
  defGetter(navProto, 'userAgent', () => cfg.ua);
  defGetter(navProto, 'appVersion', () => cfg.appVersion);
  defGetter(navProto, 'platform', () => cfg.platform);
  defGetter(navProto, 'vendor', () => cfg.vendor);
  defGetter(navProto, 'hardwareConcurrency', () => cfg.cores);
  defGetter(navProto, 'deviceMemory', () => cfg.memory);
  defGetter(navProto, 'maxTouchPoints', () => cfg.touchPoints);
  defGetter(navProto, 'language', () => cfg.language);
  defGetter(navProto, 'languages', () => Object.freeze([...cfg.languages]));
  defGetter(navProto, 'doNotTrack', () => cfg.dnt);
  defGetter(navProto, 'webdriver', () => false);

  if (!cfg.allowPlugins) {
    defGetter(navProto, 'plugins', () => []);
    defGetter(navProto, 'mimeTypes', () => []);
  }

  const uaBrands = [
    { brand: 'Not A(Brand', version: '8' },
    { brand: 'Chromium', version: cfg.uaMajor },
    { brand: 'Google Chrome', version: cfg.uaMajor }
  ];
  const fakeUaData = {
    brands: uaBrands,
    mobile: cfg.isMobile,
    platform: cfg.chPlatform,
    getHighEntropyValues: async () => ({
      brands: uaBrands,
      mobile: cfg.isMobile,
      platform: cfg.chPlatform,
      platformVersion: cfg.isMobile ? '15.0.0' : '15.0.0',
      architecture: cfg.isMobile ? 'arm' : 'x86',
      bitness: '64',
      model: cfg.isMobile ? cfg.deviceModel : '',
      uaFullVersion: cfg.uaFull,
      fullVersionList: uaBrands.map(b => ({ brand: b.brand, version: cfg.uaFull }))
    }),
    toJSON: () => ({ brands: uaBrands, mobile: cfg.isMobile, platform: cfg.chPlatform })
  };
  defGetter(navProto, 'userAgentData', () => fakeUaData);

  // 1b. Storage ROM Quota Spoofing (navigator.storage.estimate) & Network Connection
  try {
    if (navigator.storage && navigator.storage.estimate) {
      navigator.storage.estimate = async () => ({
        quota: cfg.romQuota,
        usage: cfg.romUsage,
        usageDetails: { indexedDB: Math.floor(cfg.romUsage * 0.28), caches: Math.floor(cfg.romUsage * 0.14) }
      });
    }
    const netRng = mulberry32(cfg.canvasSeed ^ cfg.audioSeed);
    const downlinkVal = Number((8.5 + netRng() * 24).toFixed(1));
    const rttVal = 50 + Math.floor(netRng() * 5) * 25;
    defGetter(navProto, 'connection', () => ({
      effectiveType: '4g',
      rtt: rttVal,
      downlink: downlinkVal,
      saveData: false,
      addEventListener: () => {},
      removeEventListener: () => {}
    }));
  } catch (_) {}

  // 2. Screen & Viewport Spoofing
  const scrProto = Screen.prototype;
  defGetter(scrProto, 'width', () => cfg.screenW);
  defGetter(scrProto, 'height', () => cfg.screenH);
  defGetter(scrProto, 'availWidth', () => cfg.screenW);
  defGetter(scrProto, 'availHeight', () => cfg.isMobile ? cfg.screenH : Math.max(cfg.screenH - 40, 200));
  defGetter(scrProto, 'colorDepth', () => cfg.colorDepth);
  defGetter(scrProto, 'pixelDepth', () => cfg.colorDepth);
  defGetter(window, 'devicePixelRatio', () => cfg.dpr);

  // 3. Timezone & Date Offset Spoofing
  try {
    const OrigDTF = Intl.DateTimeFormat;
    const WrappedDTF = function(locales, options) {
      const opts = Object.assign({}, options || {});
      if (!opts.timeZone) opts.timeZone = cfg.tz;
      return new OrigDTF(locales || cfg.language, opts);
    };
    WrappedDTF.prototype = OrigDTF.prototype;
    WrappedDTF.supportedLocalesOf = OrigDTF.supportedLocalesOf.bind(OrigDTF);
    Intl.DateTimeFormat = WrappedDTF;

    const origResolved = OrigDTF.prototype.resolvedOptions;
    OrigDTF.prototype.resolvedOptions = function() {
      const res = origResolved.call(this);
      res.timeZone = cfg.tz;
      return res;
    };

    const computeTzOffsetMinutes = (dateObj) => {
      try {
        const d = dateObj instanceof Date ? dateObj : new Date();
        const utcStr = d.toLocaleString('en-US', { timeZone: 'UTC' });
        const tzStr = d.toLocaleString('en-US', { timeZone: cfg.tz });
        const diffMs = new Date(utcStr).getTime() - new Date(tzStr).getTime();
        return Math.round(diffMs / 60000);
      } catch (_) {
        return 0;
      }
    };
    Date.prototype.getTimezoneOffset = function() {
      return computeTzOffsetMinutes(this);
    };
  } catch (_) {}

  // 4. Geolocation Spoofing
  if (navigator.geolocation) {
    const makePosition = () => ({
      coords: {
        latitude: cfg.lat,
        longitude: cfg.lon,
        accuracy: cfg.geoAcc,
        altitude: null,
        altitudeAccuracy: null,
        heading: null,
        speed: null
      },
      timestamp: Date.now()
    });
    navigator.geolocation.getCurrentPosition = function(success, error) {
      if (cfg.geoMode === '${GeolocationMode.BLOCK.name}') {
        if (error) setTimeout(() => error({ code: 1, message: 'User denied Geolocation' }), 15);
      } else {
        if (success) setTimeout(() => success(makePosition()), 15);
      }
    };
    let watchSeq = 1;
    navigator.geolocation.watchPosition = function(success, error) {
      const id = watchSeq++;
      navigator.geolocation.getCurrentPosition(success, error);
      return id;
    };
  }
  if (navigator.permissions && navigator.permissions.query) {
    const origPermQuery = navigator.permissions.query.bind(navigator.permissions);
    navigator.permissions.query = function(desc) {
      if (desc && desc.name === 'geolocation') {
        const mappedState = cfg.geoMode === '${GeolocationMode.BLOCK.name}'
          ? 'denied'
          : (cfg.geoMode === '${GeolocationMode.ALLOW.name}' ? 'granted' : 'prompt');
        return Promise.resolve({ state: mappedState, status: mappedState, onchange: null, addEventListener: () => {}, removeEventListener: () => {} });
      }
      return origPermQuery(desc);
    };
  }

  // 5. Battery Spoofing
  if (cfg.batterySpoof && navigator.getBattery) {
    navigator.getBattery = async () => ({
      charging: cfg.batteryCharging,
      chargingTime: cfg.batteryCharging ? 1800 : Infinity,
      dischargingTime: cfg.batteryCharging ? Infinity : 18400,
      level: cfg.batteryLevel,
      addEventListener: () => {},
      removeEventListener: () => {}
    });
  }

  // 6. WebRTC Protection (OFF or BASED_ON_IP - Hardened against SDP, ICE candidate, and .local mDNS leaks)
  if (cfg.webRtcMode === '${WebRtcMode.OFF.name}') {
    const BlockedRTC = function() {
      throw new DOMException('RTCPeerConnection is disabled by CloakDeck privacy policy', 'NotAllowedError');
    };
    window.RTCPeerConnection = BlockedRTC;
    window.webkitRTCPeerConnection = BlockedRTC;
    window.mozRTCPeerConnection = BlockedRTC;
  } else if (cfg.webRtcMode === '${WebRtcMode.BASED_ON_IP.name}' && window.RTCPeerConnection) {
    const OrigRTC = window.RTCPeerConnection;
    const maskIp = (sdp) => {
      if (!sdp) return sdp;
      return sdp
        .replace(/\b(\d{1,3}\.){3}\d{1,3}\b/g, cfg.fakeIp)
        .replace(/\b([0-9a-f]{1,4}:){2,7}[0-9a-f]{0,4}\b/gi, cfg.fakeIp)
        .replace(/\b[0-9a-f-]+\.local\b/gi, cfg.fakeIp);
    };
    const sanitizeCandidateEvent = (ev) => {
      try {
        if (ev && ev.candidate && ev.candidate.candidate) {
          const maskedStr = maskIp(ev.candidate.candidate);
          const patchedCand = Object.assign({}, ev.candidate, {
            candidate: maskedStr,
            address: cfg.fakeIp
          });
          return { candidate: patchedCand, target: ev.target, currentTarget: ev.currentTarget };
        }
      } catch (_) {}
      return ev;
    };
    window.RTCPeerConnection = function(config, constraints) {
      const pc = new OrigRTC(config, constraints);
      const origCreateOffer = pc.createOffer.bind(pc);
      pc.createOffer = async (opts) => {
        const offer = await origCreateOffer(opts);
        try { offer.sdp = maskIp(offer.sdp); } catch (_) {}
        return offer;
      };
      const origCreateAnswer = pc.createAnswer.bind(pc);
      pc.createAnswer = async (opts) => {
        const ans = await origCreateAnswer(opts);
        try { ans.sdp = maskIp(ans.sdp); } catch (_) {}
        return ans;
      };
      let userOnIce = null;
      pc.addEventListener('icecandidate', (ev) => {
        if (userOnIce) userOnIce.call(pc, sanitizeCandidateEvent(ev));
      });
      try {
        Object.defineProperty(pc, 'onicecandidate', {
          get: () => userOnIce,
          set: (fn) => { userOnIce = typeof fn === 'function' ? fn : null; },
          configurable: true
        });
      } catch (_) {}
      return pc;
    };
    window.RTCPeerConnection.prototype = OrigRTC.prototype;
    window.webkitRTCPeerConnection = window.RTCPeerConnection;
  }

  // 7. MediaDevices Spoofing (Exact Video/Audio Input & Output Counts)
  if (cfg.maskMedia && navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
    navigator.mediaDevices.enumerateDevices = async () => {
      const list = [];
      for (let i = 0; i < cfg.audioIn; i++) {
        list.push({ deviceId: 'mic_' + i, kind: 'audioinput', label: '', groupId: 'grp_' + cfg.canvasSeed });
      }
      for (let i = 0; i < cfg.audioOut; i++) {
        list.push({ deviceId: 'spk_' + i, kind: 'audiooutput', label: '', groupId: 'grp_' + cfg.canvasSeed });
      }
      for (let i = 0; i < cfg.videoIn; i++) {
        list.push({ deviceId: 'cam_' + i, kind: 'videoinput', label: '', groupId: 'grp_' + cfg.webGlSeed });
      }
      return list;
    };
  }

  // 8. Canvas Fingerprint Deterministic Noise / Block
  if (cfg.canvasMode !== '${SpoofMode.OFF.name}') {
    const canvasRng = mulberry32(cfg.canvasSeed);
    const deltaR = Math.floor(canvasRng() * 5) - 2 || 1;
    const deltaG = Math.floor(canvasRng() * 5) - 2 || -1;
    const deltaB = Math.floor(canvasRng() * 5) - 2 || 1;

    const origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
    const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
    const origToBlob = HTMLCanvasElement.prototype.toBlob;

    const applyNoiseToCanvas = (canvas) => {
      try {
        if (canvas.width === 0 || canvas.height === 0) return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;
        const w = Math.min(canvas.width, 64);
        const h = Math.min(canvas.height, 64);
        const img = origGetImageData.call(ctx, 0, 0, w, h);
        const d = img.data;
        if (cfg.canvasMode === '${SpoofMode.BLOCK.name}') {
          d.fill(0);
        } else {
          for (let i = 0; i < d.length; i += 16) {
            if (d[i + 3] > 0) {
              d[i] = (d[i] + deltaR) & 255;
              d[i + 1] = (d[i + 1] + deltaG) & 255;
              d[i + 2] = (d[i + 2] + deltaB) & 255;
            }
          }
        }
        ctx.putImageData(img, 0, 0);
      } catch (_) {}
    };

    HTMLCanvasElement.prototype.toDataURL = function(...args) {
      applyNoiseToCanvas(this);
      return origToDataURL.apply(this, args);
    };
    HTMLCanvasElement.prototype.toBlob = function(cb, ...args) {
      applyNoiseToCanvas(this);
      return origToBlob.call(this, cb, ...args);
    };
    CanvasRenderingContext2D.prototype.getImageData = function(sx, sy, sw, sh) {
      const img = origGetImageData.call(this, sx, sy, sw, sh);
      const d = img.data;
      if (cfg.canvasMode === '${SpoofMode.BLOCK.name}') {
        d.fill(0);
      } else {
        for (let i = 0; i < d.length; i += 20) {
          if (d[i + 3] > 0) {
            d[i] = (d[i] + deltaR) & 255;
          }
        }
      }
      return img;
    };
  }

  // 9. WebGL Metadata Mask + WebGL Image Noise + Other WebGL Parameters
  const glRng = mulberry32(cfg.webGlSeed);
  const glDelta = Math.floor(glRng() * 3) + 1;
  const patchWebGl = (proto) => {
    if (!proto) return;
    if (cfg.webGlMetaMode === '${WebGlMode.MASK.name}') {
      const origGetParam = proto.getParameter;
      proto.getParameter = function(param) {
        if (param === 37445) return cfg.glVendor;
        if (param === 37446) return cfg.glRenderer;
        if (param === 3379) return cfg.glMaxTex;
        if (param === 34921) return cfg.glMaxAttr;
        if (param === 35724) return cfg.glShading;
        return origGetParam.call(this, param);
      };
    }
    if (cfg.webGlImageMode !== '${SpoofMode.OFF.name}') {
      const origReadPixels = proto.readPixels;
      proto.readPixels = function(x, y, w, h, format, type, pixels) {
        origReadPixels.call(this, x, y, w, h, format, type, pixels);
        if (pixels && pixels.length > 4) {
          pixels[0] = (pixels[0] + glDelta) & 255;
        }
      };
    }
  };
  patchWebGl(window.WebGLRenderingContext && WebGLRenderingContext.prototype);
  patchWebGl(window.WebGL2RenderingContext && WebGL2RenderingContext.prototype);

  // 10. AudioContext Deterministic Micro-Noise
  if (cfg.audioMode !== '${SpoofMode.OFF.name}' && window.AudioBuffer) {
    const audioRng = mulberry32(cfg.audioSeed);
    const aOffset = (audioRng() - 0.5) * 0.00001;
    const origGetChannelData = AudioBuffer.prototype.getChannelData;
    AudioBuffer.prototype.getChannelData = function(channel) {
      const data = origGetChannelData.call(this, channel);
      if (data && data.length > 0) {
        for (let i = 0; i < data.length; i += 100) {
          data[i] = data[i] + aOffset;
        }
      }
      return data;
    };
  }

  // 11. DOMRect / Unicode Glyphs Sub-pixel Jitter
  if (cfg.domRectJitter) {
    const rectRng = mulberry32(cfg.rectsSeed);
    const jitter = (rectRng() * 0.004) - 0.002;
    const origRect = Element.prototype.getBoundingClientRect;
    Element.prototype.getBoundingClientRect = function() {
      const r = origRect.call(this);
      return new DOMRect(r.x + jitter, r.y + jitter, r.width + jitter, r.height);
    };
  }

  // 12. Font Masking Guard
  if (cfg.fontMasking && document.fonts && document.fonts.check) {
    const origCheck = document.fonts.check.bind(document.fonts);
    document.fonts.check = function(font, text) {
      const lower = (font || '').toLowerCase();
      for (const allowed of cfg.fonts) {
        if (lower.includes(allowed)) return true;
      }
      return false;
    };
  }
})();
        """.trimIndent()
    }

    private fun escapeJs(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
    }
}
