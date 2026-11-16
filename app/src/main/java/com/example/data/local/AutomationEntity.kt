package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class RpaActionType(val label: String, val description: String) {
    GOOGLE_SEARCH_AND_BROWSE("Google Search & Read", "Open Google, type keyword like a human, open suggested site, and read for exact seconds"),
    HUMAN_READ_DURATION("Human Read Timer", "Continuously scroll, pause, and move pointer naturally for exact seconds"),
    NAVIGATE("Open URL", "Navigate to a target web address"),
    HUMAN_WAIT("Human Dwell / Wait", "Pause with randomized human timing (min–max sec)"),
    HUMAN_SCROLL("Human Organic Scroll", "Scroll down/up smoothly with reading pauses"),
    HUMAN_CLICK("Human Tap / Click", "Find element by CSS selector or text and dispatch pointer tap"),
    HUMAN_TYPE("Human Keystroke Typing", "Type text character-by-character with realistic WPM jitter"),
    CLICK_RANDOM_LINK("Browse Random Link", "Pick a visible same-site link and tap naturally"),
    CLEAR_COOKIES("Clear Profile Cookies", "Flush session cookies mid-flow")
}

@Serializable
data class RpaStep(
    val id: String,
    val type: RpaActionType,
    val target: String = "",       // URL, CSS selector, visible text, or target domain hint
    val value: String = "",        // Search keyword, text to type, or scroll direction ("DOWN"/"UP")
    val minDelayMs: Int = 1200,
    val maxDelayMs: Int = 3500,
    val scrollCycles: Int = 4,
    val durationSeconds: Int = 30,
    val clickInternalLink: Boolean = true
)

@Entity(tableName = "automations")
data class AutomationFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String = "",
    val stepsJson: String,
    val humanTypingMinMs: Int = 85,
    val humanTypingMaxMs: Int = 215,
    val humanScrollJitter: Boolean = true,
    val loopCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
