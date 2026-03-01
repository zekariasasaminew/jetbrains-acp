package com.agentport.jetbrains

import com.agentclientprotocol.model.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PermissionHandler(private val project: Project) {

    suspend fun request(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
    ): RequestPermissionResponse = suspendCoroutine { cont ->
        ApplicationManager.getApplication().invokeLater {
            val buttonLabels = permissions.map { it.kind.toString().replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
            val message = "Agent wants to perform: ${toolCall.title ?: "an action"}"
            val chosen = Messages.showDialog(project, message, "Agent Permission Request", buttonLabels, 0, Messages.getQuestionIcon())
            val option = if (chosen >= 0) permissions[chosen] else permissions.last()
            cont.resume(RequestPermissionResponse(RequestPermissionOutcome.Selected(option.optionId), null))
        }
    }
}
