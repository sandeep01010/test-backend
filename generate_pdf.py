from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.lib import colors
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
    HRFlowable, PageBreak
)
from reportlab.lib.enums import TA_CENTER, TA_LEFT

OUTPUT = r"C:\Users\acer\Claude\Projects\Test Series\exam-platform\Exam_Platform_Documentation.pdf"

doc = SimpleDocTemplate(
    OUTPUT,
    pagesize=A4,
    rightMargin=2*cm, leftMargin=2*cm,
    topMargin=2*cm, bottomMargin=2*cm,
    title="Exam Platform Documentation",
    author="Exam Platform Team"
)

styles = getSampleStyleSheet()

# Custom styles
title_style = ParagraphStyle(
    "DocTitle", parent=styles["Title"],
    fontSize=26, textColor=colors.HexColor("#1a237e"),
    spaceAfter=6, alignment=TA_CENTER
)
subtitle_style = ParagraphStyle(
    "Subtitle", parent=styles["Normal"],
    fontSize=13, textColor=colors.HexColor("#3949ab"),
    spaceAfter=4, alignment=TA_CENTER
)
h1 = ParagraphStyle(
    "H1", parent=styles["Heading1"],
    fontSize=16, textColor=colors.HexColor("#1a237e"),
    spaceBefore=16, spaceAfter=6,
    borderPad=4
)
h2 = ParagraphStyle(
    "H2", parent=styles["Heading2"],
    fontSize=13, textColor=colors.HexColor("#283593"),
    spaceBefore=12, spaceAfter=4
)
body = ParagraphStyle(
    "Body", parent=styles["Normal"],
    fontSize=10, leading=15, spaceAfter=6
)
code_style = ParagraphStyle(
    "Code", parent=styles["Code"],
    fontSize=8.5, backColor=colors.HexColor("#f5f5f5"),
    borderColor=colors.HexColor("#cccccc"), borderWidth=0.5,
    borderPad=6, leading=13
)
label_style = ParagraphStyle(
    "Label", parent=styles["Normal"],
    fontSize=9, textColor=colors.white, alignment=TA_CENTER
)

def divider():
    return HRFlowable(width="100%", thickness=1,
                      color=colors.HexColor("#3949ab"), spaceAfter=8)

def section_header(text):
    return [Spacer(1, 6), Paragraph(text, h1), divider()]

def sub_header(text):
    return Paragraph(text, h2)

def p(text):
    return Paragraph(text, body)

def tbl(data, col_widths=None, header_color="#1a237e"):
    t = Table(data, colWidths=col_widths, repeatRows=1)
    style = TableStyle([
        ("BACKGROUND",   (0,0), (-1,0), colors.HexColor(header_color)),
        ("TEXTCOLOR",    (0,0), (-1,0), colors.white),
        ("FONTNAME",     (0,0), (-1,0), "Helvetica-Bold"),
        ("FONTSIZE",     (0,0), (-1,0), 9),
        ("ALIGN",        (0,0), (-1,0), "CENTER"),
        ("FONTNAME",     (0,1), (-1,-1), "Helvetica"),
        ("FONTSIZE",     (0,1), (-1,-1), 8.5),
        ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#e8eaf6")]),
        ("GRID",         (0,0), (-1,-1), 0.4, colors.HexColor("#9fa8da")),
        ("VALIGN",       (0,0), (-1,-1), "MIDDLE"),
        ("TOPPADDING",   (0,0), (-1,-1), 5),
        ("BOTTOMPADDING",(0,0), (-1,-1), 5),
        ("LEFTPADDING",  (0,0), (-1,-1), 6),
    ])
    t.setStyle(style)
    return t

# ─── STORY ────────────────────────────────────────────────────────────────────
story = []

# ── Cover ──────────────────────────────────────────────────────────────────────
story.append(Spacer(1, 3*cm))
story.append(Paragraph("EXAM PLATFORM", title_style))
story.append(Paragraph("Production-Grade Online Examination System", subtitle_style))
story.append(Spacer(1, 0.4*cm))
story.append(Paragraph("JEE / NEET / CUET Scale — 1M+ Concurrent Users", subtitle_style))
story.append(Spacer(1, 0.6*cm))
story.append(divider())
story.append(Spacer(1, 0.4*cm))

