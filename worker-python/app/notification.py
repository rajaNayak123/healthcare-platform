import logging
import time

logger = logging.getLogger("notification")

def send_notification(event: dict) -> str:
    event_type = event.get("eventType")
    user_email = event.get("userEmail")
    user_name = event.get("userFullName")
    doctor = event.get("doctorName")
    slot_date = event.get("slotDate")
    slot_time = event.get("slotTime")
    appointment_id = event.get("appointmentId")

    time.sleep(1)

    if event_type == "APPOINTMENT_CREATED":
        message = (
            f"Hi {user_name}, your appointment #{appointment_id} with {doctor} "
            f"on {slot_date} at {slot_time} is confirmed."
        )
    elif event_type == "APPOINTMENT_CANCELLED":
        message = (
            f"Hi {user_name}, your appointment #{appointment_id} with {doctor} "
            f"on {slot_date} at {slot_time} has been cancelled."
        )
    else:
        message = f"Update on appointment #{appointment_id} for {user_name}."

    logger.info("NOTIFICATION -> %s: %s", user_email, message)
    return message
