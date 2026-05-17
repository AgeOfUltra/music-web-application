# 🎵 Music Room — Social Music Chat Web Application

A web-based social platform where users can chat and listen to music together in real time. Users create private listening rooms, invite friends, control music playback collaboratively, and even send anonymous confessions with music in the background through email.

---

## Table of Contents

- [Features](#features)
- [Architecture Overview](#architecture-overview)
- [User Flow](#user-flow)
- [Tech Stack](#tech-stack)
- [Key Design Decisions](#key-design-decisions)
- [Room Lifecycle](#room-lifecycle)
- [Session & Room Logout Handling](#session--room-logout-handling)
- [Music Streaming Strategy](#music-streaming-strategy)
- [Error Handling & Resilience](#error-handling--resilience)
- [Security Model](#security-model)
- [Confess Feature](#confess-feature)
- [Scheduler Jobs](#scheduler-jobs)
- [Known Limitations & Future Work](#known-limitations--future-work)
- [Getting Started](#getting-started)

---

## Features

### Core
- Real-time group chat while listening to music together
- Room creation with a maximum of 5 participants
- Role-based audio control — only the organiser can control playback
- Sync playback state (play, pause, resume) across all participants via WebSocket

### Music
- Global playlist browsing and playback
- Room-level favourite playlist (persisted per room)
- Song request system — participants request songs to the organiser
- Pre-fetched song cache for near-instant playback

### Confess
- Send an anonymous confession to a loved one via email
- Background music plays on the recipient's email view
- One-way conversation — fully anonymous
- Email monitoring dashboard: tracks open, read, and read-completed events
- Bulk email processing via scheduler (not sent immediately)

### Auth & Security
- Account registration with email verification on first login
- JWT-based authentication managed by Spring Security
- Single session enforcement — one active login per user at a time

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Client layer                                                            │
│  Web Browser — REST/JSON · WebSocket (STOMP) · onbeforeunload detection │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ HTTPS / WSS
┌──────────────────┬───────────┴──────────────────────────────────────────┐
│  Nginx           │           Spring Security + JWT                       │
│  Reverse proxy   │           Token validation · one-session-per-user     │
└──────────────────┴──────────────────────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────────┐
│  Application / Business Layer                                            │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────────┐  │
│  │ Auth & user │ │ Room service │ │ Music service│ │ Confess &      │  │
│  │ Register    │ │ Create/invite│ │ Playlist     │ │ email service  │  │
│  │ Login       │ │ Organiser    │ │ Playback sync│ │ Anonymous send │  │
│  │ Email verify│ │ handoff      │ │ Song request │ │ Read monitor   │  │
│  └─────────────┘ └──────────────┘ └──────────────┘ └────────────────┘  │
│                                                                          │
│  ┌──────────────────────┐ ┌──────────────────┐ ┌──────────────────────┐ │
│  │ Role-based control   │ │ Error handling   │ │ Session guard        │ │
│  │ Organiser/participant│ │ Spring Retry     │ │ Browser-abort detect │ │
│  │ audio permissions    │ │ S3 & SMTP retry  │ │ Temp lock / unlock   │ │
│  └──────────────────────┘ └──────────────────┘ └──────────────────────┘ │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
┌─────────────────────┬────────┴────────────────────────────────────────┐
│  WebSocket (STOMP)  │              Spring Scheduler                    │
│  Chat · sync state  │  Pre-fetch songs (1 min) · cleanup (6 h)        │
│  Join/leave events  │  Bulk email dispatch · session lock cleanup      │
└─────────────────────┴────────┬────────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────────┐
│  Data Layer                                                              │
│  ┌────────────────────┐ ┌──────────────────────┐ ┌──────────────────┐   │
│  │ PostgreSQL         │ │ Redis Cache           │ │ Server song cache│   │
│  │ Users · rooms      │ │ Room + organiser state│ │ Pre-fetched audio│   │
│  │ Songs · confess    │ │ Playback state        │ │ Scheduler-loaded │   │
│  │ Email logs         │ │ Song metadata TTL 6 h │ │ Cleaned every 6h │   │
│  │ Hibernate ORM      │ │ Session lock flag     │ └──────────────────┘   │
│  └────────────────────┘ └──────────────────────┘                        │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────────┐
│  Infrastructure                                                          │
│  ┌──────────────────┐  ┌──────────────────────┐  ┌───────────────────┐  │
│  │ Docker · Linux   │  │ AWS S3 Bucket         │  │ Email SMTP        │  │
│  │ Spring Boot      │  │ Global music storage  │  │ Bulk sender       │  │
│  │ Containerised    │  │ Scheduler downloads   │  │ Delivery tracking │  │
│  └──────────────────┘  └──────────────────────┘  └───────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

> The interactive architecture diagram with clickable layers is in the project wiki or `/docs` folder.

---

## User Flow

```
Login ──► Dashboard ──► Create / Join Room ──► Enter Room ──► Chat + Listen
              │
              ├──► Send song request to organiser
              ├──► Send anonymous confess (email)
              └──► Monitor email status (opened / read / completed)

                          ↓ (if user closes tab or browser abruptly)
                    Abort detected ──► session temporarily locked
                    Scheduler unlocks session after timeout
```

### Detailed steps

1. **Register** — user signs up; an email verification link is sent for first-time authentication.
2. **Login** — JWT token issued. Only one active session is allowed per account.
3. **Dashboard** — user sees their rooms, global playlist, and action shortcuts.
4. **Create room** — user becomes the organiser. Up to 5 participants can be invited by link or username.
5. **Join room** — invited participants enter the room. Organiser controls playback.
6. **In-room actions:**
   - Chat in real time while music plays
   - Organiser plays, pauses, or resumes tracks; state is broadcast to all participants via WebSocket
   - Participants can request songs; organiser approves/plays them
   - Any participant can mark songs as room favourites
7. **Confess** — user writes an anonymous message, selects a background track, and sends it via email to a recipient.
8. **Monitor** — sender sees whether the recipient opened, read, or finished reading the email.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java |
| Framework | Spring Boot |
| Security | Spring Security · JWT |
| Resilience | Spring Retry |
| ORM | Hibernate |
| Database | PostgreSQL |
| Cache | Redis |
| Real-time | WebSocket (STOMP) |
| Scheduler | Spring Scheduler |
| Object storage | AWS S3 |
| Web server | Nginx |
| Containerisation | Docker |
| OS | Linux |

---

## Key Design Decisions

### Redis as the source of truth for live room state
Room metadata, current organiser, and playback state (play / pause / resume) are written to Redis on every change and read back to broadcast to all participants. This keeps WebSocket sync fast and avoids hitting the database on every heartbeat.

### Song metadata cached with a 6-hour TTL
Full song objects (title, duration, S3 key, etc.) are stored in Redis with a TTL. When the TTL expires the next read fetches from PostgreSQL and refreshes the cache. The actual audio bytes are never stored in Redis.

### Pre-fetching audio to the application server
A scheduler runs every minute and downloads audio files for songs that appear in any room's favourite playlist. When the organiser plays one of these, the file is already on disk and streams instantly. Songs played on demand (direct user click) are downloaded at request time, which adds a small delay — a known trade-off.

### Organiser handoff
If the organiser disconnects, the application promotes the next-joined participant to organiser automatically. If the room becomes empty, the room record is removed from Redis (and marked inactive in the database).

### Bulk email dispatch
Confess emails are not sent immediately. They are queued and dispatched in batches by the scheduler. This reduces SMTP pressure, allows retry logic, and enables the monitoring feature (open tracking pixel / read receipt).

---

## Room Lifecycle

```
User creates room
       │
       ▼
Room + organiser saved to PostgreSQL and Redis
       │
       ▼
Participants join (max 5)
       │
       ├── Organiser controls playback ──► Broadcast via WebSocket
       │
       ├── Participant leaves (clean logout) ──► room continues
       │
       ├── Participant closes browser ──► session lock applied
       │       └── Scheduler unlocks after timeout window
       │
       ├── Organiser leaves ──► next joined participant becomes organiser
       │
       └── All participants leave ──► room removed from Redis
```

---

## Session & Room Logout Handling

Managing user exit correctly is one of the more complex parts of the system. There are three distinct exit paths, each requiring different handling to avoid data inconsistency in room membership and session state.

### Clean logout and room exit
When a user explicitly signs out or leaves a room through the UI, the server receives the logout request, clears the session token from the active-session store, removes the participant from the room in both PostgreSQL and Redis, and triggers organiser handoff if needed. This path is straightforward and causes no consistency issues.

### Browser abort — closing the tab or browser window
This is the most difficult case. Closing the browser tab does not fire a clean logout request — the connection drops without any server-side notification. Without a workaround, the user would remain counted as an active room participant in Redis while their actual connection is gone, leading to a ghost participant and a blocked session that prevents re-login.

The implemented workaround:

```
Browser fires onbeforeunload
       │
       ▼ (best-effort beacon or WebSocket disconnect event received by server)
Server detects disconnect
       │
       ▼
User flagged as "temporarily locked" in Redis
(cannot re-login until lock is released)
       │
       ▼
Spring Scheduler runs on a short interval
  └── finds expired lock entries
      └── unlocks user ──► removes from room membership ──► triggers handoff if organiser
```

This prevents ghost sessions and ensures room membership is eventually consistent. The lock window is intentionally short — long enough to survive an accidental browser refresh without disrupting the session, but short enough that the room recovers within seconds if the user truly disconnected.

> **Known trade-off:** During the lock window the user cannot re-login from another device. This is intentional — it prevents duplicate ghost sessions. A proper WebSocket heartbeat with precise server-side disconnect detection is planned as a cleaner long-term solution.

### Sign-out from one device
Only one active session per user is permitted. When a new login is attempted, the existing session token is invalidated. If the prior session was inside a room, the same disconnect-and-scheduler cleanup path handles participant removal and organiser handoff.

---

## Music Streaming Strategy

```
Scheduler (every 1 min)
  └── checks all active room favourite playlists
      └── downloads missing songs from S3 ──► local server cache

Organiser plays a favourite song
  └── file already on disk ──► instant playback ──► WebSocket sync to all

Participant clicks any song directly
  └── download from S3 on demand ──► slight initial delay ──► play

Scheduler (every 6 hours)
  └── removes audio files not accessed recently
```

---

## Error Handling & Resilience

The application uses **Spring Retry** to handle transient failures in external integrations and critical internal operations, preventing single-point failures from cascading into user-visible errors.

### S3 download retries
Song downloads from AWS S3 — both on-demand and scheduler-triggered pre-fetches — are wrapped with retry logic. If a download fails due to a transient network issue or an S3 rate limit, Spring Retry reattempts the operation with a configurable back-off interval before surfacing an error to the caller.

```
S3 download attempt
  └── success ──► file cached locally, ready to play
  └── failure ──► retry (up to N times, exponential back-off)
        └── all retries exhausted ──► log error · skip song · notify caller
```

### Email dispatch retries
Bulk confess emails are dispatched by the scheduler. If the SMTP server is temporarily unavailable or rejects a message batch, Spring Retry re-queues the failed messages for the next scheduler cycle, avoiding silent message loss.

```
Bulk email dispatch
  └── success ──► marked as sent in DB · email status updated
  └── SMTP failure ──► retry on next scheduler run
        └── max retries exceeded ──► marked as failed · visible in monitor dashboard
```

### WebSocket reconnection
If a participant's WebSocket connection drops mid-session (network hiccup, mobile network switch), the client attempts to reconnect. On successful reconnection, the server re-broadcasts the current playback state so the participant's player resynchronises automatically without a page refresh.

### Database and Redis resilience
Critical write operations — updating playback state, recording a confess, changing organiser — are wrapped in transactions. If a Redis write fails, the application falls back to the PostgreSQL record to maintain consistency, with a warning logged for monitoring.

---

## Security Model

| Concern | Implementation |
|---|---|
| API authentication | JWT token validated by Spring Security on every request |
| Single-session enforcement | Active token stored; new login invalidates the previous session |
| Email verification | First-login sends a verification link; account is inactive until confirmed |
| Password storage | Hashed (never stored in plain text) |
| Browser-abort guard | Session temporarily locked; scheduler clears lock after timeout |

> **Known limitation:** The current implementation keeps access tokens valid for an extended period without refresh token rotation. Refresh token support is on the roadmap.

---

## Confess Feature

An anonymous confess lets a user write a heartfelt message to someone special. The message is delivered by email with a music track playing in the background when the recipient opens it.

- **Anonymous by design** — the sender's identity is not revealed in the email.
- **One-way conversation** — the recipient cannot reply through the platform.
- **Email monitoring** — the sender can view the status: delivered, opened, read, and reading completed.
- **Batch processing** — confess emails are queued and sent in bulk by the scheduler.
- **Retry on failure** — failed SMTP sends are retried automatically by Spring Retry on the next scheduler cycle.
- **Future enhancement** — AI-powered moderation to filter offensive or harmful language before delivery.

---

## Scheduler Jobs

| Job | Frequency | Purpose |
|---|---|---|
| Song pre-fetch | Every 1 minute | Downloads songs from S3 for active room playlists |
| Song cache cleanup | Every 6 hours | Removes stale audio files from the server |
| Bulk email dispatch | Configurable | Processes queued confess and notification emails |
| Session lock cleanup | Short interval | Unlocks users whose browser-abort lock has expired |

---

## Known Limitations & Future Work

| Area | Current state | Planned improvement |
|---|---|---|
| Access token lifecycle | Long-lived tokens, no refresh | Implement refresh token rotation |
| Browser-abort handling | Scheduler-based temp lock workaround | WebSocket heartbeat with server-side disconnect detection |
| On-demand streaming | Small delay when downloading from S3 | Progressive streaming / chunked delivery |
| Authentication | Email + password only | Google OAuth 2.0 integration |
| Confess moderation | None | AI-based detection of harmful language |
| Session security | Single session via token check | Consider device fingerprinting |

---

## Getting Started

### Prerequisites

- Java 17+
- Docker & Docker Compose
- PostgreSQL 15+
- Redis 7+
- AWS S3 bucket with appropriate IAM credentials

### Running locally

```bash
# Clone the repository
git clone https://github.com/AgeOfUltra/music-web-application.git
cd music-web-application

# Copy and edit environment config
cp .env.example .env
# Fill in your DB credentials, Redis URL, AWS keys, SMTP settings, JWT secret

# Build and start all services
docker-compose up --build
```

The application will be available at `http://localhost:8080`.

> **Note:** Do not commit `.env` or any file containing credentials to version control.

---

## Contributing

Pull requests are welcome. For significant changes, please open an issue first to discuss the proposed change.

---
