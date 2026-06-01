# Tareas: app de sensores + MQTT + InfluxDB

- [x] Infraestructura
  - [x] Crear configuración básica para Mosquitto (`mosquitto.conf`) para permitir conexiones en red local.
  - [x] Añadir servicio `mosquitto` al `docker_compose.yaml`.
  - [x] Actualizar `api/requirements.txt` añadiendo `paho-mqtt`.
- [x] API (FastAPI + InfluxDB + MQTT)
  - [x] Implementar cliente MQTT en `main.py` que se suscriba a los topics publicados por la app.
  - [x] Implementar la escritura de los datos recibidos (JSON) hacia InfluxDB.
  - [x] Corregir compatibilidad con paho-mqtt 2.x y migrar a *lifespan handler*.
- [x] App Android (com.uja.sensores)
  - [x] Selección de sensores y configuración de IP del broker.
  - [x] Servicio en segundo plano que publica lecturas por MQTT.
  - [x] Topics limpios por tipo de sensor (`sensors/<tipo>`).
- [ ] Verificación
  - [x] Levantar los servicios con Docker Compose.
  - [x] Comprobar que los logs de la API no muestran errores de conexión a InfluxDB ni a Mosquitto.
  - [ ] Recoger un dataset real desde el móvil con actividades etiquetadas (parado / caminando).
  - [ ] Crear paneles en Grafana que combinen sensores (fusión de conocimiento).
