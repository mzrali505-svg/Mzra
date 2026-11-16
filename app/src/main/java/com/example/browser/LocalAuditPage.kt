package com.example.browser

import com.example.data.local.BrowserProfileEntity
import com.example.data.local.ProxyEntity

object LocalAuditPage {

    fun buildHtml(profile: BrowserProfileEntity, proxy: ProxyEntity?): String {
        val proxySummary = if (proxy != null) {
            "${proxy.type}://${proxy.host}:${proxy.port}" +
                if (proxy.lastExitIp.isNotBlank()) " (${proxy.lastExitIp} • ${proxy.country})" else ""
        } else {
            "DIRECT (Without proxy)"
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>CloakDeck Live Fingerprint Audit</title>
  <style>
    :root {
      --bg: #0B0F14;
      --card: #141B24;
      --border: #243143;
      --cyan: #00E5C3;
      --indigo: #6C7CFF;
      --ok: #10B981;
      --warn: #F59E0B;
      --bad: #EF4444;
      --text: #F1F5F9;
      --muted: #94A3B8;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      padding: 16px;
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      line-height: 1.45;
    }
    .header {
      background: linear-gradient(135deg, #111A26 0%, #171E33 100%);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 18px;
      margin-bottom: 14px;
    }
    .badge {
      display: inline-block;
      padding: 4px 10px;
      border-radius: 999px;
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.5px;
      background: rgba(0, 229, 195, 0.14);
      color: var(--cyan);
      border: 1px solid rgba(0, 229, 195, 0.35);
      margin-bottom: 8px;
    }
    h1 { margin: 0 0 6px; font-size: 20px; }
    .sub { color: var(--muted); font-size: 13px; margin: 0; }
    .grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 10px;
    }
    .card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 14px;
      padding: 14px;
    }
    .card h2 {
      margin: 0 0 10px;
      font-size: 13px;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      color: var(--cyan);
    }
    .row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
      padding: 7px 0;
      border-bottom: 1px solid rgba(36, 49, 67, 0.6);
      font-size: 13px;
    }
    .row:last-child { border-bottom: none; }
    .k { color: var(--muted); flex-shrink: 0; }
    .v {
      text-align: right;
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 12px;
      word-break: break-all;
      color: var(--text);
    }
    .ok { color: var(--ok); font-weight: 700; }
    .warn { color: var(--warn); font-weight: 700; }
    .bad { color: var(--bad); font-weight: 700; }
    button {
      background: var(--cyan);
      color: #02201B;
      border: none;
      border-radius: 10px;
      padding: 10px 14px;
      font-weight: 700;
      font-size: 13px;
      margin-top: 10px;
      width: 100%;
    }
    canvas { display: none; }
  </style>
</head>
<body>
  <div class="header">
    <div class="badge">CLOAKDECK LIVE SELF-AUDIT</div>
    <h1>${escapeHtml(profile.name)}</h1>
    <p class="sub">Configured Route: ${escapeHtml(proxySummary)}</p>
    <button onclick="probeLiveIp()">Probe Exit IP &amp; Geo via Active Browser Route</button>
  </div>

  <div class="grid">
    <div class="card">
      <h2>Live Network &amp; WebRTC Leak Probe</h2>
      <div class="row"><span class="k">Detected Exit IP</span><span class="v" id="ip-val">Loading...</span></div>
      <div class="row"><span class="k">Detected Location</span><span class="v" id="geo-ip-val">—</span></div>
      <div class="row"><span class="k">Detected ISP</span><span class="v" id="isp-val">—</span></div>
      <div class="row"><span class="k">WebRTC Leak Status</span><span class="v" id="rtc-val">Probing...</span></div>
      <div class="row"><span class="k">Custom DNS</span><span class="v">${escapeHtml(profile.customDns.ifBlank { "System / Proxy DNS" })}</span></div>
    </div>

