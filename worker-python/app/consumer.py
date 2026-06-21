import json
import logging
import random
import threading
import time
from datetime import datetime, timezone

import httpx
from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import NoBrokersAvailable
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError

from app.config import (
    KAFKA_BOOTSTRAP_SERVERS,
    KAFKA_TOPIC,
    KAFKA_GROUP_ID,
    KAFKA_DLQ_TOPIC,
    SPRING_BACKEND_URL,
    MAX_RETRY_ATTEMPTS,
    BASE_RETRY_DELAY_S,
    INTERNAL_TOKEN,
)
from app.db import SessionLocal
from app.notification import send_notification

logger = logging.getLogger("consumer")

recent_events:    list = []
MAX_RECENT_EVENTS       = 50
_lock                   = threading.Lock()

_http_client = httpx.Client(timeout=5.0)

_dlq_producer: KafkaProducer | None = None
_producer_lock = threading.Lock()

def _get_dlq_producer() -> KafkaProducer:
    global _dlq_producer
    if _dlq_producer is None:
        with _producer_lock:
            if _dlq_producer is None:           
                _dlq_producer = KafkaProducer(
                    bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                    value_serializer=lambda v: json.dumps(v).encode("utf-8"),
                    acks="all",                 
                    retries=3,
                )
    return _dlq_producer

def _notify_spring(appointment_id: int, status: str, event_type: str, note: str) -> None:

    url = f"{SPRING_BACKEND_URL}/ws/notify"
    payload = {
        "appointmentId": appointment_id,
        "status":        status,
        "eventType":     event_type,
        "note":          note,
        "timestamp":     datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S"),
    }
    headers = {
        "X-Internal-Token": INTERNAL_TOKEN
    }
    try:
        resp = _http_client.post(url, json=payload, headers=headers)
        if resp.status_code not in (200, 204):
            logger.warning(
                "WS notify returned %s for appointment %s",
                resp.status_code, appointment_id,
            )
    except httpx.RequestError as exc:
        logger.warning(
            "WS notify failed for appointment %s: %s",
            appointment_id, exc,
        )

def _record_recent(event: dict, status: str, note: str) -> None:
    with _lock:
        recent_events.insert(0, {
            "appointmentId": event.get("appointmentId"),
            "eventType":     event.get("eventType"),
            "status":        status,
            "note":          note,
            "timestamp":     time.time(),
        })
        del recent_events[MAX_RECENT_EVENTS:]

def _update_event_log(session, appointment_id: int, event_type: str,
                      status: str, note: str) -> None:
    session.execute(
        text("""
            UPDATE appointment_event_logs
            SET event_status = :status, processing_note = :note, updated_at = NOW()
            WHERE id = (
                SELECT id FROM appointment_event_logs
                WHERE appointment_id = :appointment_id AND event_type = :event_type
                ORDER BY created_at DESC LIMIT 1
            )
        """),
        {"status": status, "note": note,
         "appointment_id": appointment_id, "event_type": event_type},
    )

def _update_appointment_status(session, appointment_id: int, event_type: str) -> None:
    new_status = "NOTIFIED" if event_type == "APPOINTMENT_CREATED" else "CANCELLED"
    session.execute(
        text("""
            UPDATE appointments
            SET status = :status, updated_at = NOW()
            WHERE id = :appointment_id AND status != 'CANCELLED'
        """),
        {"status": new_status, "appointment_id": appointment_id},
    )

def _update_processed_event_status(session, event_id: str, status: str,
                                   retry_count: int, failure_reason: str | None = None) -> None:

    session.execute(
        text("""
            UPDATE processed_events
            SET status         = :status,
                retry_count    = :retry_count,
                last_retry_at  = NOW(),
                failure_reason = :failure_reason
            WHERE event_id = :event_id
        """),
        {
            "status":         status,
            "retry_count":    retry_count,
            "failure_reason": failure_reason,
            "event_id":       event_id,
        },
    )

