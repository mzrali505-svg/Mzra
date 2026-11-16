package com.example.data.model

import com.example.data.local.BrowserProfileEntity
import kotlin.random.Random

enum class ScrollArchetype {
    CAREFUL_READER,
    SKIMMER_SCANNER,
    BURST_AND_PAUSE,
    ZIGZAG_EXPLORER,
    SECTION_SNAPPER,
    TOUCH_MOMENTUM
}

enum class HumanizeCategory(val label: String) {
    AUTO("Auto"),
    DEEP_READING("Deep Reading"),
    THUMB_PHYSICS("Thumb Physics"),
    RESEARCH_STUDY("Research & Study"),
    NEWS_FEED("News & Articles"),
    SHOPPING_REVIEW("Reviews & Compare"),
    VISUAL_MEDIA("Visual & Media"),
    LONG_STAY("Long-Stay (1–5m)")
}

enum class HumanizeEasing(val jsCurveIndex: Int) {
    CUBIC_IN_OUT(0),
    QUINTIC_IN_OUT(1),
    SINE_IN_OUT(2),
    SOFT_DECEL(3)
}

enum class HumanizeScrollStyle(
    val displayName: String,
    val shortLabel: String,
    val description: String,
    val category: HumanizeCategory = HumanizeCategory.DEEP_READING,
    val archetype: ScrollArchetype = ScrollArchetype.CAREFUL_READER,
    val paceFactor: Float = 1.0f,
    val amplitudeFactor: Float = 0.85f,
    val pauseMinMs: Long = 3500L,
    val pauseMaxMs: Long = 6800L,
    val backtrackPct: Int = 16,
    val burstMin: Int = 1,
    val burstMax: Int = 1,
    val eyeNudgePct: Int = 36,
    val easing: HumanizeEasing = HumanizeEasing.CUBIC_IN_OUT
) {
    AUTO(
        displayName = "Auto-Unique per Profile",
        shortLabel = "Auto-Unique",
        description = "Assigns a distinct scrolling style, speed, easing, and reading cadence per profile across 120+ presets",
        category = HumanizeCategory.AUTO,
        archetype = ScrollArchetype.TOUCH_MOMENTUM,
        paceFactor = 1.05f,
        amplitudeFactor = 0.90f,
        pauseMinMs = 3400L,
        pauseMaxMs = 6600L,
        backtrackPct = 18,
        burstMin = 1,
        burstMax = 2,
        eyeNudgePct = 40,
        easing = HumanizeEasing.QUINTIC_IN_OUT
    ),

    // Original 6 core styles (preserved exact names for backward compatibility)
    CAREFUL_READER(
        displayName = "Careful Deep Reader",
        shortLabel = "Careful Reader",
        description = "Short measured scrolls, long reading pauses, and occasional re-read scroll-ups",
        category = HumanizeCategory.DEEP_READING,
        archetype = ScrollArchetype.CAREFUL_READER,
        paceFactor = 1.12f,
        amplitudeFactor = 0.72f,
        pauseMinMs = 4200L,
        pauseMaxMs = 8500L,
        backtrackPct = 18,
        burstMin = 1,
        burstMax = 1,
        eyeNudgePct = 45,
        easing = HumanizeEasing.SINE_IN_OUT
    ),
    SKIMMER_SCANNER(
        displayName = "Measured Skimmer & Scanner",
        shortLabel = "Skimmer",
        description = "Smooth sweeps across headings with mid-page pause and gentle jump-backs",
        category = HumanizeCategory.NEWS_FEED,
        archetype = ScrollArchetype.SKIMMER_SCANNER,
        paceFactor = 0.98f,
        amplitudeFactor = 1.02f,
        pauseMinMs = 2600L,
        pauseMaxMs = 5200L,
        backtrackPct = 20,
        burstMin = 1,
        burstMax = 2,
        eyeNudgePct = 32,
        easing = HumanizeEasing.CUBIC_IN_OUT
    ),
    BURST_AND_PAUSE(
        displayName = "Double Flick & Long Pause",
        shortLabel = "Burst & Pause",
        description = "1-2 gentle thumb glides followed by long stationary reading pauses",
        category = HumanizeCategory.THUMB_PHYSICS,
        archetype = ScrollArchetype.BURST_AND_PAUSE,
        paceFactor = 1.08f,
        amplitudeFactor = 0.84f,
        pauseMinMs = 4000L,
        pauseMaxMs = 7600L,
        backtrackPct = 14,
        burstMin = 1,
        burstMax = 2,
        eyeNudgePct = 42,
        easing = HumanizeEasing.SOFT_DECEL
    ),
    ZIGZAG_EXPLORER(
        displayName = "Zig-Zag Curious Explorer",
        shortLabel = "Zig-Zag",
        description = "Alternates calm downward reading and backward peeks while hovering headings & links",
        category = HumanizeCategory.RESEARCH_STUDY,
        archetype = ScrollArchetype.ZIGZAG_EXPLORER,
        paceFactor = 1.04f,
        amplitudeFactor = 0.88f,
        pauseMinMs = 3100L,
        pauseMaxMs = 6400L,
        backtrackPct = 28,
        burstMin = 1,
        burstMax = 1,
        eyeNudgePct = 38,
        easing = HumanizeEasing.CUBIC_IN_OUT
    ),
    SECTION_SNAPPER(
        displayName = "Section-by-Section Reader",
        shortLabel = "Section Snap",
        description = "Scrolls smoothly to align real headings and paragraphs into comfortable view",
        category = HumanizeCategory.RESEARCH_STUDY,
        archetype = ScrollArchetype.SECTION_SNAPPER,
        paceFactor = 1.06f,
        amplitudeFactor = 0.90f,
        pauseMinMs = 3600L,
        pauseMaxMs = 7000L,
        backtrackPct = 16,
        burstMin = 1,
        burstMax = 1,
        eyeNudgePct = 40,
        easing = HumanizeEasing.QUINTIC_IN_OUT
    ),
    TOUCH_MOMENTUM(
        displayName = "Eased Touch-Flick Momentum",
        shortLabel = "Touch Flick",
        description = "Multi-frame ease-out scroll inertia simulating natural finger swipes",
        category = HumanizeCategory.THUMB_PHYSICS,
        archetype = ScrollArchetype.TOUCH_MOMENTUM,
        paceFactor = 1.02f,
        amplitudeFactor = 0.92f,
        pauseMinMs = 3200L,
        pauseMaxMs = 6400L,
        backtrackPct = 17,
        burstMin = 1,
        burstMax = 2,
        eyeNudgePct = 35,
        easing = HumanizeEasing.SOFT_DECEL
    ),

    // Category 1: Deep Reading & Focus (20 options)
    ACADEMIC_REVIEWER("Academic Paper Reviewer", "Academic", "Methodical paragraph reading with slow re-read checks", HumanizeCategory.DEEP_READING, ScrollArchetype.CAREFUL_READER, 1.24f, 0.68f, 4800L, 9200L, 24, 1, 1, 50, HumanizeEasing.SINE_IN_OUT),
    BOOKWORM_SLOW("Bookworm Slow Absorber", "Bookworm", "Steady slow line-by-line cadence with long thoughtful stops", HumanizeCategory.DEEP_READING, ScrollArchetype.CAREFUL_READER, 1.30f, 0.62f, 5200L, 9800L, 15, 1, 1, 55, HumanizeEasing.SINE_IN_OUT),
    WHITEPAPER_ANALYST("Whitepaper Technical Analyst", "Whitepaper", "Aligns dense technical blocks and pauses to digest details", HumanizeCategory.DEEP_READING, ScrollArchetype.SECTION_SNAPPER, 1.18f, 0.78f, 4500L, 8800L, 22, 1, 1, 44, HumanizeEasing.QUINTIC_IN_OUT),
    LONG_ARTICLE_ABSORBER("Long-Article Story Reader", "Long Article", "Balanced narrative pacing built for 1500+ word posts", HumanizeCategory.DEEP_READING, ScrollArchetype.CAREFUL_READER, 1.15f, 0.76f, 4000L, 7800L, 16, 1, 1, 48, HumanizeEasing.CUBIC_IN_OUT),
    TUTORIAL_FOLLOWER("Step-by-Step Tutorial Follower", "Tutorial Step", "Pauses consistently at numbered steps and subheadings", HumanizeCategory.DEEP_READING, ScrollArchetype.SECTION_SNAPPER, 1.16f, 0.80f, 4200L, 8400L, 20, 1, 1, 36, HumanizeEasing.CUBIC_IN_OUT),
    CODE_DOC_READER("Developer Docs Reader", "Dev Docs", "Deliberate stops around examples and API tables with backchecks", HumanizeCategory.DEEP_READING, ScrollArchetype.ZIGZAG_EXPLORER, 1.14f, 0.74f, 4400L, 8600L, 27, 1, 1, 40, HumanizeEasing.SOFT_DECEL),
    LEGAL_FINEPRINT_SCAN("Fine-Print Policy Reader", "Fine Print", "Extra-tiny scroll distances with patient clause inspection", HumanizeCategory.DEEP_READING, ScrollArchetype.CAREFUL_READER, 1.28f, 0.58f, 4600L, 9000L, 25, 1, 1, 52, HumanizeEasing.SINE_IN_OUT),
    MEDICAL_JOURNAL_PACE("Medical & Health Journal", "Health Journal", "Calm cautious reading with frequent cross-check scroll-ups", HumanizeCategory.DEEP_READING, ScrollArchetype.CAREFUL_READER, 1.22f, 0.66f, 4700L, 8900L, 23, 1, 1, 46, HumanizeEasing.SINE_IN_OUT),
    RECIPE_STEP_CHECKER("Recipe & How-To Checker", "How-To Check", "Bounces gently between ingredients/steps and instructions", HumanizeCategory.DEEP_READING, ScrollArchetype.ZIGZAG_EXPLORER, 1.10f, 0.82f, 3600L, 7200L, 30, 1, 1, 35, HumanizeEasing.CUBIC_IN_OUT),
    SUBTITLE_CADENCE("Subtitle Cadence Reader", "Subtitles", "Rhythmic micro-scrolls tuned to natural reading speed", HumanizeCategory.DEEP_READING, ScrollArchetype.CAREFUL_READER, 1.09f, 0.70f, 3800L, 6900L, 12, 1, 1, 60, HumanizeEasing.SINE_IN_OUT),
    SERENE_MORNING_READER("Serene Morning Coffee Read", "Morning Read", "Unrushed soft easing glides with generous rest pauses", HumanizeCategory.DEEP_READING, ScrollArchetype.TOUCH_MOMENTUM, 1.25f, 0.72f, 4900L, 9100L, 14, 1, 1, 42, HumanizeEasing.SINE_IN_OUT),
    PROOFREADER_LINE_BY_LINE("Line-by-Line Proofreader", "Proofreader", "Minimal vertical shift per pause to mirror close inspection", HumanizeCategory.DEEP_READING, ScrollArchetype.CAREFUL_READER, 1.26f, 0.56f, 4300L, 8100L, 21, 1, 1, 62, HumanizeEasing.SINE_IN_OUT),
    BIOGRAPHY_READER("Biography & Essay Reader", "Biography", "Fluid continuous downward drift with thoughtful mid-pauses", HumanizeCategory.DEEP_READING, ScrollArchetype.CAREFUL_READER, 1.14f, 0.75f, 3900L, 7500L, 15, 1, 1, 45, HumanizeEasing.QUINTIC_IN_OUT),
    ESSAY_CONTEMPLATIVE("Contemplative Essayist", "Essayist", "Long pauses after dense paragraphs before gently moving on", HumanizeCategory.DEEP_READING, ScrollArchetype.BURST_AND_PAUSE, 1.20f, 0.74f, 4800L, 9400L, 17, 1, 1, 48, HumanizeEasing.SINE_IN_OUT),
    NEWSLETTER_SIPPER("Newsletter Calm Reader", "Newsletter", "Moves evenly from topic block to topic block", HumanizeCategory.DEEP_READING, ScrollArchetype.SECTION_SNAPPER, 1.12f, 0.80f, 3700L, 7100L, 14, 1, 1, 38, HumanizeEasing.CUBIC_IN_OUT),
    BOOKMARK_EVALUATOR("Page Value Evaluator", "Evaluator", "Scans top summary, reads middle deeply, checks bottom links", HumanizeCategory.DEEP_READING, ScrollArchetype.ZIGZAG_EXPLORER, 1.07f, 0.86f, 3400L, 6700L, 26, 1, 2, 34, HumanizeEasing.CUBIC_IN_OUT),
    ENCYCLOPEDIA_HOPPER("Encyclopedia Topic Reader", "Encyclopedia", "Stops cleanly at each subtopic heading with light nudges", HumanizeCategory.DEEP_READING, ScrollArchetype.SECTION_SNAPPER, 1.13f, 0.82f, 3900L, 7600L, 19, 1, 1, 42, HumanizeEasing.QUINTIC_IN_OUT),
    CASE_STUDY_INSPECTOR("Business Case Inspector", "Case Study", "Measures metrics blocks carefully and revisits takeaways", HumanizeCategory.DEEP_READING, ScrollArchetype.ZIGZAG_EXPLORER, 1.17f, 0.78f, 4300L, 8200L, 24, 1, 1, 40, HumanizeEasing.CUBIC_IN_OUT),
    WHITEBOARD_NOTE_TAKER("Study Note-Taker Pace", "Note-Taker", "Extended pauses simulating someone jotting down key points", HumanizeCategory.DEEP_READING, ScrollArchetype.BURST_AND_PAUSE, 1.29f, 0.69f, 5400L, 9900L, 18, 1, 1, 45, HumanizeEasing.SOFT_DECEL),
    QUIET_LIBRARY_PACE("Quiet Library Reader", "Library Pace", "Zero abrupt motion, ultra-gentle glides and deep stops", HumanizeCategory.DEEP_READING, ScrollArchetype.CAREFUL_READER, 1.27f, 0.64f, 5000L, 9300L, 16, 1, 1, 50, HumanizeEasing.SINE_IN_OUT),

    // Category 2: Mobile Thumb & Touch Physics (20 options)
    GENTLE_ONE_HAND_THUMB("One-Handed Thumb Drift", "1-Hand Thumb", "Realistic portrait thumb arcs with slight asymmetric deceleration", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.TOUCH_MOMENTUM, 1.04f, 0.86f, 3200L, 6200L, 15, 1, 2, 36, HumanizeEasing.SOFT_DECEL),
    SOFA_RELAXED_SWIPE("Relaxed Couch Browser", "Couch Swipe", "Laid-back slow swipes with long comfortable pauses", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.TOUCH_MOMENTUM, 1.18f, 0.80f, 4200L, 8100L, 14, 1, 1, 44, HumanizeEasing.SINE_IN_OUT),
    MORNING_COMMUTE_FLICK("Morning Commute Reader", "Commute Flick", "Steady thumb flicks tempered by natural attention pauses", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.BURST_AND_PAUSE, 1.00f, 0.90f, 3000L, 5800L, 16, 1, 2, 30, HumanizeEasing.CUBIC_IN_OUT),
    GLIDE_AND_SETTLE("Glide & Settle Inertia", "Glide Settle", "Long smooth quintic settle at the end of every finger slide", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.TOUCH_MOMENTUM, 1.10f, 0.85f, 3500L, 6800L, 15, 1, 1, 42, HumanizeEasing.QUINTIC_IN_OUT),
    MICRO_DRIFT_READER("Micro-Drift Eye Follow", "Micro-Drift", "Continuous tiny smooth drifts as the user reads down screen", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.CAREFUL_READER, 1.12f, 0.60f, 2900L, 5600L, 12, 1, 1, 65, HumanizeEasing.SINE_IN_OUT),
    FEATHER_TOUCH_GLIDE("Feather-Touch Light Glide", "Feather Touch", "Soft lightweight swipes with delicate momentum tail", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.TOUCH_MOMENTUM, 1.06f, 0.78f, 3300L, 6500L, 18, 1, 1, 48, HumanizeEasing.SOFT_DECEL),
    SLOW_ELEVATOR_SCROLL("Slow Smooth Elevator", "Slow Elevator", "Even, unhurried vertical glide with minimal acceleration spikes", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.CAREFUL_READER, 1.19f, 0.72f, 3800L, 7100L, 10, 1, 1, 56, HumanizeEasing.SINE_IN_OUT),
    BEDTIME_LAZY_THUMB("Bedtime Night-Mode Scroll", "Bedtime Scroll", "Extra-slow night reading cadence with deep stationary pauses", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.TOUCH_MOMENTUM, 1.32f, 0.66f, 5100L, 9600L, 13, 1, 1, 38, HumanizeEasing.SINE_IN_OUT),
    DOUBLE_TAP_NUDGER("Dual Small-Nudge Reader", "Dual Nudge", "Pairs two small gentle nudges then pauses to read the card", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.BURST_AND_PAUSE, 1.08f, 0.70f, 3700L, 7200L, 15, 2, 2, 32, HumanizeEasing.CUBIC_IN_OUT),
    CURVED_ARC_DRAG("Curved Thumb Arc Drag", "Thumb Arc", "Natural curved finger easing simulating ergonomic screen drag", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.TOUCH_MOMENTUM, 1.07f, 0.84f, 3400L, 6600L, 17, 1, 1, 40, HumanizeEasing.QUINTIC_IN_OUT),
    SILK_EASE_DECEL("Silky Ease-Out Momentum", "Silk Decel", "Smooth gradual braking before each reading block", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.TOUCH_MOMENTUM, 1.11f, 0.88f, 3600L, 7000L, 14, 1, 1, 44, HumanizeEasing.SOFT_DECEL),
    STEADY_METRONOME_SWIPE("Steady Cadence Thumb", "Steady Thumb", "Consistent calm intervals like a relaxed casual reader", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.CAREFUL_READER, 1.10f, 0.79f, 3600L, 6400L, 12, 1, 1, 35, HumanizeEasing.CUBIC_IN_OUT),
    TWO_STEP_HESITATOR("Two-Step Hesitant Swipe", "Hesitator", "Pauses mid-thought with frequent short backward checks", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.ZIGZAG_EXPLORER, 1.16f, 0.76f, 3800L, 7500L, 29, 1, 1, 46, HumanizeEasing.SOFT_DECEL),
    HOLD_AND_RELEASE("Thumb Hold & Release", "Hold Release", "Deliberate pauses before each smooth finger release", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.BURST_AND_PAUSE, 1.15f, 0.82f, 4100L, 7900L, 16, 1, 1, 40, HumanizeEasing.QUINTIC_IN_OUT),
    WARMUP_GRADUAL_SWIPE("Gradual Warmup Reader", "Warmup Glide", "Starts tiny and settles into comfortable medium glides", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.TOUCH_MOMENTUM, 1.12f, 0.81f, 3700L, 7100L, 15, 1, 2, 42, HumanizeEasing.SINE_IN_OUT),
    SOFT_BRAKE_MOMENTUM("Soft-Brake Thumb Glide", "Soft Brake", "Slows gracefully so text never blurs during motion", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.TOUCH_MOMENTUM, 1.14f, 0.77f, 3500L, 6900L, 18, 1, 1, 45, HumanizeEasing.SOFT_DECEL),
    NATURAL_ARTHRITIC_SLOW("Ultra-Gentle Deliberate Thumb", "Ultra-Gentle", "Low-effort slow swipes with extra-long comfortable pauses", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.CAREFUL_READER, 1.34f, 0.63f, 4900L, 9500L, 14, 1, 1, 36, HumanizeEasing.SINE_IN_OUT),
    LEFT_HAND_THUMB_ARC("Left-Thumb Casual Glide", "Left Thumb", "Subtle left-bias pointer hovers with relaxed scroll momentum", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.TOUCH_MOMENTUM, 1.06f, 0.85f, 3300L, 6500L, 19, 1, 2, 38, HumanizeEasing.CUBIC_IN_OUT),
    CASUAL_BENCH_SWIPE("Park Bench Relaxed Scroll", "Bench Swipe", "Unbothered slow scroll rhythm with occasional look-backs", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.TOUCH_MOMENTUM, 1.21f, 0.75f, 4300L, 8400L, 21, 1, 1, 42, HumanizeEasing.SINE_IN_OUT),
    PATIENT_WAITING_SCROLL("Waiting-Room Patient Reader", "Patient Read", "Slow unhurried reading rhythm ideal for any page length", HumanizeCategory.THUMB_PHYSICS, ScrollArchetype.CAREFUL_READER, 1.23f, 0.71f, 4600L, 8900L, 16, 1, 1, 48, HumanizeEasing.QUINTIC_IN_OUT),

    // Category 3: Research & Study (16 options)
    CITATION_CROSS_CHECKER("Citation Cross-Checker", "Cross-Checker", "Scrolls back often to compare references and claims", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.ZIGZAG_EXPLORER, 1.13f, 0.80f, 3600L, 7400L, 34, 1, 1, 38, HumanizeEasing.CUBIC_IN_OUT),
    HEADING_INDEX_PLANNER("Heading Index Scanner", "Heading Index", "Steps smoothly from heading to heading to map article scope", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.SECTION_SNAPPER, 1.04f, 0.92f, 3200L, 6400L, 18, 1, 1, 34, HumanizeEasing.QUINTIC_IN_OUT),
    TABLE_OF_CONTENTS_STEP("TOC Structured Reader", "TOC Stepper", "Follows logical H2/H3 section structure down the page", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.SECTION_SNAPPER, 1.09f, 0.88f, 3800L, 7300L, 15, 1, 1, 40, HumanizeEasing.CUBIC_IN_OUT),
    CHART_DATA_PAUSER("Chart & Table Inspector", "Chart Pauser", "Stops smoothly over figures and data tables with gentle hovers", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.SECTION_SNAPPER, 1.19f, 0.79f, 4500L, 8800L, 21, 1, 1, 45, HumanizeEasing.SINE_IN_OUT),
    FAQ_EXPAND_CHECKER("FAQ Question-Answer Browser", "FAQ Browser", "Reads Q&A blocks smoothly with curious look-backs", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.ZIGZAG_EXPLORER, 1.08f, 0.84f, 3400L, 6700L, 25, 1, 1, 36, HumanizeEasing.CUBIC_IN_OUT),
    TERMINOLOGY_BACKTRACKER("Terminology Re-Reader", "Term Re-Read", "Frequently scrolls up slightly to re-check earlier definitions", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.ZIGZAG_EXPLORER, 1.15f, 0.77f, 3900L, 7700L, 32, 1, 1, 44, HumanizeEasing.SOFT_DECEL),
    BENCHMARK_COMPARER("Benchmark Score Comparer", "Benchmarks", "Compares specs across sections using controlled back-scrolls", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.ZIGZAG_EXPLORER, 1.06f, 0.89f, 3300L, 6600L, 30, 1, 2, 35, HumanizeEasing.CUBIC_IN_OUT),
    WIKI_FOOTNOTE_PEEKER("Wiki & Reference Explorer", "Wiki Explorer", "Section-guided reading with gentle pauses at dense blocks", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.SECTION_SNAPPER, 1.12f, 0.83f, 3700L, 7400L, 22, 1, 1, 41, HumanizeEasing.QUINTIC_IN_OUT),
    PATENT_DIAGRAM_EXAMINER("Diagram & Flow Examiner", "Diagram Exam", "Slow deliberate glides with extended visual contemplation stops", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.CAREFUL_READER, 1.24f, 0.70f, 4900L, 9400L, 20, 1, 1, 46, HumanizeEasing.SINE_IN_OUT),
    FORUM_THREAD_TRACKER("Discussion Thread Reader", "Thread Track", "Reads post-by-post down discussions and checks parent replies", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.SECTION_SNAPPER, 1.05f, 0.87f, 3100L, 6300L, 24, 1, 1, 38, HumanizeEasing.CUBIC_IN_OUT),
    STACK_ANSWER_HUNTER("Q&A Answer Verifier", "Q&A Verifier", "Glides through top answer, compares second answer below", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.ZIGZAG_EXPLORER, 1.02f, 0.90f, 3000L, 6100L, 29, 1, 2, 33, HumanizeEasing.SOFT_DECEL),
    SPEC_SHEET_VERIFIER("Technical Spec Verifier", "Spec Sheet", "Measured downward stepping with frequent row verification", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.CAREFUL_READER, 1.16f, 0.73f, 4100L, 7900L, 25, 1, 1, 50, HumanizeEasing.SINE_IN_OUT),
    GLOSSARY_LOOKBACK("Glossary Lookback Scholar", "Glossary Look", "Balanced reading interleaved with gentle upward verification", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.ZIGZAG_EXPLORER, 1.14f, 0.78f, 3800L, 7500L, 28, 1, 1, 42, HumanizeEasing.CUBIC_IN_OUT),
    SUMMARY_FIRST_REVERSER("Summary-First Investigator", "Summary Check", "Reads steadily down then thoughtfully revisits earlier claims", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.ZIGZAG_EXPLORER, 1.09f, 0.85f, 3500L, 6900L, 27, 1, 1, 39, HumanizeEasing.QUINTIC_IN_OUT),
    THESIS_PARAGRAPH_DWELLER("Thesis Paragraph Dweller", "Thesis Dwell", "Takes long analytical pauses on substantive body paragraphs", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.CAREFUL_READER, 1.25f, 0.69f, 5100L, 9500L, 19, 1, 1, 47, HumanizeEasing.SINE_IN_OUT),
    METHODOLOGY_DEEP_DIVE("Methodology Deep Diver", "Methodology", "Follows headings carefully without skipping technical notes", HumanizeCategory.RESEARCH_STUDY, ScrollArchetype.SECTION_SNAPPER, 1.20f, 0.76f, 4400L, 8600L, 21, 1, 1, 44, HumanizeEasing.QUINTIC_IN_OUT),

    // Category 4: News, Blog & Feed (16 options)
    HEADLINE_TO_LEAD_READER("Headline-to-Lead Reader", "Headline Lead", "Pauses cleanly on headlines and intro paragraphs", HumanizeCategory.NEWS_FEED, ScrollArchetype.SECTION_SNAPPER, 1.03f, 0.90f, 3000L, 5900L, 16, 1, 1, 36, HumanizeEasing.CUBIC_IN_OUT),
    OPINION_COLUMN_DRIFTER("Opinion Column Drifter", "Opinion Col", "Thoughtful mid-speed reading through commentary columns", HumanizeCategory.NEWS_FEED, ScrollArchetype.CAREFUL_READER, 1.11f, 0.80f, 3800L, 7400L, 17, 1, 1, 43, HumanizeEasing.SINE_IN_OUT),
    LIVE_BLOG_CHECKER("Live-Update Blog Reader", "Live Blog", "Steps gently through timeline updates with periodic rechecks", HumanizeCategory.NEWS_FEED, ScrollArchetype.BURST_AND_PAUSE, 1.05f, 0.86f, 3300L, 6500L, 22, 1, 2, 34, HumanizeEasing.SOFT_DECEL),
    TECH_REVIEW_SCROLLER("Tech Gadget Review Reader", "Tech Review", "Reads overview, hands-on notes, and verdict smoothly", HumanizeCategory.NEWS_FEED, ScrollArchetype.SECTION_SNAPPER, 1.07f, 0.87f, 3500L, 6800L, 20, 1, 1, 40, HumanizeEasing.CUBIC_IN_OUT),
    FINANCE_TICKER_WATCHER("Market Analysis Reader", "Market Read", "Calm analytical reading of commentary and summary bullets", HumanizeCategory.NEWS_FEED, ScrollArchetype.CAREFUL_READER, 1.13f, 0.78f, 3900L, 7600L, 23, 1, 1, 42, HumanizeEasing.QUINTIC_IN_OUT),
    SPORTS_RECAP_BROWSER("Match Recap & Stats Browser", "Sports Recap", "Medium glides across recap highlights and box scores", HumanizeCategory.NEWS_FEED, ScrollArchetype.SKIMMER_SCANNER, 0.99f, 0.95f, 2800L, 5600L, 19, 1, 2, 32, HumanizeEasing.CUBIC_IN_OUT),
    LOCAL_NEWS_BROWSER("Local Story Calm Browser", "Local News", "Comfortable pace through community news & bulletins", HumanizeCategory.NEWS_FEED, ScrollArchetype.TOUCH_MOMENTUM, 1.09f, 0.84f, 3400L, 6700L, 15, 1, 1, 38, HumanizeEasing.SINE_IN_OUT),
    TREND_DIGEST_SWIPER("Trending Digest Reader", "Trend Digest", "Gentle card-by-card swiping with pause on interesting items", HumanizeCategory.NEWS_FEED, ScrollArchetype.BURST_AND_PAUSE, 1.02f, 0.91f, 3100L, 6100L, 17, 1, 2, 35, HumanizeEasing.SOFT_DECEL),
    INTERVIEW_QNA_STEPPER("Interview Q&A Reader", "Interview Q&A", "Alternates smooth stops at questions and longer pauses on answers", HumanizeCategory.NEWS_FEED, ScrollArchetype.SECTION_SNAPPER, 1.12f, 0.83f, 3800L, 7400L, 16, 1, 1, 41, HumanizeEasing.CUBIC_IN_OUT),
    LISTICLE_ITEM_READER("Top-10 Listicle Reader", "Listicle", "Pauses consistently on each numbered heading and blurb", HumanizeCategory.NEWS_FEED, ScrollArchetype.SECTION_SNAPPER, 1.06f, 0.89f, 3400L, 6600L, 18, 1, 1, 37, HumanizeEasing.QUINTIC_IN_OUT),
    MAGAZINE_FEATURE_PACE("Weekend Magazine Feature", "Magazine", "Relaxed literary pacing for feature journalism", HumanizeCategory.NEWS_FEED, ScrollArchetype.CAREFUL_READER, 1.18f, 0.75f, 4300L, 8300L, 15, 1, 1, 47, HumanizeEasing.SINE_IN_OUT),
    EDITORIAL_PAUSE_READER("Editorial Deep Reader", "Editorial", "Slow considered pacing with re-read pause on key quotes", HumanizeCategory.NEWS_FEED, ScrollArchetype.CAREFUL_READER, 1.21f, 0.72f, 4500L, 8700L, 20, 1, 1, 46, HumanizeEasing.SINE_IN_OUT),
    PODCAST_NOTES_SKIMMER("Show-Notes & Links Reader", "Show Notes", "Scans timestamps and guest highlights smoothly", HumanizeCategory.NEWS_FEED, ScrollArchetype.SKIMMER_SCANNER, 1.01f, 0.92f, 2900L, 5800L, 21, 1, 2, 34, HumanizeEasing.CUBIC_IN_OUT),
    EVENT_SCHEDULE_SCANNER("Event Agenda Scanner", "Agenda Scan", "Checks session blocks calmly and double-checks timings", HumanizeCategory.NEWS_FEED, ScrollArchetype.ZIGZAG_EXPLORER, 1.06f, 0.85f, 3300L, 6500L, 26, 1, 1, 38, HumanizeEasing.SOFT_DECEL),
    WEEKEND_FEATURE_DRIFTER("Sunday Longform Drifter", "Sunday Read", "Slow scenic reading speed with zero urgency", HumanizeCategory.NEWS_FEED, ScrollArchetype.TOUCH_MOMENTUM, 1.22f, 0.74f, 4600L, 8900L, 16, 1, 1, 48, HumanizeEasing.SINE_IN_OUT),
    CURATED_ESSAY_BROWSER("Curated Culture Reader", "Culture Blog", "Balanced reading glides hovering over pull-quotes and links", HumanizeCategory.NEWS_FEED, ScrollArchetype.CAREFUL_READER, 1.15f, 0.78f, 4100L, 7800L, 18, 1, 1, 44, HumanizeEasing.QUINTIC_IN_OUT),

    // Category 5: Shopping, Product & Reviews (15 options)
    PRODUCT_SPEC_INSPECTOR("Product Spec Inspector", "Product Spec", "Scrolls carefully down feature bullets and spec rows", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.SECTION_SNAPPER, 1.11f, 0.81f, 3700L, 7300L, 23, 1, 1, 40, HumanizeEasing.CUBIC_IN_OUT),
    USER_REVIEW_RE_READER("Customer Review Reader", "User Reviews", "Reads customer reviews slowly and scrolls back to compare ratings", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.ZIGZAG_EXPLORER, 1.14f, 0.80f, 3900L, 7600L, 29, 1, 1, 42, HumanizeEasing.SOFT_DECEL),
    PROS_CONS_BALANCER("Pros & Cons Comparer", "Pros & Cons", "Alternates smoothly between pros, cons, and summary verdict", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.ZIGZAG_EXPLORER, 1.09f, 0.84f, 3500L, 6900L, 31, 1, 1, 38, HumanizeEasing.CUBIC_IN_OUT),
    STAR_RATING_COMPARER("Ratings & Trust Checker", "Rating Check", "Checks review scores, hovers stars/badges, pauses thoughtfully", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.BURST_AND_PAUSE, 1.12f, 0.82f, 3800L, 7400L, 24, 1, 2, 36, HumanizeEasing.QUINTIC_IN_OUT),
    WARRANTY_POLICY_CHECKER("Warranty & Return Checker", "Policy Check", "Careful slow inspection of terms, guarantees, and footer notes", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.CAREFUL_READER, 1.22f, 0.67f, 4400L, 8600L, 22, 1, 1, 48, HumanizeEasing.SINE_IN_OUT),
    PRICING_TIER_SCRUTINIZER("Pricing Plan Scrutinizer", "Pricing Tier", "Long pauses over plan tiers and feature comparison grids", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.ZIGZAG_EXPLORER, 1.18f, 0.76f, 4300L, 8500L, 28, 1, 1, 40, HumanizeEasing.SOFT_DECEL),
    FAQ_BUYER_HESITATOR("Careful Pre-Buy Hesitator", "Pre-Buy Read", "Re-checks highlights, FAQs, and testimonials before deciding", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.ZIGZAG_EXPLORER, 1.16f, 0.79f, 4000L, 7800L, 33, 1, 1, 43, HumanizeEasing.SINE_IN_OUT),
    SHIPPING_INFO_LOOKUP("Delivery & Details Checker", "Delivery Info", "Scrolls cleanly to logistics sections and reads thoroughly", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.SECTION_SNAPPER, 1.07f, 0.86f, 3400L, 6700L, 20, 1, 1, 35, HumanizeEasing.CUBIC_IN_OUT),
    BUYER_GUIDE_READER("Buying Guide Deep Reader", "Buyer Guide", "Moves smoothly through top picks, criteria, and advice", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.CAREFUL_READER, 1.15f, 0.78f, 4100L, 8000L, 19, 1, 1, 44, HumanizeEasing.QUINTIC_IN_OUT),
    UNBOXING_POST_BROWSER("Hands-On Review Browser", "Hands-On", "Calm visual and text pacing across impressions sections", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.TOUCH_MOMENTUM, 1.08f, 0.85f, 3500L, 6800L, 18, 1, 1, 39, HumanizeEasing.SOFT_DECEL),
    GIFT_LIST_EVALUATOR("Curated Picks Evaluator", "Gift Picks", "Stops gracefully on each featured item and price note", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.SECTION_SNAPPER, 1.06f, 0.88f, 3300L, 6500L, 21, 1, 1, 37, HumanizeEasing.CUBIC_IN_OUT),
    DEAL_TERMS_CHECKER("Offer & Discount Verifier", "Offer Terms", "Short controlled glides checking eligibility and fine details", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.CAREFUL_READER, 1.19f, 0.71f, 4200L, 8200L, 24, 1, 1, 46, HumanizeEasing.SINE_IN_OUT),
    BRAND_STORY_READER("Brand About & Mission Read", "Brand Story", "Warm, patient read-through of brand background and values", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.CAREFUL_READER, 1.14f, 0.77f, 3900L, 7500L, 14, 1, 1, 45, HumanizeEasing.SINE_IN_OUT),
    CUSTOMER_PHOTO_PAUSER("Gallery Review Inspector", "Review Photos", "Pauses longer between visual sections and written feedback", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.BURST_AND_PAUSE, 1.13f, 0.83f, 4000L, 7900L, 20, 1, 2, 36, HumanizeEasing.QUINTIC_IN_OUT),
    SIDE_BY_SIDE_CHECKER("Alternative Option Comparer", "Alt Comparer", "Revisits previous product options during bottom-to-mid review", HumanizeCategory.SHOPPING_REVIEW, ScrollArchetype.ZIGZAG_EXPLORER, 1.10f, 0.84f, 3600L, 7100L, 30, 1, 1, 39, HumanizeEasing.CUBIC_IN_OUT),

    // Category 6: Visual, Gallery & Media (13 options)
    CAPTION_UNDER_IMAGE("Image & Caption Reader", "Img Caption", "Stops neatly so captions and visuals sit comfortably in center", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.SECTION_SNAPPER, 1.12f, 0.82f, 3700L, 7200L, 16, 1, 1, 40, HumanizeEasing.QUINTIC_IN_OUT),
    INFOGRAPHIC_ZOOM_PAUSE("Infographic Slow Pauser", "Infographic", "Takes extra-long visual pauses as graphics pass through viewport", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.CAREFUL_READER, 1.24f, 0.72f, 4800L, 9200L, 18, 1, 1, 44, HumanizeEasing.SINE_IN_OUT),
    PORTFOLIO_SHOWCASE_GLIDE("Creative Portfolio Glide", "Portfolio", "Smooth designer-style gliding across showcase cards", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.TOUCH_MOMENTUM, 1.14f, 0.85f, 3800L, 7500L, 19, 1, 1, 42, HumanizeEasing.QUINTIC_IN_OUT),
    ARCHITECTURE_BLOG_PACE("Architecture Showcase Pace", "Architecture", "Calm contemplation pauses over photos and design commentary", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.CAREFUL_READER, 1.21f, 0.76f, 4500L, 8800L, 15, 1, 1, 46, HumanizeEasing.SINE_IN_OUT),
    TRAVEL_DIARY_EXPLORER("Travel Diary & Guide", "Travel Diary", "Explores itinerary days, peeks back at map notes, lingers calmly", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.ZIGZAG_EXPLORER, 1.13f, 0.83f, 3900L, 7600L, 23, 1, 1, 41, HumanizeEasing.CUBIC_IN_OUT),
    FOOD_BLOG_STORY_PACE("Culinary & Food Story Pace", "Food Story", "Scrolls softly past story intro, lingers on ingredients & steps", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.BURST_AND_PAUSE, 1.10f, 0.86f, 3600L, 7100L, 22, 1, 2, 38, HumanizeEasing.SOFT_DECEL),
    DESIGN_CASE_INSPECTOR("UX Design Case Reader", "UX Case", "Stops carefully at wireframes, mockups, and rationale text", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.SECTION_SNAPPER, 1.17f, 0.79f, 4200L, 8300L, 21, 1, 1, 43, HumanizeEasing.QUINTIC_IN_OUT),
    COMIC_STRIP_STEPPER("Vertical Story Panel Stepper", "Story Panel", "Consistent medium frame steps with steady reading rhythm", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.CAREFUL_READER, 1.11f, 0.84f, 3500L, 6600L, 12, 1, 1, 36, HumanizeEasing.CUBIC_IN_OUT),
    MAP_EMBED_LINGERER("Location & Guide Lingerer", "Map Guide", "Lingers thoughtfully around address/map sections and details", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.BURST_AND_PAUSE, 1.18f, 0.77f, 4300L, 8400L, 20, 1, 1, 40, HumanizeEasing.SINE_IN_OUT),
    VIDEO_TRANSCRIPT_READER("Transcript & Media Reader", "Transcript", "Follows long transcript lines steadily with small eye nudges", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.CAREFUL_READER, 1.15f, 0.73f, 3900L, 7400L, 17, 1, 1, 56, HumanizeEasing.SINE_IN_OUT),
    SLIDESHOW_NOTES_READER("Deck & Slide Notes Reader", "Slide Notes", "Moves smoothly section-by-section with calm contemplation", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.SECTION_SNAPPER, 1.13f, 0.85f, 3900L, 7500L, 16, 1, 1, 39, HumanizeEasing.QUINTIC_IN_OUT),
    PHOTO_ESSAY_DWELLER("Photo Essay Slow Dweller", "Photo Essay", "Extended calm pauses appreciating visual storytelling", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.TOUCH_MOMENTUM, 1.25f, 0.75f, 4900L, 9300L, 15, 1, 1, 42, HumanizeEasing.SINE_IN_OUT),
    LOOKBOOK_SLOW_DRIFT("Style Lookbook Slow Drift", "Lookbook", "Silky low-velocity downward drift with gentle reverse peeks", HumanizeCategory.VISUAL_MEDIA, ScrollArchetype.TOUCH_MOMENTUM, 1.16f, 0.80f, 4000L, 7700L, 21, 1, 1, 45, HumanizeEasing.SOFT_DECEL),

    // Category 7: Long-Stay / High-Dwell Specialists (14 options — built specifically for 60s–300s+ 1-page stays)
    DEEP_DWELL_ZEN("Zen Full-Duration Dweller", "Zen 2m+ Stay", "Ultra-calm long-stay pacing that smoothly traverses & re-reads across full session", HumanizeCategory.LONG_STAY, ScrollArchetype.CAREFUL_READER, 1.32f, 0.68f, 5200L, 9800L, 22, 1, 1, 52, HumanizeEasing.SINE_IN_OUT),
    MARATHON_ARTICLE_READER("Marathon Long-Form Reader", "Marathon Read", "Paced for 2–5 minute articles without hitting bottom prematurely", HumanizeCategory.LONG_STAY, ScrollArchetype.CAREFUL_READER, 1.28f, 0.70f, 4900L, 9400L, 20, 1, 1, 50, HumanizeEasing.QUINTIC_IN_OUT),
    TWO_MINUTE_CONCENTRATOR("2-Minute Focused Concentrator", "2m Focus", "Designed for 120s single-page dwell with calm bidirectional re-reads", HumanizeCategory.LONG_STAY, ScrollArchetype.CAREFUL_READER, 1.26f, 0.72f, 4800L, 9100L, 24, 1, 1, 48, HumanizeEasing.SINE_IN_OUT),
    THREE_MINUTE_SCHOLAR("3-Minute Deep Scholar", "3m Scholar", "Extended analytical reading pauses + structured section revisits", HumanizeCategory.LONG_STAY, ScrollArchetype.SECTION_SNAPPER, 1.35f, 0.67f, 5400L, 10200L, 26, 1, 1, 46, HumanizeEasing.QUINTIC_IN_OUT),
    THOROUGH_AUDITOR("Complete Page Auditor", "Full Auditor", "Reads top-to-bottom calmly then reviews key upper sections smoothly", HumanizeCategory.LONG_STAY, ScrollArchetype.ZIGZAG_EXPLORER, 1.24f, 0.74f, 4600L, 8800L, 28, 1, 1, 44, HumanizeEasing.CUBIC_IN_OUT),
    SLOW_BREATH_CADENCE("Slow Breath Natural Cadence", "Slow Cadence", "Gentle rhythmic scroll-pause cadence matching relaxed human breathing", HumanizeCategory.LONG_STAY, ScrollArchetype.TOUCH_MOMENTUM, 1.30f, 0.69f, 5000L, 9500L, 18, 1, 1, 54, HumanizeEasing.SINE_IN_OUT),
    RE_READ_HIGHLIGHTER("Re-Read & Hover Highlighter", "Highlighter", "Hovers real paragraphs while pausing, then slowly re-checks above", HumanizeCategory.LONG_STAY, ScrollArchetype.ZIGZAG_EXPLORER, 1.22f, 0.71f, 4700L, 8900L, 31, 1, 1, 48, HumanizeEasing.SOFT_DECEL),
    FOOTER_TO_TOP_RECHECK("Bidirectional Calm Re-Checker", "Bidirectional", "Reads calmly down to footer, pauses, then glides smoothly back upward", HumanizeCategory.LONG_STAY, ScrollArchetype.TOUCH_MOMENTUM, 1.25f, 0.73f, 4600L, 8700L, 22, 1, 1, 45, HumanizeEasing.QUINTIC_IN_OUT),
    MIDPOINT_THOUGHT_PAUSE("Deep Midpoint Reflector", "Midpoint Rest", "Extra reflective pauses midway through long pages", HumanizeCategory.LONG_STAY, ScrollArchetype.BURST_AND_PAUSE, 1.29f, 0.70f, 5100L, 9700L, 19, 1, 1, 50, HumanizeEasing.SINE_IN_OUT),
    ANCHOR_HOP_GENTLE("Gentle Anchor Stepper", "Gentle Anchor", "Smoothly aligns each major section without ever rushing across screens", HumanizeCategory.LONG_STAY, ScrollArchetype.SECTION_SNAPPER, 1.23f, 0.75f, 4600L, 8800L, 20, 1, 1, 42, HumanizeEasing.QUINTIC_IN_OUT),
    END_TO_END_COMPLETE("End-to-End Page Companion", "End-to-End", "Balanced single-page companion tuned to fill the full countdown cleanly", HumanizeCategory.LONG_STAY, ScrollArchetype.CAREFUL_READER, 1.24f, 0.72f, 4500L, 8600L, 21, 1, 1, 46, HumanizeEasing.CUBIC_IN_OUT),
    DISTRACTED_COFFEE_PAUSE("Coffee-Break Distracted Reader", "Coffee Pause", "Includes realistic longer natural pauses between calm thumb glides", HumanizeCategory.LONG_STAY, ScrollArchetype.BURST_AND_PAUSE, 1.38f, 0.68f, 5600L, 10500L, 17, 1, 1, 40, HumanizeEasing.SOFT_DECEL),
    MULTI_PARAGRAPH_ANCHOR("Paragraph-by-Paragraph Dwell", "Para Dwell", "Moves gently one paragraph at a time for high-dwell engagement", HumanizeCategory.LONG_STAY, ScrollArchetype.CAREFUL_READER, 1.27f, 0.65f, 4800L, 9200L, 19, 1, 1, 55, HumanizeEasing.SINE_IN_OUT),
    PATIENT_RESEARCH_TWO_MIN("Patient 2m+ Researcher", "2m+ Research", "Smooth calm scrolling + section inspection + zero hurry across long timers", HumanizeCategory.LONG_STAY, ScrollArchetype.ZIGZAG_EXPLORER, 1.31f, 0.71f, 5100L, 9600L, 27, 1, 1, 47, HumanizeEasing.QUINTIC_IN_OUT);

    companion object {
        val concreteStyles: List<HumanizeScrollStyle> = entries.filter { it != AUTO }

        fun fromRaw(raw: String?): HumanizeScrollStyle {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO
        }
    }
}

