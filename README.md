# Exam Platform — Production-Grade Online Examination System

> Designed for 1M+ concurrent users | JEE / NEET / CUET scale | Spring Boot + Kafka + Redis + MongoDB

---

## Project Structure

```
exam-platform/
├── docs/
│   └── ARCHITECTURE.md           ← Full system design (READ THIS FIRST)
├── services/
│   ├── api-gateway/              ← Spring Cloud Gateway (port 8080)
│   ├── user-service/             ← Auth, OTP, JWT (port 8081)
│   ├── exam-service/             ← Exams, Questions, Paper Gen (port 8082)
│   ├── test-engine/              ← WebSocket, Autosave, Anti-Cheat (port 8083)
│   └── result-service/           ← Evaluation, Ranks, Analytics (port 8084)
├── db/
│   ├── migrations/               ← Flyway PostgreSQL migrations
│   └── mongodb/                  ← MongoDB schema + index definitions
├── infra/
│   ├── docker/                   ← Dockerfiles + docker-compose (dev)
│   └── k8s/                      ← Kubernetes manifests + HPA + CronJobs
└── .github/workflows/            ← GitHub Actions CI/CD pipeline
```

## Quick Start (Local Dev)

```bash
# 1. Start all infrastructure + services
cd infra/docker
docker-compose up -d

# 2. Verify services are healthy
curl http://localhost:8080/actuator/health

# 3. Register a student
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Rahul","lastName":"Sharma","email":"rahul@example.com","password":"Test@1234"}'

# 4. Login and get JWT
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"rahul@example.com","password":"Test@1234"}'
```

## Service Ports

| Service | Port | Description |
|---------|------|-------------|
| API Gateway | 8080 | Single entry point |
| User Service | 8081 | Auth & profiles |
| Exam Service | 8082 | Exams & questions |
| Test Engine | 8083 | Real-time test taking |
| Result Service | 8084 | Evaluation & analytics |
| Kafka UI | 9000 | Monitor message queues |

## Key APIs

### Start an Exam Session
```bash
POST /api/v1/sessions/start
Authorization: Bearer <student-jwt>
{
  "examId": "uuid",
  "enrollmentId": "uuid",
  "durationSeconds": 10800
}
```

### WebSocket Connection (STOMP)
```javascript
const client = new Client({
  brokerURL: 'wss://api.examplatform.com/ws/exam',
  connectHeaders: { Authorization: 'Bearer <token>' }
});

client.subscribe('/topic/session/<sessionId>', (msg) => {
  const ack = JSON.parse(msg.body);  // { type: "ACK", timeRemaining: 3599 }
});

// Send heartbeat every 5s
setInterval(() => {
  client.publish({
    destination: '/app/exam/<sessionId>/heartbeat',
    body: JSON.stringify({ type: 'HEARTBEAT', answers: {...}, timeRemaining: 3599 })
  });
}, 5000);
```

### Save Answer (HTTP fallback)
```bash
POST /api/v1/sessions/{sessionId}/answer
{ "questionId": "uuid", "answer": "B", "timeSpentSecs": 45 }
```

## Architecture Summary

- **API Gateway** → JWT validation, rate limiting, circuit breaking, routing
- **User Service** → Argon2id passwords, RS256 JWT, OTP via Redis TTL
- **Exam Service** → Deterministic paper shuffle per student (seed = hash(studentId+examId))
- **Test Engine** → Answers in Redis (< 1ms), Kafka for durability, MongoDB for persistence
- **Result Service** → SQL window functions for O(n log n) rank computation

See `docs/ARCHITECTURE.md` for complete design including flows, DR plan, and cost optimization.

## Scale Numbers

| Scenario | Capacity |
|----------|----------|
| Concurrent WebSocket sessions | 1,000,000 (500 pods × 2000 connections) |
| Answer save latency (p99) | < 500ms |
| Peak join rate | 500,000/min (handled by pre-scaling) |
| Zero data loss guarantee | Redis AOF + Kafka replication factor 3 |
| RTO | < 2 minutes (Azure Traffic Manager failover) |

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.2 + Java 21 (ZGC) |
| Real-time | STOMP WebSocket over SockJS |
| Session State | Redis 7 (AOF persistence) |
| Durability | Apache Kafka (3 brokers, rf=3) |
| Relational DB | PostgreSQL 16 (primary + 3 replicas) |
| Document DB | MongoDB 7 Atlas (3-shard) |
| Infra | Azure AKS + Azure Container Registry |
| CDN/WAF | Cloudflare Enterprise |
| Observability | Prometheus + Grafana + Azure Monitor |
| CI/CD | GitHub Actions → ArgoCD (GitOps) |