meta = [
    ["Version", "1.0.0"],
    ["Date", "June 2026"],
    ["Stack", "Spring Boot 3.2 · Kafka · Redis · PostgreSQL · MongoDB"],
    ["Infra", "Docker · Kubernetes (Azure AKS) · Cloudflare CDN"],
]
story.append(tbl(meta, col_widths=[4*cm, 12*cm], header_color="#283593"))
story.append(PageBreak())

# ── 1. Overview ────────────────────────────────────────────────────────────────
story += section_header("1. System Overview")
story.append(p(
    "The Exam Platform is a <b>production-grade, microservices-based online examination system</b> "
    "designed to handle <b>1 million+ concurrent users</b>. It supports real-time test taking, "
    "massive concurrency, high availability (99.99%), fault tolerance, and strict anti-cheating mechanisms. "
    "The architecture is inspired by systems like JEE Main, NEET, CUET, and TCS iON."
))
story.append(Spacer(1, 8))

story.append(sub_header("User Roles"))
roles = [
    ["Role", "Description"],
    ["Student", "Test taker — registers, enrolls in exams, takes the test in real time"],
    ["Admin", "Exam creator — designs papers, manages questions and slots"],
    ["Super Admin", "Platform monitor — views real-time dashboards and analytics"],
]
story.append(tbl(roles, col_widths=[4*cm, 12*cm]))
story.append(Spacer(1, 10))

# ── 2. Architecture ─────────────────────────────────────────────────────────────
story += section_header("2. High-Level Architecture")
story.append(p(
    "The system follows an <b>event-driven, microservices architecture</b>. "
    "All client traffic enters through a single <b>API Gateway</b> (Spring Cloud Gateway) "
    "which handles JWT validation, rate limiting, and routing. Services are stateless and "
    "communicate asynchronously via <b>Apache Kafka</b>. "
    "Answer state is maintained in <b>Redis</b> (hot path) and durably persisted to "
    "<b>MongoDB</b> (warm path) every 15 seconds."
))

arch = [
    ["Layer", "Component", "Technology"],
    ["Entry",         "API Gateway",        "Spring Cloud Gateway + JWT Filter"],
    ["Auth",          "User Service",       "Spring Boot · Argon2id · RS256 JWT"],
    ["Exam Mgmt",     "Exam Service",       "Spring Boot · PostgreSQL · MongoDB"],
    ["Real-time",     "Test Engine",        "Spring Boot · STOMP WebSocket · Redis"],
    ["Evaluation",    "Result Service",     "Spring Boot · PostgreSQL window functions"],
    ["Messaging",     "Event Bus",          "Apache Kafka (3 brokers, rf=3)"],
    ["Cache",         "Session Store",      "Redis 7 (AOF persistence)"],
    ["Relational DB", "Structured Data",    "PostgreSQL 16 (1 primary + 3 replicas)"],
    ["Document DB",   "Questions/Sessions", "MongoDB 7 (3-shard Atlas cluster)"],
    ["CDN / WAF",     "Static Assets",      "Cloudflare Enterprise"],
    ["Infra",         "Orchestration",      "Azure AKS + Docker + Kubernetes"],
]
story.append(tbl(arch, col_widths=[4*cm, 5*cm, 7*cm]))
story.append(PageBreak())

# ── 3. Services ─────────────────────────────────────────────────────────────────
story += section_header("3. Microservices Breakdown")

services = [
    ["Service", "Port", "Responsibility"],
    ["api-gateway",    "8080", "JWT validation · Rate limiting · Circuit breaking · Routing"],
    ["user-service",   "8081", "Registration · Login · OTP · JWT issuance · Refresh tokens"],
    ["exam-service",   "8082", "Exam CRUD · Question bank · Slot management · Enrollment · Paper generation"],
    ["test-engine",    "8083", "WebSocket sessions · Autosave · Anti-cheat · Timer enforcement"],
    ["result-service", "8084", "Score evaluation · Rank computation · Percentile · Analytics export"],
]
story.append(tbl(services, col_widths=[4*cm, 2*cm, 10*cm]))
story.append(Spacer(1, 10))

