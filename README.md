# 🏥 Meridian Health — Healthcare Appointment Management Platform

A production-style, event-driven healthcare appointment booking system built with
**Spring Boot**, a **Python** notification worker, **Kafka**, **PostgreSQL**, **Next.js**,
and **Docker**.

---

## 1. Architecture

```
┌──────────────┐      REST/JWT      ┌────────────────────┐
│  Next.js UI  │ ──────────────────▶│   Spring Boot API   │
│ (port 3000)  │◀──────────────────  │     (port 8080)     │
└──────┬───────┘                    └─────────┬────────────┘
       │  polls worker for                    │ publishes
       │  live event status                   │ Kafka events
       ▼                                       ▼
┌──────────────┐     Kafka topic     ┌────────────────────┐
│ Python Worker│◀────────────────────│   appointment-events│
│  (FastAPI)   │   "appointment-     │       topic         │
│ (port 8000)  │      events"        └────────────────────┘
└──────┬───────┘
       │ writes processing status
       ▼
┌──────────────┐
│  PostgreSQL  │◀── shared by both services (Spring Boot owns schema)
│ (port 5432)  │
└──────────────┘
```

**Flow:**
1. User registers/logs in via Spring Boot → receives a JWT.
2. User books an appointment → Spring Boot locks the slot row (pessimistic lock),
   validates availability, persists the appointment, and publishes an
   `APPOINTMENT_CREATED` event to Kafka.
3. The Python worker consumes the event, simulates sending a notification
   (email/SMS), and writes the processing result back to Postgres
   (`appointment_event_logs` + `appointments.status`).
4. The Next.js UI polls both services so the booking status visibly transitions:
   `Processing → Confirmed → Confirmed & Notified` in real time.
5. Cancellation follows the same event-driven path with `APPOINTMENT_CANCELLED`.

---

## 2. Tech Stack

| Layer            | Technology                          |
|-------------------|--------------------------------------|
| Backend Core      | Spring Boot 3.2 (Java 17)            |
| Worker Service    | Python 3.11, FastAPI, kafka-python   |
| Database          | PostgreSQL 16                        |
| Messaging         | Apache Kafka (Confluent images)      |
| Auth              | JWT (jjwt) + Spring Security         |
| API Docs          | springdoc-openapi / Swagger UI       |
| Frontend          | Next.js 14 (App Router) + Tailwind   |
| Containerization  | Docker + Docker Compose              |
| Cloud (suggested) | GCP (Cloud Run / GKE + Cloud SQL)    |

---

## 3. Project Structure

```
healthcare-platform/
├── backend-spring/        # Spring Boot REST API + Kafka producer
├── worker-python/         # FastAPI + Kafka consumer (notification worker)
├── frontend-nextjs/       # Next.js UI
├── db/schema.sql          # Reference DDL
├── docker-compose.yml     # One-command full-stack startup
└── README.md
```

---

## 4. Running Locally (Docker — recommended)

**Prerequisites:** Docker & Docker Compose installed.

```bash
git clone <your-repo-url>
cd healthcare-platform
docker compose up --build
```

This starts, in order: Postgres → Zookeeper/Kafka → Spring Boot backend
(auto-creates schema + seeds demo slots) → Python worker → Next.js frontend.

| Service              | URL                                    |
|-----------------------|------------------------------------------|
| Frontend              | http://localhost:3000                  |
| Backend API           | http://localhost:8080                  |
| Swagger UI            | http://localhost:8080/swagger-ui.html  |
| OpenAPI JSON          | http://localhost:8080/v3/api-docs      |
| Python worker health  | http://localhost:8000/health           |
| Worker recent events  | http://localhost:8000/events/recent    |

First boot takes 1–2 minutes while Kafka/Postgres become healthy. Demo doctor
slots for the next 5 days are seeded automatically on backend startup.

To stop: `docker compose down` (add `-v` to also wipe the Postgres volume).

---

## 5. Running Without Docker (local dev)

### 5.1 PostgreSQL & Kafka
Easiest to still run these two via Docker even in local dev mode:
```bash
docker compose up -d postgres zookeeper kafka
```

### 5.2 Spring Boot backend
```bash
cd backend-spring
mvn spring-boot:run
# or build a jar:
mvn clean package -DskipTests
java -jar target/appointment-service.jar
```
Runs on `http://localhost:8080` using the default `application.yml`
(`localhost:5432` / `localhost:9092`).

