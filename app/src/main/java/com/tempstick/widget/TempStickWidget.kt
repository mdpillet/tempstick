package com.tempstick.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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
        if (intent.action == ACTION_REFRESH) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                showRefreshing(context, widgetId)
                enqueueUpdate(context, widgetId)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val wm = WorkManager.getInstance(context)
        for (widgetId in appWidgetIds) {
            WidgetPreferences.deleteWidget(context, widgetId)
            wm.cancelUniqueWork(periodicWorkName(widgetId))
        }
    }

    companion object {

        const val ACTION_REFRESH = "com.tempstick.widget.ACTION_REFRESH"

        fun periodicWorkName(widgetId: Int) = "tempstick_periodic_$widgetId"

        fun enqueueUpdate(context: Context, widgetId: Int) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInputData(workDataOf(WidgetUpdateWorker.KEY_WIDGET_ID to widgetId))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("tempstick_once_$widgetId", ExistingWorkPolicy.REPLACE, request)
        }

        fun updateWithSensorData(context: Context, widgetId: Int, sensor: SensorData) {
            val useFahrenheit = WidgetPreferences.getUseFahrenheit(context, widgetId)
            val sensorName = WidgetPreferences.getSensorName(context, widgetId)
                .ifEmpty { sensor.sensorName }

            val tempText = sensor.lastTempCelsius?.let { c ->
                val value = if (useFahrenheit) c * 9.0 / 5.0 + 32 else c
                val unit = if (useFahrenheit) "°F" else "°C"
                "%.1f%s".format(value, unit)
            } ?: "--"

            val humidityText = sensor.lastHumidity?.let { "%.0f%%".format(it) } ?: ""

            val updatedText = "Updated ${
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            }"

            applyRemoteViews(context, widgetId) { views ->
                views.setTextViewText(R.id.widget_sensor_name, sensorName)
                views.setTextViewText(R.id.widget_temperature, tempText)
                views.setTextViewText(R.id.widget_humidity, humidityText)
                views.setTextViewText(R.id.widget_updated, updatedText)
            }
        }

        fun showError(context: Context, widgetId: Int, message: String) {
            val sensorName = WidgetPreferences.getSensorName(context, widgetId)
                .ifEmpty { context.getString(R.string.widget_label) }
            applyRemoteViews(context, widgetId) { views ->
                views.setTextViewText(R.id.widget_sensor_name, sensorName)
                views.setTextViewText(R.id.widget_temperature, "--")
                views.setTextViewText(R.id.widget_humidity, "")
                views.setTextViewText(R.id.widget_updated, message)
            }
        }

        private fun showRefreshing(context: Context, widgetId: Int) {
            applyRemoteViews(context, widgetId) { views ->
                views.setTextViewText(R.id.widget_updated, "Refreshing…")
            }
        }

        private fun applyRemoteViews(context: Context, widgetId: Int, block: (RemoteViews) -> Unit) {
            val manager = AppWidgetManager.getInstance(context)
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val refreshIntent = Intent(context, TempStickWidget::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, widgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            block(views)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
