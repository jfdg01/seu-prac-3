package com.uja.sensores

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private val mqttManager = MqttManager()
    private val activeSensorTypes = mutableListOf<Int>()
    private var wakeLock: PowerManager.WakeLock? = null
    private val CHANNEL_ID = "SensorServiceChannel"

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ip = intent?.getStringExtra("IP") ?: return START_NOT_STICKY
        val sensors = intent.getIntegerArrayListExtra("SENSORS") ?: ArrayList()

        val notificationIntent = Intent(this, MainActivity::class.java)
        
        // Flags corregidos para Android 12+
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Transmitiendo Sensores")
            .setContentText("Enviando datos MQTT a $ip en segundo plano.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()

        // Adquirir WakeLock para que la CPU no se duerma
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorService::WakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes max for testing*/)

        startForeground(1, notification)

        CoroutineScope(Dispatchers.IO).launch {
            if (mqttManager.connect(ip)) {
                registerSensors(sensors)
            }
        }

        return START_STICKY
    }

    private fun registerSensors(sensors: ArrayList<Int>) {
        activeSensorTypes.clear()
        activeSensorTypes.addAll(sensors)
        for (type in sensors) {
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transmisión de Sensores",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        mqttManager.disconnect()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Build a clean MQTT topic from the sensor's standard type string, e.g.
    // "android.sensor.accelerometer" -> "sensors/accelerometer". Falls back to the
    // numeric type for OEM sensors without a standard string type.
    private fun topicForSensor(sensor: Sensor): String {
        val clean = (sensor.stringType ?: "type_${sensor.type}")
            .substringAfterLast('.')
            .replace(' ', '_')
            .lowercase()
        return "sensors/$clean"
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !mqttManager.isConnected()) return

        val topic = topicForSensor(event.sensor)

        val json = JSONObject()
        for (i in event.values.indices) {
            json.put("value_$i", event.values[i])
        }
        json.put("accuracy", event.accuracy)
        json.put("timestamp", System.currentTimeMillis())

        CoroutineScope(Dispatchers.IO).launch {
            mqttManager.publish(topic, json.toString())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