### 5.3 Python worker
```bash
cd worker-python
python -m venv venv && source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

### 5.4 Next.js frontend
```bash
cd frontend-nextjs
cp .env.local.example .env.local
npm install
npm run dev
```
Runs on `http://localhost:3000`.

---

## 6. API Reference

Full interactive docs at **`/swagger-ui.html`** once the backend is running.
Summary:

| Method | Endpoint                      | Auth | Description                       |
|--------|--------------------------------|------|------------------------------------|
| POST   | `/api/auth/register`          | No   | Register a new patient user        |
| POST   | `/api/auth/login`             | No   | Log in, returns JWT                |
| GET    | `/api/slots/available`        | Yes  | List open slots (optional `?date=`)|
| POST   | `/api/appointments`           | Yes  | Book an appointment (`slotId`)     |
| DELETE | `/api/appointments/{id}`      | Yes  | Cancel an appointment              |
| GET    | `/api/appointments/me`        | Yes  | List the caller's appointments     |

Authenticated requests require header: `Authorization: Bearer <token>`.

**Example: register**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Jordan Lee","email":"jordan@example.com","password":"secret123","phone":"+919876543210"}'
```

**Example: book an appointment**
```bash
curl -X POST http://localhost:8080/api/appointments \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"slotId": 1}'
```

---

## 7. Key Design Decisions

- **Preventing duplicate / concurrent bookings:** the slot row is fetched with a
  `PESSIMISTIC_WRITE` lock inside a transaction, an `@Version` optimistic-lock
  column guards against lost updates, and a partial unique index
  (`uq_active_appointment_per_slot`) on Postgres is a final safety net —
  three independent layers so a slot can never be double-booked even under
  high concurrency.
- **Event-driven decoupling:** Spring Boot never calls the Python worker
  directly. It publishes to Kafka and returns immediately; the worker
  processes asynchronously and writes status back to the shared database,
  which Spring Boot's read APIs then expose to the frontend.
- **Audit trail:** every event (published / processing / processed / failed)
  is logged in `appointment_event_logs`, independent of the appointment's own
  status, so the full processing history is always inspectable.
- **JWT auth:** stateless, role-aware (`PATIENT` / `ADMIN`) tokens signed with
  HMAC-SHA256; `JwtAuthenticationFilter` populates the Spring Security context
  per-request.
- **Clean architecture:** controller → service → repository layering with DTOs
  at the API boundary so entities never leak into responses directly.

---

## 8. Deploying to GCP (suggested path)

- **Cloud SQL (PostgreSQL)** for the database.
- **Cloud Run** for the Spring Boot backend and Python worker (each as its own
  container, built from the provided Dockerfiles).
- **Managed Kafka** via Confluent Cloud, or self-host on **GKE**, since Cloud
  Run doesn't run long-lived TCP consumers well — the worker is best run on
  **GKE** or **Compute Engine** so its Kafka consumer loop stays alive.
- **Cloud Run** (or **Firebase Hosting** via `next export` if going static) for
  the Next.js frontend.
- **Artifact Registry** to store built Docker images, **Cloud Build** for CI.
- Use **Secret Manager** for `JWT_SECRET` and DB credentials instead of plain
  environment variables in production.

---

## 9. Demo Walkthrough

1. Open `http://localhost:3000`, register a new patient.
2. On the dashboard, pick an open slot → status shows **Processing**.
3. Within ~1–2 seconds the **Live activity** panel shows the worker processing
   the event, and the appointment status updates to **Confirmed & Notified**.
4. Cancel an appointment — same async flow runs in reverse.
5. Check `GET /api/appointments/me` or Swagger UI to see the same data from
   the API directly.

---

## 10. Notes on Testing Concurrency

To verify duplicate-booking prevention, fire two simultaneous requests at the
same `slotId`:
```bash
for i in 1 2; do
  curl -X POST http://localhost:8080/api/appointments \
    -H "Authorization: Bearer <token>" \
    -H "Content-Type: application/json" \
    -d '{"slotId": 1}' &
done
wait
```
Exactly one request succeeds (`201 Created`); the other receives `409 Conflict`.
