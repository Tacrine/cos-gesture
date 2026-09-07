package com.cos.lspit.gesture.config

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Read-only config channel queried by the SystemUI-side GestureConfigClient,
 * plus a status insert endpoint the hook uses to report its state back.
 * Runs inside the module APK only; SystemUI reaches it cross-process via
 * the exported grant.
 */
class ConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.cos.lspit.gesture.config"
        val CONFIG_URI: Uri = Uri.parse("content://$AUTHORITY/config")
        val STATUS_URI: Uri = Uri.parse("content://$AUTHORITY/status")
        val CONFIG_COLUMNS = arrayOf("master", "left", "right", "version")
        private const val STATUS_PREFS = "gesture_status"
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (uri.path != "/config") return MatrixCursor(CONFIG_COLUMNS)
        val config = context?.let { ConfigStore.load(it) } ?: GestureConfig()
        return MatrixCursor(CONFIG_COLUMNS).apply {
            addRow(
                listOf(
                    if (config.masterEnabled) 1 else 0,
                    if (config.leftEnabled) 1 else 0,
                    if (config.rightEnabled) 1 else 0,
                    config.version,
                ),
            )
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (uri.path != "/status") throw IllegalArgumentException("Unsupported insert: $uri")
        val ctx = context ?: return null
        ctx.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("outcome", values?.getAsString("outcome") ?: "UNKNOWN")
            .putString("detail", values?.getAsString("detail") ?: "")
            .putLong("at_millis", values?.getAsLong("at_millis") ?: System.currentTimeMillis())
            .apply()
        return uri
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? = null
}
