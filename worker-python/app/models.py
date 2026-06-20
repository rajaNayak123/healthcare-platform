from sqlalchemy import Column, BigInteger, Integer, String, Text, DateTime, UniqueConstraint
from sqlalchemy.orm import declarative_base
from datetime import datetime

Base = declarative_base()

class AppointmentEventLog(Base):
    __tablename__ = "appointment_event_logs"

    id             = Column(BigInteger, primary_key=True)
    appointment_id = Column(BigInteger, nullable=False)
    event_type     = Column(String, nullable=False)
    event_status   = Column(String, nullable=False)
    payload        = Column(Text)
    processing_note = Column(Text)
    created_at     = Column(DateTime, default=datetime.utcnow)
    updated_at     = Column(DateTime, default=datetime.utcnow)

class Appointment(Base):
    __tablename__ = "appointments"

    id         = Column(BigInteger, primary_key=True)
    status     = Column(String, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow)

class ProcessedEvent(Base):

    __tablename__ = "processed_events"

    id             = Column(BigInteger, primary_key=True, autoincrement=True)
    event_id       = Column(String(255), nullable=False)

    status         = Column(String(20), nullable=False, default="CLAIMED")
    retry_count    = Column(Integer, nullable=False, default=0)
    last_retry_at  = Column(DateTime, nullable=True)
    failure_reason = Column(Text, nullable=True)
    processed_at   = Column(DateTime, default=datetime.utcnow, nullable=False)

    __table_args__ = (
        UniqueConstraint("event_id", name="uq_processed_events_event_id"),
    )

class DlqEvent(Base):

    __tablename__ = "dlq_events"

    id               = Column(BigInteger, primary_key=True, autoincrement=True)
    event_id         = Column(String(255), nullable=False)
    appointment_id   = Column(BigInteger, nullable=True)
    event_type       = Column(String(30), nullable=True)
    original_payload = Column(Text, nullable=True)
    failure_reason   = Column(Text, nullable=False)
    retry_count      = Column(Integer, nullable=False, default=0)
    dlq_topic        = Column(String(255), nullable=False)
    published_at     = Column(DateTime, default=datetime.utcnow, nullable=False)
