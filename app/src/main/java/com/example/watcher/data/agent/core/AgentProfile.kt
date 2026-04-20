package com.example.watcher.data.agent.core

/** Immutable identity of an agent expert. */
data class AgentProfile(
    val name: String,
    val description: String,
    val persona: String,
    val perspective: String,
    val expertKind: String = "specialist"
)
