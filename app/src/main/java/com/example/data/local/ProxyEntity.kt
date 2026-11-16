package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ProxyStatus
import com.example.data.model.ProxyType

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String = ProxyType.HTTP.name,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val lastExitIp: String = "",
    val country: String = "",
    val countryCode: String = "",
    val city: String = "",
    val isp: String = "",
    val timezone: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val latencyMs: Long? = null,
    val status: String = ProxyStatus.UNCHECKED.name,
    val lastError: String = "",
    val lastCheckedAt: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),

    // Humanized Google Search & Dwell Config per Proxy
    val humanizeEnabled: Boolean = false,
    val humanizeKeyword: String = "",
    val humanizeTargetDomain: String = "",
    val humanizeDwellSeconds: Int = 30,
    val humanizeClickInternalLink: Boolean = true,
    val humanizeScrollStyle: String = "AUTO",

    // Default Geo, WebRTC & Privacy Config attached to this Proxy
    val geolocationMode: String = "PROMPT",
    val geolocationBasedOnIp: Boolean = true,
    val timezoneFillFromIp: Boolean = true,
    val webRtcMode: String = "BASED_ON_IP"
)
