import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.consumer import start_consumer_in_background, recent_events
from app.config import KAFKA_TOPIC, KAFKA_BOOTSTRAP_SERVERS

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("worker")

app = FastAPI(
    title="Healthcare Appointment - Notification Worker",
    description="Python service that consumes Kafka appointment events and sends notifications",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
def on_startup():
    logger.info("Worker starting up. Kafka=%s topic=%s", KAFKA_BOOTSTRAP_SERVERS, KAFKA_TOPIC)
    start_consumer_in_background()

@app.get("/health")
def health():
    return {"status": "UP", "service": "notification-worker"}

@app.get("/events/recent")
def get_recent_events():

    return {"events": recent_events}