story.append(sub_header("Infrastructure Containers (Dev)"))
infra = [
    ["Container", "Image", "Port", "Status"],
    ["exam-postgres",      "postgres:16-alpine",             "5432",  "Healthy"],
    ["exam-mongodb",       "mongo:7.0",                      "27017", "Healthy"],
    ["exam-redis",         "redis:7.2-alpine",               "6379",  "Healthy"],
    ["exam-kafka",         "confluentinc/cp-kafka:7.6.0",    "9092",  "Up"],
    ["exam-zookeeper",     "confluentinc/cp-zookeeper:7.6.0","2181",  "Up"],
    ["exam-kafka-ui",      "provectuslabs/kafka-ui:latest",  "9000",  "Up"],
]
story.append(tbl(infra, col_widths=[5*cm, 6*cm, 2.5*cm, 2.5*cm]))
story.append(PageBreak())

# ── 4. API Design ───────────────────────────────────────────────────────────────
story += section_header("4. API Design — Key Endpoints")

endpoints = [
    ["Method", "Endpoint", "Service", "Auth", "Description"],
    ["POST", "/api/v1/auth/register",           "user-service",   "None",    "Register new student"],
    ["POST", "/api/v1/auth/login",              "user-service",   "None",    "Login, returns JWT"],
    ["POST", "/api/v1/auth/otp/send",           "user-service",   "None",    "Send OTP to phone"],
    ["POST", "/api/v1/auth/refresh",            "user-service",   "None",    "Refresh access token"],
    ["POST", "/api/v1/auth/logout",             "user-service",   "JWT",     "Blacklist token"],
    ["POST", "/api/v1/exams",                   "exam-service",   "ADMIN",   "Create new exam"],
    ["GET",  "/api/v1/exams/{id}",              "exam-service",   "JWT",     "Get exam details"],
    ["POST", "/api/v1/exams/{id}/publish",      "exam-service",   "ADMIN",   "Publish exam + trigger pre-scale"],
    ["GET",  "/api/v1/exams/{id}/slots",        "exam-service",   "JWT",     "List available slots"],
    ["POST", "/api/v1/exams/{id}/enroll",       "exam-service",   "STUDENT", "Enroll student in slot"],
    ["GET",  "/api/v1/exams/{id}/paper/{sid}",  "exam-service",   "STUDENT", "Get student's shuffled paper"],
    ["POST", "/api/v1/sessions/start",          "test-engine",    "STUDENT", "Start or resume exam session"],
    ["GET",  "/api/v1/sessions/{id}",           "test-engine",    "STUDENT", "Get current session state"],
    ["POST", "/api/v1/sessions/{id}/answer",    "test-engine",    "STUDENT", "Save single answer (HTTP fallback)"],
    ["POST", "/api/v1/sessions/{id}/submit",    "test-engine",    "STUDENT", "Final exam submission"],
    ["GET",  "/api/v1/results/{examId}/{stuId}","result-service", "JWT",     "Get student result"],
    ["GET",  "/api/v1/results/{examId}/leaderboard","result-service","JWT",  "Top-N leaderboard"],
    ["POST", "/api/v1/results/{examId}/compute","result-service", "ADMIN",   "Trigger rank computation"],
    ["GET",  "/api/v1/results/{examId}/export", "result-service", "ADMIN",   "Download results CSV"],
]
story.append(tbl(endpoints, col_widths=[1.5*cm, 6.5*cm, 3*cm, 2*cm, 3*cm]))
story.append(PageBreak())

# ── 5. Database Schema ──────────────────────────────────────────────────────────
story += section_header("5. Database Schema")

story.append(sub_header("PostgreSQL Tables"))
pg_tables = [
    ["Table", "Key Columns", "Notes"],
    ["users",       "id (UUID PK), email, phone, password_hash, role, is_verified", "Argon2id hashed passwords"],
    ["exams",       "id, title, exam_type, status, start_time, end_time, duration_mins", "DRAFT→PUBLISHED→LIVE→COMPLETED"],
    ["exam_sections","id, exam_id (FK), name, subject, max_questions, marks_per_q", "Ordered sections per exam"],
    ["exam_slots",  "id, exam_id (FK), slot_date, start_time, capacity, enrolled_count", "Distributed lock on enrollment"],
    ["enrollments", "id, student_id, exam_id, slot_id (FK), roll_number, status", "Unique constraint (student+exam)"],
    ["results",     "id, enrollment_id, student_id, exam_id, total_score, rank, percentile", "Window fn for rank/percentile"],
]
story.append(tbl(pg_tables, col_widths=[3.5*cm, 7.5*cm, 5*cm]))
story.append(Spacer(1, 10))

