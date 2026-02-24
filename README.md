# 🚀 AutoDeploy — Automated Build & Deploy Tool for Java/Ant Projects

> A JavaFX desktop application that automates the **build → detect → upload → restart** cycle for Java enterprise projects using Apache Ant, replacing manual workflows with FileZilla/WinSCP.

Built to solve a real team productivity problem: deploying code changes to a shared development server required **4 separate manual steps** across multiple tools. AutoDeploy reduces this to **one click**.

---

## 📋 Table of Contents

- [The Problem](#-the-problem)
- [The Solution](#-the-solution)
- [Key Features](#-key-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Server Restart System — Deep Dive](#-server-restart-system--deep-dive)
- [Screenshots](#-screenshots)
- [Project Structure](#-project-structure)

---

## 🔴 The Problem

In our team's development workflow:

1. **Build** the project manually with `ant -f build.xml`
2. **Identify** which JARs and JSPs changed
3. **Upload** the changed files to the correct server paths using FileZilla/WinSCP
4. **Restart** the application server — but first, coordinate with teammates who might be testing

Each cycle took **5–10 minutes** of manual work, was error-prone (wrong files, wrong paths), and server restarts without warning disrupted the entire team.

## ✅ The Solution

AutoDeploy automates the entire pipeline in a single desktop application:

```
┌─────────┐     ┌──────────┐     ┌──────────┐     ┌───────────┐
│  Build   │────▶│  Detect  │────▶│  Upload  │────▶│  Restart  │
│ (Ant)    │     │ (Changes)│     │ (SFTP)   │     │ (Coordin.)│
└─────────┘     └──────────┘     └──────────┘     └───────────┘
    One-click        Auto           One-click       Team-aware
```

---

## ✨ Key Features

### 🔨 Automated Build
- Executes Apache Ant builds (`ant -f build.xml`) directly from the UI
- Real-time build output streaming to the application console
- Automatic detection of build success/failure

### 🔍 Smart Change Detection
- Automatically detects newly created/modified files after build:
  - **JAR files** — compiled libraries
  - **JSP files** — server pages
- Compares timestamps to identify only what changed since last build

### 📤 One-Click Upload
- Uploads detected JARs and JSPs to pre-configured server paths via SFTP/SSH
- No need to manually navigate directory structures in FileZilla
- Upload progress tracking with visual feedback

### 🔄 Coordinated Server Restart (Team-Aware)
- **Real-time notifications** to all connected users when a restart is requested
- **30-second approval window** — teammates can reject if they're mid-testing
- **Concurrent restart handling** — request a new restart while one is running
- **Persistent timer** — tracks restart duration from server timestamps
- Full state machine: `idle → pending → executing → completed`

### 🖥️ Modern Desktop UI
- Built with JavaFX + MaterialFX for a modern look
- Toast notifications (bottom-right corner) for restart events
- Overlay blur effects for confirmation dialogs
- Real-time console logging of all operations

---

## 🏗️ Architecture

The application follows a **layered architecture** with clear separation of concerns:

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer                          │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ Windows  │  │  Components  │  │  Overlays     │  │
│  │ (FXML)   │  │(RestartHandler│  │(UIOverlayMgr) │  │
│  └──────────┘  └──────────────┘  └───────────────┘  │
├─────────────────────────────────────────────────────┤
│                 Service Layer                        │
│  ┌──────────────┐  ┌─────────────────────────────┐  │
│  │RestartService│  │  NotificationHandler         │  │
│  │  (Facade)    │  │  (Toast notifications)       │  │
│  └─────────��────┘  └─────────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│                 Domain Layer                         │
│  ┌──────────────┐  ┌─────────────────────────────┐  │
│  │RestartManager│  │  RestartStatus (Model)       │  │
│  │  (Polling)   │  │  ActiveRestart, Rejection    │  │
│  └──────────────┘  └─────────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│              Infrastructure Layer                    │
│  ┌──────────────────┐  ┌────────────────────────┐   │
│  │ConnectionManager  │  │  SSH/SFTP Transport    │   │
│  │  (SSH sessions)   │  │                        │   │
│  └──────────────────┘  └────────────────────────┘   │
├─────────────────────────────────────────────────────┤
│            Server-Side (Bash)                        │
│  ┌──────────────────────────────────────────────┐   │
│  │  restart_manager.sh                           │   │
│  │  (Atomic locks, status JSON, .dat tracking)   │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **UI** | JavaFX 17+ | Desktop UI framework |
| **UI Components** | MaterialFX | Modern Material Design controls |
| **Window Decoration** | NFX (AbstractNfxUndecoratedWindow) | Custom undecorated windows |
| **Build** | Apache Ant | Project compilation (legacy enterprise) |
| **Transport** | SSH / SFTP | Secure file upload & remote command execution |
| **Serialization** | Gson | JSON parsing for restart status |
| **Server Scripts** | Bash | Restart coordination with atomic file locking |
| **Build Tool** | Maven | Application build management |

---

## 🔄 Server Restart System — Deep Dive

The restart system is the most complex feature, designed for **multi-user coordination** on a shared development server.

### State Machine

```
                    ┌──────────────────────────────────────┐
                    │                                      │
                    ▼                                      │
┌──────┐  request  ┌─────────┐  30s timeout  ┌───────────┐│  5s cleanup
│ IDLE │──────────▶│ PENDING │──────────────▶│ EXECUTING ││──────────────▶ IDLE
└──────┘           └─────────┘               └───────────┘│
                    │       ▲                  │           │
                    │reject │new request       │           │
                    ▼       │                  ▼           │
                 ┌──────────┐              ┌───────────┐  │
                 │ REJECTED │              │ COMPLETED │──┘
                 └──────────┘              └─────────���─┘
```

### Dual-Layer Status

The system tracks **two independent concerns**:

| Layer | What it tracks | Persistence |
|-------|---------------|-------------|
| **Request status** (`status` field) | Current request lifecycle: pending → executing → completed | `restart_status.json` |
| **Active restart** (`active_restart`) | Physical server restart process | `active_restart.dat` |

This separation enables scenarios like:
- **Rejected but still restarting**: A new request was rejected, but the previous restart continues
- **Pending over active**: A new request is pending while a previous restart runs
- **Timer persistence**: The UI timer tracks the physical restart, not the request

### Concurrency Control

```bash
# Atomic file locking using noclobber
set -o noclobber
echo "$$" > "$LOCK_FILE"  # Fails atomically if file exists
```

- **File-based atomic locks** prevent race conditions between concurrent users
- **Stale lock detection**: checks if the PID that holds the lock is still alive
- **Watcher/Executor pattern**: background processes handle the 30s approval window and restart execution independently

### Client-Server Communication

```
┌────────────┐  SSH exec restart_manager.sh   ┌──────────────┐
│ Java Client│ ──────────────────────────────▶ │ Bash Script  │
│ (Polling   │  request/reject/get             │ (Server)     │
│  every 2s) │ ◀────────────────────────────── │              │
│            │  JSON status response            │ status.json  │
└────────────┘                                 │ active.dat   │
                                               │ lock file    │
                                               └���─────────────┘
```

### UI Timer Accuracy

The restart timer displays elapsed time based on **server timestamps**, not local clocks:

```java
// Timer reads active_restart.started_at from server JSON
long startedAtEpoch = latestStatus.getActiveRestart().getStartedAt();
long elapsedMillis = System.currentTimeMillis() - (startedAtEpoch * 1000);
```

This ensures:
- ✅ Timer survives application restarts
- ✅ Timer is consistent across all connected clients
- ✅ Timer persists through rejected requests (physical restart continues)
- ✅ Timer resets only when a genuinely new restart begins

---

## 📸 Screenshots

[Selection window] <img width="636" height="864" alt="selection" src="https://github.com/user-attachments/assets/2d9d8be2-4130-4468-9579-b757ffe98b00" />

[Main Window] <img width="643" height="749" alt="deployment" src="https://github.com/user-attachments/assets/06348a53-8744-4efa-8226-8c2202061db0" />

[Restart Request] <img width="645" height="947" alt="restart_request" src="https://github.com/user-attachments/assets/a606eca8-9a28-4924-9452-344a65a5cafe" />

[Restart] <img width="640" height="938" alt="restart" src="https://github.com/user-attachments/assets/281684dd-eec9-46fc-9ab3-7bc281c226af" />

[Restart Rejected] <img width="484" height="162" alt="restart_rejected" src="https://github.com/user-attachments/assets/8715db4a-c863-45c5-af75-be473c787e12" />

[Custom Themes] <img width="641" height="801" alt="Custom_Themes" src="https://github.com/user-attachments/assets/8975ae5e-ee66-4931-ad80-b9fddf4355fd" />


---

The application stores configuration via `ApplicationConfig`:

| Setting | Description | Example |
|---------|-------------|---------|
| **Server Host** | SSH hostname/IP | `192.168.1.100` |
| **Server Port** | SSH port | `22` |
| **Username** | SSH & display username | `developer1` |
| **Ant Build File** | Path to `build.xml` | `/projects/myapp/build.xml` |
| **JAR Upload Path** | Server path for JARs | `/opt/app/lib/` |
| **JSP Upload Path** | Server path for JSPs | `/opt/app/webapp/` |
| **Restart Script** | Path to `restart_manager.sh` | `/opt/scripts/restart_manager.sh` |

---

## 📁 Project Structure

```
autodeploy/
├── src/main/java/com/autodeploy/
│   ├── core/
│   │   ├── config/          # ApplicationConfig, Constants
│   │   └── constants/       # App-wide constants (polling intervals, etc.)
│   ├── domain/
│   │   ├── manager/         # RestartManager (polling, SSH commands)
│   │   └── model/           # RestartStatus, ActiveRestart, Server
│   ├── infrastructure/
│   │   └── connection/      # ConnectionManager (SSH/SFTP sessions)
│   ├── notification/
│   │   ├── NotificationController.java   # Toast window (JavaFX Stage)
│   │   └── RestartNotificationHandler.java  # Notification logic & dedup
│   ├── service/
│   │   └── restart/         # RestartService (facade)
│   └── ui/
│       ├── dialog/          # CustomAlert (confirmation/error dialogs)
│       ├── overlay/         # UIOverlayManager (blur effects)
│       └── window/
│           └── component/   # RestartHandler (button + timer logic)
├── src/main/resources/
│   ├── fxml/                # FXML layouts
│   └── css/                 # Stylesheets (notification.css, etc.)
├── scripts/
│   └── restart_manager.sh   # Server-side restart coordination script
└── README.md
```

---

## 💡 What I Learned

Building this project taught me:

- **Concurrency in desktop apps** — JavaFX threading model (`Platform.runLater`), Timeline animations, and avoiding race conditions between UI and background tasks
- **Distributed coordination without a database** — using atomic file locks and polling over SSH to coordinate multiple users on a shared resource
- **State machine design** — managing complex UI states (pending, executing, rejected-but-active, pending-over-active) without spaghetti code
- **Real-world problem solving** — identifying a team bottleneck and building a tool that directly improved developer productivity

---

## 📊 Impact

| Metric | Before (Manual) | After (AutoDeploy) |
|--------|-----------------|---------------------|
| **Deploy cycle time** | 5–10 min | ~30 sec |
| **Tools required** | 3 (Terminal + FileZilla + SSH) | 1 |
| **Server restart conflicts** | Frequent (no coordination) | Zero (approval system) |
| **Wrong file uploads** | Occasional | Eliminated (auto-detection) |

---


<p align="center">
  Built with ☕ and frustration with manual deployments<br>
  <b>Bogdan Samy</b>
</p>
