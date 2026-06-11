"""
HLD PDF Generator — Exam Platform (JEE/NEET/CUET Scale)
Generates a detailed, fully labelled architecture + UI document using ReportLab.

Pages:
  1  Cover
  2  System Architecture (6 layers)            — A3 landscape
  3  Happy Flow Part 1 (Login -> Start Exam)   — A3 landscape
  4  Happy Flow Part 2 (Answer -> Result)      — A3 landscape
  5  UI Mockup: Login + Register               — A4 landscape
  6  UI Mockup: Student Dashboard              — A4 landscape
  7  UI Mockup: Exam Interface (annotated)     — A3 landscape
  8  UI Mockup: Result Page                    — A4 landscape
  9  Component & Data Store reference tables   — A4 portrait
 10  Scaling + Security + Design decisions     — A4 portrait
"""

import os
from reportlab.lib.pagesizes import A4, A3, landscape
from reportlab.lib import colors
from reportlab.lib.units import mm
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    Paragraph, Spacer, PageBreak, Table, TableStyle, HRFlowable,
    BaseDocTemplate, Frame, PageTemplate,
)
from pypdf import PdfWriter, PdfReader

# ── Output path ─────────────────────────────────────────────────────────────
OUT_DIR  = os.path.dirname(os.path.abspath(__file__))
OUT_PATH = os.path.join(OUT_DIR, "HLD_ExamPlatform.pdf")

# ── Colour palette ──────────────────────────────────────────────────────────
C_NAVY       = colors.HexColor("#0D1B2A")
C_DARK_BLUE  = colors.HexColor("#0F3460")
C_MED_BLUE   = colors.HexColor("#16213E")
C_ACCENT     = colors.HexColor("#E94560")
C_TEAL       = colors.HexColor("#1A8FE3")
C_GREEN      = colors.HexColor("#27AE60")
C_ORANGE     = colors.HexColor("#F39C12")
C_PURPLE     = colors.HexColor("#8E44AD")
C_LIGHT_GREY = colors.HexColor("#F5F6FA")
C_MID_GREY   = colors.HexColor("#BDC3C7")
C_TEXT       = colors.HexColor("#2C3E50")
C_WHITE      = colors.white
C_RED        = colors.HexColor("#E74C3C")
C_GW_PURPLE  = colors.HexColor("#6C3483")
C_SVC_GREEN  = colors.HexColor("#117A65")
C_SVC_RED    = colors.HexColor("#922B21")

A4W,  A4H  = A4
A4LW, A4LH = landscape(A4)
A3LW, A3LH = landscape(A3)


# ══════════════════════════════════════════════════════════════════════════
#  LOW-LEVEL DRAW HELPERS
# ══════════════════════════════════════════════════════════════════════════

def page_chrome(c, title, page_num, width, height):
    """Header + footer band on every non-cover page."""
    c.setFillColor(C_NAVY)
    c.rect(0, height - 26*mm, width, 26*mm, fill=1, stroke=0)
    c.setFillColor(C_ACCENT)
    c.rect(0, height - 27*mm, width, 1.2*mm, fill=1, stroke=0)
    c.setFillColor(C_WHITE)
    c.setFont("Helvetica-Bold", 11)
    c.drawString(15*mm, height - 17*mm, "EXAM PLATFORM  —  HIGH LEVEL DESIGN")
    c.setFont("Helvetica", 9.5)
    c.drawRightString(width - 15*mm, height - 17*mm, title)

    c.setFillColor(C_NAVY)
    c.rect(0, 0, width, 10*mm, fill=1, stroke=0)
    c.setFillColor(C_WHITE)
    c.setFont("Helvetica", 7.5)
    c.drawString(15*mm, 3.5*mm,
                 "CONFIDENTIAL — Internal Architecture Document   |   1M+ Concurrent Users   |   99.99% SLA")
    c.drawRightString(width - 15*mm, 3.5*mm, f"Page {page_num}")


def box(c, x, y, w, h, fill, stroke=None, radius=4, lw=1.0):
    c.setFillColor(fill)
    if stroke:
        c.setStrokeColor(stroke)
        c.setLineWidth(lw)
        c.roundRect(x, y, w, h, radius, fill=1, stroke=1)
    else:
        c.roundRect(x, y, w, h, radius, fill=1, stroke=0)


def centre(c, cx, y, text, font="Helvetica", size=9, color=C_TEXT):
    c.setFont(font, size)
    c.setFillColor(color)
    c.drawCentredString(cx, y, text)


def ltext(c, x, y, text, font="Helvetica", size=9, color=C_TEXT):
    c.setFont(font, size)
    c.setFillColor(color)
    c.drawString(x, y, text)


def varrow(c, x, y_top, y_bot, color=C_MID_GREY, lw=1.6):
    c.setStrokeColor(color); c.setFillColor(color); c.setLineWidth(lw)
    c.line(x, y_top, x, y_bot + 5)
    p = c.beginPath(); p.moveTo(x, y_bot); p.lineTo(x-4.5, y_bot+9); p.lineTo(x+4.5, y_bot+9); p.close()
    c.drawPath(p, fill=1, stroke=0)


def harrow(c, x_from, x_to, y, color=C_MID_GREY, lw=1.3, dashed=False):
    c.setStrokeColor(color); c.setFillColor(color); c.setLineWidth(lw)
    if dashed: c.setDash(4, 3)
    end = x_to - 6 if x_to > x_from else x_to + 6
    c.line(x_from, y, end, y)
    c.setDash()
    p = c.beginPath()
    if x_to > x_from:
        p.moveTo(x_to, y); p.lineTo(x_to-9, y+4.5); p.lineTo(x_to-9, y-4.5)
    else:
        p.moveTo(x_to, y); p.lineTo(x_to+9, y+4.5); p.lineTo(x_to+9, y-4.5)
    p.close(); c.drawPath(p, fill=1, stroke=0)


# ══════════════════════════════════════════════════════════════════════════
#  PAGE 1 — COVER
# ══════════════════════════════════════════════════════════════════════════

def draw_cover(c):
    w, h = A4W, A4H
    c.setFillColor(C_NAVY); c.rect(0, 0, w, h, fill=1, stroke=0)
    c.setFillColor(C_MED_BLUE); c.rect(0, h*0.42, w, h*0.58, fill=1, stroke=0)
    c.setFillColor(C_ACCENT); c.rect(0, h*0.565, w, 5, fill=1, stroke=0)

    centre(c, w/2, h*0.70, "EXAM PLATFORM", "Helvetica-Bold", 34, C_WHITE)
    centre(c, w/2, h*0.635, "HIGH LEVEL DESIGN  (HLD)", "Helvetica-Bold", 19, C_TEAL)
    centre(c, w/2, h*0.585, "JEE / NEET / CUET / TCS iON Scale   |   1M+ Concurrent Users",
           "Helvetica", 11.5, C_WHITE)

    meta = [
        ("Version", "2.0  (Detailed + UI)"),
        ("Date", "June 2026"),
        ("Scale", "1,000,000+ concurrent users"),
        ("Availability", "99.99% SLA  |  Sub-second latency"),
        ("Frontend", "Next.js 14 (App Router, SSR + BFF)"),
        ("Backend", "Spring Boot 3.2  (4 microservices)"),
        ("Data", "PostgreSQL  |  MongoDB  |  Redis  |  Kafka"),
        ("Infra", "Azure AKS  |  Docker  |  Cloudflare CDN"),
    ]
    bx, by, bw = w*0.13, h*0.27, w*0.74
    bh = len(meta)*20 + 18
    c.setFillColor(colors.HexColor("#1A2A3A"))
    c.roundRect(bx, by, bw, bh, 8, fill=1, stroke=0)
    for i, (k, v) in enumerate(reversed(meta)):
        ry = by + 12 + i*20
        ltext(c, bx+18, ry, k, "Helvetica-Bold", 9, C_TEAL)
        ltext(c, bx+140, ry, v, "Helvetica", 9, C_WHITE)

    centre(c, w/2, h*0.205,
           "Microservices  •  Event-Driven  •  Server-Side Rendering  •  Horizontal Scaling  •  Fault Tolerant",
           "Helvetica-Oblique", 9.5, C_MID_GREY)
    centre(c, w/2, 18*mm,
           "Contents:  Architecture  ·  Happy Flow  ·  UI Mockups  ·  Component Reference  ·  Scaling & Security",
           "Helvetica", 8, colors.HexColor("#556677"))


# ══════════════════════════════════════════════════════════════════════════
#  PAGE 2 — ARCHITECTURE (6 layers)
# ══════════════════════════════════════════════════════════════════════════

