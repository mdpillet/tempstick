package com.tempstick.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var tvEmpty: TextView
    private val widgetIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        listView = findViewById(R.id.lv_widgets)
        tvEmpty = findViewById(R.id.tv_empty)
    }

    override fun onResume() {
        super.onResume()
        refreshWidgetList()
    }

    private fun refreshWidgetList() {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, TempStickWidget::class.java))
        widgetIds.clear()
        widgetIds.addAll(ids.toList())

        if (widgetIds.isEmpty()) {
            listView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            listView.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE

            val items = widgetIds.map { id ->
                val sensorId = WidgetPreferences.getSensorId(this, id)
                if (sensorId.isEmpty()) {
                    getString(R.string.main_widget_not_configured)
                } else {
                    val name = WidgetPreferences.getSensorName(this, id).ifEmpty { "TempStick" }
                    "$name  •  $sensorId"
                }
            }

            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
            listView.setOnItemClickListener { _, _, position, _ ->
                val widgetId = widgetIds[position]
                val intent = Intent(this, WidgetConfigActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                startActivity(intent)
            }
        }
    }
}