story.append(sub_header("MongoDB Collections"))
mongo_tables = [
    ["Collection", "Key Fields", "Purpose"],
    ["questions",        "exam_type, subject, difficulty, type, options, correct_answer", "Question bank — MCQ/Numerical/Subjective"],
    ["answer_snapshots", "session_id, student_id, exam_id, answers{}, time_remaining_secs, status", "Durable autosave backup (flush every 15s)"],
]
story.append(tbl(mongo_tables, col_widths=[4*cm, 8*cm, 4*cm]))
story.append(PageBreak())

# ── 6. Critical Flows ───────────────────────────────────────────────────────────
story += section_header("6. Critical System Flows")

story.append(sub_header("6.1 Student Takes Exam"))
flow1 = [
    ["Step", "Action", "Component"],
    ["1", "Student logs in, receives RS256 JWT (15 min) + Refresh token (7 days)", "user-service"],
    ["2", "Student fetches shuffled paper (deterministic seed = hash(studentId+examId))", "exam-service"],
    ["3", "POST /sessions/start — session created in Redis + MongoDB baseline", "test-engine"],
    ["4", "WebSocket (STOMP) connection established to /ws/exam", "test-engine"],
    ["5", "Client sends heartbeat every 5s with answers + time remaining", "test-engine"],
    ["6", "Server saves to Redis < 2ms, marks session dirty for MongoDB flush", "test-engine"],
    ["7", "Scheduled job flushes dirty sessions to MongoDB every 15s", "test-engine"],
    ["8", "Student submits — immediate MongoDB flush + Kafka event published", "test-engine"],
    ["9", "Result service consumes EXAM_SUBMITTED event, scores answers", "result-service"],
    ["10","Admin triggers rank computation using PostgreSQL window functions", "result-service"],
]
story.append(tbl(flow1, col_widths=[1*cm, 11*cm, 4*cm]))
story.append(Spacer(1, 10))

story.append(sub_header("6.2 Session Resume After Disconnect"))
flow2 = [
    ["Step", "Action"],
    ["1", "Client reconnects, calls GET /sessions/{sessionId}"],
    ["2", "test-engine checks Redis for session state"],
    ["3", "Redis miss? Load from MongoDB, recompute time remaining (now - startedAt)"],
    ["4", "Re-populate Redis with restored state + new TTL"],
    ["5", "Return full session state (answers, time remaining) to client"],
    ["6", "Client resumes from exact state — zero answer loss"],
]
story.append(tbl(flow2, col_widths=[1*cm, 15*cm]))
story.append(PageBreak())

# ── 7. Scaling Strategy ─────────────────────────────────────────────────────────
story += section_header("7. Scaling Strategy")

story.append(sub_header("Scale Numbers"))
scale = [
    ["Metric", "Target"],
    ["Concurrent WebSocket sessions",   "1,000,000 (500 pods x 2,000 connections)"],
    ["Answer save latency (p99)",        "< 500ms"],
    ["Peak join rate",                  "500,000 users/minute"],
    ["Data loss guarantee",             "Zero (Redis AOF + Kafka rf=3)"],
    ["Availability",                    "99.99% (Azure Traffic Manager multi-region)"],
    ["RTO (Recovery Time Objective)",   "< 2 minutes"],
]
story.append(tbl(scale, col_widths=[7*cm, 9*cm]))
story.append(Spacer(1, 10))

story.append(sub_header("Kubernetes HPA Configuration"))
hpa = [
    ["Service", "Min Pods", "Max Pods", "Scale Trigger"],
    ["api-gateway",    "3",   "20",  "CPU > 70%"],
    ["user-service",   "2",   "10",  "CPU > 70%"],
    ["exam-service",   "2",   "10",  "CPU > 70%"],
    ["test-engine",    "10", "500",  "WebSocket connections > 1,800/pod"],
    ["result-service", "2",   "20",  "Kafka consumer lag > 1,000"],
]
story.append(tbl(hpa, col_widths=[5*cm, 2.5*cm, 2.5*cm, 6*cm]))
story.append(PageBreak())