def draw_architecture(c):
    w, h = A3LW, A3LH
    page_chrome(c, "System Architecture — 6 Layers", 2, w, h)

    PAD = 12*mm
    LEFT, RIGHT = PAD, w - PAD
    CW = RIGHT - LEFT
    top = h - 26*mm - 8*mm

    # layer heights / gaps
    L1, L2, L3, L4, L5, L6 = 46, 46, 56, 74, 92, 80
    GAP = 22

    y1 = top - L1
    y2 = y1 - GAP - L2
    y3 = y2 - GAP - L3
    y4 = y3 - GAP - L4
    y5 = y4 - GAP - L5
    y6 = y5 - GAP - L6

    bands = [
        (y1, L1, colors.HexColor("#EBF5FB"), "LAYER 1  —  CLIENT TIER"),
        (y2, L2, colors.HexColor("#FEF9E7"), "LAYER 2  —  EDGE / CDN / WAF"),
        (y3, L3, colors.HexColor("#E8F8F5"), "LAYER 3  —  LOAD BALANCER + FRONTEND (Next.js SSR)"),
        (y4, L4, colors.HexColor("#F4ECF7"), "LAYER 4  —  API GATEWAY (Spring Cloud Gateway)"),
        (y5, L5, colors.HexColor("#EAF2FF"), "LAYER 5  —  MICROSERVICES (Spring Boot)"),
        (y6, L6, colors.HexColor("#FDEDEC"), "LAYER 6  —  DATA & MESSAGING TIER"),
    ]
    for (ly, lh, lc, lt) in bands:
        c.setFillColor(lc); c.rect(LEFT-5, ly-3, CW+10, lh+6, fill=1, stroke=0)
        # label sits in the gap ABOVE the band (left-aligned) — never over content
        ltext(c, LEFT, ly+lh+6, lt, "Helvetica-Bold", 7.5, colors.HexColor("#8895A0"))

    cxmid = LEFT + CW/2

    # ── Layer 1: clients
    cw, ch = 150, 30
    clients = [("Student", "Web / Mobile Browser", C_TEAL, LEFT+CW*0.16),
               ("Admin",   "Exam Creator Console",  C_DARK_BLUE, LEFT+CW*0.5),
               ("Super Admin", "Monitoring + Analytics", C_PURPLE, LEFT+CW*0.84)]
    for (t, s, col, cx) in clients:
        bx, by = cx-cw/2, y1+(L1-ch)/2
        box(c, bx, by, cw, ch, col, C_WHITE, 6)
        centre(c, cx, by+ch/2+3, t, "Helvetica-Bold", 10, C_WHITE)
        centre(c, cx, by+ch/2-8, s, "Helvetica", 7, colors.HexColor("#DDEEFF"))
        varrow(c, cx, y1-2, y2+L2+2)
    centre(c, cxmid, y1-GAP/2-1, "HTTPS  (TLS 1.3)", "Helvetica-Oblique", 7.5, C_MID_GREY)

    # ── Layer 2: CDN
    cdx, cdw = LEFT+20, CW-40
    cdy, cdh = y2+8, L2-16
    box(c, cdx, cdy, cdw, cdh, C_ORANGE, C_WHITE, 6)
    centre(c, cdx+cdw/2, cdy+cdh-13, "CLOUDFLARE CDN  /  WAF  /  DDoS SHIELD", "Helvetica-Bold", 10, C_WHITE)
    chips = ["DDoS Protection", "Rate Limit (IP)", "SSL Termination",
             "Static Asset Cache", "Bot Detection", "Edge Image Serving"]
    iw = cdw/len(chips)
    for i, ch_ in enumerate(chips):
        box(c, cdx+i*iw+5, cdy+5, iw-10, 15, colors.HexColor("#FFF3D6"), radius=3)
        centre(c, cdx+i*iw+iw/2, cdy+10, ch_, "Helvetica", 7, colors.HexColor("#7D5A00"))
    varrow(c, cxmid, y2-2, y3+L3+2)
    centre(c, cxmid, y2-GAP/2-1, "Dynamic requests -> origin", "Helvetica-Oblique", 7.5, C_MID_GREY)

    # ── Layer 3: LB + Next.js pods
    lbw = CW*0.26
    lbx, lby, lbh = LEFT+8, y3+10, L3-20
    box(c, lbx, lby, lbw, lbh, C_SVC_GREEN, C_WHITE, 6)
    centre(c, lbx+lbw/2, lby+lbh-14, "AZURE LOAD BALANCER", "Helvetica-Bold", 9, C_WHITE)
    centre(c, lbx+lbw/2, lby+lbh-26, "Layer-7  |  Round-Robin  |  TLS", "Helvetica", 7, colors.HexColor("#D5F5E3"))
    centre(c, lbx+lbw/2, lby+10, "HPA: 3 - 20 frontend pods", "Helvetica-Oblique", 7, colors.HexColor("#D5F5E3"))

    pods = ["Next.js-1", "Next.js-2", "Next.js-3", "Next.js-N"]
    pw = (CW-lbw-70)/len(pods)
    px0 = lbx+lbw+25
    for i, pl in enumerate(pods):
        px, py, ph = px0+i*(pw+6), y3+12, L3-24
        col = C_TEAL if i < 3 else C_MID_GREY
        box(c, px, py, pw, ph, col, C_WHITE, 5)
        centre(c, px+pw/2, py+ph-13, pl, "Helvetica-Bold", 8, C_WHITE)
        centre(c, px+pw/2, py+ph/2-1, ":3000", "Helvetica", 7, C_WHITE)
        centre(c, px+pw/2, py+9, "SSR + BFF", "Helvetica", 6.5, colors.HexColor("#E8F6FF"))
        harrow(c, lbx+lbw, px, py+ph/2, C_GREEN)
    varrow(c, cxmid, y3-2, y4+L4+2)
    centre(c, cxmid, y3-GAP/2-1, "HTTP  (internal K8s network)", "Helvetica-Oblique", 7.5, C_MID_GREY)

    # ── Layer 4: API Gateway
    gx, gy, gw, gh = LEFT+8, y4+8, CW-16, L4-16
    box(c, gx, gy, gw, gh, C_GW_PURPLE, C_WHITE, 7)
    centre(c, gx+gw/2, gy+gh-14, "SPRING CLOUD GATEWAY   (WebFlux / Netty)   :8080",
           "Helvetica-Bold", 10, C_WHITE)
    filters = [("JWT Filter", "RS256 verify"), ("Rate Limiter", "Redis bucket"),
               ("Circuit Breaker", "Resilience4j"), ("CORS Guard", "origin allow"),
               ("Retry", "x2 on 5xx")]
    fw = (gw*0.52)/len(filters)
    for i, (a, b) in enumerate(filters):
        fx, fy, fh = gx+10+i*fw, gy+12, gh-44
        box(c, fx, fy, fw-6, fh, colors.HexColor("#9B59B6"), C_WHITE, 4)
        centre(c, fx+(fw-6)/2, fy+fh/2+3, a, "Helvetica-Bold", 7, C_WHITE)
        centre(c, fx+(fw-6)/2, fy+fh/2-7, b, "Helvetica", 6.3, colors.HexColor("#EBDEF0"))
    # route table
    rx = gx+gw*0.55
    ry = gy+gh-15
    ltext(c, rx, ry, "ROUTE TABLE", "Helvetica-Bold", 7, C_TEAL)
    routes = ["/api/v1/auth/**      ->  user-service:8081   (no JWT)",
              "/api/v1/exams/**     ->  exam-service:8082",
              "/api/v1/sessions/**  ->  test-engine:8083",
              "/api/v1/results/**   ->  result-service:8084",
              "/ws/**               ->  test-engine:8083   (WebSocket)"]
    for i, rt in enumerate(routes):
        ltext(c, rx, ry-11-i*9.5, rt, "Courier", 6.2, C_WHITE)
    # gateway footer note
    centre(c, gx+gw*0.27, gy+6, "Every request: verify JWT, rate-limit per user, inject X-User-Id / X-User-Role headers",
           "Helvetica-Oblique", 6.3, colors.HexColor("#EBDEF0"))
    varrow(c, cxmid, y4-2, y5+L5+2)

    # ── Layer 5: microservices
    svcs = [
        ("USER-SERVICE", ":8081", C_TEAL,
         ["Register / Login (Argon2id)", "OTP verify (SMS / email)",
          "JWT issue — RS256 private key", "Refresh-token rotation",
          "Token blacklist in Redis"]),
        ("EXAM-SERVICE", ":8082", C_DARK_BLUE,
         ["Create / publish exams", "Slot + capacity management",
          "Student enrolment", "Randomised paper generation",
          "Pre-cache paper into Redis"]),
        ("TEST-ENGINE", ":8083", C_SVC_GREEN,
         ["Start / resume session", "Save answers -> Redis (atomic)",
          "WebSocket heartbeat + autosave", "Anti-cheat: tab / copy events",
          "Submit -> emit Kafka event"]),
        ("RESULT-SERVICE", ":8084", C_SVC_RED,
         ["Kafka consumer (async)", "Auto-evaluate MCQ / numerical",
          "Score: +4 / -1 / 0", "Rank + percentile across cohort",
          "Publish result -> PostgreSQL"]),
    ]
    sw = (CW-30)/len(svcs)
    for i, (name, port, col, bullets) in enumerate(svcs):
        sx, sy, sh = LEFT+8+i*(sw+5), y5+8, L5-18
        box(c, sx, sy, sw, sh, col, C_WHITE, 6)
        centre(c, sx+sw/2, sy+sh-14, name, "Helvetica-Bold", 9.5, C_WHITE)
        centre(c, sx+sw/2, sy+sh-25, "Spring Boot 3.2 " + port, "Helvetica", 7, colors.HexColor("#DDEEFF"))
        for j, bl in enumerate(bullets):
            ltext(c, sx+9, sy+sh-39-j*11, "• " + bl, "Helvetica", 6.8, C_WHITE)
    varrow(c, cxmid, y5-2, y6+L6+2)

    # ── Layer 6: data stores
    stores = [
        ("POSTGRESQL", ":5432", colors.HexColor("#336791"),
         ["users, exams, slots", "enrollments, results", "Sharded by user_id", "2 read replicas"]),
        ("MONGODB", ":27017", colors.HexColor("#47A248"),
         ["questions (LaTeX+img)", "session snapshots", "pre-built papers", "idx: subject+topic"]),
        ("REDIS", ":6379", colors.HexColor("#DC382D"),
         ["JWT blacklist + OTP", "session answers (hot)", "rate-limit counters",
          "paper cache (pre-built)", "TTL = exam duration"]),
        ("KAFKA", ":9092", colors.HexColor("#231F20"),
         ["user / exam events", "answer / result events", "3 brokers, LZ4",
          "7-day retention", "decouples submit spike"]),
        ("AZURE BLOB + CDN", "", colors.HexColor("#0072C6"),
         ["question images (WebP)", "diagrams / graphs", "chemical structures",
          "Cloudflare edge cache", "<50ms global"]),
    ]
    dw = (CW-20)/len(stores)
    for i, (name, port, col, bullets) in enumerate(stores):
        dx, dy, dh = LEFT+8+i*dw, y6+8, L6-16
        box(c, dx, dy, dw-6, dh, col, C_WHITE, 6)
        centre(c, dx+(dw-6)/2, dy+dh-13, name, "Helvetica-Bold", 8.5, C_WHITE)
        if port:
            centre(c, dx+(dw-6)/2, dy+dh-23, port, "Helvetica", 7, colors.HexColor("#DDEEFF"))
        for j, bl in enumerate(bullets):
            ltext(c, dx+7, dy+dh-35-j*10, "• " + bl, "Helvetica", 6.5, C_WHITE)

    # ── Legend
    ly = y6-20
    ltext(c, LEFT, ly, "LEGEND:", "Helvetica-Bold", 8, C_TEXT)
    legend = [(C_TEAL, "Frontend / User"), (C_SVC_GREEN, "Exam / Test svc"),
              (C_SVC_RED, "Result svc"), (C_ORANGE, "CDN / Cache"),
              (C_GW_PURPLE, "API Gateway"), (C_MID_GREY, "HTTP / WS flow")]
    for i, (col, txt) in enumerate(legend):
        lxi = LEFT+70+i*150
        box(c, lxi, ly-2, 13, 11, col, radius=2)
        ltext(c, lxi+17, ly, txt, "Helvetica", 7.5, C_TEXT)


# ══════════════════════════════════════════════════════════════════════════
#  SEQUENCE-DIAGRAM ENGINE  (used by happy-flow pages, no overlaps)
# ══════════════════════════════════════════════════════════════════════════

class Lanes:
    def __init__(self, c, width, height, lanes, page_num, title):
        self.c = c
        self.w = width
        self.h = height
        page_chrome(c, title, page_num, width, height)
        self.PAD = 11*mm
        self.col_w = (width - 2*self.PAD) / len(lanes)
        self.centers = [self.PAD + (i+0.5)*self.col_w for i in range(len(lanes))]
        self.n = len(lanes)
        self.lane_bottom = 12*mm
        # header
        hy = height - 26*mm - 10
        for i, (name, sub, col) in enumerate(lanes):
            bx = self.PAD + i*self.col_w + 3
            box(c, bx, hy-26, self.col_w-6, 28, col, C_WHITE, 5)
            centre(c, self.centers[i], hy-11, name, "Helvetica-Bold", 8.5, C_WHITE)
            centre(c, self.centers[i], hy-21, sub, "Helvetica", 6.8, colors.HexColor("#EAF2FF"))
        # lifelines
        c.setStrokeColor(colors.HexColor("#D5DBDB")); c.setLineWidth(0.5)
        for i in range(self.n):
            lx = self.centers[i]
            c.line(lx, hy-28, lx, self.lane_bottom)
        self.y = hy - 44

    def step(self, num, title, bg=C_NAVY):
        bx, bw = self.PAD+2, self.w-2*self.PAD-4
        box(self.c, bx, self.y-15, bw, 17, bg, radius=3)
        ltext(self.c, bx+9, self.y-6, f"STEP {num}", "Helvetica-Bold", 8.5, C_WHITE)
        ltext(self.c, bx+62, self.y-6, title, "Helvetica-Bold", 8.5, colors.HexColor("#FFE0E6"))
        self.y -= 26

    def msg(self, frm, to, text, color=C_TEAL, dashed=False):
        fx, tx = self.centers[frm], self.centers[to]
        harrow(self.c, fx, tx, self.y, color, 1.3, dashed)
        mid = (fx+tx)/2
        centre(self.c, mid, self.y+4, text, "Helvetica", 6.6, color)
        self.y -= 15

    def note(self, lane, text, bg=colors.HexColor("#FFFDE7"), span=1):
        bx = self.PAD + lane*self.col_w + 4
        bw = self.col_w*span - 8
        box(self.c, bx, self.y-13, bw, 14, bg, C_MID_GREY, 2, 0.4)
        centre(self.c, bx+bw/2, self.y-9, text, "Helvetica-Oblique", 6.3, C_TEXT)
        self.y -= 18

    def gap(self, px=6):
        self.y -= px