def _write_dlq_audit(session, event_id: str, appointment_id: int | None,
                     event_type: str | None, original_payload: str,
                     failure_reason: str, retry_count: int) -> None:

    session.execute(
        text("""
            INSERT INTO dlq_events
                (event_id, appointment_id, event_type, original_payload,
                 failure_reason, retry_count, dlq_topic, published_at)
            VALUES
                (:event_id, :appointment_id, :event_type, :original_payload,
                 :failure_reason, :retry_count, :dlq_topic, NOW())
        """),
        {
            "event_id":         event_id,
            "appointment_id":   appointment_id,
            "event_type":       event_type,
            "original_payload": original_payload,
            "failure_reason":   failure_reason,
            "retry_count":      retry_count,
            "dlq_topic":        KAFKA_DLQ_TOPIC,
        },
    )

def _claim_event(session, event_id: str) -> bool:

    result = session.execute(
        text("""
            INSERT INTO processed_events (event_id, status, processed_at)
            VALUES (:event_id, 'CLAIMED', NOW())
            ON CONFLICT (event_id) DO NOTHING
        """),
        {"event_id": event_id},
    )
    return result.rowcount == 1

def _send_to_dlq(session, event_id: str, event: dict,
                 raw_payload: str, failure_reason: str, retry_count: int) -> None:

    appointment_id = event.get("appointmentId")
    event_type     = event.get("eventType")

    dlq_message = {
        "originalEventId":   event_id,
        "originalTopic":     KAFKA_TOPIC,
        "originalPayload":   raw_payload,
        "appointmentId":     appointment_id,
        "eventType":         event_type,
        "failureReason":     failure_reason,
        "retryCount":        retry_count,
        "maxRetryAttempts":  MAX_RETRY_ATTEMPTS,
        "sentToDlqAt":       datetime.now(timezone.utc).isoformat(),
    }

    try:
        producer = _get_dlq_producer()
        future = producer.send(
            KAFKA_DLQ_TOPIC,
            key=str(appointment_id).encode() if appointment_id else None,
            value=dlq_message,
        )

        future.get(timeout=10)
        logger.warning(
            "Published to DLQ — event_id=%s appointment=%s reason=%s",
            event_id, appointment_id, failure_reason,
        )
    except Exception as exc:

        logger.error(
            "CRITICAL: DLQ publish failed for event_id=%s: %s — "
            "message may be lost! Check Kafka connectivity.",
            event_id, exc,
        )

    _write_dlq_audit(
        session, event_id, appointment_id, event_type,
        raw_payload, failure_reason, retry_count,
    )

def _run_with_retry(session, event_id: str, event: dict,
                    raw_payload: str, appointment_id: int,
                    event_type: str) -> str:

    last_exc: Exception | None = None

    for attempt in range(1, MAX_RETRY_ATTEMPTS + 1):
        if attempt > 1:

            backoff = BASE_RETRY_DELAY_S * (2 ** (attempt - 1)) + random.uniform(0, 1)
            logger.info(
                "Retry attempt %s/%s for appointment=%s — sleeping %.1fs",
                attempt, MAX_RETRY_ATTEMPTS, appointment_id, backoff,
            )

            _update_processed_event_status(
                session, event_id,
                status="FAILED",
                retry_count=attempt - 1,
                failure_reason=str(last_exc),
            )
            session.commit()
            _notify_spring(
                appointment_id, "PROCESSING", event_type,
                f"⏳ Retry {attempt - 1}/{MAX_RETRY_ATTEMPTS - 1} — retrying in {backoff:.0f}s…",
            )
            time.sleep(backoff)

        try:
            result = send_notification(event)
            logger.info(
                "Notification succeeded on attempt %s/%s for appointment=%s",
                attempt, MAX_RETRY_ATTEMPTS, appointment_id,
            )
            return result

        except Exception as exc:
            last_exc = exc
            logger.warning(
                "Attempt %s/%s failed for appointment=%s: %s",
                attempt, MAX_RETRY_ATTEMPTS, appointment_id, exc,
            )

    if last_exc is not None:
        raise last_exc
    raise RuntimeError("Max retry attempts exhausted")

