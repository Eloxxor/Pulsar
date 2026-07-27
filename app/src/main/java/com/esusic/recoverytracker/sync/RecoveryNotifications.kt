package com.esusic.recoverytracker.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.esusic.recoverytracker.MainActivity
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.ui.presentation.recoveryStatus

object RecoveryNotifications {
    const val MORNING_CHANNEL_ID = "morning_recovery"
    private const val MORNING_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            MORNING_CHANNEL_ID,
            context.getString(R.string.notification_channel_morning_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_morning_description)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    // POST_NOTIFICATIONS is runtime-gated from API 33. Nothing in this package owns UI, so
    // an ungranted permission means silently skipping - the request has to come from a screen.
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun postMorningSummary(context: Context, recoveryValue: Int, isCalibrating: Boolean) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, MORNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.recovery))
            .setContentTitle(
                context.getString(R.string.notification_morning_title, recoveryValue)
            )
            .setContentText(morningSummaryText(context, recoveryValue, isCalibrating))
            .setContentIntent(launchIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(MORNING_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    fun morningSummaryText(context: Context, recoveryValue: Int, isCalibrating: Boolean): String {
        val guidance = context.getString(
            when {
                recoveryValue >= 80 -> R.string.notification_guidance_high
                recoveryValue >= 60 -> R.string.notification_guidance_moderate
                recoveryValue >= 40 -> R.string.notification_guidance_light
                else -> R.string.notification_guidance_rest
            }
        )
        if (isCalibrating) {
            return context.getString(R.string.notification_morning_calibrating, guidance)
        }
        return context.getString(
            R.string.notification_morning_summary,
            context.getString(recoveryStatus(recoveryValue, false).label),
            guidance
        )
    }

    private fun launchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