LANES_DEF = [
    ("STUDENT", "Browser", C_TEAL),
    ("NEXT.JS", "SSR + BFF", C_DARK_BLUE),
    ("API GATEWAY", ":8080", C_GW_PURPLE),
    ("MICROSERVICES", "Spring Boot", C_SVC_GREEN),
    ("DATA STORES", "DB / Cache / Bus", C_SVC_RED),
]


def draw_happy_flow_1(c):
    L = Lanes(c, A3LW, A3LH, LANES_DEF, 3, "Happy Flow (1/2) — Login to Start Exam")

    L.step(1, "REGISTER / LOGIN", C_TEAL)
    L.msg(0, 1, "POST /api/auth/login  {email, password}", C_TEAL)
    L.msg(1, 2, "POST /api/v1/auth/login   (public route, no JWT)", C_DARK_BLUE)
    L.msg(2, 3, "route -> user-service:8081", C_GW_PURPLE)
    L.msg(3, 4, "verify Argon2id hash  +  check Redis blacklist", C_SVC_GREEN)
    L.msg(4, 3, "user row + 'not blacklisted'", C_MID_GREY, dashed=True)
    L.msg(3, 1, "AuthResponse {accessToken, refreshToken, role}", C_MID_GREY, dashed=True)
    L.note(1, "Set 3 httpOnly cookies: access_token, refresh_token, user_info", span=2)
    L.msg(1, 0, "200 OK + Set-Cookie   (tokens NEVER exposed to JS)", C_GREEN, dashed=True)
    L.gap()

    L.step(2, "VIEW DASHBOARD  (Server-Side Rendered)", C_DARK_BLUE)
    L.msg(0, 1, "GET /dashboard", C_TEAL)
    L.note(1, "Server Component reads cookie, fetches exams server-side", span=2)
    L.msg(1, 2, "GET /api/v1/exams?status=PUBLISHED   (Bearer)", C_DARK_BLUE)
    L.msg(2, 3, "route -> exam-service:8082", C_GW_PURPLE)
    L.msg(3, 4, "SELECT published exams", C_SVC_GREEN)
    L.msg(4, 1, "exam list", C_MID_GREY, dashed=True)
    L.note(1, "Render full HTML on server — no client spinner", span=2)
    L.msg(1, 0, "Fully-rendered dashboard HTML (exam cards ready)", C_GREEN, dashed=True)
    L.gap()

    L.step(3, "START EXAM   (SSR — most critical path)", C_GW_PURPLE)
    L.msg(0, 1, "GET /exam/{examId}   (click 'Start Exam')", C_TEAL)
    L.note(1, "Server Component runs 2 backend calls in parallel", span=2)
    L.msg(1, 2, "(1) POST /api/v1/sessions/start  {examId, enrollmentId}", C_DARK_BLUE)
    L.msg(2, 3, "-> test-engine:8083", C_GW_PURPLE)
    L.msg(3, 4, "Redis: look up existing session", C_SVC_GREEN)
    L.msg(4, 3, "MISS -> create session, TTL = exam duration", C_MID_GREY, dashed=True)
    L.msg(3, 1, "{sessionId, timeRemainingSecs}", C_MID_GREY, dashed=True)
    L.msg(1, 2, "(2) GET /api/v1/exams/{id}/paper/{sessionId}", C_DARK_BLUE)
    L.msg(2, 3, "-> exam-service:8082", C_GW_PURPLE)
    L.msg(3, 4, "Redis GET paper:{examId}  (pre-built cache)", C_SVC_GREEN)
    L.msg(4, 1, "HIT: 90 questions JSON  (< 5 ms)", C_GREEN, dashed=True)
    L.note(1, "KaTeX renders ALL LaTeX -> HTML on the server (no KaTeX JS shipped)", span=3)
    L.msg(1, 0, "Full exam HTML: 90 Qs pre-rendered, math baked in, ZERO spinners", C_GREEN, dashed=True)
    L.note(0, "React hydrates -> timer starts, autosave starts, anti-cheat hooks attach",
           bg=colors.HexColor("#E8F8F5"), span=2)


def draw_happy_flow_2(c):
    L = Lanes(c, A3LW, A3LH, LANES_DEF, 4, "Happy Flow (2/2) — Answer to Result")

    L.step("4 + 5", "ANSWER QUESTIONS  +  AUTOSAVE  (every 5 s)", C_ORANGE)
    L.note(0, "Click option -> React useState only (NO server call per answer)",
           bg=colors.HexColor("#FFF6D6"), span=2)
    L.note(0, "setInterval(5s): flush only 'dirty' (changed) answers",
           bg=colors.HexColor("#FFF6D6"), span=2)
    L.msg(0, 1, "POST /api/exam/{id}/autosave  {sessionId, dirtyAnswers}", C_ORANGE)
    L.msg(1, 2, "POST /api/v1/sessions/{id}/answer   (parallel)", C_DARK_BLUE)
    L.msg(2, 3, "-> test-engine:8083  (JWT verified)", C_GW_PURPLE)
    L.msg(3, 4, "HSET session:{id} answers   (atomic, < 1 ms)", C_SVC_GREEN)
    L.msg(4, 0, "200 -> save badge shows 'Saved'", C_MID_GREY, dashed=True)
    L.note(0, "keepalive:true ensures save completes even on tab close / refresh",
           bg=colors.HexColor("#FFF6D6"), span=2)
    L.gap()

    L.step(6, "SUBMIT EXAM", C_RED)
    L.msg(0, 1, "POST /api/exam/{id}/submit  {sessionId, answers, reason}", C_RED)
    L.note(1, "(1) Final flush of remaining answers — Promise.allSettled (never blocks)", span=2)
    L.msg(1, 2, "(2) POST /api/v1/sessions/{id}/submit?examId=...", C_RED)
    L.msg(2, 3, "-> test-engine:8083", C_GW_PURPLE)
    L.msg(3, 4, "Redis: status = SUBMITTED", C_SVC_GREEN)
    L.msg(3, 4, "Kafka: publish EXAM_SUBMITTED event", C_ORANGE)
    L.msg(3, 1, "{status: SUBMITTED}", C_MID_GREY, dashed=True)
    L.msg(1, 0, "redirect -> /results/{examId}", C_GREEN, dashed=True)
    L.gap()

    L.step(7, "RESULT COMPUTATION (async)  +  VIEW RESULT (SSR)", C_GREEN)
    L.note(3, "Kafka consumer (result-service) picks up EXAM_SUBMITTED",
           bg=colors.HexColor("#E8F8F5"), span=2)
    L.msg(3, 4, "fetch answers (Redis) + answer key (MongoDB)", C_SVC_GREEN)
    L.note(3, "Score = correct x4 - wrong x1 ; compute rank + percentile across cohort",
           bg=colors.HexColor("#E8F8F5"), span=2)
    L.msg(3, 4, "INSERT result row (PostgreSQL)", C_SVC_GREEN)
    L.gap(4)
    L.msg(0, 1, "GET /results/{examId}", C_GREEN)
    L.msg(1, 2, "GET /api/v1/results/{examId}   (Server Component)", C_DARK_BLUE)
    L.msg(2, 3, "-> result-service:8084", C_GW_PURPLE)
    L.msg(3, 4, "SELECT result WHERE exam_id + student_id", C_SVC_GREEN)
    L.msg(4, 1, "score / rank / percentile / section breakdown", C_MID_GREY, dashed=True)
    L.msg(1, 0, "SSR result page (score, rank, stats)", C_GREEN, dashed=True)
    L.gap()

    # reconnect callout
    bx = L.PAD+2
    bw = L.w-2*L.PAD-4
    box(c, bx, L.y-46, bw, 46, colors.HexColor("#FFF3CD"), C_ORANGE, 5, 1)
    ltext(c, bx+10, L.y-13, "RESILIENCE — Network drop / refresh mid-exam:",
          "Helvetica-Bold", 8.5, colors.HexColor("#7D5A00"))
    rec = ["1. Student refreshes -> GET /exam/{id} hits server again.",
           "2. POST /sessions/start finds ACTIVE session in Redis -> returns it (no new session).",
           "3. GET paper -> Redis cache HIT. Saved answers restored. Timer resumes from stored remaining time.",
           "4. No answers lost — Redis AOF (everysec) + keepalive autosave guarantee durability."]
    for i, r in enumerate(rec):
        ltext(c, bx+14, L.y-25-i*8.5, r, "Helvetica", 7, colors.HexColor("#7D5A00"))


# ══════════════════════════════════════════════════════════════════════════
#  UI MOCKUP HELPERS
# ══════════════════════════════════════════════════════════════════════════

def browser_frame(c, x, y, w, h, url):
    """Draw a browser chrome window."""
    box(c, x, y, w, h, C_WHITE, C_MID_GREY, 6, 1)
    # title bar
    c.setFillColor(colors.HexColor("#E8EAED"))
    c.roundRect(x, y+h-26, w, 26, 6, fill=1, stroke=0)
    c.setFillColor(colors.HexColor("#E8EAED"))
    c.rect(x, y+h-26, w, 14, fill=1, stroke=0)
    for i, col in enumerate([C_RED, C_ORANGE, C_GREEN]):
        c.setFillColor(col); c.circle(x+14+i*16, y+h-13, 4.5, fill=1, stroke=0)
    # url bar with a small drawn padlock (no emoji — avoids missing-glyph boxes)
    box(c, x+62, y+h-21, w-80, 16, C_WHITE, C_MID_GREY, 8, 0.5)
    # padlock: body + shackle
    c.setFillColor(C_GREEN); c.setStrokeColor(C_GREEN)
    c.rect(x+71, y+h-19, 6, 4.5, fill=1, stroke=0)
    c.setLineWidth(0.8)
    c.arc(x+72, y+h-16.5, x+76, y+h-12.5, startAng=0, extent=180)
    ltext(c, x+82, y+h-16, url, "Helvetica", 7, colors.HexColor("#5F6368"))
    return (x, y, w, h-26)  # content area below title bar


def callout(c, x, y, text, target_x, target_y, color=C_ACCENT, width=120, align="left"):
    """Annotation bubble with a connector line to a UI element."""
    lines = text.split("\n")
    bh = len(lines)*10 + 8
    box(c, x, y-bh, width, bh, color, radius=4)
    for i, ln in enumerate(lines):
        ltext(c, x+6, y-12-i*10, ln, "Helvetica-Bold", 6.8, C_WHITE)
    # connector
    c.setStrokeColor(color); c.setLineWidth(1)
    c.setDash(2, 2)
    sx = x if align == "left" else x+width
    c.line(sx, y-bh/2, target_x, target_y)
    c.setDash()
    c.setFillColor(color); c.circle(target_x, target_y, 2.5, fill=1, stroke=0)


