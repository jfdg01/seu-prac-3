import os
import json
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI
import paho.mqtt.client as mqtt
from influxdb_client import InfluxDBClient, Point, WritePrecision
from influxdb_client.client.write_api import WriteOptions

MQTT_BROKER = os.getenv("MQTT_BROKER", "mosquitto")
MQTT_PORT = int(os.getenv("MQTT_PORT", 1883))

INFLUXDB_URL = os.getenv("INFLUXDB_URL", "http://influxdb:8086")
INFLUXDB_TOKEN = os.getenv("INFLUXDB_TOKEN", "my-super-secret-auth-token")
INFLUXDB_ORG = os.getenv("INFLUXDB_ORG", "ujaen")
INFLUXDB_BUCKET = os.getenv("INFLUXDB_BUCKET", "sensores_bucket")

influx_client = InfluxDBClient(url=INFLUXDB_URL, token=INFLUXDB_TOKEN, org=INFLUXDB_ORG)

# Batched writes: buffer points and flush them to InfluxDB in bulk. With every
# phone sensor streaming at max speed the broker delivers thousands of msgs/s;
# a synchronous one-POST-per-point write capped throughput at ~200/s, so the
# ingest queue (and therefore the dashboard latency) grew without bound. The
# batching writer appends each point in microseconds and a background thread
# flushes every batch_size points or flush_interval ms, whichever comes first.
write_api = influx_client.write_api(
    write_options=WriteOptions(
        batch_size=5000,
        flush_interval=1000,
        jitter_interval=200,
        retry_interval=2000,
        max_retries=3,
    )
)

_saved = 0  # rough running total, only for periodic liveness logging


def on_connect(client, userdata, flags, rc):
    print(f"Connected to MQTT broker with result code {rc}")
    # Subscribe to every topic; the phone publishes under "sensors/<type>".
    client.subscribe("#")


def on_message(client, userdata, msg):
    global _saved
    try:
        payload = msg.payload.decode("utf-8")
        data = json.loads(payload)

        # The MQTT topic becomes the InfluxDB measurement (e.g. "sensors/accelerometer").
        point = Point(msg.topic)

        if isinstance(data, dict):
            for key, value in data.items():
                if isinstance(value, bool):
                    point.field(key, value)
                elif isinstance(value, (int, float)):
                    # Sensor readings (value_0, value_1, ...) must keep a consistent
                    # type. Android serialises a whole-number reading like 0.0 as "0"
                    # (int) but 0.12 as a float; InfluxDB locks the field to the first
                    # type seen and then drops the rest. Force all sensor values to
                    # float so they never conflict. (accuracy / timestamp stay int.)
                    point.field(key, float(value) if key.startswith("value") else value)
                elif isinstance(value, str):
                    point.field(key, value)
                elif value is not None:
                    point.field(key, str(value))
        else:
            point.field("value", str(data))

        # Stamp every point with a unique high-resolution server time. Without an
        # explicit time, InfluxDB assigns the whole flushed batch a single ingest
        # timestamp; because these points carry no tags, all same-measurement
        # points in that batch collapse into one row (we were keeping only ~1
        # sample/s/sensor). A per-message nanosecond clock keeps each reading
        # distinct so the full sampling rate is preserved.
        point.time(time.time_ns(), write_precision=WritePrecision.NS)

        write_api.write(bucket=INFLUXDB_BUCKET, org=INFLUXDB_ORG, record=point)
        # Avoid printing on every message (thousands/s); log liveness periodically.
        _saved += 1
        if _saved % 10000 == 0:
            print(f"Buffered {_saved} points (latest topic: {msg.topic})")

    except json.JSONDecodeError:
        print(f"Failed to decode JSON from topic {msg.topic}: {msg.payload}")
    except Exception as e:
        print(f"Error processing MQTT message: {e}")


# paho-mqtt 2.x requires an explicit callback API version. We pin VERSION1 so the
# (client, userdata, flags, rc) callback signatures above stay valid.
mqtt_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1)
mqtt_client.on_connect = on_connect
mqtt_client.on_message = on_message


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: connect to the broker and process messages in a background thread.
    print(f"Connecting to MQTT broker at {MQTT_BROKER}:{MQTT_PORT}...")
    try:
        mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
        mqtt_client.loop_start()
    except Exception as e:
        print(f"Failed to connect to MQTT broker: {e}")

    yield

    # Shutdown: close MQTT and InfluxDB connections cleanly.
    mqtt_client.loop_stop()
    mqtt_client.disconnect()
    write_api.close()
    influx_client.close()


app = FastAPI(title="API Red de Sensores", lifespan=lifespan)


@app.get("/")
def read_root():
    return {"mensaje": "Servidor FastAPI funcionando. Listo para recibir datos de los sensores."}
