package com.tempstick.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import com.tempstick.widget.databinding.ActivityWidgetConfigBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class WidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var sensors: List<SensorData> = emptyList()

    private val intervalOptions = listOf(15, 30, 60)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedApiKey = WidgetPreferences.getApiKey(this)
        if (savedApiKey.isNotEmpty()) {
            binding.etApiKey.setText(savedApiKey)
        }

        binding.spinnerInterval.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("Every 15 minutes", "Every 30 minutes", "Every hour")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.btnLoadSensors.setOnClickListener { loadSensors() }
        binding.btnSave.setOnClickListener { saveConfiguration() }
    }

    private fun loadSensors() {
        val apiKey = binding.etApiKey.text.toString().trim()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Enter your TempStick API key first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnLoadSensors.isEnabled = false
        binding.sensorGroup.visibility = View.GONE
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) { TempStickApiService.getSensors(apiKey) }
                WidgetPreferences.saveApiKey(this@WidgetConfigActivity, apiKey)
                sensors = fetched

                if (sensors.isEmpty()) {
                    Toast.makeText(this@WidgetConfigActivity, "No sensors found on this account", Toast.LENGTH_LONG).show()
                } else {
                    binding.spinnerSensor.adapter = ArrayAdapter(
                        this@WidgetConfigActivity,
                        android.R.layout.simple_spinner_item,
                        sensors.map { it.sensorName }
                    ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                    binding.sensorGroup.visibility = View.VISIBLE
                    binding.btnSave.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@WidgetConfigActivity,
                    "Could not load sensors: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnLoadSensors.isEnabled = true
            }
        }
    }

    private fun saveConfiguration() {
        if (sensors.isEmpty()) {
            Toast.makeText(this, "Load your sensors first", Toast.LENGTH_SHORT).show()
            return
        }

        val sensor = sensors[binding.spinnerSensor.selectedItemPosition]
        val useFahrenheit = binding.radioFahrenheit.isChecked
        val intervalMinutes = intervalOptions[binding.spinnerInterval.selectedItemPosition]

        WidgetPreferences.saveSensorId(this, widgetId, sensor.sensorId)
        WidgetPreferences.saveSensorName(this, widgetId, sensor.sensorName)
        WidgetPreferences.saveUseFahrenheit(this, widgetId, useFahrenheit)
        WidgetPreferences.saveUpdateIntervalMinutes(this, widgetId, intervalMinutes)

        val periodicWork = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES
        ).setInputData(workDataOf(WidgetUpdateWorker.KEY_WIDGET_ID to widgetId))
            .build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TempStickWidget.periodicWorkName(widgetId),
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )

        TempStickWidget.enqueueUpdate(this, widgetId)

        setResult(RESULT_OK, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        })
        finish()
    }
}