# ── UI PAGE: LOGIN + REGISTER ───────────────────────────────────────────────

def _field(c, x, y_top, width, label, placeholder, label_size=7.5):
    """Draw a form field: label above, input box below. Returns y below the box."""
    ltext(c, x, y_top, label, "Helvetica-Bold", label_size, colors.HexColor("#444"))
    box_y = y_top - 22
    box(c, x, box_y, width, 18, C_WHITE, C_MID_GREY, 5, 1)
    if placeholder:
        ltext(c, x+10, box_y+6, placeholder, "Helvetica", 8, colors.HexColor("#AAA"))
    return box_y  # bottom of box


def draw_ui_login(c):
    w, h = A4LW, A4LH
    page_chrome(c, "UI Mockup — Login & Register", 5, w, h)
    top = h - 26*mm - 8*mm

    by = 16*mm
    bw = (w - 50*mm) / 2
    bh = top - by

    # ===== LOGIN (left) =====
    bx = 18*mm
    cx0, cy0, cw0, ch0 = browser_frame(c, bx, by, bw, bh, "examplatform.com/login")
    c.setFillColor(C_NAVY); c.rect(cx0+1, cy0+1, cw0-2, ch0-2, fill=1, stroke=0)
    card_w = cw0*0.66
    card_h = ch0*0.66
    card_x = cx0+(cw0-card_w)/2
    card_y = cy0+(ch0-card_h)/2
    box(c, card_x, card_y, card_w, card_h, C_WHITE, radius=10)

    cur = card_y + card_h - 32
    ltext(c, card_x+24, cur, "Welcome back", "Helvetica-Bold", 15, C_NAVY)
    cur -= 18
    ltext(c, card_x+24, cur, "Sign in to your exam account", "Helvetica", 8, colors.HexColor("#888"))
    cur -= 30
    fw = card_w - 48
    bot = _field(c, card_x+24, cur, fw, "Email or Phone", "you@example.com")
    cur = bot - 24
    bot = _field(c, card_x+24, cur, fw, "Password", "* * * * * * * *")
    cur = bot - 30
    box(c, card_x+24, cur, fw, 24, C_DARK_BLUE, radius=6)
    centre(c, card_x+card_w/2, cur+8, "Sign In", "Helvetica-Bold", 10, C_WHITE)
    cur -= 22
    centre(c, card_x+card_w/2, cur, "Don't have an account?  Register here",
           "Helvetica", 7.5, C_DARK_BLUE)

    centre(c, bx+bw/2, by-6, "LOGIN PAGE  —  SSG shell + client form", "Helvetica-Bold", 8.5, C_TEXT)
    callout(c, cx0+cw0-126, cy0+ch0-18, "httpOnly cookie set by\nNext.js BFF — token is\nnever exposed to JS",
            card_x+card_w-24, card_y+card_h*0.5, C_ACCENT, 122)

    # ===== REGISTER (right) =====
    bx2 = bx + bw + 14*mm
    cx1, cy1, cw1, ch1 = browser_frame(c, bx2, by, bw, bh, "examplatform.com/register")
    c.setFillColor(C_NAVY); c.rect(cx1+1, cy1+1, cw1-2, ch1-2, fill=1, stroke=0)
    rw = cw1*0.70
    rh = ch1*0.92
    rx = cx1+(cw1-rw)/2
    ry0 = cy1+(ch1-rh)/2
    box(c, rx, ry0, rw, rh, C_WHITE, radius=10)

    cur = ry0 + rh - 30
    ltext(c, rx+22, cur, "Create Account", "Helvetica-Bold", 14, C_NAVY)
    cur -= 16
    ltext(c, rx+22, cur, "Register to take exams", "Helvetica", 8, colors.HexColor("#888"))
    cur -= 28
    # name row (two columns)
    half = (rw-44-10)/2
    ltext(c, rx+22, cur, "First Name", "Helvetica-Bold", 7, colors.HexColor("#444"))
    ltext(c, rx+22+half+10, cur, "Last Name", "Helvetica-Bold", 7, colors.HexColor("#444"))
    box(c, rx+22, cur-20, half, 18, C_WHITE, C_MID_GREY, 5, 1)
    box(c, rx+22+half+10, cur-20, half, 18, C_WHITE, C_MID_GREY, 5, 1)
    cur = cur - 20 - 24
    fw2 = rw - 44
    for lbl, ph in [("Email", "rahul@example.com"), ("Phone (optional)", "9876543210"),
                    ("Password", "* * * * * * * *"), ("Confirm Password", "* * * * * * * *")]:
        bot = _field(c, rx+22, cur, fw2, lbl, ph, 7)
        cur = bot - 24
    cur -= 2
    box(c, rx+22, cur, fw2, 24, C_DARK_BLUE, radius=6)
    centre(c, rx+rw/2, cur+8, "Create Account", "Helvetica-Bold", 10, C_WHITE)

    centre(c, bx2+bw/2, by-6, "REGISTER PAGE  —  validates, then auto-login", "Helvetica-Bold", 8.5, C_TEXT)
    callout(c, cx1+8, cy1+24, "Password rule: 8+ chars,\nupper, lower, digit, symbol\n(matches RegisterRequest)",
            rx+22, cur+12, C_GREEN, 122)


# ── UI PAGE: DASHBOARD ──────────────────────────────────────────────────────

def draw_ui_dashboard(c):
    w, h = A4LW, A4LH
    page_chrome(c, "UI Mockup — Dashboard (Categories)", 6, w, h)
    top = h - 26*mm - 6*mm

    bx, by = 16*mm, 14*mm
    bw, bh = w-92*mm, top-16*mm
    cx, cy, cw, ch = browser_frame(c, bx, by, bw, bh, "examplatform.com/dashboard")
    c.setFillColor(C_LIGHT_GREY); c.rect(cx+1, cy+1, cw-2, ch-2, fill=1, stroke=0)

    # header
    ltext(c, cx+22, cy+ch-28, "Explore Tests", "Helvetica-Bold", 15, C_NAVY)
    ltext(c, cx+22, cy+ch-42, "rahul@example.com", "Helvetica", 8, colors.HexColor("#888"))
    box(c, cx+cw-86, cy+ch-42, 62, 20, C_WHITE, C_MID_GREY, 6, 1)
    centre(c, cx+cw-55, cy+ch-36, "Logout", "Helvetica", 8, C_TEXT)

    # category cards — 2 x 2 grid
    cats = [
        ("JEE Main", "Engineering", C_TEAL, 48, 12, [("Full Mocks", 15), ("Previous Years", 18), ("Subject-wise", 9), ("Chapter-wise", 6)]),
        ("JEE Advanced", "Engineering", C_DARK_BLUE, 34, 4, [("Full Mocks", 10), ("Previous Years", 14), ("Subject-wise", 6), ("Chapter-wise", 4)]),
        ("NEET", "Medical", C_GREEN, 52, 20, [("Full Mocks", 18), ("Previous Years", 16), ("Subject-wise", 12), ("Chapter-wise", 6)]),
        ("CUET", "University", C_PURPLE, 26, 2, [("Full Mocks", 8), ("Previous Years", 6), ("Subject-wise", 8), ("Chapter-wise", 4)]),
    ]
    gx0 = cx+22
    gy0 = cy+ch-58
    cardw = (cw-22-22-16)/2
    cardh = 150
    for idx, (title, tag, col, total, attempted, rows) in enumerate(cats):
        gcol, grow = idx % 2, idx // 2
        cardx = gx0 + gcol*(cardw+16)
        cardy = gy0 - grow*(cardh+14) - cardh
        box(c, cardx, cardy, cardw, cardh, C_WHITE, colors.HexColor("#EAEAEA"), 12, 1)
        # top colour stripe
        c.setFillColor(col); c.roundRect(cardx, cardy+cardh-5, cardw, 5, 2, fill=1, stroke=0)
        c.rect(cardx, cardy+cardh-5, cardw, 3, fill=1, stroke=0)
        # title + tag
        ltext(c, cardx+16, cardy+cardh-26, title, "Helvetica-Bold", 13, C_NAVY)
        tagw = len(tag)*4.6+12
        box(c, cardx+16, cardy+cardh-42, tagw, 12, colors.HexColor("#F0F2F5"), radius=6)
        centre(c, cardx+16+tagw/2, cardy+cardh-39, tag, "Helvetica", 6.5, colors.HexColor("#888"))
        # total
        c.setFont("Helvetica-Bold", 18); c.setFillColor(C_DARK_BLUE)
        c.drawRightString(cardx+cardw-16, cardy+cardh-28, str(total))
        ltext(c, cardx+cardw-44, cardy+cardh-40, "tests", "Helvetica", 6.5, colors.HexColor("#AAA"))
        # progress bar
        pct = attempted/total
        box(c, cardx+16, cardy+cardh-52, cardw-32, 5, colors.HexColor("#EEF0F3"), radius=3)
        c.setFillColor(C_TEAL); c.roundRect(cardx+16, cardy+cardh-52, (cardw-32)*pct, 5, 3, fill=1, stroke=0)
        ltext(c, cardx+16, cardy+cardh-63, f"{attempted} attempted  ·  {int(pct*100)}%", "Helvetica", 6.3, colors.HexColor("#999"))
        # test-type rows
        ry = cardy+cardh-78
        for (label, cnt) in rows:
            box(c, cardx+16, ry-14, cardw-32, 16, colors.HexColor("#F7F8FA"), radius=6)
            ltext(c, cardx+24, ry-7, label, "Helvetica-Bold", 7.5, C_NAVY)
            box(c, cardx+cardw-44, ry-13, 24, 14, C_WHITE, colors.HexColor("#E0E0E0"), 5, 0.8)
            centre(c, cardx+cardw-32, ry-9, str(cnt), "Helvetica-Bold", 8, C_DARK_BLUE)
            ry -= 18

    centre(c, bx+bw/2, by-6, "DASHBOARD  —  exams grouped by category, counts per test type", "Helvetica-Bold", 9, C_TEXT)

    # annotations
    ax = bx+bw+8
    notes = [
        ("Category card", "One per exam track:\nJEE Main / Advanced /\nNEET / CUET / BITSAT.", C_DARK_BLUE),
        ("Test-type counts", "Full Mocks, Previous\nYears, Subject-wise,\nChapter-wise — each\nlinks to a filtered list.", C_GREEN),
        ("Progress bar", "attemptedCount / total\nfrom categories/summary\nendpoint (per student).", C_ACCENT),
        ("Drill-down", "Click a row ->\n/exams?category=..\n&type=.. listing page.", C_PURPLE),
    ]
    ny = cy+ch-16
    for (t, body, col) in notes:
        nlines = body.split("\n")
        nh = 22+len(nlines)*9
        box(c, ax, ny-nh, 62*mm, nh, C_WHITE, col, 5, 1.2)
        ltext(c, ax+8, ny-15, t, "Helvetica-Bold", 8.5, col)
        for i, ln in enumerate(nlines):
            ltext(c, ax+8, ny-27-i*9, ln, "Helvetica", 6.8, C_TEXT)
        ny -= nh+10


# ── UI PAGE: EXAM INTERFACE (the big one) ───────────────────────────────────

