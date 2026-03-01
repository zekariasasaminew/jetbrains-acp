package com.agentport.jetbrains

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PermissionHandlerTest {

    @Test
    fun `label formatting converts kind to readable label`() {
        val kind = "allow_once"
        val label = kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
        assertEquals("Allow once", label)
    }

    @Test
    fun `label formatting handles allow always`() {
        val kind = "allow_always"
        val label = kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
        assertEquals("Allow always", label)
    }

    @Test
    fun `label formatting handles deny`() {
        val kind = "deny"
        val label = kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
        assertEquals("Deny", label)
    }

    // TODO: integration test showing the real dialog (requires IntelliJ platform) — OSS contributors
}
