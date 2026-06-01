package com.uja.sensores

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttManager {
    private var mqttClient: MqttClient? = null

    suspend fun connect(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val brokerUrl = "tcp://$ip:1883"
            val clientId = MqttClient.generateClientId()
            
            mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
            
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
            }
            
            mqttClient?.connect(options)
            Log.d("MqttManager", "Conectado a $brokerUrl")
            true
        } catch (e: Exception) {
            Log.e("MqttManager", "Error conectando MQTT: ${e.message}")
            false
        }
    }

    fun isConnected() = mqttClient?.isConnected == true

    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            Log.e("MqttManager", "Error desconectando: ${e.message}")
        }
    }

    fun publish(topic: String, payload: String) {
        try {
            if (isConnected()) {
                val message = MqttMessage(payload.toByteArray())
                message.qos = 0
                mqttClient?.publish(topic, message)
            }
        } catch (e: Exception) {
            Log.e("MqttManager", "Error publicando: ${e.message}")
        }
    }
}
