package com.tempstick.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.Toast

class WidgetConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var sensors: List<SensorData> = emptyList()

    private lateinit var etApiKey: EditText
    private lateinit var btnLoadSensors: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var sensorSpinnerGroup: LinearLayout
    private lateinit var spinnerSensor: Spinner
    private lateinit var etSensorId: EditText
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
        sensorSpinnerGroup = findViewById(R.id.sensor_spinner_group)
        spinnerSensor = findViewById(R.id.spinner_sensor)
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

        spinnerSensor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (sensors.isNotEmpty() && pos < sensors.size) {
                    etSensorId.setText(sensors[pos].sensorId)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

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
        sensorSpinnerGroup.visibility = View.GONE

        Thread {
            try {
                val fetched = TempStickApiService.getSensors(apiKey)
                runOnUiThread {
                    WidgetPreferences.saveApiKey(this, apiKey)
                    sensors = fetched
                    if (sensors.isEmpty()) {
                        Toast.makeText(this, "No sensors found. Enter Sensor ID manually below.", Toast.LENGTH_LONG).show()
                    } else {
                        spinnerSensor.adapter = ArrayAdapter(
                            this, android.R.layout.simple_spinner_item,
                            sensors.map { it.sensorName }.toTypedArray()
                        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                        sensorSpinnerGroup.visibility = View.VISIBLE
                        etSensorId.setText(sensors[0].sensorId)
                    }
                    progressBar.visibility = View.GONE
                    btnLoadSensors.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val msg = when {
                        e.message?.contains("402") == true ->
                            "API access unavailable (402). Enter your Sensor ID manually below (find it in the TempStick app)."
                        e.message?.contains("401") == true || e.message?.contains("403") == true ->
                            "Invalid API key. Check it in the TempStick app under Settings."
                        else -> "Could not load sensors: ${e.message}. Enter Sensor ID manually below."
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                    btnLoadSensors.isEnabled = true
                }
            }
        }.start()
    }

    private fun saveConfiguration() {
        val apiKey = etApiKey.text.toString().trim()
        val sensorId = etSensorId.text.toString().trim()

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Enter your TempStick API key", Toast.LENGTH_SHORT).show()
            return
        }
        if (sensorId.isEmpty()) {
            Toast.makeText(this, "Enter a Sensor ID or tap Load Sensors first", Toast.LENGTH_SHORT).show()
            return
        }

        val sensorName = sensors.find { it.sensorId == sensorId }?.sensorName ?: ""
        val useFahrenheit = radioFahrenheit.isChecked
        val intervalMinutes = intervalOptions[spinnerInterval.selectedItemPosition]

        WidgetPreferences.saveApiKey(this, apiKey)
        WidgetPreferences.saveSensorId(this, widgetId, sensorId)
        WidgetPreferences.saveSensorName(this, widgetId, sensorName)
        WidgetPreferences.saveUseFahrenheit(this, widgetId, useFahrenheit)
        WidgetPreferences.saveUpdateIntervalMinutes(this, widgetId, intervalMinutes)

        try {
            TempStickWidget.schedulePeriodicUpdate(this, widgetId, intervalMinutes)
            TempStickWidget.enqueueUpdate(this, widgetId)
        } catch (_: Exception) {}

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
