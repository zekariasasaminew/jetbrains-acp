package com.agentport.jetbrains

import com.agentclientprotocol.model.*
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonElement

class PermissionHandler(private val project: Project) {
    // TODO: Implement native IntelliJ dialog — feat/permission-handler
    suspend fun request(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
    ): RequestPermissionResponse {
        val chosen = permissions.firstOrNull { it.kind == "allow_once" } ?: permissions.first()
        return RequestPermissionResponse(RequestPermissionOutcome.Selected(chosen.optionId), null)
    }
}
