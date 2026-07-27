package com.esusic.recoverytracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.esusic.recoverytracker.MainActivity
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecoveryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            val data = try {
                RecoveryWidgetData.load(ServiceLocator.repository)
            } catch (e: Exception) {
                RecoveryWidgetData.EMPTY
            }
            val views = buildViews(appContext, data)
            appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            pendingResult.finish()
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refresh(context: Context) {
            val appContext = context.applicationContext
            val component = ComponentName(appContext, RecoveryWidgetProvider::class.java)
            val ids = AppWidgetManager.getInstance(appContext).getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                this.component = component
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            appContext.sendBroadcast(intent)
        }

        private fun buildViews(context: Context, data: RecoveryWidgetData): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_recovery).apply {
                setImageViewBitmap(R.id.widget_rings, RecoveryRingsRenderer.render(context, data))
                setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
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
}
