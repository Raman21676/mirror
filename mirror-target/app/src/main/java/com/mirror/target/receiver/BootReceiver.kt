package com.mirror.target.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mirror.target.service.MirrorTargetService
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.i("Boot completed - starting service")
            context.startForegroundService(
                Intent(context, MirrorTargetService::class.java)
                    .setAction(MirrorTargetService.ACTION_START)
            )
        }
    }
}