def draw_ui_exam(c):
    w, h = A3LW, A3LH
    page_chrome(c, "UI Mockup — Exam Interface (annotated)", 7, w, h)
    top = h - 26*mm - 6*mm

    bx, by = 14*mm, 14*mm
    bw, bh = w-28*mm, top-18*mm
    cx, cy, cw, ch = browser_frame(c, bx, by, bw, bh, "examplatform.com/exam/jee-2026-mock-1")

    # ===== exam header (dark bar) =====
    hdr_h = 34
    c.setFillColor(C_NAVY); c.rect(cx+1, cy+ch-hdr_h, cw-2, hdr_h, fill=1, stroke=0)
    ltext(c, cx+18, cy+ch-21, "JEE Main 2026 — Full Mock Test 1", "Helvetica-Bold", 10, C_WHITE)
    # timer (clock drawn, no emoji)
    box(c, cx+cw*0.46, cy+ch-26, 96, 18, colors.HexColor("#1F2A44"), radius=4)
    c.setStrokeColor(colors.HexColor("#FF6B6B")); c.setLineWidth(1.1)
    c.circle(cx+cw*0.46+13, cy+ch-17, 5, fill=0, stroke=1)
    c.line(cx+cw*0.46+13, cy+ch-17, cx+cw*0.46+13, cy+ch-14)
    c.line(cx+cw*0.46+13, cy+ch-17, cx+cw*0.46+16, cy+ch-17)
    centre(c, cx+cw*0.46+56, cy+ch-20, "02:47:13", "Helvetica-Bold", 11, colors.HexColor("#FF6B6B"))
    # save badge
    box(c, cx+cw*0.46+106, cy+ch-25, 60, 16, colors.HexColor("#D4EDDA"), radius=8)
    centre(c, cx+cw*0.46+136, cy+ch-20, "Saved", "Helvetica-Bold", 7.5, colors.HexColor("#155724"))
    c.setFillColor(colors.HexColor("#155724"))
    c.setFont("Helvetica-Bold", 8); c.drawString(cx+cw*0.46+112, cy+ch-20.5, "v")
    # tab-switch warning
    box(c, cx+cw*0.46+172, cy+ch-25, 124, 16, colors.HexColor("#FFF3CD"), radius=8)
    centre(c, cx+cw*0.46+234, cy+ch-20, "!  1 tab switch detected", "Helvetica-Bold", 6.8, colors.HexColor("#856404"))

    body_top = cy+ch-hdr_h
    # ===== question panel (left ~70%) =====
    qp_w = cw*0.68
    c.setFillColor(C_WHITE); c.rect(cx+1, cy+1, qp_w, body_top-cy-1, fill=1, stroke=0)
    c.setStrokeColor(colors.HexColor("#E0E0E0")); c.setLineWidth(1)
    c.line(cx+qp_w, cy+1, cx+qp_w, body_top)

    qx = cx+24
    qy = body_top-22
    ltext(c, qx, qy, "Question 13  •  Mathematics  •  +4 / −1", "Helvetica-Bold", 8.5, colors.HexColor("#888"))
    qy -= 24
    ltext(c, qx, qy, "Evaluate the following indefinite integral:", "Helvetica", 11, C_TEXT)
    qy -= 32
    # math block (rendered look) — KaTeX-style fraction drawn with shapes, ASCII-safe text
    mbh = 38
    box(c, qx, qy-mbh+14, qp_w*0.5, mbh, colors.HexColor("#F8F9FB"), colors.HexColor("#E8E8E8"), 4, 1)
    # draw a script integral sign using a tall curved path (no glyph -> no missing box)
    c.setStrokeColor(C_NAVY); c.setLineWidth(1.6)
    ix = qx+22
    c.bezier(ix-4, qy-16, ix-9, qy-8, ix+8, qy-2, ix+3, qy+8)
    c.bezier(ix+3, qy+8, ix+6, qy+12, ix-1, qy+13, ix-3, qy+10)
    c.bezier(ix-4, qy-16, ix-7, qy-20, ix, qy-21, ix+2, qy-18)
    # numerator / denominator with fraction bar
    ltext(c, qx+34, qy+1, "e^(arctan x)", "Helvetica-Oblique", 10.5, C_NAVY)
    c.setStrokeColor(C_NAVY); c.setLineWidth(0.9)
    c.line(qx+34, qy-4, qx+108, qy-4)
    ltext(c, qx+50, qy-15, "1 + x^2", "Helvetica-Oblique", 9.5, C_NAVY)
    ltext(c, qx+120, qy-7, "dx   =", "Helvetica-Oblique", 11, C_NAVY)
    qy -= mbh + 8

    # options (ASCII-safe math notation)
    opts = [("A", "log(1 + x^2) + c", False),
            ("B", "log_e [ e^(arctan x) ] + c", False),
            ("C", "e^(arctan x) + c", True),
            ("D", "arctan( e^(arctan x) ) + c", False)]
    for (oid, otext, sel) in opts:
        ow = qp_w*0.82
        oh = 26
        ocol = colors.HexColor("#E8F0FE") if sel else C_WHITE
        ostroke = C_DARK_BLUE if sel else colors.HexColor("#E8E8E8")
        box(c, qx, qy-oh, ow, oh, ocol, ostroke, 7, 2 if sel else 1)
        c.setFillColor(C_DARK_BLUE); c.setFont("Helvetica-Bold", 10)
        c.drawString(qx+14, qy-oh+9, oid)
        ltext(c, qx+34, qy-oh+9, otext, "Helvetica" + ("-Bold" if sel else ""), 9.5,
              C_NAVY if sel else C_TEXT)
        if sel:
            centre(c, qx+ow-16, qy-oh+9, "●", "Helvetica", 10, C_DARK_BLUE)
        qy -= oh+8

    qy -= 6
    # action buttons
    actions = [("Flag", colors.HexColor("#F39C12")), ("Clear", C_RED),
               ("< Prev", C_MID_GREY), ("Next >", C_DARK_BLUE)]
    ax = qx
    for (txt, col) in actions:
        bwid = 62
        box(c, ax, qy-22, bwid, 22, C_WHITE, col, 7, 1.2)
        centre(c, ax+bwid/2, qy-15, txt, "Helvetica-Bold", 7.5, col)
        ax += bwid+10

    # ===== navigation panel (right ~32%) =====
    npx = cx+qp_w+1
    npw = cw-qp_w-2
    c.setFillColor(C_WHITE); c.rect(npx, cy+1, npw, body_top-cy-1, fill=1, stroke=0)
    nx = npx+16
    ny = body_top-20
    ltext(c, nx, ny, "QUESTIONS", "Helvetica-Bold", 8.5, colors.HexColor("#888"))
    ny -= 18
    # section tabs
    secs = [("Physics", False), ("Chemistry", False), ("Maths", True)]
    sx = nx
    for (s, active) in secs:
        sw = 56
        box(c, sx, ny-14, sw, 16, C_DARK_BLUE if active else C_WHITE,
            C_DARK_BLUE if not active else None, 8, 1)
        centre(c, sx+sw/2, ny-9, s, "Helvetica-Bold", 6.8, C_WHITE if active else C_DARK_BLUE)
        sx += sw+6
    ny -= 28

    # question grid 5 cols
    grid_cols = 5
    cell = (npw-32-4*6)/grid_cols
    # status pattern for 25 questions
    import random
    random.seed(7)
    statuses = []
    for i in range(25):
        r = random.random()
        if i == 12: statuses.append("current")
        elif r < 0.45: statuses.append("answered")
        elif r < 0.6: statuses.append("flagged")
        else: statuses.append("none")
    gx0 = nx
    gy = ny
    for i in range(25):
        row, col_ = divmod(i, grid_cols)
        gx = gx0+col_*(cell+6)
        gyy = gy-row*(cell+6)
        st = statuses[i]
        if st == "answered": fill, tcol, stroke = C_DARK_BLUE, C_WHITE, None
        elif st == "flagged": fill, tcol, stroke = C_ORANGE, C_WHITE, None
        elif st == "current": fill, tcol, stroke = C_WHITE, C_DARK_BLUE, C_DARK_BLUE
        else: fill, tcol, stroke = C_WHITE, C_TEXT, C_MID_GREY
        box(c, gx, gyy-cell, cell, cell, fill, stroke, 5, 2 if st == "current" else 1)
        centre(c, gx+cell/2, gyy-cell/2-3, str(i+1), "Helvetica-Bold", 7.5, tcol)
    grid_rows = 5
    ny = gy - grid_rows*(cell+6) - 8

    # legend
    legend = [("Answered (11)", C_DARK_BLUE), ("Flagged (4)", C_ORANGE), ("Not visited (10)", C_WHITE)]
    for (lt, col) in legend:
        box(c, nx, ny-9, 11, 9, col, C_MID_GREY if col == C_WHITE else None, 2, 0.5)
        ltext(c, nx+16, ny-8, lt, "Helvetica", 7, colors.HexColor("#666"))
        ny -= 14
    ny -= 6
    # submit
    box(c, nx, ny-26, npw-32, 26, C_GREEN, radius=8)
    centre(c, nx+(npw-32)/2, ny-17, "Submit Exam", "Helvetica-Bold", 10, C_WHITE)

    # ===== annotations (overlaid callouts pointing to features) =====
    callout(c, cx+18, cy+ch+34, "Pre-rendered server-side:\nKaTeX math is HTML, not JS",
            qx+60, body_top-100, C_ACCENT, 150)
    callout(c, cx+cw*0.30, cy+ch+34, "Timer counts down from\nRedis-stored remaining time\n(survives reconnect)",
            cx+cw*0.46+45, cy+ch-22, C_TEAL, 150)
    callout(c, cx+cw-180, cy+ch+34, "Autosave badge: dirty answers\nflushed every 5s (keepalive)",
            cx+cw*0.46+132, cy+ch-20, C_GREEN, 168, align="left")

    centre(c, bx+bw/2, by-6,
           "EXAM INTERFACE  —  ExamShell (Server Component, renders HTML)  +  ExamClient ('use client', timer/answers/anti-cheat)",
           "Helvetica-Bold", 9, C_TEXT)


# ── UI PAGE: RESULT ─────────────────────────────────────────────────────────

