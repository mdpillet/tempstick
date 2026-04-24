package com.tempstick.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.LinearLayout
import android.widget.Toast

class WidgetConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var sensors: List<SensorData> = emptyList()

    private lateinit var etApiKey: EditText
    private lateinit var btnLoadSensors: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var sensorGroup: LinearLayout
    private lateinit var spinnerSensor: Spinner
    private lateinit var radioFahrenheit: RadioButton
    private lateinit var spinnerInterval: Spinner
    private lateinit var btnSave: Button

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

        setContentView(R.layout.activity_widget_config)

        etApiKey = findViewById(R.id.et_api_key)
        btnLoadSensors = findViewById(R.id.btn_load_sensors)
        progressBar = findViewById(R.id.progress_bar)
        sensorGroup = findViewById(R.id.sensor_group)
        spinnerSensor = findViewById(R.id.spinner_sensor)
        radioFahrenheit = findViewById(R.id.radio_fahrenheit)
        spinnerInterval = findViewById(R.id.spinner_interval)
        btnSave = findViewById(R.id.btn_save)

        val savedApiKey = WidgetPreferences.getApiKey(this)
        if (savedApiKey.isNotEmpty()) etApiKey.setText(savedApiKey)

        spinnerInterval.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            arrayOf("Every 15 minutes", "Every 30 minutes", "Every hour")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        btnLoadSensors.setOnClickListener { loadSensors() }
        btnSave.setOnClickListener { saveConfiguration() }
    }

    private fun loadSensors() {
        val apiKey = etApiKey.text.toString().trim()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Enter your TempStick API key first", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnLoadSensors.isEnabled = false
        sensorGroup.visibility = View.GONE

        Thread {
            try {
                val fetched = TempStickApiService.getSensors(apiKey)
                runOnUiThread {
                    WidgetPreferences.saveApiKey(this, apiKey)
                    sensors = fetched
                    if (sensors.isEmpty()) {
                        Toast.makeText(this, "No sensors found on this account", Toast.LENGTH_LONG).show()
                    } else {
                        spinnerSensor.adapter = ArrayAdapter(
                            this, android.R.layout.simple_spinner_item,
                            sensors.map { it.sensorName }.toTypedArray()
                        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                        sensorGroup.visibility = View.VISIBLE
                    }
                    progressBar.visibility = View.GONE
                    btnLoadSensors.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Could not load sensors: ${e.message}", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                    btnLoadSensors.isEnabled = true
                }
            }
        }.start()
    }

    private fun saveConfiguration() {
        if (sensors.isEmpty()) {
            Toast.makeText(this, "Load your sensors first", Toast.LENGTH_SHORT).show()
            return
        }
        val sensor = sensors[spinnerSensor.selectedItemPosition]
        val useFahrenheit = radioFahrenheit.isChecked
        val intervalMinutes = intervalOptions[spinnerInterval.selectedItemPosition]

        WidgetPreferences.saveSensorId(this, widgetId, sensor.sensorId)
        WidgetPreferences.saveSensorName(this, widgetId, sensor.sensorName)
        WidgetPreferences.saveUseFahrenheit(this, widgetId, useFahrenheit)
        WidgetPreferences.saveUpdateIntervalMinutes(this, widgetId, intervalMinutes)

        TempStickWidget.schedulePeriodicUpdate(this, widgetId, intervalMinutes)
        TempStickWidget.enqueueUpdate(this, widgetId)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
