package com.agentport.jetbrains

import com.google.gson.JsonParser

object RegistryClient {

    private const val URL =
        "https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json"

    fun fetchAgents(): List<RegistryAgent> = parse(fetch())

    private fun fetch(): String =
        java.net.URL(URL).openConnection().apply {
            connectTimeout = 5_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "jetbrains-acp/0.2.0")
        }.getInputStream().bufferedReader().readText()

    private fun parse(json: String): List<RegistryAgent> {
        val root = JsonParser.parseString(json).asJsonObject
        val arr  = root.getAsJsonArray("agents") ?: return emptyList()
        return arr.mapNotNull { el ->
            runCatching {
                val obj  = el.asJsonObject
                val dist = obj.getAsJsonObject("distribution") ?: return@mapNotNull null
                val npx  = dist.getAsJsonObject("npx")
                val bin  = dist.getAsJsonObject("binary")
                RegistryAgent(
                    id               = obj.get("id")?.asString ?: return@mapNotNull null,
                    name             = obj.get("name")?.asString ?: return@mapNotNull null,
                    version          = obj.get("version")?.asString ?: "",
                    description      = obj.get("description")?.asString ?: "",
                    repository       = obj.get("repository")?.asString ?: "",
                    icon             = obj.get("icon")?.asString,
                    distributionType = when {
                        npx != null -> "npx"
                        bin != null -> "binary"
                        else        -> "unknown"
                    },
                    npxPackage = npx?.get("package")?.asString,
                    npxArgs    = npx?.getAsJsonArray("args")
                        ?.map { it.asString } ?: emptyList(),
                )
            }.getOrNull()
        }
    }
}