def draw_ui_result(c):
    w, h = A3LW, A3LH
    page_chrome(c, "UI Mockup — Result Page (detailed review)", 8, w, h)
    top = h - 26*mm - 6*mm

    bx, by = 14*mm, 14*mm
    bw, bh = w-28*mm, top-16*mm
    cx, cy, cw, ch = browser_frame(c, bx, by, bw, bh, "examplatform.com/results/jee-2026-mock-1")
    c.setFillColor(C_LIGHT_GREY); c.rect(cx+1, cy+1, cw-2, ch-2, fill=1, stroke=0)

    PAD = 18
    contentw = cw*0.66          # left column for content; right for annotations
    colx = cx+PAD

    # ── summary bar ──
    sb_y = cy+ch-16
    box(c, colx, sb_y-44, contentw, 44, C_WHITE, colors.HexColor("#EAEAEA"), 10, 1)
    ltext(c, colx+16, sb_y-20, "JEE Main 2026 — Full Mock Test 1", "Helvetica-Bold", 11, C_NAVY)
    ltext(c, colx+16, sb_y-34, "Detailed Analysis", "Helvetica", 7.5, colors.HexColor("#888"))
    c.setFont("Helvetica-Bold", 20); c.setFillColor(C_DARK_BLUE)
    c.drawRightString(colx+contentw-16, sb_y-22, "284 / 360")
    c.setFont("Helvetica", 7.5); c.setFillColor(colors.HexColor("#888"))
    c.drawRightString(colx+contentw-16, sb_y-35, "78.9%  ·  Rank #1,423  ·  97.2 percentile")

    # ── stat strip ──
    strip_y = sb_y-44-10
    stats = [("78", "Correct", C_GREEN), ("14", "Wrong", C_RED), ("8", "Skipped", colors.HexColor("#888"))]
    sbw = (contentw-20)/3
    for i, (val, lbl, col) in enumerate(stats):
        sxx = colx+i*(sbw+10)
        box(c, sxx, strip_y-34, sbw, 34, C_WHITE, colors.HexColor("#EAEAEA"), 8, 1)
        c.setFont("Helvetica-Bold", 15); c.setFillColor(col)
        c.drawString(sxx+12, strip_y-24, val)
        ltext(c, sxx+40, strip_y-22, lbl, "Helvetica", 8, colors.HexColor("#888"))

    # ── filter tabs ──
    ft_y = strip_y-34-14
    tabs = [("All", 100, C_DARK_BLUE, True), ("Wrong", 14, C_RED, False),
            ("Skipped", 8, colors.HexColor("#888"), False), ("Correct", 78, C_GREEN, False)]
    tx = colx
    for (label, cnt, col, active) in tabs:
        tw = len(label)*5.5+34
        box(c, tx, ft_y-16, tw, 16, col if active else C_WHITE, None if active else C_MID_GREY, 8, 1)
        ltext(c, tx+9, ft_y-11, label, "Helvetica-Bold", 7.5, C_WHITE if active else colors.HexColor("#555"))
        # count chip
        cc = colors.HexColor("#FFFFFF") if active else colors.HexColor("#EEF0F3")
        c.setFillColor(colors.Color(1,1,1,0.25) if active else colors.HexColor("#EEF0F3"))
        c.roundRect(tx+tw-22, ft_y-13.5, 16, 11, 4, fill=1, stroke=0)
        centre(c, tx+tw-14, ft_y-11, str(cnt), "Helvetica-Bold", 6.5, C_WHITE if active else colors.HexColor("#555"))
        tx += tw+8

    # ── review card 1 (WRONG, review mode) ──
    card_y = ft_y-16-12
    cardh1 = 150
    _review_card(c, colx, card_y-cardh1, contentw, cardh1,
                 qnum="Q13", subject="Mathematics", status="Wrong", marks="-1",
                 question="Evaluate:  S e^(arctan x) / (1 + x^2) dx  =",
                 options=[("A", "log(1 + x^2) + c", None),
                          ("B", "log_e [ e^(arctan x) ] + c", "wrong"),
                          ("C", "e^(arctan x) + c", "correct"),
                          ("D", "arctan( e^(arctan x) ) + c", None)],
                 expanded=False)

    # ── review card 2 (WRONG, solution expanded + re-attempt) ──
    card2_y = card_y-cardh1-12
    cardh2 = 168
    _review_card(c, colx, card2_y-cardh2, contentw, cardh2,
                 qnum="Q27", subject="Chemistry", status="Wrong", marks="-1",
                 question="Which of the following has the highest lattice energy?",
                 options=[("A", "NaCl", None),
                          ("B", "KCl", "wrong"),
                          ("C", "MgO", "correct"),
                          ("D", "CaO", None)],
                 expanded=True,
                 solution=["Lattice energy increases with higher ionic charge and",
                           "smaller ionic radius (U proportional to q+ q- / r).",
                           "MgO has 2+/2- charges and small radii -> highest lattice energy."])

    centre(c, bx+bw/2, by-6,
           "RESULT PAGE  —  per-question review: your answer vs correct, View Solution, Re-attempt",
           "Helvetica-Bold", 9, C_TEXT)

    # ── annotations (right column) ──
    ax = cx+contentw+PAD+6
    aw = cw-contentw-2*PAD-6
    notes = [
        ("Filter tabs", "All / Wrong / Skipped /\nCorrect — jump straight\nto mistakes.", C_ACCENT),
        ("Colour-coded options", "Green = correct answer,\nRed = your wrong answer.\nShown per question.", C_GREEN),
        ("View Solution", "Toggles step-by-step\nsolution (KaTeX HTML,\npre-rendered server-side).", C_DARK_BLUE),
        ("Re-attempt", "Practice the question\ninline with instant check\n— does NOT change the\nrecorded score.", colors.HexColor("#D68910")),
        ("Async pipeline", "Detailed result built by\nresult-service Kafka\nconsumer; answer key\nfrom MongoDB.", C_PURPLE),
    ]
    ny = cy+ch-16
    for (t, body, col) in notes:
        nlines = body.split("\n")
        nh = 22+len(nlines)*9
        box(c, ax, ny-nh, aw, nh, C_WHITE, col, 5, 1.2)
        ltext(c, ax+8, ny-15, t, "Helvetica-Bold", 8.5, col)
        for i, ln in enumerate(nlines):
            ltext(c, ax+8, ny-27-i*9, ln, "Helvetica", 6.8, C_TEXT)
        ny -= nh+10


def _review_card(c, x, y, wd, ht, qnum, subject, status, marks, question, options, expanded, solution=None):
    """Draw one question-review card (used by the result mockup)."""
    edge = {"Wrong": C_RED, "Correct": C_GREEN, "Skipped": C_ORANGE}[status]
    box(c, x, y, wd, ht, C_WHITE, colors.HexColor("#EAEAEA"), 10, 1)
    c.setFillColor(edge); c.roundRect(x, y, 5, ht, 2, fill=1, stroke=0)
    c.rect(x+2, y, 3, ht, fill=1, stroke=0)

    head_y = y+ht-20
    ltext(c, x+16, head_y, qnum, "Helvetica-Bold", 9.5, C_DARK_BLUE)
    ltext(c, x+44, head_y, subject, "Helvetica", 7.5, colors.HexColor("#888"))
    # status badge
    sb = {"Wrong": (colors.HexColor("#F8D7DA"), colors.HexColor("#721C24")),
          "Correct": (colors.HexColor("#D4EDDA"), colors.HexColor("#155724")),
          "Skipped": (colors.HexColor("#FFF3CD"), colors.HexColor("#856404"))}[status]
    box(c, x+100, head_y-3, 50, 13, sb[0], radius=6)
    centre(c, x+125, head_y, status, "Helvetica-Bold", 6.8, sb[1])
    c.setFont("Helvetica-Bold", 10); c.setFillColor(edge)
    c.drawRightString(x+wd-16, head_y, marks)

    # question
    ltext(c, x+16, head_y-20, question, "Helvetica", 9, C_TEXT)

    # options
    oy = head_y-38
    for (oid, otext, mark) in options:
        ow = wd-32
        if mark == "correct": ofill, ostroke = colors.HexColor("#EAFAF0"), C_GREEN
        elif mark == "wrong": ofill, ostroke = colors.HexColor("#FDECEA"), C_RED
        else: ofill, ostroke = C_WHITE, colors.HexColor("#EEEEEE")
        box(c, x+16, oy-16, ow, 16, ofill, ostroke, 6, 1)
        c.setFont("Helvetica-Bold", 8); c.setFillColor(C_DARK_BLUE)
        c.drawString(x+24, oy-11, oid)
        ltext(c, x+40, oy-11, otext, "Helvetica", 8, C_TEXT)
        if mark == "correct":
            box(c, x+16+ow-78, oy-14, 72, 12, C_GREEN, radius=6)
            centre(c, x+16+ow-42, oy-11, "Correct answer", "Helvetica-Bold", 6, C_WHITE)
        elif mark == "wrong":
            box(c, x+16+ow-66, oy-14, 60, 12, C_RED, radius=6)
            centre(c, x+16+ow-36, oy-11, "Your answer", "Helvetica-Bold", 6, C_WHITE)
        oy -= 19

    # action buttons
    btn_y = oy-4
    box(c, x+16, btn_y-18, 78, 18, C_DARK_BLUE if expanded else C_WHITE,
        None if expanded else C_DARK_BLUE, 6, 1)
    centre(c, x+16+39, btn_y-12, "Hide Solution" if expanded else "View Solution",
           "Helvetica-Bold", 7, C_WHITE if expanded else C_DARK_BLUE)
    box(c, x+102, btn_y-18, 72, 18, C_WHITE, C_ORANGE, 6, 1)
    centre(c, x+102+36, btn_y-12, "Re-attempt", "Helvetica-Bold", 7, colors.HexColor("#D68910"))

    # solution (if expanded)
    if expanded and solution:
        soly = btn_y-26
        solh = 14+len(solution)*10
        c.setFillColor(colors.HexColor("#F7F9FC"))
        c.setStrokeColor(colors.HexColor("#CFD8E3")); c.setLineWidth(0.8); c.setDash(3, 2)
        c.roundRect(x+16, soly-solh, wd-32, solh, 6, fill=1, stroke=1); c.setDash()
        ltext(c, x+24, soly-12, "Solution", "Helvetica-Bold", 7.5, C_DARK_BLUE)
        for i, ln in enumerate(solution):
            ltext(c, x+24, soly-23-i*10, ln, "Helvetica", 7, C_TEXT)


# ── UI PAGE: ADMIN CONSOLE ──────────────────────────────────────────────────

