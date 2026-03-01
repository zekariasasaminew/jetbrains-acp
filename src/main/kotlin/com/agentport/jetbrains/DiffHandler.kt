package com.agentport.jetbrains

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DiffHandler(private val project: Project) {

    suspend fun showAndApply(path: String, newContent: String): Boolean {
        val file = File(path)
        val oldContent = if (file.exists()) file.readText() else ""
        if (oldContent == newContent) return true

        return suspendCoroutine { cont ->
            ApplicationManager.getApplication().invokeLater {
                val contentFactory = DiffContentFactory.getInstance()
                val oldDoc = contentFactory.create(oldContent)
                val newDoc = contentFactory.create(newContent)
                val request = SimpleDiffRequest(file.name, oldDoc, newDoc, "Current", "Agent Proposed")

                request.putUserData(com.intellij.diff.util.DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS, booleanArrayOf(true, false))

                val future = CompletableFuture<Boolean>()
                DiffManager.getInstance().showDiff(project, request)

                // After the diff window closes, write the new content
                future.complete(applyContent(path, newContent))
                cont.resume(future.get())
            }
        }
    }

    private fun applyContent(path: String, content: String): Boolean {
        val vFile = LocalFileSystem.getInstance().findFileByPath(path)
            ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            ?: return run { File(path).writeText(content); true }

        val doc = FileDocumentManager.getInstance().getDocument(vFile) ?: return run { File(path).writeText(content); true }
        WriteCommandAction.runWriteCommandAction(project) {
            doc.replaceString(0, doc.textLength, content)
        }
        return true
    }
}
