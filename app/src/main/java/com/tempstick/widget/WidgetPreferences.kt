package com.tempstick.widget

import android.content.Context
import android.content.SharedPreferences

object WidgetPreferences {

    private const val PREFS_NAME = "com.tempstick.widget.prefs"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveApiKey(context: Context, apiKey: String) =
        prefs(context).edit().putString("api_key", apiKey).apply()

    fun getApiKey(context: Context): String =
        prefs(context).getString("api_key", "") ?: ""

    fun saveSensorId(context: Context, widgetId: Int, sensorId: String) =
        prefs(context).edit().putString("sensor_id_$widgetId", sensorId).apply()

    fun getSensorId(context: Context, widgetId: Int): String =
        prefs(context).getString("sensor_id_$widgetId", "") ?: ""

    fun saveSensorName(context: Context, widgetId: Int, name: String) =
        prefs(context).edit().putString("sensor_name_$widgetId", name).apply()

    fun getSensorName(context: Context, widgetId: Int): String =
        prefs(context).getString("sensor_name_$widgetId", "") ?: ""

    fun saveUseFahrenheit(context: Context, widgetId: Int, value: Boolean) =
        prefs(context).edit().putBoolean("use_fahrenheit_$widgetId", value).apply()

    fun getUseFahrenheit(context: Context, widgetId: Int): Boolean =
        prefs(context).getBoolean("use_fahrenheit_$widgetId", true)

    fun saveUpdateIntervalMinutes(context: Context, widgetId: Int, minutes: Int) =
        prefs(context).edit().putInt("update_interval_$widgetId", minutes).apply()

    fun getUpdateIntervalMinutes(context: Context, widgetId: Int): Int =
        prefs(context).getInt("update_interval_$widgetId", 15)

    fun deleteWidget(context: Context, widgetId: Int) {
        prefs(context).edit().apply {
            remove("sensor_id_$widgetId")
            remove("sensor_name_$widgetId")
            remove("use_fahrenheit_$widgetId")
            remove("update_interval_$widgetId")
            apply()
        }
    }
}
