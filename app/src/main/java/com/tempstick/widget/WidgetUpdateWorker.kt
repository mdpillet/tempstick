package com.tempstick.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
    }

    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(KEY_WIDGET_ID, -1)
        if (widgetId == -1) return Result.failure()

        val apiKey = WidgetPreferences.getApiKey(applicationContext)
        val sensorId = WidgetPreferences.getSensorId(applicationContext, widgetId)

        if (apiKey.isEmpty() || sensorId.isEmpty()) {
            TempStickWidget.showError(applicationContext, widgetId, "Tap to configure")
            return Result.success()
        }

        return withContext(Dispatchers.IO) {
            try {
                val sensor = TempStickApiService.getSensor(apiKey, sensorId)
                if (sensor != null) {
                    TempStickWidget.updateWithSensorData(applicationContext, widgetId, sensor)
                    Result.success()
                } else {
                    TempStickWidget.showError(applicationContext, widgetId, "Sensor not found")
                    Result.failure()
                }
            } catch (e: Exception) {
                TempStickWidget.showError(applicationContext, widgetId, "Update failed")
                Result.retry()
            }
        }
    }
}
