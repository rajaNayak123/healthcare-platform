import os

KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
KAFKA_TOPIC             = os.getenv("KAFKA_TOPIC",             "appointment-events")
KAFKA_GROUP_ID          = os.getenv("KAFKA_GROUP_ID",          "appointment-notification-worker")

KAFKA_DLQ_TOPIC = os.getenv("KAFKA_DLQ_TOPIC", "appointment-events-dlq")

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql+psycopg2://postgres:postgres@localhost:5432/healthcare_db",
)

WORKER_PORT = int(os.getenv("WORKER_PORT", "8000"))

SPRING_BACKEND_URL = os.getenv("SPRING_BACKEND_URL", "http://backend:8080")

INTERNAL_TOKEN = os.getenv("INTERNAL_TOKEN", "default-internal-token-secret")

MAX_RETRY_ATTEMPTS = int(os.getenv("MAX_RETRY_ATTEMPTS", "3"))

BASE_RETRY_DELAY_S = float(os.getenv("BASE_RETRY_DELAY_S", "2.0"))
