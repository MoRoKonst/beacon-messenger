# Beacon Messenger

A privacy-focused end-to-end encrypted messenger for Android with advanced anti-forensics, intrusion detection, and multi-layer data protection.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Self-Hosting](#self-hosting)
- [Build Instructions](#build-instructions)
- [Quick Start](#quick-start)
- [Security Overview](#security-overview)
- [Documentation](#documentation)

---

## Overview

Beacon is a self-hosted encrypted messenger. All user data is protected by end-to-end encryption; the server never has access to plaintext messages, keys, or call content. The application is designed for adversarial environments: it includes detection of active interception tools, configurable automatic data destruction, and a decoy mode that presents a clean device to a coercing party.

**Version:** 1.038
**Min Android:** 8.0 (API 26)
**Target SDK:** 34
**Language:** Kotlin (Jetpack Compose), Python 3 (server)

---

## Features

### Messaging
- One-to-one encrypted text messages
- Voice messages
- Image and file transfer (chunked, up to 6 MB per chunk)
- Disappearing messages with configurable TTL
- Message reactions, edit, delete
- Reply-to threading
- Read receipts and delivery status
- Message drafts

### Groups
- Encrypted group chats
- Per-group AES-256 key, distributed encrypted per member
- Admin and member roles
- Group descriptions and emoji avatars
- Key rotation on member removal

### Channels
- Broadcast-only channels (admin → subscribers)
- Subscribe via deep link (`beacon://channel?...`)
- Up to 200 posts cached per channel

### Voice & Video Calls
- WebRTC peer-to-peer audio and video
- TURN relay over TCP (port 4433) for NAT traversal
- Call signaling via server (no media passes through server)

### Contacts & Invitations
- ECDSA-signed invite codes with 7-day TTL
- QR code sharing
- Contact fingerprint verification screen
- Contact avatar (128×128 JPEG)

### Security
- End-to-end encryption (ECDH + AES-256-GCM)
- Forward secrecy via one-time prekey bundles
- Double encryption at rest (Storage Master Key layer)
- Biometric unlock
- Panic password (triggers wipe on login)
- Three-level wipe: SOFT / HARD / NUCLEAR
- Decoy mode (post-wipe fake account)
- Dead Man's Switch with configurable check-in interval
- Paranoid Mode (logcat suppression, alert HTTP POST)
- Intrusion detection (proxy, user CA, VPN, ADB, developer options)
- Anti-debugging checks
- Screen capture prevention (`FLAG_SECURE`)
- Root detection
- Certificate pinning

### Backup
- Full encrypted backup and restore
- AES-256-GCM + PBKDF2-SHA256 (100 000 iterations)
- Export via system share sheet

---

## Requirements

### Client
- Android 8.0+ (API 26)
- Google Play Services (optional, for push notifications)
- Camera and microphone permissions for calls

### Server
- Python 3.10+
- `websockets` library
- `cryptography` library
- Accessible IP with port 443 (WSS) or configurable port
- TURN server for NAT traversal (coturn recommended)

---

## Self-Hosting

You can run your own Beacon server and connect the app to it — no recompilation needed.

### 1. Deploy the server

**With Docker (recommended):**

```bash
git clone https://github.com/MoRoKonst/beacon-messenger
cd beacon-messenger
cp ForEXP/.env.example .env
```

Edit `.env` and set `CHANNEL_ADMIN_SECRET` to a random secret:

```bash
# Generate a secret:
python3 -c "import secrets; print(secrets.token_hex(32))"
```

```bash
docker compose up -d
```

The server listens on `127.0.0.1:9000` by default.

**Without Docker:**

```bash
cd ForEXP
pip install websockets cryptography
export CHANNEL_ADMIN_SECRET="your-secret-here"
python server.py --dev
```

### 2. Configure nginx (reverse proxy + TLS)

Add to your nginx site config:

```nginx
location /ws {
    proxy_pass http://127.0.0.1:9000;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 86400;
}
```

### 3. Connect the app to your server

No rebuild required. In the app:

1. Go to **Profile → Servers**
2. Tap **+** to add a server
3. Enter your server hostname and port (default: 9000)
4. Tap the server to switch to it — the app reconnects immediately

---

## Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1) or newer
- JDK 17
- Android SDK with API 34

### Steps

```bash
# Clone the project
cd beacon-messenger

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config in app/build.gradle.kts)
./gradlew assembleRelease
```

The release APK is output to `app/build/outputs/apk/release/`.

### Signing

Configure your release keystore in `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("your-keystore.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = "messenger"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
```

> **Note:** Never hard-code keystore credentials. Use environment variables or `local.properties`.

---

## Quick Start

**Using the public server (beacon-app.org):**

1. Install the APK from [Releases](https://github.com/MoRoKonst/beacon-messenger/releases).
2. Open the app and register a username and password.
3. Share your invite code with a contact (`beacon://invite?...`).
4. When your contact adds you, start a conversation.

**Using your own server:**

See [Self-Hosting](#self-hosting) above, then add your server in **Profile → Servers**.

---

## Security Overview

Beacon uses a layered security model:

| Layer | Mechanism |
|---|---|
| Transport | WSS (TLS 1.2+) with certificate pinning |
| Message E2EE | ECDH (P-256) key agreement + AES-256-GCM |
| Forward secrecy | One-time prekey bundles (X3DH-style) |
| At-rest (L1) | EncryptedSharedPreferences + AndroidKeyStore |
| At-rest (L2) | Storage Master Key, PBKDF2-SHA256 300 000 iter |
| Authentication | PBKDF2-SHA256 100 000 iterations |
| Key signatures | ECDSA (SHA256withECDSA) |

See [SECURITY.md](SECURITY.md) for the full threat model and cryptographic specification.
See [ARCHITECTURE.md](ARCHITECTURE.md) for the system design and module reference.

---

## Documentation

| Document | Description |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System design, module reference, data flows |
| [SECURITY.md](SECURITY.md) | Threat model, cryptographic design, anti-forensics |
| [CHANGEL_LOG.md](CHANGEL_LOG.md) | Engineering change log |
