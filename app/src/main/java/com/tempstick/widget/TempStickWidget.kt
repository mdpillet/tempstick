package com.tempstick.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TempStickWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            enqueueUpdate(context, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH, ACTION_PERIODIC_UPDATE -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    if (intent.action == ACTION_REFRESH) showRefreshing(context, widgetId)
                    enqueueUpdate(context, widgetId)
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            cancelPeriodicUpdate(context, widgetId)
            WidgetPreferences.deleteWidget(context, widgetId)
        }
    }

    companion object {

        const val ACTION_REFRESH = "com.tempstick.widget.ACTION_REFRESH"
        const val ACTION_PERIODIC_UPDATE = "com.tempstick.widget.ACTION_PERIODIC_UPDATE"

        fun enqueueUpdate(context: Context, widgetId: Int) {
            WidgetUpdateJobService.schedule(context, widgetId)
        }

        fun schedulePeriodicUpdate(context: Context, widgetId: Int, intervalMinutes: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = periodicPendingIntent(context, widgetId, PendingIntent.FLAG_UPDATE_CURRENT)
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + intervalMinutes.toLong() * 60_000,
                intervalMinutes.toLong() * 60_000,
                pi
            )
        }

        fun cancelPeriodicUpdate(context: Context, widgetId: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = periodicPendingIntent(context, widgetId, PendingIntent.FLAG_NO_CREATE)
            if (pi != null) alarmManager.cancel(pi)
        }

        private fun periodicPendingIntent(context: Context, widgetId: Int, flags: Int): PendingIntent? {
            val intent = Intent(context, TempStickWidget::class.java).apply {
                action = ACTION_PERIODIC_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            return PendingIntent.getBroadcast(
                context, widgetId, intent,
                flags or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun updateWithSensorData(context: Context, widgetId: Int, sensor: SensorData) {
            val useFahrenheit = WidgetPreferences.getUseFahrenheit(context, widgetId)
            val sensorName = WidgetPreferences.getSensorName(context, widgetId)
                .ifEmpty { sensor.sensorName }

            val tempText = sensor.lastTempCelsius?.let { c ->
                val v = if (useFahrenheit) c * 9.0 / 5.0 + 32 else c
                val unit = if (useFahrenheit) "°F" else "°C"
                "%.1f%s".format(v, unit)
            } ?: "--"

            val humText = sensor.lastHumidity?.let { "%.0f%%".format(it) } ?: ""
            val updated = "Updated " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

            applyViews(context, widgetId) { views ->
                views.setTextViewText(R.id.widget_sensor_name, sensorName)
                views.setTextViewText(R.id.widget_temperature, tempText)
                views.setTextViewText(R.id.widget_humidity, humText)
                views.setTextViewText(R.id.widget_updated, updated)
            }
        }

        fun showError(context: Context, widgetId: Int, message: String) {
            val name = WidgetPreferences.getSensorName(context, widgetId)
                .ifEmpty { "TempStick" }
            applyViews(context, widgetId) { views ->
                views.setTextViewText(R.id.widget_sensor_name, name)
                views.setTextViewText(R.id.widget_temperature, "--")
                views.setTextViewText(R.id.widget_humidity, "")
                views.setTextViewText(R.id.widget_updated, message)
            }
        }

        private fun showRefreshing(context: Context, widgetId: Int) {
            applyViews(context, widgetId) { views ->
                views.setTextViewText(R.id.widget_updated, "Refreshing…")
            }
        }

        private fun applyViews(context: Context, widgetId: Int, block: (RemoteViews) -> Unit) {
            val manager = AppWidgetManager.getInstance(context)
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val refreshIntent = Intent(context, TempStickWidget::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val pi = PendingIntent.getBroadcast(
                context, widgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            block(views)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