def process_event(message) -> None:

    event_id   = f"{message.topic}:{message.partition}:{message.offset}"
    raw_value: str = message.value
    event      = json.loads(raw_value)
    appointment_id = event.get("appointmentId")
    event_type     = event.get("eventType")

    session = SessionLocal()
    try:

        claimed = _claim_event(session, event_id)
        if not claimed:
            note = f"Already processed (event_id={event_id})"
            logger.info(
                "Duplicate skipped — event_id=%s appointment=%s",
                event_id, appointment_id,
            )
            _record_recent(event, "DUPLICATE", note)
            _notify_spring(appointment_id, "DUPLICATE", event_type, "⏭ " + note)
            session.commit()
            return

        _update_event_log(session, appointment_id, event_type,
                          "PROCESSING", "Worker picked up event")
        session.commit()
        _record_recent(event, "PROCESSING", "Worker picked up event")
        _notify_spring(appointment_id, "PROCESSING", event_type,
                       "⏳ Worker is processing your appointment…")

        try:
            message_text = _run_with_retry(
                session, event_id, event, raw_value, appointment_id, event_type,
            )
        except Exception as final_exc:

            failure_reason = str(final_exc)
            logger.error(
                "All %s attempts exhausted for appointment=%s — sending to DLQ. reason=%s",
                MAX_RETRY_ATTEMPTS, appointment_id, failure_reason,
            )

            _send_to_dlq(
                session, event_id, event, raw_value,
                failure_reason, MAX_RETRY_ATTEMPTS,
            )

            _update_processed_event_status(
                session, event_id,
                status="DLQ",
                retry_count=MAX_RETRY_ATTEMPTS,
                failure_reason=failure_reason,
            )

            _update_event_log(session, appointment_id, event_type,
                              "FAILED", f"DLQ after {MAX_RETRY_ATTEMPTS} attempts: {failure_reason}")
            session.commit()

            _record_recent(event, "FAILED",
                           f"DLQ after {MAX_RETRY_ATTEMPTS} retries: {failure_reason}")
            _notify_spring(
                appointment_id, "FAILED", event_type,
                f"☠ Sent to DLQ after {MAX_RETRY_ATTEMPTS} failed attempts. "
                f"Ops team notified. reason={failure_reason}",
            )
            return

        _update_event_log(session, appointment_id, event_type, "PROCESSED", message_text)
        _update_appointment_status(session, appointment_id, event_type)
        _update_processed_event_status(
            session, event_id,
            status="PROCESSED",
            retry_count=0,
            failure_reason=None,
        )
        session.commit()

        _record_recent(event, "PROCESSED", message_text)
        _notify_spring(appointment_id, "PROCESSED", event_type,
                       "✅ Notification sent. " + message_text)

        logger.info(
            "Successfully processed — event_id=%s appointment=%s",
            event_id, appointment_id,
        )

    except IntegrityError:

        session.rollback()
        note = f"Integrity conflict (event_id={event_id}) — treated as duplicate"
        logger.warning(note)
        _record_recent(event, "DUPLICATE", note)
        _notify_spring(appointment_id, "DUPLICATE", event_type, "⏭ " + note)

    except Exception as exc:

        session.rollback()
        note = str(exc)
        logger.exception("Unexpected error processing appointment=%s", appointment_id)
        try:
            _update_event_log(session, appointment_id, event_type, "FAILED", note)
            session.commit()
        except Exception:
            session.rollback()
        _record_recent(event, "FAILED", note)
        _notify_spring(appointment_id, "FAILED", event_type, "❌ Error: " + note)

    finally:
        session.close()

def _build_consumer(retries: int = 10, delay: int = 5) -> KafkaConsumer:
    for attempt in range(1, retries + 1):
        try:
            return KafkaConsumer(
                KAFKA_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                group_id=KAFKA_GROUP_ID,
                auto_offset_reset="earliest",

                enable_auto_commit=False,
                value_deserializer=lambda v: v.decode("utf-8"),
            )
        except NoBrokersAvailable:
            logger.warning(
                "Kafka not ready (attempt %s/%s), retrying in %ss…",
                attempt, retries, delay,
            )
            time.sleep(delay)
    raise RuntimeError("Could not connect to Kafka after several retries")

def start_consumer_loop() -> None:
    logger.info("Starting Kafka consumer on topic '%s'…", KAFKA_TOPIC)
    consumer = _build_consumer()
    for msg in consumer:
        try:
            process_event(msg)

            consumer.commit()
        except Exception:
            logger.exception(
                "Unhandled error processing Kafka message — offset NOT committed"
            )

def start_consumer_in_background() -> threading.Thread:
    thread = threading.Thread(target=start_consumer_loop, daemon=True)
    thread.start()
    return thread
