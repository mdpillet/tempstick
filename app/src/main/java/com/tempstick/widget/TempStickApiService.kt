package com.tempstick.widget

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object TempStickApiService {

    private const val BASE_URL = "https://tempstickapi.com/api/v1"

    fun getSensor(apiKey: String, sensorId: String): SensorData? {
        val url = URL("$BASE_URL/sensor/$sensorId")
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("X-API-Key", apiKey)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            if (connection.responseCode != 200) return null
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            json.optJSONObject("data")?.let { parseSensor(it) }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSensor(obj: JSONObject): SensorData {
        val temp = obj.optDouble("last_temp")
        val humidity = obj.optDouble("last_humidity")
        return SensorData(
            sensorId = obj.optString("sensor_id"),
            sensorName = obj.optString("sensor_name", "Sensor"),
            lastTempCelsius = if (temp.isNaN()) null else temp,
            lastHumidity = if (humidity.isNaN()) null else humidity,
            lastCheckin = obj.optString("last_checkin").ifEmpty { null }
        )
    }
}