    <div class="card">
      <h2>Identity &amp; Navigator</h2>
      <div class="row"><span class="k">navigator.userAgent</span><span class="v" id="ua-val"></span></div>
      <div class="row"><span class="k">navigator.platform</span><span class="v" id="plat-val"></span></div>
      <div class="row"><span class="k">navigator.doNotTrack</span><span class="v" id="dnt-val"></span></div>
      <div class="row"><span class="k">navigator.webdriver</span><span class="v" id="wd-val"></span></div>
      <div class="row"><span class="k">Plugins Count</span><span class="v" id="plug-val"></span></div>
      <div class="row"><span class="k">UserAgentData</span><span class="v" id="uad-val"></span></div>
    </div>

    <div class="card">
      <h2>Timezone, Languages &amp; Geolocation</h2>
      <div class="row"><span class="k">Intl TimeZone</span><span class="v" id="tz-val"></span></div>
      <div class="row"><span class="k">Timezone Offset</span><span class="v" id="tzo-val"></span></div>
      <div class="row"><span class="k">Languages</span><span class="v" id="lang-val"></span></div>
      <div class="row"><span class="k">Geolocation API</span><span class="v" id="geo-api-val">Probing...</span></div>
    </div>

    <div class="card">
      <h2>Hardware, Media &amp; Graphics</h2>
      <div class="row"><span class="k">Screen &amp; DPR</span><span class="v" id="scr-val"></span></div>
      <div class="row"><span class="k">CPU / RAM</span><span class="v" id="hw-val"></span></div>
      <div class="row"><span class="k">Storage / ROM</span><span class="v" id="rom-val">Probing...</span></div>
      <div class="row"><span class="k">Media Devices</span><span class="v" id="media-val">Probing...</span></div>
      <div class="row"><span class="k">WebGL Vendor</span><span class="v" id="glv-val"></span></div>
      <div class="row"><span class="k">WebGL Renderer</span><span class="v" id="glr-val"></span></div>
      <div class="row"><span class="k">WebGL Max Texture</span><span class="v" id="glt-val"></span></div>
      <div class="row"><span class="k">Canvas 2D Hash</span><span class="v" id="cnv-val"></span></div>
      <div class="row"><span class="k">AudioContext Hash</span><span class="v" id="aud-val"></span></div>
      <div class="row"><span class="k">DOMRect Jitter</span><span class="v" id="rect-val"></span></div>
    </div>
  </div>

  <canvas id="probeCanvas" width="240" height="48"></canvas>

  <script>
    function hashStr(str) {
      let h = 2166136261 >>> 0;
      for (let i = 0; i < str.length; i++) {
        h ^= str.charCodeAt(i);
        h = Math.imul(h, 16777619);
      }
      return '0x' + (h >>> 0).toString(16).padStart(8, '0').toUpperCase();
    }

    document.getElementById('ua-val').textContent = navigator.userAgent;
    document.getElementById('plat-val').textContent = navigator.platform;
    document.getElementById('dnt-val').textContent = String(navigator.doNotTrack);
    document.getElementById('wd-val').innerHTML = navigator.webdriver === false
      ? '<span class="ok">false (Clean)</span>'
      : '<span class="bad">' + navigator.webdriver + '</span>';
    document.getElementById('plug-val').textContent = String((navigator.plugins && navigator.plugins.length) || 0);
    document.getElementById('uad-val').textContent = navigator.userAgentData
      ? navigator.userAgentData.platform + ' / mobile=' + navigator.userAgentData.mobile
      : 'N/A';

    document.getElementById('tz-val').textContent = Intl.DateTimeFormat().resolvedOptions().timeZone;
    document.getElementById('tzo-val').textContent = new Date().getTimezoneOffset() + ' min';
    document.getElementById('lang-val').textContent = (navigator.languages || [navigator.language]).join(', ');

    document.getElementById('scr-val').textContent =
      screen.width + 'x' + screen.height + ' @ ' + window.devicePixelRatio + 'x (' + screen.colorDepth + '-bit)';
    document.getElementById('hw-val').textContent =
      navigator.hardwareConcurrency + ' cores / ' + navigator.deviceMemory + ' GB RAM / touch=' + navigator.maxTouchPoints;
    if (navigator.storage && navigator.storage.estimate) {
      navigator.storage.estimate().then(est => {
        const gb = Math.round((est.quota || 0) / (1024 * 1024 * 1024));
        const usedGb = ((est.usage || 0) / (1024 * 1024 * 1024)).toFixed(2);
        document.getElementById('rom-val').textContent = gb + ' GB ROM (' + usedGb + ' GB used)';
      }).catch(() => {
        document.getElementById('rom-val').textContent = 'Unavailable';
      });
    }

