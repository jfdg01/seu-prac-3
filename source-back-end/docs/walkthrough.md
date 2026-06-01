# Integración de la app de sensores: MQTT + InfluxDB

El sistema de recolección de datos en tiempo real está completamente implementado, satisfaciendo los requisitos de la práctica de Sistemas Empotrados y Ubicuos. Hemos optado por el protocolo **MQTT** por ser el más eficiente para la transmisión asíncrona de datos en tiempo real procedentes de múltiples sensores (acelerómetro, giroscopio, luz, etc.).

## Arquitectura

```
App Android (com.uja.sensores)  ->  Mosquitto (MQTT)  ->  FastAPI  ->  InfluxDB  ->  Grafana
        publica                        broker            puente        almacén       visualización
   sensors/<tipo>                      :1883             :8000          :8086          :3000
```

## Componentes

### 1. Aplicación Android (productor de datos)
- App nativa propia en Kotlin (`source-android-app`, paquete `com.uja.sensores`).
- El usuario introduce la IP del ordenador, selecciona los sensores deseados y pulsa *Iniciar transmisión*.
- Un *foreground service* (`SensorService`) registra los sensores elegidos y, por cada lectura, publica un mensaje JSON en el topic `sensors/<tipo>` (p. ej. `sensors/accelerometer`).
- Formato del mensaje: `{"value_0":..., "value_1":..., "value_2":..., "accuracy":..., "timestamp":...}`.

### 2. Infraestructura MQTT (Mosquitto)
- Broker **Eclipse Mosquitto** añadido al entorno Docker.
- `mosquitto/config/mosquitto.conf` permite conexiones anónimas desde cualquier interfaz (`0.0.0.0`), de modo que el móvil pueda publicar en la red local.

### 3. API e integración con InfluxDB
- `paho-mqtt` (>= 2.0) en las dependencias del proyecto.
- `api/main.py` arranca un cliente MQTT junto con FastAPI (gestionado con un *lifespan handler*).
- Se suscribe al topic `#` (todo) y convierte cada JSON en un **Point** de InfluxDB, usando el topic como nombre de *measurement* y guardándolo en el bucket `sensores_bucket`.

## Instrucciones de prueba

Consulta `EJECUCIÓN.md` para el paso a paso completo. En resumen:

1. **Levanta el backend** con Docker Compose (Mosquitto + InfluxDB + Grafana + API).
2. **Configura la app** en el móvil: introduce la IP del ordenador, selecciona sensores y pulsa *Iniciar transmisión*. El móvil y el ordenador deben estar en la misma red WiFi.
3. **Visualiza** en Grafana ([http://localhost:3000](http://localhost:3000)) conectando a InfluxDB:
   - URL: `http://influxdb:8086`
   - Org: `ujaen`
   - Token: `my-super-secret-auth-token`
   - Bucket: `sensores_bucket`
   - Los topics MQTT aparecerán como *measurements* (`sensors/accelerometer`, `sensors/gyroscope`, ...).

> [!TIP]
> Para la "fusión de conocimiento", crea paneles en Grafana que combinen varias gráficas (p. ej. acelerómetro + giroscopio) usando el *timestamp* común para inferir el estado del usuario (caminando, parado, etc.).
