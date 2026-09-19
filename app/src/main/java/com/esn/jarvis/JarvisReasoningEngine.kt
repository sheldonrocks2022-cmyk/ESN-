package com.esn.jarvis

import android.content.Context

interface JarvisReasoningEngine {
    fun plan(
        context: Context,
        goal: String,
        state: AgentSnapshot,
        history: String
    ): List<AgentStep>
}

object JarvisOnDeviceReasoner : JarvisReasoningEngine {
    override fun plan(
        context: Context,
        goal: String,
        state: AgentSnapshot,
        history: String
    ): List<AgentStep> {
        val modelOutput = JarvisModelRuntime.complete(
            context,
            buildPrompt(goal, state, history)
        )
        if (!modelOutput.isNullOrBlank()) {
            val modelPlan = parsePlan(modelOutput)
            if (modelPlan.isNotEmpty()) return modelPlan
        }

        val localPlan = JarvisLocalPlanner.plan(goal, state)
        if (localPlan.isNotEmpty()) return localPlan

        val goalWords = goal.lowercase()
            .split(Regex("\\W+"))
            .filter { it.length > 2 }
            .toSet()

        val target = state.text
            .filter { it.length in 2..60 }
            .map { label ->
                val score = label.lowercase()
                    .split(Regex("\\W+"))
                    .count { it in goalWords }
                label to score
            }
            .maxByOrNull { it.second }

        return if (target != null && target.second >= 2) {
            listOf(AgentStep.Tap(target.first))
        } else {
            emptyList()
        }
    }

    private fun buildPrompt(
        goal: String,
        state: AgentSnapshot,
        history: String
    ): String {
        return buildString {
            appendLine("Goal: $goal")
            appendLine("Package: ${state.packageName}")
            appendLine("Visible: ${state.text.joinToString(" | ").take(5000)}")
            appendLine("Controls: ${JarvisScreenInspector.semanticSummary().take(3000)}")
            appendLine("Recent: ${history.takeLast(1500)}")
            appendLine("Learned: ${JarvisAgentMemory.relevant(context,goal).take(1800)}")
            append("Return only lines: TAP <label>, TYPE <text>, BACK, SCROLL, HOME, RECENTS, NOTIFICATIONS, or QUICK_SETTINGS.")
        }
    }

    private fun parsePlan(raw: String): List<AgentStep> {
        val steps = mutableListOf<AgentStep>()
        for (line in raw.lines()) {
            val value = line.trim()
            val step = when {
                value.startsWith("TAP ", ignoreCase = true) ->
                    AgentStep.Tap(value.substring(4).trim())
                value.startsWith("TYPE ", ignoreCase = true) ->
                    AgentStep.Type(value.substring(5).trim())
                value.equals("BACK", ignoreCase = true) ->
                    AgentStep.Back
                value.equals("SCROLL", ignoreCase = true) -> AgentStep.Scroll
                value.equals("HOME", ignoreCase = true) -> AgentStep.Home
                value.equals("RECENTS", ignoreCase = true) -> AgentStep.Recents
                value.equals("NOTIFICATIONS", ignoreCase = true) -> AgentStep.Notifications
                value.equals("QUICK_SETTINGS", ignoreCase = true) -> AgentStep.QuickSettings
                else -> null
            }
            if (step != null) steps.add(step)
            if (steps.size >= 12) break
        }
        return steps
    }
}

object JarvisReasoning {
    @Volatile
    var engine: JarvisReasoningEngine = JarvisOnDeviceReasoner

    fun plan(
        context: Context,
        goal: String,
        state: AgentSnapshot
    ): List<AgentStep> {
        val history = context
            .getSharedPreferences("jarvis_agent", Context.MODE_PRIVATE)
            .getString("history", "")
            .orEmpty()
            .takeLast(3000)
        return engine.plan(context, goal, state, history)
    }
}
