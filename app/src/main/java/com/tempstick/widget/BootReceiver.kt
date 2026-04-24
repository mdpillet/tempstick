package com.tempstick.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, TempStickWidget::class.java)
        val prefs = WidgetPreferences
        manager.getAppWidgetIds(component).forEach { widgetId ->
            val intervalMinutes = prefs.getUpdateIntervalMinutes(context, widgetId)
            try { TempStickWidget.schedulePeriodicUpdate(context, widgetId, intervalMinutes) } catch (_: Exception) {}
            try { TempStickWidget.enqueueUpdate(context, widgetId) } catch (_: Exception) {}
        }
    }
}
