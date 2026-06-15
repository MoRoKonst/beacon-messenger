package com.bcon.messenger

import android.content.Context

object ServerManager {

    private const val PREFS_NAME = "server_prefs"
    private const val KEY_SERVERS = "server_list"
    private const val KEY_CURRENT = "current_server"

    data class Server(
        val host: String,
        val port: Int = 9000,
        val name: String = "",
        val enabled: Boolean = true,
        val path: String = ""
    ) {
        fun toWssUrl(): String {
            // Если хост уже содержит протокол — разобрать через Uri и пересобрать правильно
            if (host.contains("://")) {
                return try {
                    val uri = android.net.Uri.parse(host)
                    val h = uri.host?.takeIf { it.isNotEmpty() } ?: host
                    val p = if (uri.port != -1) uri.port else port
                    val rawPath = uri.path?.trim('/') ?: ""
                    val finalPath = rawPath.ifEmpty { path }
                    val scheme = if (uri.scheme == "ws") "ws" else "wss"
                    if (finalPath.isNotEmpty()) "$scheme://$h:$p/$finalPath"
                    else "$scheme://$h:$p"
                } catch (e: Exception) {
                    host
                }
            }
            val suffix = if (path.isNotEmpty()) "/$path" else ""
            // Локальный сервер — без TLS
            if (host == "10.0.2.2" ||
                host == "localhost" ||
                host == "127.0.0.1" ||
                host.startsWith("192.168.") ||
                host.startsWith("10.0.") ||
                host.startsWith("172.16.") ||
                host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"))) {
                return "ws://$host:$port$suffix"
            }
            // Продакшн — TLS
            return if (port == 9000) "wss://$host$suffix" else "wss://$host:$port$suffix"
        }
    }

    fun getServers(context: Context): List<Server> {
        val prefs = EncryptedStorage.getEncryptedPrefs(context, PREFS_NAME)
        val json = prefs.getString(KEY_SERVERS, null) ?: return getDefaultServers()
        return try {
            val array = org.json.JSONArray(json)
            val servers = (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Server(
                    host    = obj.getString("host"),
                    port    = obj.optInt("port", 443),
                    name    = obj.optString("name", ""),
                    enabled = obj.optBoolean("enabled", true),
                    path    = obj.optString("path", "")
                )
            }
            // Миграция: заменяем устаревший адрес сервера на актуальный
            val defaults = getDefaultServers()
            val migrated = servers.map { s ->
                if (s.host == "beacon-app.org") defaults.first() else s
            }
            if (migrated != servers) saveServers(context, migrated)
            migrated
        } catch (e: Exception) {
            getDefaultServers()
        }
    }

    fun saveServers(context: Context, servers: List<Server>) {
        val prefs = EncryptedStorage.getEncryptedPrefs(context, PREFS_NAME)
        val array = org.json.JSONArray()
        servers.forEach { server ->
            array.put(org.json.JSONObject().apply {
                put("host", server.host)
                put("port", server.port)
                put("name", server.name)
                put("enabled", server.enabled)
                put("path", server.path)
            })
        }
        prefs.edit().putString(KEY_SERVERS, array.toString()).apply()
    }

    fun getCurrentServer(context: Context): Server? {
        val prefs = EncryptedStorage.getEncryptedPrefs(context, PREFS_NAME)
        val index = prefs.getInt(KEY_CURRENT, 0)
        val servers = getServers(context).filter { it.enabled }
        return servers.getOrNull(index)
    }

    fun switchToNext(context: Context): Server? {
        val prefs = EncryptedStorage.getEncryptedPrefs(context, PREFS_NAME)
        val servers = getServers(context).filter { it.enabled }
        if (servers.isEmpty()) return null
        val current = prefs.getInt(KEY_CURRENT, 0)
        val next = (current + 1) % servers.size
        prefs.edit().putInt(KEY_CURRENT, next).apply()
        return servers[next]
    }

    private fun getDefaultServers() = listOf(
        Server("api.beacon-app.org", 443, "Основной сервер", true, "ws")
    )

    fun addServer(context: Context, server: Server) {
        val servers = getServers(context).toMutableList()
        servers.add(server)
        saveServers(context, servers)
    }

    fun removeServer(context: Context, index: Int) {
        val servers = getServers(context).toMutableList()
        if (index in servers.indices) {
            servers.removeAt(index)
            saveServers(context, servers)
        }
    }
}