data class ResolvedScrollPersona(
    val style: HumanizeScrollStyle,
    val archetype: ScrollArchetype,
    val easing: HumanizeEasing,
    val label: String,
    val paceMultiplier: Float,
    val amplitudeMultiplier: Float,
    val backtrackChancePct: Int,
    val burstCountMin: Int,
    val burstCountMax: Int,
    val pauseMinMs: Long,
    val pauseMaxMs: Long,
    val eyeNudgeChancePct: Int,
    val hoverRealElements: Boolean,
    val clickRatioPercent: Int
)

object HumanizeBehaviorResolver {

    fun resolve(profile: BrowserProfileEntity?): ResolvedScrollPersona {
        val rawStyle = HumanizeScrollStyle.fromRaw(profile?.humanizeScrollStyle)
        val seed = if (profile != null) {
            (profile.id * 1_000_003L) xor
                profile.canvasSeed xor
                (profile.audioSeed shl 7) xor
                profile.webViewProfileName.hashCode().toLong()
        } else {
            System.nanoTime()
        }
        val seededRng = Random(seed)

        val concreteStyle = if (rawStyle == HumanizeScrollStyle.AUTO) {
            val concrete = HumanizeScrollStyle.concreteStyles
            val indexBase = if (profile != null && profile.id > 0L) {
                ((profile.id - 1L) % concrete.size.toLong()).toInt()
            } else {
                seededRng.nextInt(concrete.size)
            }
            concrete[indexBase]
        } else {
            rawStyle
        }

        // Per-profile micro-variance layered around the preset's natural calm cadence (never hyper-fast)
        val paceVariance = 0.94f + seededRng.nextFloat() * 0.24f      // 0.94x .. 1.18x
        val ampVariance = 0.86f + seededRng.nextFloat() * 0.28f       // 0.86x .. 1.14x
        val paceMultiplier = (concreteStyle.paceFactor * paceVariance).coerceIn(0.92f, 1.65f)
        val amplitudeMultiplier = (concreteStyle.amplitudeFactor * ampVariance).coerceIn(0.50f, 1.25f)
        val pauseMin = concreteStyle.pauseMinMs.coerceAtLeast(2200L)
        val pauseMax = concreteStyle.pauseMaxMs.coerceAtLeast(pauseMin + 1200L)
        val backtrackPct = (concreteStyle.backtrackPct + seededRng.nextInt(-3, 5)).coerceIn(6, 42)
        val clickRatioPercent = seededRng.nextInt(42, 75)

        return ResolvedScrollPersona(
            style = concreteStyle,
            archetype = concreteStyle.archetype,
            easing = concreteStyle.easing,
            label = concreteStyle.shortLabel,
            paceMultiplier = paceMultiplier,
            amplitudeMultiplier = amplitudeMultiplier,
            backtrackChancePct = backtrackPct,
            burstCountMin = concreteStyle.burstMin.coerceIn(1, 2),
            burstCountMax = concreteStyle.burstMax.coerceIn(concreteStyle.burstMin, 2),
            pauseMinMs = pauseMin,
            pauseMaxMs = pauseMax,
            eyeNudgeChancePct = concreteStyle.eyeNudgePct.coerceIn(15, 75),
            hoverRealElements = true,
            clickRatioPercent = clickRatioPercent
        )
    }
}
