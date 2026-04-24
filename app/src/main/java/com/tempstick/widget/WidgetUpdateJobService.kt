package com.tempstick.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle

class WidgetUpdateJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val widgetId = params.extras.getInt(EXTRA_WIDGET_ID, -1)
        if (widgetId == -1) {
            jobFinished(params, false)
            return false
        }

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
                jobFinished(params, false)
            }
        }.start()

        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        const val EXTRA_WIDGET_ID = "widget_id"

        fun schedule(context: Context, widgetId: Int) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
            val extras = PersistableBundle().apply {
                putInt(EXTRA_WIDGET_ID, widgetId)
            }
            val job = JobInfo.Builder(
                widgetId,
                ComponentName(context, WidgetUpdateJobService::class.java)
            )
                .setExtras(extras)
                .setOverrideDeadline(0)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()
            js.schedule(job)
        }
    }
}
