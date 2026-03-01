package com.agentport.jetbrains

import com.agentclientprotocol.model.*
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonElement

class DiffHandler(private val project: Project) {
    // TODO: Implement diff viewer using IntelliJ DiffManager — feat/diff-handler
    suspend fun showAndApply(path: String, newContent: String): Boolean {
        val file = java.io.File(path)
        file.writeText(newContent)
        return true
    }
}