def draw_ui_admin(c):
    w, h = A4LW, A4LH
    page_chrome(c, "UI Mockup — Admin Console (exam creator)", 9, w, h)
    top = h - 26*mm - 6*mm

    bx, by = 16*mm, 14*mm
    bw, bh = w-92*mm, top-16*mm
    cx, cy, cw, ch = browser_frame(c, bx, by, bw, bh, "examplatform.com/admin")
    c.setFillColor(C_LIGHT_GREY); c.rect(cx+1, cy+1, cw-2, ch-2, fill=1, stroke=0)

    # sidebar
    sbw = 120
    c.setFillColor(C_NAVY); c.rect(cx+1, cy+1, sbw, ch-2, fill=1, stroke=0)
    ltext(c, cx+14, cy+ch-24, "Admin", "Helvetica-Bold", 12, C_WHITE)
    nav = [("Overview", True), ("Exams", False), ("Categories", False), ("Questions", False)]
    ny = cy+ch-48
    for (label, active) in nav:
        if active:
            box(c, cx+8, ny-13, sbw-16, 18, colors.HexColor("#1F2A44"), radius=5)
        ltext(c, cx+18, ny-8, label, "Helvetica-Bold" if active else "Helvetica", 8.5,
              C_WHITE if active else colors.HexColor("#9FB0C0"))
        ny -= 24

    mx = cx+sbw+18
    mw = cw-sbw-36
    ltext(c, mx, cy+ch-28, "Overview", "Helvetica-Bold", 14, C_NAVY)
    ltext(c, mx, cy+ch-42, "Manage exams, questions and categories.", "Helvetica", 8, colors.HexColor("#888"))

    # stat cards
    stats = [("128", "Total Exams"), ("18,540", "Enrollments"), ("96", "Published"), ("32", "Drafts")]
    scw = (mw-30)/4
    sy = cy+ch-58
    for i, (val, lbl) in enumerate(stats):
        sxx = mx+i*(scw+10)
        box(c, sxx, sy-44, scw, 44, C_WHITE, colors.HexColor("#EAEAEA"), 8, 1)
        centre(c, sxx+scw/2, sy-24, val, "Helvetica-Bold", 16, C_DARK_BLUE)
        centre(c, sxx+scw/2, sy-38, lbl, "Helvetica", 7, colors.HexColor("#888"))

    # exams-by-category bar list (left panel)
    py = sy-44-14
    panel_w = mw*0.54
    box(c, mx, py-150, panel_w, 150, C_WHITE, colors.HexColor("#EAEAEA"), 10, 1)
    ltext(c, mx+14, py-20, "Exams by category", "Helvetica-Bold", 9.5, C_NAVY)
    bars = [("JEE_MAIN", 48), ("NEET", 52), ("JEE_ADVANCED", 34), ("CUET", 26), ("BITSAT", 12)]
    mxv = max(v for _, v in bars)
    byy = py-40
    for (lbl, v) in bars:
        ltext(c, mx+14, byy, lbl, "Helvetica", 7, C_TEXT)
        box(c, mx+96, byy-2, panel_w-130, 8, colors.HexColor("#EEF0F3"), radius=4)
        c.setFillColor(C_TEAL); c.roundRect(mx+96, byy-2, (panel_w-130)*v/mxv, 8, 4, fill=1, stroke=0)
        c.setFont("Helvetica-Bold", 7); c.setFillColor(C_DARK_BLUE)
        c.drawRightString(mx+panel_w-14, byy, str(v))
        byy -= 20

    # quick actions + extensibility (right panel)
    qx = mx+panel_w+14
    qw = mw-panel_w-14
    box(c, qx, py-150, qw, 150, C_WHITE, colors.HexColor("#EAEAEA"), 10, 1)
    ltext(c, qx+14, py-20, "Quick actions", "Helvetica-Bold", 9.5, C_NAVY)
    box(c, qx+14, py-44, qw-28, 22, C_DARK_BLUE, radius=6)
    centre(c, qx+qw/2, py-37, "+ Create Exam", "Helvetica-Bold", 8.5, C_WHITE)
    box(c, qx+14, py-72, qw-28, 22, C_WHITE, C_DARK_BLUE, 6, 1)
    centre(c, qx+qw/2, py-65, "Manage Categories", "Helvetica-Bold", 8.5, C_DARK_BLUE)
    # extensibility highlight
    box(c, qx+14, py-142, qw-28, 60, colors.HexColor("#FFF8E8"), C_ORANGE, 6, 1)
    ltext(c, qx+22, py-96, "Extensible by design", "Helvetica-Bold", 8, colors.HexColor("#9A6A00"))
    for i, ln in enumerate(["Add a new exam track (GATE,", "SAT, CLAT...) from Categories", "-> Add Category. Stored in DB,", "no code change or redeploy."]):
        ltext(c, qx+22, py-108-i*9, ln, "Helvetica", 6.8, colors.HexColor("#9A6A00"))

    centre(c, bx+bw/2, by-6, "ADMIN CONSOLE  —  exam creation, question bank, data-driven categories", "Helvetica-Bold", 9, C_TEXT)

    # annotations
    ax = bx+bw+8
    notes = [
        ("Role-gated", "/admin/** requires ADMIN\nor SUPER_ADMIN — enforced\nin middleware + gateway JWT.", C_DARK_BLUE),
        ("Analytics", "Stat cards + bar list from\nexam-service /admin/analytics\n(counts by status & category).", C_GREEN),
        ("Add Category", "POST /exams/categories ->\nnew row in exam_categories.\nAppears on dashboards instantly.", C_ORANGE),
        ("Create Exam", "POST /exams (DRAFT) ->\nadd sections -> Publish\nschedules + emits Kafka event.", C_PURPLE),
    ]
    ny = cy+ch-16
    for (t, body, col) in notes:
        nlines = body.split("\n")
        nh = 22+len(nlines)*9
        box(c, ax, ny-nh, 62*mm, nh, C_WHITE, col, 5, 1.2)
        ltext(c, ax+8, ny-15, t, "Helvetica-Bold", 8.5, col)
        for i, ln in enumerate(nlines):
            ltext(c, ax+8, ny-27-i*9, ln, "Helvetica", 6.8, C_TEXT)
        ny -= nh+10


# ── UI PAGE: SUPER ADMIN MONITORING ─────────────────────────────────────────

def draw_ui_superadmin(c):
    w, h = A3LW, A3LH
    page_chrome(c, "UI Mockup — Super Admin (monitoring + analytics)", 10, w, h)
    top = h - 26*mm - 6*mm

    bx, by = 14*mm, 14*mm
    bw, bh = w-28*mm, top-16*mm
    cx, cy, cw, ch = browser_frame(c, bx, by, bw, bh, "examplatform.com/super-admin")
    c.setFillColor(colors.HexColor("#0F1828")); c.rect(cx+1, cy+1, cw-2, ch-2, fill=1, stroke=0)

    PAD = 18
    # header
    ltext(c, cx+PAD, cy+ch-26, "Platform Monitoring", "Helvetica-Bold", 15, C_WHITE)
    ltext(c, cx+PAD, cy+ch-40, "super@examplatform.com  ·  SUPER ADMIN", "Helvetica", 8, colors.HexColor("#8FA3B8"))

    # KPI row
    kpis = [("1,284", "Active Sessions", C_ACCENT, True), ("128", "Total Exams", C_TEAL, False),
            ("18.5k", "Enrollments", C_GREEN, False), ("3", "Live Exams", C_PURPLE, True)]
    kw = (cw-2*PAD-30)/4
    ky = cy+ch-54
    for i, (val, lbl, col, pulse) in enumerate(kpis):
        kx = cx+PAD+i*(kw+10)
        box(c, kx, ky-50, kw, 50, colors.HexColor("#16223A"), radius=8)
        c.setFillColor(col); c.roundRect(kx, ky-5, kw, 5, 2, fill=1, stroke=0); c.rect(kx, ky-5, kw, 3, fill=1, stroke=0)
        c.setFont("Helvetica-Bold", 19); c.setFillColor(C_WHITE)
        c.drawString(kx+14, ky-30, val)
        if pulse:
            c.setFillColor(col); c.circle(kx+14+len(val)*11+8, ky-26, 4, fill=1, stroke=0)
        ltext(c, kx+14, ky-44, lbl, "Helvetica", 8, colors.HexColor("#8FA3B8"))

    # panels row
    panel_top = ky-50-16
    colw = (cw-2*PAD-16)/2

    # Live Operations
    lh = 150
    box(c, cx+PAD, panel_top-lh, colw, lh, colors.HexColor("#16223A"), radius=10)
    ltext(c, cx+PAD+14, panel_top-20, "Live Operations", "Helvetica-Bold", 10, C_WHITE)
    c.setFillColor(C_GREEN); c.circle(cx+PAD+colw-44, panel_top-17, 4, fill=1, stroke=0)
    ltext(c, cx+PAD+colw-36, panel_top-19, "realtime", "Helvetica", 7, colors.HexColor("#8FA3B8"))
    rows = [("Active exam sessions", "1,284", "test-engine / Redis"),
            ("Answers / sec (autosave)", "4,910", "Redis HSET (5s avg)"),
            ("Sessions started (total)", "42,108", "since reset"),
            ("Sessions submitted (total)", "40,824", "since reset")]
    ry = panel_top-40
    for (lbl, val, note) in rows:
        ltext(c, cx+PAD+14, ry, lbl, "Helvetica", 8, colors.HexColor("#C8D4E0"))
        c.setFont("Helvetica-Bold", 9); c.setFillColor(C_TEAL)
        c.drawRightString(cx+PAD+colw*0.66, ry, val)
        ltext(c, cx+PAD+colw*0.69, ry, note, "Helvetica", 6.5, colors.HexColor("#6F8298"))
        c.setStrokeColor(colors.HexColor("#22344F")); c.setLineWidth(0.5)
        c.line(cx+PAD+14, ry-7, cx+PAD+colw-14, ry-7)
        ry -= 22
    ltext(c, cx+PAD+14, ry-2, "GET /api/v1/sessions/metrics/live  (admin only)", "Helvetica-Oblique", 6.5, colors.HexColor("#6F8298"))

    # Service Health
    hx = cx+PAD+colw+16
    box(c, hx, panel_top-lh, colw, lh, colors.HexColor("#16223A"), radius=10)
    ltext(c, hx+14, panel_top-20, "Service Health", "Helvetica-Bold", 10, C_WHITE)
    svcs = ["api-gateway", "user-service", "exam-service", "test-engine", "result-service",
            "postgresql", "mongodb", "redis", "kafka"]
    sy2 = panel_top-40
    for i, s in enumerate(svcs):
        col_, row_ = i % 2, i // 2
        ix = hx+14+col_*(colw/2)
        iy = sy2-row_*20
        c.setFillColor(C_GREEN); c.circle(ix+4, iy, 4, fill=1, stroke=0)
        ltext(c, ix+14, iy-2.5, s, "Helvetica", 8, colors.HexColor("#C8D4E0"))
    ltext(c, hx+14, panel_top-lh+12, "mirrors /actuator/health per service", "Helvetica-Oblique", 6.5, colors.HexColor("#6F8298"))

    # analytics bar panels
    aps_top = panel_top-lh-16
    aph = 120
    for pi, (title, data) in enumerate([
        ("Exams by Category", [("JEE_MAIN", 48), ("NEET", 52), ("JEE_ADV", 34), ("CUET", 26), ("BITSAT", 12)]),
        ("Exams by Status", [("PUBLISHED", 96), ("DRAFT", 32), ("LIVE", 3), ("COMPLETED", 410)]),
    ]):
        px = cx+PAD+pi*(colw+16)
        box(c, px, aps_top-aph, colw, aph, colors.HexColor("#16223A"), radius=10)
        ltext(c, px+14, aps_top-20, title, "Helvetica-Bold", 10, C_WHITE)
        mxv = max(v for _, v in data)
        byy = aps_top-40
        for (lbl, v) in data:
            ltext(c, px+14, byy, lbl, "Helvetica", 7, colors.HexColor("#C8D4E0"))
            box(c, px+96, byy-2, colw-140, 8, colors.HexColor("#22344F"), radius=4)
            c.setFillColor(C_TEAL); c.roundRect(px+96, byy-2, (colw-140)*v/mxv, 8, 4, fill=1, stroke=0)
            c.setFont("Helvetica-Bold", 7); c.setFillColor(C_WHITE)
            c.drawRightString(px+colw-14, byy, str(v))
            byy -= 18

    centre(c, bx+bw/2, by-6,
           "SUPER ADMIN  —  live ops (active sessions, answers/sec), service health, platform analytics",
           "Helvetica-Bold", 9, C_TEXT)


# ══════════════════════════════════════════════════════════════════════════
#  PLATYPUS TABLE PAGES
# ══════════════════════════════════════════════════════════════════════════

