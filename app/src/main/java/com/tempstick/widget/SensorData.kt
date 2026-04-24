package com.tempstick.widget

data class SensorData(
    val sensorId: String,
    val sensorName: String,
    val lastTempCelsius: Double?,
    val lastHumidity: Double?,
    val lastCheckin: String?
)
