package com.tempstick.widget

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class WidgetUpdateService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val widgetId = intent?.getIntExtra(EXTRA_WIDGET_ID, -1) ?: -1
        if (widgetId == -1) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val id = startId
        Thread {
            try {
                val apiKey = WidgetPreferences.getApiKey(this)
                val sensorId = WidgetPreferences.getSensorId(this, widgetId)
                if (apiKey.isEmpty() || sensorId.isEmpty()) {
                    TempStickWidget.showError(this, widgetId, "Tap to configure")
                } else {
                    val sensor = TempStickApiService.getSensor(apiKey, sensorId)
                    if (sensor != null) {
                        TempStickWidget.updateWithSensorData(this, widgetId, sensor)
                    } else {
                        TempStickWidget.showError(this, widgetId, "Sensor not found")
                    }
                }
            } catch (e: Exception) {
                TempStickWidget.showError(this, widgetId, "Update failed")
            } finally {
                stopSelf(id)
            }
        }.start()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_WIDGET_ID = "widget_id"

        fun start(context: Context, widgetId: Int) {
            context.startService(
                Intent(context, WidgetUpdateService::class.java)
                    .putExtra(EXTRA_WIDGET_ID, widgetId)
            )
        }
    }
}