def build_component_story(styles):
    story = []
    h1 = ParagraphStyle("h1", parent=styles["Heading1"], textColor=C_NAVY, fontSize=14, spaceAfter=6)
    h2 = ParagraphStyle("h2", parent=styles["Heading2"], textColor=C_DARK_BLUE, fontSize=11, spaceBefore=10, spaceAfter=4)

    story.append(Paragraph("Microservice Component Reference", h1))
    story.append(HRFlowable(width="100%", thickness=2, color=C_ACCENT, spaceAfter=8))

    svc = [
        ["Service", "Port", "Technology", "Responsibilities", "Scales"],
        ["user-service", "8081", "Spring Boot 3.2\nPostgreSQL\nRedis · Kafka",
         "• Registration & login\n• OTP (SMS / email)\n• JWT issue (RS256)\n• Argon2id hashing\n• Token blacklist", "2 – 10 pods\nCPU > 70%"],
        ["exam-service", "8082", "Spring Boot 3.2\nPostgreSQL\nMongoDB · Redis",
         "• Create / publish exams\n• Slot management\n• Enrolment\n• Paper generation\n• Redis paper cache", "3 – 15 pods\nCPU > 60%"],
        ["test-engine", "8083", "Spring Boot 3.2\nMongoDB · Redis\nKafka · WebSocket",
         "• Start / resume session\n• Save answers (Redis)\n• Heartbeat / autosave\n• Anti-cheat events\n• Submit -> Kafka", "5 – 50 pods\nCPU > 50%\n(hottest)"],
        ["result-service", "8084", "Spring Boot 3.2\nPostgreSQL\nMongoDB · Kafka",
         "• Kafka consumer\n• Auto-evaluate\n• Score (+4/−1/0)\n• Rank & percentile\n• Publish result", "2 – 8 pods\nKafka lag\n> 1000"],
        ["api-gateway", "8080", "Spring Cloud\nGateway (Netty)\nRedis",
         "• JWT verify (RS256)\n• Rate limit (Redis)\n• Circuit breaker\n• CORS\n• WS proxy + fallback", "3 – 20 pods\nRPS > 10k"],
        ["frontend", "3000", "Next.js 14\nApp Router\nTypeScript",
         "• SSR Server Components\n• BFF API routes\n• httpOnly cookie JWT\n• KaTeX server render\n• Anti-cheat + autosave", "3 – 20 pods\nCPU > 60%"],
    ]
    t = Table(svc, colWidths=[78, 30, 92, 188, 62], repeatRows=1)
    t.setStyle(_tbl_style(C_NAVY))
    story.append(t)

    story.append(Spacer(1, 14))
    story.append(Paragraph("Data Store Reference", h2))
    story.append(HRFlowable(width="100%", thickness=1, color=C_MID_GREY, spaceAfter=6))
    db = [
        ["Store", "Type", "Stores", "Key design decision"],
        ["PostgreSQL :5432", "Relational\nACID", "users, exams, slots,\nenrollments, results",
         "Sharded by user_id range. 2 read replicas serve dashboard & result queries."],
        ["MongoDB :27017", "Document\nNoSQL", "questions (LaTeX+img),\nsnapshots, papers",
         "Index subject+topic+difficulty for fast paper generation. Flexible block schema."],
        ["Redis :6379", "In-memory\nKV", "JWT blacklist, OTP,\nanswers, rate-limit, papers",
         "All hot-path data here. TTL = exam duration + 2h. Cluster mode, AOF everysec."],
        ["Kafka :9092", "Event\nstreaming", "user / exam / answer /\nresult event topics",
         "Decouples 500k-submit spike from evaluation. 3 brokers, LZ4, 7-day retention."],
        ["Azure Blob + CDN", "Object +\nCDN", "images, diagrams,\nchem structures (WebP)",
         "Served from Cloudflare edge (<50ms). WebP ~60% smaller than PNG."],
    ]
    dt = Table(db, colWidths=[95, 62, 150, 218], repeatRows=1)
    dt.setStyle(_tbl_style(C_DARK_BLUE))
    story.append(dt)
    return story


def build_scale_story(styles):
    story = []
    h1 = ParagraphStyle("h1b", parent=styles["Heading1"], textColor=C_NAVY, fontSize=14, spaceAfter=6)
    h2 = ParagraphStyle("h2b", parent=styles["Heading2"], textColor=C_DARK_BLUE, fontSize=11, spaceBefore=10, spaceAfter=4)
    body = ParagraphStyle("bd", parent=styles["Normal"], fontSize=8, leading=11, textColor=C_TEXT, leftIndent=10)
    q = ParagraphStyle("q", parent=styles["Normal"], fontSize=8.5, leading=12, textColor=C_DARK_BLUE, fontName="Helvetica-Bold")

    story.append(Paragraph("Scaling Strategy &amp; Security Model", h1))
    story.append(HRFlowable(width="100%", thickness=2, color=C_ACCENT, spaceAfter=10))
    story.append(Paragraph("Kubernetes HPA — Auto-Scaling", h2))
    scale = [
        ["Service", "Min", "Max", "Trigger", "Why stateless"],
        ["frontend", "3", "20", "CPU > 60%", "Cookies on client; no server-side session"],
        ["api-gateway", "3", "20", "RPS > 10,000", "Rate-limit state lives in Redis"],
        ["user-service", "2", "10", "CPU > 70%", "DB pooled (HikariCP); no local state"],
        ["exam-service", "3", "15", "CPU > 60%", "Paper in Redis; light DB load"],
        ["test-engine", "5", "50", "CPU > 50%", "Answers in Redis; no DB write per save"],
        ["result-service", "2", "8", "Kafka lag > 1000", "Async consumer; off the hot path"],
        ["Redis Cluster", "3", "—", "Memory > 70%", "Sharded across 3 primary nodes"],
        ["Kafka", "3", "—", "Partition lag", "3 replicas/topic; no loss on broker fail"],
    ]
    st = Table(scale, colWidths=[105, 30, 30, 110, 245], repeatRows=1)
    st.setStyle(_tbl_style(C_NAVY))
    story.append(st)

    story.append(Spacer(1, 12))
    story.append(Paragraph("Security Controls", h2))
    story.append(HRFlowable(width="100%", thickness=1, color=C_MID_GREY, spaceAfter=6))
    sec = [
        ["Threat", "Control", "Where"],
        ["JWT theft (XSS)", "httpOnly cookies — JS can't read tokens", "Next.js login/register routes"],
        ["CSRF", "SameSite=Strict cookies", "All Set-Cookie headers"],
        ["Brute-force login", "Redis rate limit: 20 req/s per IP", "Gateway · ipKeyResolver"],
        ["DDoS", "Cloudflare WAF + edge rate limiting", "Edge layer"],
        ["Answer scraping", "Questions rendered to HTML server-side", "ExamShell (Server Component)"],
        ["Tab switching", "visibilitychange -> warn + report", "ExamClient.tsx"],
        ["Copy / paste", "copy/cut/paste/contextmenu blocked", "ExamClient.tsx"],
        ["Token replay", "JWT blacklisted in Redis on logout", "UserService · JwtService"],
        ["Session hijack", "sessionId bound to studentId in Redis", "SessionController"],
        ["Crash / data loss", "Redis AOF + Mongo replica + Kafka repl", "Infrastructure"],
        ["Insecure transport", "HTTPS TLS 1.3 + HSTS everywhere", "Cloudflare · Gateway"],
        ["Privilege escalation", "Role claim in JWT verified at gateway", "JwtAuthFilter"],
    ]
    sect = Table(sec, colWidths=[130, 220, 170], repeatRows=1)
    sect.setStyle(_tbl_style(C_SVC_RED, alt=colors.HexColor("#FDF2F2")))
    story.append(sect)

    story.append(Spacer(1, 12))
    story.append(Paragraph("Key Design Decisions", h2))
    story.append(HRFlowable(width="100%", thickness=1, color=C_MID_GREY, spaceAfter=6))
    decisions = [
        ("Why SSR for the exam page?",
         "Questions are pre-rendered to HTML on the server — never present in the JS bundle, so they can't be scraped from network/DevTools. KaTeX runs on server CPU, so no math-rendering JS ships to the browser and equations appear instantly."),
        ("Why Redis for live answers?",
         "1M users saving every 5s ~ 200k writes/sec — beyond PostgreSQL's ~10k/sec. Redis handles 500k+ ops/sec sub-millisecond. After the exam, a Kafka consumer persists final answers to PostgreSQL asynchronously."),
        ("Why Kafka between test-engine and result-service?",
         "It decouples the submission spike (500k submits in 60s) from CPU-heavy evaluation. If result-service crashes, Kafka retains and re-delivers messages — no submission is ever lost."),
        ("Why httpOnly cookies, not localStorage?",
         "localStorage is readable by any JS (XSS risk). httpOnly cookies are not. With SameSite=Strict and the BFF pattern, the browser never calls Spring Boot directly — preventing both XSS token theft and CSRF."),
        ("Why pre-build papers in Redis?",
         "Otherwise 1M students each trigger a MongoDB query + randomisation at start = DB overload. On publish, the paper is built once and cached in Redis with a TTL; every student gets a <5ms cache hit."),
    ]
    for qa, a in decisions:
        story.append(Paragraph(qa, q))
        story.append(Paragraph(a, body))
        story.append(Spacer(1, 5))
    return story


def _tbl_style(header_bg, alt=None):
    alt = alt or C_LIGHT_GREY
    return TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), header_bg),
        ("TEXTCOLOR", (0, 0), (-1, 0), C_WHITE),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, 0), 8),
        ("FONTNAME", (0, 1), (-1, -1), "Helvetica"),
        ("FONTSIZE", (0, 1), (-1, -1), 7.5),
        ("LEADING", (0, 1), (-1, -1), 10),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [alt, C_WHITE]),
        ("GRID", (0, 0), (-1, -1), 0.4, C_MID_GREY),
        ("TOPPADDING", (0, 1), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 1), (-1, -1), 5),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
    ])


# ══════════════════════════════════════════════════════════════════════════
#  ASSEMBLE
# ══════════════════════════════════════════════════════════════════════════

def generate():
    from reportlab.pdfgen import canvas as cv

    diagram_path = OUT_PATH.replace(".pdf", "_diagrams_tmp.pdf")
    c = cv.Canvas(diagram_path)

    c.setPageSize(A4);            draw_cover(c);          c.showPage()
    c.setPageSize(landscape(A3)); draw_architecture(c);   c.showPage()
    c.setPageSize(landscape(A3)); draw_happy_flow_1(c);   c.showPage()
    c.setPageSize(landscape(A3)); draw_happy_flow_2(c);   c.showPage()
    c.setPageSize(landscape(A4)); draw_ui_login(c);       c.showPage()
    c.setPageSize(landscape(A4)); draw_ui_dashboard(c);   c.showPage()
    c.setPageSize(landscape(A3)); draw_ui_exam(c);        c.showPage()
    c.setPageSize(landscape(A3)); draw_ui_result(c);      c.showPage()
    c.setPageSize(landscape(A4)); draw_ui_admin(c);       c.showPage()
    c.setPageSize(landscape(A3)); draw_ui_superadmin(c);  c.showPage()
    c.save()

    # table pages (A4 portrait, platypus)
    MARGIN = 18*mm
    tables_path = OUT_PATH.replace(".pdf", "_tables_tmp.pdf")
    doc = BaseDocTemplate(tables_path, pagesize=A4,
                          leftMargin=MARGIN, rightMargin=MARGIN,
                          topMargin=34*mm, bottomMargin=16*mm)

    def on_page(canvas_obj, doc_obj):
        page_num = doc_obj.page + 10
        titles = {11: "Component & Data Store Reference", 12: "Scaling & Security"}
        page_chrome(canvas_obj, titles.get(page_num, ""), page_num, A4W, A4H)

    frame = Frame(MARGIN, 16*mm, A4W-2*MARGIN, A4H-50*mm, id="m")
    doc.addPageTemplates([PageTemplate(id="m", frames=[frame], onPage=on_page)])

    styles = getSampleStyleSheet()
    story = build_component_story(styles)
    story.append(PageBreak())
    story += build_scale_story(styles)
    doc.build(story)

    # merge
    writer = PdfWriter()
    for src in [diagram_path, tables_path]:
        for page in PdfReader(src).pages:
            writer.add_page(page)
    with open(OUT_PATH, "wb") as f:
        writer.write(f)
    os.remove(diagram_path)
    os.remove(tables_path)
    print(f"HLD PDF generated: {OUT_PATH}  (10 pages)")


if __name__ == "__main__":
    generate()