# ── 8. Security ─────────────────────────────────────────────────────────────────
story += section_header("8. Security Design")
security = [
    ["Area", "Implementation"],
    ["Authentication",   "RS256 JWT (15 min access + 7 day refresh) with Redis blacklist"],
    ["Password Hashing", "Argon2id (iterations=3, memory=64MB, parallelism=4)"],
    ["OTP",              "6-digit TOTP, 5 min TTL, 3 attempts max, 30 min lockout in Redis"],
    ["Rate Limiting",    "Spring Cloud Gateway RequestRateLimiter — per IP and per user"],
    ["DDoS Protection",  "Cloudflare Enterprise WAF + Azure DDoS Standard"],
    ["Transport",        "HTTPS everywhere — TLS 1.3 enforced at Cloudflare edge"],
    ["Anti-Cheat",       "Tab switch (15pts), copy-paste (10pts), dev tools (25pts) risk scoring"],
    ["Secrets",          "JWT keys injected via environment variables — never committed to git"],
    ["Headers",          "X-Frame-Options: DENY, X-Content-Type-Options: nosniff, CSP"],
]
story.append(tbl(security, col_widths=[4*cm, 12*cm]))
story.append(PageBreak())

# ── 9. CI/CD ────────────────────────────────────────────────────────────────────
story += section_header("9. CI/CD Pipeline")
story.append(p(
    "The platform uses <b>GitHub Actions</b> for CI and <b>ArgoCD (GitOps)</b> for CD "
    "to Azure AKS. Every push triggers automated build, test, Docker image publish, "
    "and rolling deployment."
))
cicd = [
    ["Stage", "Tool", "Action"],
    ["Build",         "GitHub Actions", "Maven build + unit tests for all 5 services"],
    ["Containerise",  "Docker",         "Multi-stage build → distroless Java 21 image"],
    ["Publish",       "Azure Container Registry", "Push image with commit SHA tag"],
    ["Deploy (Dev)",  "ArgoCD",         "Auto-sync on main branch push"],
    ["Deploy (Prod)", "ArgoCD",         "Manual approval gate → rolling update on AKS"],
    ["Health Check",  "Kubernetes",     "Readiness + liveness probes on /actuator/health"],
    ["Rollback",      "ArgoCD",         "One-click rollback to any previous image SHA"],
]
story.append(tbl(cicd, col_widths=[3.5*cm, 5*cm, 7.5*cm]))
story.append(PageBreak())

# ── 10. Quick Start ─────────────────────────────────────────────────────────────
story += section_header("10. Local Quick Start")
story.append(p("<b>Prerequisites:</b> Docker Desktop, OpenSSL, Java 21"))
story.append(Spacer(1, 4))

steps = [
    ["Step", "Command"],
    ["1. Generate JWT keys",
     "openssl genrsa -out private.pem 2048\nopenssl rsa -in private.pem -pubout -out public.pem"],
    ["2. Create .env",
     "JWT_PRIVATE_KEY_PEM=$(base64 -w0 private.pem)\nJWT_PUBLIC_KEY_PEM=$(base64 -w0 public.pem)"],
    ["3. Start stack",
     "cd infra/docker\ndocker-compose --env-file ../../.env up -d"],
    ["4. Verify health",
     "curl http://localhost:8080/actuator/health"],
    ["5. Register student",
     'curl -X POST http://localhost:8080/api/v1/auth/register \\\n  -H "Content-Type: application/json" \\\n  -d \'{"firstName":"Test","lastName":"User","email":"test@example.com","password":"Test@1234"}\''],
]
story.append(tbl(steps, col_widths=[3*cm, 13*cm]))
story.append(Spacer(1, 10))

story.append(sub_header("Service URLs"))
urls = [
    ["Service", "URL"],
    ["API Gateway",      "http://localhost:8080"],
    ["Kafka UI",         "http://localhost:9000"],
    ["PostgreSQL",       "localhost:5432  (user: examuser / pass: exampass)"],
    ["MongoDB",          "localhost:27017"],
    ["Redis",            "localhost:6379"],
]
story.append(tbl(urls, col_widths=[5*cm, 11*cm]))

# ── Footer note ─────────────────────────────────────────────────────────────────
story.append(Spacer(1, 1*cm))
story.append(divider())
story.append(Paragraph(
    "Exam Platform v1.0.0 — Confidential — Generated June 2026",
    ParagraphStyle("footer", parent=styles["Normal"],
                   fontSize=8, textColor=colors.grey, alignment=TA_CENTER)
))

# ── Build ───────────────────────────────────────────────────────────────────────
doc.build(story)
print(f"PDF saved to: {OUTPUT}")
