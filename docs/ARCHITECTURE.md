# Production-Grade Online Examination Platform — System Architecture

## Table of Contents
1. [System Overview](#1-system-overview)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Microservices Breakdown](#3-microservices-breakdown)
4. [Database Design](#4-database-design)
5. [API Design](#5-api-design)
6. [Critical Flows](#6-critical-flows)
7. [Caching Strategy](#7-caching-strategy)
8. [Messaging & Event-Driven Design](#8-messaging--event-driven-design)
9. [Security Architecture](#9-security-architecture)
10. [Scaling Strategy](#10-scaling-strategy)
11. [Deployment Pipeline](#11-deployment-pipeline)
12. [Cost Optimization](#12-cost-optimization)
13. [Disaster Recovery](#13-disaster-recovery)

---

## 1. System Overview

This platform is designed to support **1M+ concurrent exam takers** with sub-second answer latency, 99.99% uptime, and zero answer loss — modeled after JEE, NEET, CUET, and TCS iON scale systems.

### Scale Targets
| Metric | Target |
|---|---|
| Concurrent users | 1,000,000+ |
| Peak join rate | 500,000 users/minute |
| Answer save latency (p99) | < 500ms |
| API response time (p95) | < 200ms |
| Availability | 99.99% (< 52 min/year downtime) |
| Data durability | Zero loss (RPO = 0) |
| Recovery time | < 2 minutes (RTO) |

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENTS (Next.js)                            │
│          Students │ Admins │ Super Admins (Browser / Mobile)        │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ HTTPS / WSS
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   Cloudflare (CDN + DDoS + WAF)                     │
│           Static Assets │ Edge Caching │ Rate Limiting              │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│              Azure Application Gateway (L7 Load Balancer)           │
│          SSL Termination │ Path-Based Routing │ Health Checks        │
└───────┬──────────────────┬──────────────────┬───────────────────────┘
        │                  │                  │
        ▼                  ▼                  ▼
┌───────────────────────────────────────────────────────────────────┐
│               API Gateway (Spring Cloud Gateway)                   │
│    JWT Validation │ Rate Limiting │ Circuit Breaking │ Routing     │
└──┬──────────┬──────────┬──────────┬──────────┬───────────────────┘
   │          │          │          │          │
   ▼          ▼          ▼          ▼          ▼
┌──────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────────┐
│ User │ │  Exam  │ │  Test  │ │ Result │ │ Monitoring │
│ Svc  │ │  Svc   │ │ Engine │ │  Svc   │ │    Svc     │
│ :8081│ │  :8082 │ │  :8083 │ │  :8084 │ │    :8085   │
└──┬───┘ └───┬────┘ └───┬────┘ └───┬────┘ └─────┬──────┘
   │         │          │          │             │
   └────┬────┴──────────┴────┬─────┘             │
        │                   │                    │
        ▼                   ▼                    ▼
┌───────────────┐  ┌─────────────────┐  ┌───────────────┐
│  PostgreSQL   │  │   Kafka Cluster │  │     Redis     │
│  (Primary +  │  │  (3 brokers,   │  │   Cluster     │
│  3 Replicas) │  │  replication=3)│  │  (6 nodes)    │
└───────────────┘  └─────────────────┘  └───────────────┘
        │
        ▼
┌───────────────┐
│   MongoDB     │
│  (Atlas /     │
│  Sharded)     │
└───────────────┘
```

### Zone Architecture (Azure Multi-Region)
- **Primary Region**: Central India (Mumbai)
- **Secondary Region**: South India (Chennai) — hot standby
- **Tertiary**: East Asia — read replicas + CDN edge

Each region runs a full Kubernetes cluster with identical microservices. Azure Traffic Manager handles geo-routing and automatic failover.

---

## 3. Microservices Breakdown

### 3.1 User Service (Port 8081)
**Responsibility**: Registration, authentication, profile management, role management.

**Endpoints**: POST /auth/register, POST /auth/login, POST /auth/otp/send, POST /auth/otp/verify, POST /auth/refresh, GET /users/{id}, PUT /users/{id}

**Storage**: PostgreSQL (users, roles, sessions) + Redis (OTP cache, token blacklist)

**Key Design**:
- Stateless JWT with RS256 signing (public key distributed to all services)
- OTP stored in Redis with 5-min TTL and attempt counter
- Argon2id password hashing
- Refresh tokens stored in Redis, rotated on every use

### 3.2 Exam Service (Port 8082)
**Responsibility**: Exam creation, question bank, paper generation, slot allocation.

**Endpoints**: POST /exams, GET /exams/{id}, POST /exams/{id}/schedule, GET /exams/{id}/paper/{studentId}, POST /questions (bulk), GET /exams/{id}/slots

**Storage**: PostgreSQL (exam metadata, schedules) + MongoDB (question bank — supports MCQ, numerical, subjective with images/LaTeX)

**Key Design**:
- Paper generation uses deterministic shuffle seeded by `hash(studentId + examId)` — reproducible but unique per student
- Question bank sharded by `subject` in MongoDB
- Exam papers cached in Redis for the exam duration (invalidated at end)
- Slot allocation uses distributed lock (Redis SETNX) to prevent double-booking

### 3.3 Test Engine Service (Port 8083)
**Responsibility**: Real-time test session management, answer autosave, resume, anti-cheat event logging.

**Endpoints**: POST /sessions/start, GET /sessions/{id}/resume, WebSocket /ws/exam/{sessionId}, POST /sessions/{id}/submit

**Storage**: Redis (active session state — answers, timer, navigation state) + Kafka (answer event stream) + MongoDB (persisted answer snapshots)

**Key Design**:
- WebSocket connection per student via STOMP over SockJS
- Answers saved to Redis every 5s (in-memory, < 1ms)
- Kafka consumer persists Redis → MongoDB every 15s (durable write)
- Session state survives server crash via Redis persistence (AOF)
- On reconnect: session is rehydrated from Redis; if Redis miss, from MongoDB
- Anti-cheat events (tab switch, copy-paste) published to Kafka `anti-cheat-events` topic

### 3.4 Result Service (Port 8084)
**Responsibility**: Auto-evaluation, rank computation, percentile, analytics generation.

**Endpoints**: GET /results/{examId}/{studentId}, GET /results/{examId}/leaderboard, GET /results/{examId}/analytics, POST /results/{examId}/compute (admin trigger)

**Storage**: PostgreSQL (results, ranks) + MongoDB (detailed per-question analytics)

**Key Design**:
- Triggered by Kafka `exam-submitted` events
- MCQ/Numerical evaluated instantly via pre-loaded answer key (Redis cache)
- Rank computed using PostgreSQL window functions: `RANK() OVER (ORDER BY score DESC)`
- Percentile: `PERCENT_RANK() OVER (PARTITION BY exam_id ORDER BY score)`
- Heavy analytics jobs run async via Kafka, results pushed to MongoDB

### 3.5 Monitoring Service (Port 8085)
**Responsibility**: Real-time admin dashboards, live student tracking, anti-cheat alerts.

**Endpoints**: GET /monitor/exams/{id}/live, GET /monitor/students/{id}/session, WebSocket /ws/monitor/{examId}

**Storage**: Kafka Streams (real-time aggregation) + Redis (live counters)

**Key Design**:
- Subscribes to `answer-events` and `anti-cheat-events` Kafka topics
- Publishes aggregated stats to admin WebSocket every 5s
- Redis sorted sets track per-exam: active users, submissions, anomaly scores

### 3.6 API Gateway (Port 8080)
**Responsibility**: Single entry point — routing, JWT validation, rate limiting, circuit breaking.

**Filters**: JwtAuthFilter → RateLimitFilter → CircuitBreakerFilter → route

**Rate Limits**:
- Unauthenticated: 20 req/min per IP
- Student: 300 req/min per user
- Admin: 1000 req/min per user

---

## 4. Database Design

### 4.1 PostgreSQL — Relational Data

#### Users Table
```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    phone           VARCHAR(20) UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    aadhaar_hash    VARCHAR(255),           -- hashed, never stored plaintext
    role            VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT','ADMIN','SUPER_ADMIN')),
    is_verified     BOOLEAN DEFAULT FALSE,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_role  ON users(role);
```

#### Exams Table
```sql
CREATE TABLE exams (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    exam_type       VARCHAR(50) NOT NULL,   -- JEE_MAIN, NEET, CUSTOM, etc.
    created_by      UUID REFERENCES users(id),
    duration_mins   INT NOT NULL,
    total_marks     INT NOT NULL,
    negative_marks  NUMERIC(4,2) DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','LIVE','COMPLETED','CANCELLED')),
    instructions    TEXT,
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_exams_status     ON exams(status);
CREATE INDEX idx_exams_start_time ON exams(start_time);
CREATE INDEX idx_exams_created_by ON exams(created_by);
```

#### Exam Sections Table
```sql
CREATE TABLE exam_sections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id         UUID REFERENCES exams(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    subject         VARCHAR(100),
    max_questions   INT NOT NULL,
    marks_per_q     NUMERIC(4,2) NOT NULL,
    section_order   INT NOT NULL
);
CREATE INDEX idx_sections_exam_id ON exam_sections(exam_id);
```

#### Slots Table
```sql
CREATE TABLE exam_slots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id         UUID REFERENCES exams(id),
    slot_date       DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    capacity        INT NOT NULL,
    enrolled_count  INT DEFAULT 0,
    center_id       UUID,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_slots_exam_id   ON exam_slots(exam_id);
CREATE INDEX idx_slots_slot_date ON exam_slots(slot_date);
```

#### Enrollments Table
```sql
CREATE TABLE enrollments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID REFERENCES users(id),
    exam_id         UUID REFERENCES exams(id),
    slot_id         UUID REFERENCES exam_slots(id),
    roll_number     VARCHAR(50) UNIQUE,
    status          VARCHAR(20) DEFAULT 'ENROLLED' CHECK (status IN ('ENROLLED','APPEARED','ABSENT','DISQUALIFIED')),
    enrolled_at     TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(student_id, exam_id)
);
CREATE INDEX idx_enrollments_student ON enrollments(student_id);
CREATE INDEX idx_enrollments_exam    ON enrollments(exam_id);
CREATE INDEX idx_enrollments_slot    ON enrollments(slot_id);
```

#### Results Table
```sql
CREATE TABLE results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id   UUID REFERENCES enrollments(id) UNIQUE,
    student_id      UUID REFERENCES users(id),
    exam_id         UUID REFERENCES exams(id),
    total_score     NUMERIC(8,2),
    section_scores  JSONB,                  -- {"Physics": 60, "Chemistry": 45, ...}
    correct_count   INT,
    wrong_count     INT,
    skipped_count   INT,
    time_taken_secs INT,
    rank            INT,
    percentile      NUMERIC(6,3),
    status          VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','EVALUATED','PUBLISHED')),
    evaluated_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_results_exam_id    ON results(exam_id);
CREATE INDEX idx_results_student_id ON results(student_id);
CREATE INDEX idx_results_rank       ON results(exam_id, rank);
-- Partition by exam_id for large datasets
CREATE INDEX idx_results_score      ON results(exam_id, total_score DESC);
```

#### Audit Log Table
```sql
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID,
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     UUID,
    ip_address      INET,
    metadata        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
) PARTITION BY RANGE (created_at);
-- Monthly partitions
CREATE TABLE audit_log_2025_01 PARTITION OF audit_log
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE INDEX idx_audit_user_id    ON audit_log(user_id, created_at);
CREATE INDEX idx_audit_created_at ON audit_log(created_at);
```

### 4.2 MongoDB — Document Store

#### Question Bank Collection
```json
{
  "_id": "ObjectId",
  "question_id": "UUID",
  "exam_type": "JEE_MAIN",
  "subject": "PHYSICS",
  "chapter": "Mechanics",
  "topic": "Newton's Laws",
  "difficulty": "HARD",
  "type": "MCQ",           // MCQ | NUMERICAL | SUBJECTIVE | MULTI_SELECT
  "question_text": "A block of mass...",
  "question_html": "<p>...</p>",
  "question_image_urls": ["https://cdn.example.com/q1.png"],
  "options": [
    {"id": "A", "text": "2 m/s²", "image_url": null},
    {"id": "B", "text": "4 m/s²", "image_url": null},
    {"id": "C", "text": "6 m/s²", "image_url": null},
    {"id": "D", "text": "8 m/s²", "image_url": null}
  ],
  "correct_answer": "B",
  "correct_range": null,    // for numerical: {"min": 3.95, "max": 4.05}
  "explanation": "By Newton's second law...",
  "marks": 4,
  "negative_marks": -1,
  "tags": ["mechanics", "force", "acceleration"],
  "used_in_exams": ["exam_id_1", "exam_id_2"],
  "created_by": "admin_uuid",
  "created_at": "ISODate",
  "updated_at": "ISODate"
}
// Indexes:
// { exam_type: 1, subject: 1, difficulty: 1 }
// { tags: 1 }
// { chapter: 1, topic: 1 }
```

#### Student Answer Snapshots Collection
```json
{
  "_id": "ObjectId",
  "session_id": "UUID",
  "student_id": "UUID",
  "exam_id": "UUID",
  "snapshot_time": "ISODate",
  "answers": {
    "question_uuid_1": {"answer": "B", "time_spent_secs": 45, "marked_review": false},
    "question_uuid_2": {"answer": "3.14", "time_spent_secs": 120, "marked_review": true},
    "question_uuid_3": {"answer": null, "time_spent_secs": 10, "marked_review": false}
  },
  "navigation_log": [
    {"question_id": "uuid", "action": "VISIT", "timestamp": "ISODate"},
    {"question_id": "uuid", "action": "ANSWER", "timestamp": "ISODate"}
  ],
  "time_remaining_secs": 3600,
  "is_submitted": false
}
// Indexes:
// { session_id: 1, snapshot_time: -1 }
// { student_id: 1, exam_id: 1 }
// TTL index: snapshot_time (expire after 30 days post-exam)
```

#### Anti-Cheat Events Collection
```json
{
  "_id": "ObjectId",
  "session_id": "UUID",
  "student_id": "UUID",
  "exam_id": "UUID",
  "events": [
    {"type": "TAB_SWITCH", "timestamp": "ISODate", "count": 3},
    {"type": "COPY_PASTE", "timestamp": "ISODate", "text_length": 0},
    {"type": "FULLSCREEN_EXIT", "timestamp": "ISODate"},
    {"type": "MULTIPLE_IP", "timestamp": "ISODate", "ips": ["1.2.3.4", "5.6.7.8"]}
  ],
  "risk_score": 75,
  "flagged": true,
  "reviewed_by": null,
  "created_at": "ISODate"
}
```

### 4.3 Sharding Strategy

**PostgreSQL**:
- `results` table: Partitioned by `exam_id` (HASH partitioning, 16 partitions)
- `audit_log`: Range partitioned by month, auto-created via pg_cron
- Use Citus extension for horizontal sharding across nodes if needed

**MongoDB**:
- `questions` collection: Sharded on `{exam_type: 1, subject: 1}` (compound shard key)
- `answer_snapshots`: Sharded on `{exam_id: 1}` with zone sharding per exam
- `anti_cheat_events`: Sharded on `{exam_id: 1}`

---

## 5. API Design

### Authentication APIs

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/register` | None | Register new student |
| POST | `/api/v1/auth/login` | None | Login, get JWT + refresh |
| POST | `/api/v1/auth/otp/send` | None | Send OTP to phone/email |
| POST | `/api/v1/auth/otp/verify` | None | Verify OTP |
| POST | `/api/v1/auth/refresh` | Refresh Token | Get new access token |
| POST | `/api/v1/auth/logout` | JWT | Invalidate tokens |

### Exam APIs

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/exams` | ADMIN | Create exam |
| GET | `/api/v1/exams/{id}` | JWT | Get exam details |
| PUT | `/api/v1/exams/{id}` | ADMIN | Update exam |
| POST | `/api/v1/exams/{id}/publish` | ADMIN | Publish exam |
| GET | `/api/v1/exams/{id}/slots` | JWT | Get available slots |
| POST | `/api/v1/exams/{id}/enroll` | STUDENT | Enroll in exam slot |
| POST | `/api/v1/questions/bulk` | ADMIN | Bulk import questions |
| GET | `/api/v1/questions` | ADMIN | Search question bank |

### Test Engine APIs

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/sessions/start` | STUDENT | Start exam session |
| GET | `/api/v1/sessions/{id}` | STUDENT | Get/resume session |
| POST | `/api/v1/sessions/{id}/answer` | STUDENT | Save single answer |
| POST | `/api/v1/sessions/{id}/submit` | STUDENT | Final submission |
| WS | `/ws/exam/{sessionId}` | STUDENT | WebSocket connection |

### Result APIs

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/results/{examId}/{studentId}` | JWT | Get individual result |
| GET | `/api/v1/results/{examId}/leaderboard` | JWT | Top rankers |
| GET | `/api/v1/results/{examId}/analytics` | ADMIN | Exam-level analytics |
| POST | `/api/v1/results/{examId}/compute` | ADMIN | Trigger evaluation |
| GET | `/api/v1/results/{examId}/export` | ADMIN | Export results CSV |

### Sample Request/Response

**POST /api/v1/sessions/{id}/answer**
```json
// Request
{
  "questionId": "uuid-of-question",
  "answer": "B",
  "timeSpentSecs": 45,
  "markedForReview": false,
  "clientTimestamp": "2025-01-15T10:30:00Z"
}

// Response (200)
{
  "status": "SAVED",
  "questionId": "uuid-of-question",
  "savedAt": "2025-01-15T10:30:00.123Z",
  "timeRemainingSeconds": 3215
}
```

**WebSocket STOMP Message (autosave)**
```json
// Client → Server (topic: /app/exam/{sessionId}/heartbeat)
{
  "type": "HEARTBEAT",
  "answers": {
    "q1": {"answer": "A", "timeSpent": 30},
    "q2": {"answer": "3.14", "timeSpent": 90}
  },
  "timeRemaining": 3600,
  "timestamp": 1705312200000
}

// Server → Client (topic: /topic/session/{sessionId})
{
  "type": "ACK",
  "savedAnswers": ["q1", "q2"],
  "serverTime": 1705312200500,
  "timeRemaining": 3599
}
```

---

## 6. Critical Flows

### 6.1 Student Takes Exam

```
1. Student hits POST /sessions/start
   → Gateway validates JWT (RS256, cached public key)
   → Test Engine checks enrollment (PostgreSQL via Exam Service)
   → Generates deterministic paper: shuffle(questions, seed=hash(studentId+examId))
   → Stores paper mapping in Redis: exam:session:{sessionId}:paper → [q_id_1...q_id_n]
   → Creates session state in Redis: exam:session:{sessionId}:state
     {answers:{}, timeRemaining: 10800, startedAt: epoch, status: ACTIVE}
   → Publishes EXAM_STARTED event to Kafka topic `exam-events`
   → Returns: {sessionId, paper: [questions without answers], timeRemaining}

2. Student opens WebSocket /ws/exam/{sessionId}
   → STOMP handshake, subscribe to /topic/session/{sessionId}
   → Server validates session ownership from Redis

3. Every 5 seconds, client sends heartbeat with current answers
   → Server does HSET on Redis key (O(1), < 1ms)
   → Server sends ACK with server-side timeRemaining

4. Every 15 seconds, Kafka consumer reads Redis state
   → Writes snapshot to MongoDB (async, non-blocking to student)
   → Updates answer_snapshots collection (upsert on session_id)
```

### 6.2 Autosave Under Load

```
Client                  Test Engine              Redis           Kafka           MongoDB
  │──── WS heartbeat ───▶│                         │               │               │
  │                      │── HSET answers ─────────▶│               │               │
  │◀──── ACK ────────────│                         │               │               │
  │                      │   (every 15s)           │               │               │
  │                      │── PUBLISH event ────────────────────────▶│               │
  │                      │                         │               │── consume ────▶│
  │                      │                         │               │   upsert      │
  │                      │                         │               │   snapshot    │
```

If Test Engine pod crashes:
- Redis (AOF persistence) retains all answer data — zero loss
- New pod connects to same Redis, WebSocket client reconnects, session resumes

### 6.3 Submission Under Load (500k simultaneous)

```
1. Student sends POST /sessions/{id}/submit
   → Test Engine: Atomic Redis operation:
     MULTI
       SET exam:session:{id}:state:status = SUBMITTED
       SET exam:session:{id}:submitted_at = now()
     EXEC
   → Publishes EXAM_SUBMITTED event to Kafka (partitioned by exam_id)
     → Ensures all submissions for same exam go to same partition set
   → Returns 200 immediately — student sees "Submitted"

2. Result Service consumes EXAM_SUBMITTED from Kafka (async)
   → Fetches final answer snapshot from MongoDB
   → Fetches answer key from Redis (pre-loaded at exam start)
   → Evaluates: correct +4, wrong -1, skip 0
   → Writes to results table (PostgreSQL)

3. After all submissions processed (or exam end_time + buffer):
   → Admin triggers POST /results/{examId}/compute
   → Result Service: RANK() OVER, PERCENT_RANK() OVER in single query
   → Updates results table with rank, percentile
   → Publishes RESULTS_READY event
   → Monitoring Service notifies admin dashboard via WebSocket
```

### 6.4 Session Resume After Disconnect

```
1. Student's network drops mid-exam
2. Client attempts reconnect (exponential backoff: 1s, 2s, 4s, 8s...)
3. On reconnect: GET /sessions/{sessionId}
   → Test Engine looks up Redis: exam:session:{sessionId}:state
   → If Redis hit: returns current state (answers, timeRemaining)
   → If Redis miss (Redis restart): reads latest snapshot from MongoDB
     → Restores state to Redis, returns to student
4. Student sees answers intact, timer continues from server-side timeRemaining
   (timer runs on server, not client — client cannot manipulate time)
```

---

## 7. Caching Strategy

### Redis Key Design
```
# OTP
auth:otp:{phone}  → "123456"  TTL: 300s
auth:otp:{phone}:attempts → "2"  TTL: 300s

# JWT Blacklist (on logout/revoke)
auth:blacklist:{jti}  → "1"  TTL: token_expiry

# Exam Paper (cached at session start, expires at exam end)
exam:paper:{sessionId}  → [q_id_list]  TTL: exam_duration + 1hr

# Active Session State (AOF persistent)
exam:session:{sessionId}:state  → {answers, timeRemaining, status}  TTL: exam_duration + 2hr

# Answer Key (loaded 30 min before exam start)
exam:answerkey:{examId}  → {q_id: correct_answer}  TTL: until results published

# Live Counters (per exam)
exam:live:{examId}:active_users  → 847293  (Redis INCR/DECR)
exam:live:{examId}:submitted  → 12450

# Enrollment verification (burst cache)
exam:enrolled:{studentId}:{examId}  → "slot_id"  TTL: 1hr
```

### Cache Invalidation
- Answer key: Invalidated after results are published
- Session state: Invalidated 2 hours after exam end
- Enrollment cache: Event-driven invalidation via Kafka `enrollment-events`

---

## 8. Messaging & Event-Driven Design

### Kafka Topics
| Topic | Partitions | Replication | Retention | Consumers |
|-------|-----------|-------------|-----------|-----------|
| `exam-events` | 50 | 3 | 7 days | Result Svc, Monitor Svc |
| `answer-events` | 200 | 3 | 2 days | MongoDB Writer, Monitor Svc |
| `anti-cheat-events` | 50 | 3 | 30 days | Monitor Svc, Audit Svc |
| `result-events` | 20 | 3 | 30 days | Notification Svc |
| `notification-events` | 30 | 3 | 3 days | Email/SMS workers |

### Partition Strategy
- `answer-events`: Partitioned by `exam_id` — ensures all answers for an exam land on same partition set, enabling ordered processing
- `exam-events`: Partitioned by `exam_id`
- Consumer group per service; multiple instances per group for parallel processing

### Kafka Consumer Guarantees
- `enable.auto.commit = false` — manual commit after successful MongoDB write
- `isolation.level = read_committed` — only read committed transactions
- Idempotent producers: `enable.idempotence = true`, `acks = all`
- Dead Letter Queue: Failed messages after 3 retries → `{topic}-dlq`

---

## 9. Security Architecture

### Authentication
- **JWT**: RS256, 15-minute access tokens, 7-day refresh tokens (rotated)
- **OTP**: 6-digit, 5-minute TTL, max 3 attempts, then 30-min lockout
- **Aadhaar** (optional): DigiLocker API integration for KYC verification

### Anti-Cheat Mechanisms
1. **Tab Switch Detection**: `visibilitychange` event on frontend; 3 switches → auto-flag
2. **Copy-Paste Block**: `oncopy`, `onpaste`, `oncut` disabled via JS + `user-select: none`
3. **Right-Click Disable**: `contextmenu` event blocked
4. **Fullscreen Enforcement**: Kiosk-mode via Fullscreen API; exit → warning
5. **Browser Lock**: Detect dev tools open (window size heuristic), block
6. **Multiple IP Detection**: Session IP tracked; IP change mid-exam → flag
7. **Answer Timing Analysis**: Too-fast answers (< 3s for complex questions) flagged
8. **Webcam Proctoring** (optional): Stream to Azure Media Services, face detection via Azure Face API

### Network Security
- **Cloudflare WAF**: OWASP rules, SQL injection, XSS protection
- **DDoS**: Cloudflare Magic Transit + Azure DDoS Standard
- **Rate Limiting**: Per-IP + per-user at Gateway level (Redis token bucket)
- **HTTPS Everywhere**: TLS 1.3 minimum, HSTS headers
- **mTLS**: Between internal microservices (Kubernetes service mesh via Istio)

### Data Security
- **PII Encryption**: Aadhaar number hashed (SHA-256 + salt), never stored plaintext
- **Passwords**: Argon2id with per-user salt
- **Database**: Transparent Data Encryption (TDE) on PostgreSQL
- **Secrets**: Azure Key Vault, injected as K8s secrets at runtime
- **Audit**: All admin actions logged to immutable audit table

---

## 10. Scaling Strategy

### Kubernetes HPA Configuration
```yaml
# Test Engine: Scale aggressively on CPU + custom WebSocket connection metric
minReplicas: 10
maxReplicas: 500
metrics:
  - CPU utilization: 60%
  - Custom: ws_connections_per_pod < 2000

# User Service: Scale on RPS
minReplicas: 5
maxReplicas: 100
metrics:
  - CPU: 70%
  - RPS > 1000/pod

# Result Service: Scale on Kafka lag
minReplicas: 3
maxReplicas: 50
metrics:
  - Kafka consumer lag > 10000 messages
```

### Pre-Scaling for Known Peaks
30 minutes before any scheduled exam, a Kubernetes CronJob triggers:
```
kubectl scale deployment test-engine --replicas=200
kubectl scale deployment user-service --replicas=50
```
This is registered as a Kubernetes CronJob with exam schedule from the Exam Service.

### Database Scaling
- **PostgreSQL**: Primary + 3 read replicas; write to primary, reads distributed round-robin
- **MongoDB**: Atlas M80 cluster, 3-shard configuration; auto-scale storage
- **Redis**: 6-node cluster (3 primary + 3 replica); read from replicas for non-critical reads
- **Connection Pooling**: HikariCP for PostgreSQL (pool size = DB_CPU_CORES × 2 + disk_spindles)

### Load Balancer Strategy
- **L4 (Azure Load Balancer)**: TCP-level, distributes to Application Gateway nodes
- **L7 (Azure Application Gateway)**: HTTP/WS routing, SSL offload, sticky sessions for WebSocket
- **Service-level**: Spring Cloud LoadBalancer (round-robin with health check filter)
- **WebSocket**: Sticky sessions via Azure AG cookie-based affinity — same pod for WS lifetime

---

## 11. Deployment Pipeline

### CI/CD Flow
```
Developer Push → GitHub PR
  → GitHub Actions: Build + Unit Tests
  → SonarQube: Code Quality Gate (coverage > 80%)
  → Docker Build: Multi-stage (builder + distroless runtime)
  → Trivy: Container security scan
  → Push to Azure Container Registry (ACR)
  → ArgoCD: GitOps sync to Staging K8s cluster
  → Integration Tests (Testcontainers)
  → Load Test (k6: 10k virtual users)
  → Manual Approval Gate (for Production)
  → ArgoCD: Sync to Production (blue-green deployment)
  → Smoke Tests
  → Slack notification
```

### Blue-Green Deployment
- Two identical production environments (Blue = live, Green = standby)
- New version deployed to Green, tested, then traffic switched via Azure Traffic Manager
- Instant rollback: switch traffic back to Blue (< 30 seconds)
- Zero downtime deployment guaranteed

---

## 12. Cost Optimization

### Infrastructure
- **Spot Instances**: Use Azure Spot VMs for Result Service batch computation (60-80% cost reduction)
- **Auto-scaling down**: Scale to minimum replicas during off-peak hours (11pm–6am)
- **Reserved Instances**: Purchase 1-year reserved capacity for baseline load (40% savings)
- **CDN Aggressively**: Serve question images, CSS, JS from Cloudflare (reduces origin traffic 90%)

### Database
- **Read Replicas**: Route analytics queries to replicas (avoid primary contention)
- **MongoDB TTL Indexes**: Auto-delete old answer snapshots (reduces storage cost)
- **Redis Memory**: Use Redis `allkeys-lru` eviction for non-critical cached data
- **Cold Storage**: Archive old exam data to Azure Blob Storage (Blob cold tier) after 90 days

### Kafka
- Use Kafka Tiered Storage (KIP-405) for old segments → Azure Blob Storage
- Compress messages: `compression.type=lz4`

### Estimated Monthly Cost (1M users peak)
| Component | Estimated Cost |
|-----------|---------------|
| AKS (100 nodes D4s_v3) | ~$12,000 |
| PostgreSQL (Flexible Server, Business Critical) | ~$3,500 |
| MongoDB Atlas M80 | ~$2,800 |
| Redis Cache (P4 × 6) | ~$1,800 |
| Kafka (Azure Event Hubs Premium) | ~$2,000 |
| Cloudflare Enterprise | ~$1,000 |
| Azure Blob + CDN | ~$500 |
| **Total** | **~$23,600/month** |

---

## 13. Disaster Recovery

### Strategy: Multi-Region Active-Passive

**RPO (Recovery Point Objective)**: 0 seconds for exam answers (Redis AOF + Kafka replication)
**RTO (Recovery Time Objective)**: < 2 minutes (Azure Traffic Manager health probe → failover)

### Backup Schedule
- PostgreSQL: Continuous WAL streaming to secondary region + daily full backup to Blob cold storage
- MongoDB: Continuous oplog shipping via Atlas Global Clusters
- Redis: RDB snapshot every 60s + AOF append-every-second; replicated to secondary region
- Kafka: Cross-region replication via MirrorMaker 2 (lag < 5s)

### Failover Procedure
1. Azure Traffic Manager detects primary region unhealthy (health probe fails 3 times in 30s)
2. Automatic DNS failover to secondary region (TTL = 30s)
3. Secondary region's read replicas promoted to primary (PostgreSQL: ~60s for replica promotion)
4. Application connects to new primary; students reconnect via WebSocket
5. Session state is intact (Redis was being replicated to secondary region continuously)
6. Exam resumes seamlessly for students — at most 2-minute interruption

### Chaos Engineering
Run monthly Chaos experiments via Azure Chaos Studio:
- Kill random pods → verify auto-restart and no answer loss
- Simulate Redis primary failure → verify replica takes over
- Kill 50% of Kafka brokers → verify consumer group rebalancing
- Network partition between regions → verify failover

---

## Summary

This architecture achieves 1M+ concurrent users through:
1. **Stateless microservices** that scale horizontally without coordination
2. **Redis as the source of truth** for active exam state (sub-millisecond reads/writes)
3. **Kafka as the durability backbone** — no answer is lost even if every service crashes
4. **Pre-scaling before known peaks** (CronJob-driven) + HPA for unexpected spikes
5. **Multi-region active-passive** with RPO=0 for exam data
6. **Cloudflare at the edge** absorbing static traffic and DDoS before it hits origin
7. **Event-driven result computation** that never blocks students during submission
