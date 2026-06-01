# Guía de ejecución

### 1. Inicialización

Abre un terminal, dirígete a la carpeta del backend (donde está `docker_compose.yaml`) y levanta los servicios:

```bash
cd source-back-end
sudo docker compose -f docker_compose.yaml up --build -d
```

Esto levantará cuatro contenedores:

- **Mosquitto** (broker MQTT) en el puerto `1883`
- **InfluxDB** en el puerto `8086`
- **Grafana** en el puerto `3000`
- **API** (FastAPI) en el puerto `8000`

Puedes comprobar que todo funciona con `sudo docker ps`, y revisar que la API conecta sin errores con `sudo docker logs sensores_api`.

### 2. Averiguar la IP del ordenador

El móvil necesita saber a qué dirección IP enviar los datos. Ambos deben estar en la **misma red WiFi**.

Para averiguar la IP de tu ordenador, ejecuta:

```bash
ip a
```

Busca la interfaz conectada a tu WiFi/router (suele llamarse `wlan0`, `eth0` o `enp...`) y anota la dirección que aparece junto a `inet` (por ejemplo, `192.168.1.50`).

### 3. Configurar el móvil (app com.uja.sensores)

1. Instala el APK de la app en el móvil (`source-android-app`, o compílalo con Android Studio).
2. Abre la app y, en el campo de texto, introduce la **IP del ordenador** que anotaste antes (p. ej. `192.168.1.50`). El puerto MQTT (`1883`) está fijado en la app.
3. Activa los sensores de los que quieras obtener datos (Accelerometer, Gyroscope, Light, etc.).
4. Pulsa **Iniciar transmisión**. La app abre un servicio en segundo plano y comienza a publicar las lecturas.

A partir de ese momento, la app publica continuamente los datos de los sensores hacia el broker Mosquitto del ordenador, y la API en FastAPI los recibe y guarda automáticamente en InfluxDB.

### 4. Visualizar los datos (Grafana)

1. Abre el navegador en [http://localhost:3000](http://localhost:3000).
2. Inicia sesión con usuario `admin` y contraseña `admin` (te pedirá cambiar la contraseña la primera vez).
3. Ve a *Connections > Add new connection* y busca **InfluxDB**.
4. Configura la conexión con los datos definidos en `docker_compose.yaml`:
   - Query Language: **Flux**
   - URL: `http://influxdb:8086`
   - Organization: `ujaen`
   - Token: `my-super-secret-auth-token`
   - Default Bucket: `sensores_bucket`
5. Haz clic en *Save & Test*. Si todo es correcto, indicará que la conexión es exitosa.
6. Ve a *Dashboards > New Dashboard > Add panel*. Como lenguaje de consulta usa **Flux**, y verás que en los *Measurements* aparecen los nombres de los topics MQTT (como `sensors/accelerometer`, etc.).
