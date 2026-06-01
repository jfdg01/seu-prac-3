package com.uja.sensores

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var sensorManager: SensorManager
    private val selectedSensors = mutableSetOf<Int>()

    private lateinit var btnConnect: Button
    private lateinit var etIpAddress: EditText
    private lateinit var sensorListContainer: LinearLayout
    private var isTracking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        btnConnect = findViewById(R.id.btnConnect)
        etIpAddress = findViewById(R.id.etIpAddress)
        sensorListContainer = findViewById(R.id.sensorListContainer)

        checkPermissions()
        setupSensorsList()

        btnConnect.setOnClickListener {
            if (isTracking) {
                stopTracking()
            } else {
                startTracking()
            }
        }
    }

    private fun checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 101)
            }
        }
    }

    private fun setupSensorsList() {
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val uniqueSensors = allSensors.distinctBy { it.name }

        for (sensor in uniqueSensors) {
            val switch = Switch(this)
            switch.text = sensor.name
            switch.setPadding(0, 16, 0, 16)
            
            switch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedSensors.add(sensor.type)
                } else {
                    selectedSensors.remove(sensor.type)
                }
            }
            sensorListContainer.addView(switch)
        }
    }

    private fun startTracking() {
        val ip = etIpAddress.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Introduce una IP", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedSensors.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos un sensor", Toast.LENGTH_SHORT).show()
            return
        }

        isTracking = true
        btnConnect.text = "Detener transmisión (Segundo plano)"
        
        // Desactivar botones mientras transmite
        for (i in 0 until sensorListContainer.childCount) {
            sensorListContainer.getChildAt(i).isEnabled = false
        }
        etIpAddress.isEnabled = false

        val intent = Intent(this, SensorService::class.java).apply {
            putExtra("IP", ip)
            putIntegerArrayListExtra("SENSORS", ArrayList(selectedSensors))
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopTracking() {
        isTracking = false
        btnConnect.text = "Iniciar transmisión"
        
        for (i in 0 until sensorListContainer.childCount) {
            sensorListContainer.getChildAt(i).isEnabled = true
        }
        etIpAddress.isEnabled = true

        val intent = Intent(this, SensorService::class.java)
        stopService(intent)
    }
}
