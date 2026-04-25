package com.tempstick.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.Toast

class WidgetConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var etApiKey: EditText
    private lateinit var etSensorId: EditText
    private lateinit var radioFahrenheit: RadioButton
    private lateinit var spinnerInterval: Spinner
    private lateinit var btnSave: Button

    private val intervalOptions = listOf(15, 30, 60)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        etSensorId = findViewById(R.id.et_sensor_id)
        radioFahrenheit = findViewById(R.id.radio_fahrenheit)
        spinnerInterval = findViewById(R.id.spinner_interval)
        btnSave = findViewById(R.id.btn_save)

        val savedApiKey = WidgetPreferences.getApiKey(this)
        if (savedApiKey.isNotEmpty()) etApiKey.setText(savedApiKey)

        val savedSensorId = WidgetPreferences.getSensorId(this, widgetId)
        if (savedSensorId.isNotEmpty()) etSensorId.setText(savedSensorId)

        spinnerInterval.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            arrayOf("Every 15 minutes", "Every 30 minutes", "Every hour")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        btnSave.setOnClickListener { saveConfiguration() }
    }

    private fun saveConfiguration() {
        val apiKey = etApiKey.text.toString().trim()
        val sensorId = etSensorId.text.toString().trim()

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Enter your TempStick API key", Toast.LENGTH_SHORT).show()
            return
        }
        if (sensorId.isEmpty()) {
            Toast.makeText(this, "Enter your Sensor ID", Toast.LENGTH_SHORT).show()
            return
        }

        val useFahrenheit = radioFahrenheit.isChecked
        val intervalMinutes = intervalOptions[spinnerInterval.selectedItemPosition]

        WidgetPreferences.saveApiKey(this, apiKey)
        WidgetPreferences.saveSensorId(this, widgetId, sensorId)
        WidgetPreferences.saveSensorName(this, widgetId, "")
        WidgetPreferences.saveUseFahrenheit(this, widgetId, useFahrenheit)
        WidgetPreferences.saveUpdateIntervalMinutes(this, widgetId, intervalMinutes)

        try {
            TempStickWidget.schedulePeriodicUpdate(this, widgetId, intervalMinutes)
            TempStickWidget.enqueueUpdate(this, widgetId)
        } catch (_: Exception) {}

        finish()
    }
}
