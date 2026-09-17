package com.daksh.webdav

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class WebDAVPlugin : Plugin() {
    companion object {
        private const val PREFS_NAME = "webdav_provider_preferences"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"

        private var appContext: Context? = null

        fun init(context: Context) {
            appContext = context.applicationContext ?: context
        }

        fun getServerUrl(): String {
            val ctx = appContext ?: return ""
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_SERVER_URL, "")?.trim()?.trimEnd('/') ?: ""
        }

        fun getUsername(): String {
            val ctx = appContext ?: return ""
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_USERNAME, "")?.trim() ?: ""
        }

        fun getPassword(): String {
            val ctx = appContext ?: return ""
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_PASSWORD, "") ?: ""
        }

        fun saveConfig(context: Context, url: String, user: String, pass: String) {
            val ctx = context.applicationContext ?: context
            appContext = ctx
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_SERVER_URL, url.trim().trimEnd('/'))
                .putString(KEY_USERNAME, user.trim())
                .putString(KEY_PASSWORD, pass)
                .apply()
        }
    }

    override fun load(context: Context) {
        init(context)
        registerMainAPI(WebDAVProvider())

        this.openSettings = { ctx ->
            showSettingsDialog(ctx)
        }
    }

    private fun showSettingsDialog(context: Context) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val urlLabel = TextView(context).apply {
            text = "WebDAV Server URL:"
            textSize = 14f
        }
        val urlInput = EditText(context).apply {
            hint = "https://example.com/dav"
            setText(getServerUrl())
            setSingleLine(true)
        }

        val userLabel = TextView(context).apply {
            text = "Username (optional):"
            textSize = 14f
            val topPad = (10 * context.resources.displayMetrics.density).toInt()
            setPadding(0, topPad, 0, 0)
        }
        val userInput = EditText(context).apply {
            hint = "Username"
            setText(getUsername())
            setSingleLine(true)
        }

        val passLabel = TextView(context).apply {
            text = "Password (optional):"
            textSize = 14f
            val topPad = (10 * context.resources.displayMetrics.density).toInt()
            setPadding(0, topPad, 0, 0)
        }
        val passInput = EditText(context).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(getPassword())
            setSingleLine(true)
        }

        layout.addView(urlLabel)
        layout.addView(urlInput)
        layout.addView(userLabel)
        layout.addView(userInput)
        layout.addView(passLabel)
        layout.addView(passInput)

        AlertDialog.Builder(context)
            .setTitle("WebDAV Configuration")
            .setMessage("Enter your WebDAV server details:")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = urlInput.text.toString().trim()
                val newUser = userInput.text.toString().trim()
                val newPass = passInput.text.toString()

                saveConfig(context, newUrl, newUser, newPass)
                Toast.makeText(context, "WebDAV settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
