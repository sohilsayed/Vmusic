// File: java/com/example/holodex/background/SyncLogger.kt
package com.example.holodex.background

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class LogAction {
    // Upstream
    UPSTREAM_DELETE_SUCCESS,
    UPSTREAM_DELETE_FAILED,
    UPSTREAM_UPSERT_SUCCESS,
    UPSTREAM_UPSERT_FAILED,
    // Downstream
    DOWNSTREAM_INSERT_LOCAL,
    DOWNSTREAM_DELETE_LOCAL,
    DOWNSTREAM_UPDATE_LOCAL,
    // Reconciliation
    RECONCILE_SKIP
}

@Singleton
class SyncLogger @Inject constructor() {
    private val TAG = "SYNC"

    fun startSection(name: String) {
        Timber.tag(TAG).i("====== STARTING $name SYNC ======")
    }

    fun endSection(name: String, success: Boolean) {
        val status = if (success) "SUCCESSFUL" else "FAILED"
        Timber.tag(TAG).i("====== $name SYNC $status ======")
    }

    fun info(message: String) {
        Timber.tag(TAG).i(message)
    }

    fun warning(message: String) {
        Timber.tag(TAG).w(message)
    }

    fun error(throwable: Throwable, message: String) {
        Timber.tag(TAG).e(throwable, message)
    }

    fun logItemAction(
        action: LogAction,
        itemName: String?,
        localId: Long?,
        serverId: String?,
        reason: String? = null
    ) {
        val formattedName = "'${itemName ?: "Unknown"}'"
        val formattedIds = "(LID: ${localId ?: "N/A"}, SID: ${serverId ?: "N/A"})"
        val formattedReason = reason?.let { " | Reason: $it" } ?: ""
        Timber.tag(TAG).d("-> [${action.name}] Item: $formattedName $formattedIds$formattedReason")
    }
}