package com.pochipay.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * BroadcastReceiver for handling download completion events.
 * Uses goAsync() to allow async processing without blocking the main thread.
 */
class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != -1L) {
                // Use goAsync() to allow async work in BroadcastReceiver
                val pendingResult = goAsync()
                
                // Create a coroutine scope with SupervisorJob for this operation
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val query = DownloadManager.Query().setFilterById(id)
                        downloadManager.query(query).use { cursor ->
                            if (cursor.moveToFirst()) {
                                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                if (statusIndex != -1) {
                                    val status = cursor.getInt(statusIndex)
                                    Timber.d("Download complete: id=$id, status=$status")
                                    UpdateEventManager.emitEvent(
                                        UpdateDownloadEvent.DownloadCompleted(id, status)
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing download complete event for id=$id")
                    } finally {
                        // Always finish the pending result
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