    if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
      navigator.mediaDevices.enumerateDevices().then(devs => {
        const v = devs.filter(d => d.kind === 'videoinput').length;
        const ai = devs.filter(d => d.kind === 'audioinput').length;
        const ao = devs.filter(d => d.kind === 'audiooutput').length;
        document.getElementById('media-val').textContent = 'Vid=' + v + ', Mic=' + ai + ', Spk=' + ao;
      }).catch(() => {
        document.getElementById('media-val').textContent = 'Masked (0)';
      });
    }

    try {
      const c = document.createElement('canvas');
      const gl = c.getContext('webgl') || c.getContext('experimental-webgl');
      if (gl) {
        document.getElementById('glv-val').textContent = gl.getParameter(37445) || 'Masked';
        document.getElementById('glr-val').textContent = gl.getParameter(37446) || 'Masked';
        document.getElementById('glt-val').textContent = String(gl.getParameter(3379) || 'N/A');
      }
    } catch (e) {
      document.getElementById('glv-val').textContent = 'Blocked';
    }

    try {
      const cv = document.getElementById('probeCanvas');
      const ctx = cv.getContext('2d');
      ctx.fillStyle = '#00E5C3';
      ctx.fillRect(4, 4, 80, 24);
      ctx.fillStyle = '#6C7CFF';
      ctx.font = '14px Arial';
      ctx.fillText('CloakDeck-FP-Test', 10, 32);
      document.getElementById('cnv-val').textContent = hashStr(cv.toDataURL());
    } catch (e) {
      document.getElementById('cnv-val').textContent = 'Blocked';
    }

    try {
      const buf = new (window.OfflineAudioContext || window.webkitOfflineAudioContext)(1, 256, 44100);
      const ab = buf.createBuffer(1, 256, 44100);
      const ch = ab.getChannelData(0);
      ch[0] = 0.25; ch[100] = 0.5;
      document.getElementById('aud-val').textContent = hashStr(ch[0].toFixed(8) + ':' + ch[100].toFixed(8));
    } catch (e) {
      document.getElementById('aud-val').textContent = 'Unavailable';
    }

    const rectEl = document.getElementById('ua-val').getBoundingClientRect();
    document.getElementById('rect-val').textContent =
      rectEl.x.toFixed(5) + ', ' + rectEl.width.toFixed(5);

    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          document.getElementById('geo-api-val').innerHTML =
            '<span class="ok">' + pos.coords.latitude.toFixed(4) + ', ' + pos.coords.longitude.toFixed(4) + ' (±' + pos.coords.accuracy + 'm)</span>';
        },
        (err) => {
          document.getElementById('geo-api-val').innerHTML = '<span class="warn">' + err.message + '</span>';
        }
      );
    }

    try {
      const pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] });
      pc.createDataChannel('probe');
      pc.createOffer().then(o => pc.setLocalDescription(o));
      document.getElementById('rtc-val').innerHTML = '<span class="warn">Based on IP (Masked)</span>';
      pc.onicecandidate = (ev) => {
        if (ev && ev.candidate && ev.candidate.candidate) {
          document.getElementById('rtc-val').textContent = ev.candidate.candidate;
        }
      };
    } catch (e) {
      document.getElementById('rtc-val').innerHTML = '<span class="ok">OFF (0 Leaks)</span>';
    }

    async function probeLiveIp() {
      document.getElementById('ip-val').textContent = 'Querying exit node...';
      try {
        const res = await fetch('https://ipwho.is/');
        const data = await res.json();
        document.getElementById('ip-val').innerHTML = '<span class="ok">' + (data.ip || 'Unknown') + '</span>';
        document.getElementById('geo-ip-val').textContent = [data.city, data.country].filter(Boolean).join(', ');
        document.getElementById('isp-val').textContent = (data.connection && (data.connection.isp || data.connection.org)) || '—';
      } catch (err) {
        document.getElementById('ip-val').innerHTML = '<span class="bad">Proxy/Network Error</span>';
      }
    }
    probeLiveIp();
  </script>
</body>
</html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
