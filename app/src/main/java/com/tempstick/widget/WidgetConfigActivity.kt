package com.tempstick.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.Toast

class WidgetConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var etApiKey: EditText
    private lateinit var etSensorId: EditText
    private lateinit var cbShowKey: CheckBox
    private lateinit var radioFahrenheit: RadioButton
    private lateinit var radioCelsius: RadioButton
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
        cbShowKey = findViewById(R.id.cb_show_key)
        radioFahrenheit = findViewById(R.id.radio_fahrenheit)
        radioCelsius = findViewById(R.id.radio_celsius)
        spinnerInterval = findViewById(R.id.spinner_interval)
        btnSave = findViewById(R.id.btn_save)

        cbShowKey.setOnCheckedChangeListener { _, checked ->
            val start = etApiKey.selectionStart
            val end = etApiKey.selectionEnd
            etApiKey.inputType = if (checked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etApiKey.setSelection(start, end)
        }

        val savedApiKey = WidgetPreferences.getApiKey(this)
        if (savedApiKey.isNotEmpty()) etApiKey.setText(savedApiKey)

        spinnerInterval.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            arrayOf("Every 15 minutes", "Every 30 minutes", "Every hour")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val savedSensorId = WidgetPreferences.getSensorId(this, widgetId)
        if (savedSensorId.isNotEmpty()) {
            etSensorId.setText(savedSensorId)
            btnSave.text = getString(R.string.btn_save_changes)

            if (!WidgetPreferences.getUseFahrenheit(this, widgetId)) {
                radioCelsius.isChecked = true
            }

            val savedInterval = WidgetPreferences.getUpdateIntervalMinutes(this, widgetId)
            val idx = intervalOptions.indexOf(savedInterval).coerceAtLeast(0)
            spinnerInterval.setSelection(idx)
        }

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

        btnSave.isEnabled = false
        btnSave.text = getString(R.string.btn_validating)

        Thread {
            val sensor = try {
                TempStickApiService.getSensor(apiKey, sensorId)
            } catch (_: Exception) {
                null
            }

            runOnUiThread {
                if (sensor == null) {
                    btnSave.isEnabled = true
                    btnSave.text = if (WidgetPreferences.getSensorId(this, widgetId).isNotEmpty()) {
                        getString(R.string.btn_save_changes)
                    } else {
                        getString(R.string.btn_save)
                    }
                    Toast.makeText(
                        this,
                        "Could not reach sensor. Check your API key and Sensor ID.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    WidgetPreferences.saveApiKey(this, apiKey)
                    WidgetPreferences.saveSensorId(this, widgetId, sensorId)
                    WidgetPreferences.saveSensorName(this, widgetId, sensor.sensorName)
                    WidgetPreferences.saveUseFahrenheit(this, widgetId, useFahrenheit)
                    WidgetPreferences.saveUpdateIntervalMinutes(this, widgetId, intervalMinutes)

                    // Immediately show configured state so the widget doesn't linger on
                    // the configure prompt while waiting for the background job.
                    try { TempStickWidget.showError(this, widgetId, "Loading…") } catch (_: Exception) {}

                    try {
                        TempStickWidget.schedulePeriodicUpdate(this, widgetId, intervalMinutes)
                        TempStickWidget.enqueueUpdate(this, widgetId)
                    } catch (_: Exception) {}

                    finish()
                }
            }
        }.start()
    }
}
