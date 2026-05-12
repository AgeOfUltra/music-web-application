# 🎵 Music Room — Social Music Chat Web Application

A web-based social platform where users can chat and listen to music together in real time. Users create private listening rooms, invite friends, control music playback collaboratively, and even send anonymous confessions with music in the background.

---

## Table of Contents

- [Features](#features)
- [Architecture Overview](#architecture-overview)
- [User Flow](#user-flow)
- [Tech Stack](#tech-stack)
- [Key Design Decisions](#key-design-decisions)
- [Room Lifecycle](#room-lifecycle)
- [Music Streaming Strategy](#music-streaming-strategy)
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
│  Client                                                                  │
│  Web Browser — React/HTML · WebSocket client                            │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ HTTPS / WSS
┌──────────────────────────────┴──────────────────────────────────────────┐
│  Gateway                                                                 │
│  Nginx (reverse proxy / SSL)  ←→  Spring Security (JWT · session lock)  │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────────┐
│  Application Services                                                    │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌─────────────┐ │
│  │ Auth & User   │ │ Room Service  │ │ Music Service │ │Confess &    │ │
│  │ Register      │ │ Create/Invite │ │ Playlist      │ │Email Service│ │
│  │ Login         │ │ Organiser     │ │ Playback sync │ │ Anonymous   │ │
│  │ Email verify  │ │ Participants  │ │ Song request  │ │ Monitor     │ │
│  └───────────────┘ └───────────────┘ └───────────────┘ └─────────────┘ │
│                                                                          │
│  ┌──────────────────────────────────┐  ┌──────────────────────────────┐ │
│  │ WebSocket (STOMP)                │  │ Spring Scheduler             │ │
│  │ Chat · Play/Pause/Resume sync    │  │ Pre-fetch songs every minute │ │
│  │ Role control broadcast          │  │ Cleanup every 6 h            │ │
│  └──────────────────────────────────┘  │ Bulk email dispatch         │ │
│                                        └──────────────────────────────┘ │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────────┐
│  Data Layer                                                              │
│  ┌────────────────────┐ ┌─────────────────────┐ ┌────────────────────┐  │
│  │ PostgreSQL         │ │ Redis Cache          │ │ Server Song Cache  │  │
│  │ Users, Rooms       │ │ Room + organiser     │ │ Downloaded audio   │  │
│  │ Songs, Confess     │ │ Playback state       │ │ Pre-fetched files  │  │
│  │ Email logs         │ │ Song metadata TTL 6h │ │ Cleaned every 6 h  │  │
│  │ Hibernate ORM      │ └─────────────────────┘ └────────────────────┘  │
│  └────────────────────┘                                                  │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────────┐
│  Infrastructure                                                          │
│  ┌─────────────────┐  ┌─────────────────────┐  ┌──────────────────────┐ │
│  │ Docker · Linux  │  │ AWS S3 Bucket        │  │ Email SMTP           │ │
│  │ Spring Boot     │  │ Global music storage │  │ Bulk confess sender  │ │
│  │ Containerised   │  │ Scheduler downloads  │  │ Delivery tracking    │ │
│  └─────────────────┘  └─────────────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

> See the interactive architecture diagram in the project wiki or the `/docs` folder.

---

## User Flow

```
Login ──► Dashboard ──► Create / Join Room ──► Enter Room
              │
              ├──► Send Song Request to organiser
              ├──► Send Anonymous Confess (email)
              └──► Monitor Email Status (opened / read / completed)
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
       ├── Participant leaves ──► room continues
       │
       ├── Organiser leaves ──► next joined participant becomes organiser
       │
       └── All participants leave ──► room removed from Redis
```

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

## Security Model

| Concern | Implementation |
|---|---|
| API authentication | JWT token validated by Spring Security on every request |
| Single-session enforcement | Active token stored; new login invalidates the previous session |
| Email verification | First-login sends a verification link; account is inactive until confirmed |
| Password storage | Hashed (never stored in plain text) |

> **Known limitation:** The current implementation keeps access tokens valid for an extended period without refresh token rotation. Refresh token support is on the roadmap.

---

## Confess Feature

An anonymous confess lets a user write a heartfelt message to someone special. The message is delivered by email with a music track playing in the background when the recipient opens it.

- **Anonymous by design** — the sender's identity is not revealed in the email.
- **One-way conversation** — the recipient cannot reply through the platform.
- **Email monitoring** — the sender can view the status of their confess: whether the email was delivered, opened, read, and when reading was completed.
- **Batch processing** — confess emails are queued and sent in bulk by the scheduler.
- **Future enhancement** — AI-powered moderation to filter offensive or harmful language before delivery.

---

## Scheduler Jobs

| Job | Frequency | Purpose |
|---|---|---|
| Song pre-fetch | Every 1 minute | Downloads songs from S3 for active room playlists |
| Song cache cleanup | Every 6 hours | Removes stale audio files from the server |
| Bulk email dispatch | Configurable | Processes queued confess and notification emails |

---

## Known Limitations & Future Work

| Area | Current state | Planned improvement |
|---|---|---|
| Access token lifecycle | Long-lived tokens, no refresh | Implement refresh token rotation |
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
