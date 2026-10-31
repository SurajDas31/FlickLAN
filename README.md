# FlickLAN 🚀
> **Decentralized Peer-to-Peer (P2P) Local Network Chat & File Sharing Application**  
> Standalone Windows Executable (`.exe`) with Automatic Peer Discovery, Local SQLite Persistence, Auto-Update, and Chat Backup/Restore.

---

### 🔗 Quick Navigation
[📥 **Download**](#download) • [🏛️ **Architecture**](#architecture) • [💻 **Dev**](#dev) • [📖 **API Docs**](#api-docs) • [🌟 **Features**](#features)

---

### 🌐 Port Configuration Notice
| Service | Configured Port | Notes |
| :--- | :--- | :--- |
| **Frontend (React / Vite)** | **`65000`** | `http://localhost:65000` (Dev server) |
| **Backend (Spring Boot Engine)** | **`65005`** | `http://localhost:65005` *(Standard TCP/IP allows ports 1 to 65535; `65005` is configured as the 5-digit port within range)* |
| **P2P Discovery (UDP)** | **`8888`** | Automatic broadcast heartbeat |

---

<a id="download"></a>
## 📥 Download & Packaging

FlickLAN compiles into a **100% self-contained Windows executable package**. 

### No Dependencies Required for End-Users
Users who run the `.exe` do **NOT** need to install:
* ❌ No Java (JRE or JDK)
* ❌ No Node.js or npm
* ❌ No external database server (SQLite is embedded)

### How to Build the Executable
Run the included packaging script from the root folder:
```powershell
.\scripts\build-exe.bat
```

This automated pipeline:
1. Compiles the optimized React 18 production bundle (`npm run build`).
2. Syncs the web build directly into Spring Boot's embedded resources (`src/main/resources/static/`).
3. Compiles the Java backend into a fat JAR (`mvn clean package`).
4. Invokes JDK 21's `jpackage` to bundle a custom, stripped-down private JRE and generates:
   ```
   dist-app\FlickLAN\
   ├── FlickLAN.exe    <-- Double-click to launch
   ├── app\            <-- Application binaries & UI
   └── runtime\        <-- Private embedded Java runtime
   ```

To distribute, simply zip the `dist-app\FlickLAN` folder or send `FlickLAN.exe`. Any Windows 10 or 11 computer can run it immediately.

---

<a id="architecture"></a>
## 🏛️ Architecture

FlickLAN operates as a **True Decentralized Peer-to-Peer (P2P) Mesh Network**. Every user installs and runs an identical application instance. There is **no central server** and **no single point of failure**.

### Network Topology

```mermaid
flowchart TD
    subgraph LAN ["Local Network / Wi-Fi Mesh"]
        subgraph NodeA ["Alice's PC (FlickLAN.exe)"]
            UIA["React UI (localhost:8080)"]
            EngineA["P2P Engine"]
            DBA[(Local SQLite DB)]
        end

        subgraph NodeB ["Bob's PC (FlickLAN.exe)"]
            UIB["React UI (localhost:8080)"]
            EngineB["P2P Engine"]
            DBB[(Local SQLite DB)]
        end

        subgraph NodeC ["Charlie's PC (FlickLAN.exe)"]
            UIC["React UI (localhost:8080)"]
            EngineC["P2P Engine"]
            DBC[(Local SQLite DB)]
        end

        EngineA <-->|Direct P2P HTTP & Files| EngineB
        EngineB <-->|Direct P2P HTTP & Files| EngineC
        EngineA <-->|Direct P2P HTTP & Files| EngineC
        
        EngineA -.->|UDP Discovery Beacon :8888| LAN
        EngineB -.->|UDP Discovery Beacon :8888| LAN
        EngineC -.->|UDP Discovery Beacon :8888| LAN
    end
```

### Core Architecture Principles

1. **Zero-Configuration Discovery (UDP Port 8888)**:
   * Every running node broadcasts a lightweight UDP heartbeat beacon every 3 seconds containing its Node ID, display name, and listening port.
   * Neighbor nodes catch these packets and dynamically maintain their peer lists in real time.
   * If a node disconnects or shuts down, the absence of beacons for >12 seconds marks them offline across all peers.
   * **Smart Interface Selection**: The discovery service automatically filters out virtual adapters (WSL, Hyper-V, VirtualBox, VMware, Docker) and prioritizes your computer's real physical Wi-Fi or Ethernet adapter.

2. **Direct Peer-to-Peer Messaging**:
   * **Direct Messages (1-to-1)**: Dispatched directly from the sender's PC to the recipient's IP address (`POST /api/p2p/direct-message`). Both sender and recipient store a copy in their local SQLite databases.
   * **Public Channels (`#general`)**: Broadcast in parallel across all currently discovered online peer IPs (`POST /api/p2p/channel-message`).

3. **High-Speed Point-to-Point File Transfers**:
   * When you attach a photo, video, or document (up to 500MB), the file is stored locally on your machine.
   * A download descriptor is sent to the recipient, who streams the file directly from your machine over LAN at local Gigabit/Wi-Fi speeds.

4. **Private Local Persistence (SQLite)**:
   * Each machine maintains its own independent database at `data/flicklan.db`.
   * Conversations are stored locally. If one colleague leaves the office, your chat history remains completely intact.

5. **Security Hardening**:
   * **Path Traversal Guard**: Download endpoints enforce canonical directory boundary checks to prevent arbitrary file access outside `data/uploads/`.
   * **RFC 3986 Compliance**: Direct P2P calls safely wrap IPv6 addresses in square brackets to prevent URI parsing exceptions.

---

<a id="dev"></a>
## 💻 Dev Setup

### Prerequisites
* **Java JDK 21+** (e.g. OpenJDK or Oracle JDK 21 with `jpackage`)
* **Node.js v18+** & **npm**
* **Apache Maven 3.8+** (Required for compilation: `winget install Apache.Maven` or from [maven.apache.org](https://maven.apache.org/download.cgi))

### Running in Development Mode
To start both the Spring Boot backend and the React Vite development server concurrently:

```powershell
.\scripts\dev-start.bat
```

Or run them individually in separate terminals:

#### Terminal 1: Backend Engine (Spring Boot)
```powershell
cd backend
mvn spring-boot:run
```
* Backend runs on: `http://localhost:65005`
* SQLite database initializes automatically at: `backend/data/flicklan.db`

#### Terminal 2: Frontend UI (React + Vite)
```powershell
cd frontend
npm install
npm run dev
```
* Vite Dev Server opens at: `http://localhost:65000`
* Proxies `/api` and `/ws` automatically to backend port `65005`.

---

<a id="api-docs"></a>
## 📖 API Documentation (Swagger / OpenAPI)

FlickLAN includes full OpenAPI 3.0 documentation:

* **Interactive Swagger UI**: `http://localhost:65005/swagger-ui.html`
* **Raw OpenAPI 3 JSON**: `http://localhost:65005/v3/api-docs`
* **Standalone OpenAPI YAML**: Located at [`docs/openapi.yaml`](file:///d:/works/FlickLAN/docs/openapi.yaml) (Ready to import into Postman or Insomnia).

### Summary of REST & P2P Endpoints

| Category | Method | Endpoint | Description |
| :--- | :--- | :--- | :--- |
| **Node Info** | `GET` | `/api/info` | Returns local machine ID, host name, and LAN IP |
| **Peers** | `GET` | `/api/peers` | Lists all discovered active peers on the LAN |
| **Messages** | `GET` | `/api/messages/channel/{channel}` | Retrieves public channel history from local SQLite |
| **Messages** | `GET` | `/api/messages/direct/{peerId}` | Retrieves 1-on-1 direct chat history with a peer |
| **Messages** | `DELETE` | `/api/messages/direct/{peerId}` | Deletes direct chat history with a specific peer |
| **P2P Inbound** | `POST` | `/api/p2p/direct-message` | Inbound webhook called by remote peers to deliver DMs |
| **P2P Inbound** | `POST` | `/api/p2p/channel-message`| Inbound webhook called by remote peers for channel messages |
| **Files** | `POST` | `/api/files/upload` | Uploads local file attachment for LAN sharing |
| **Files** | `GET` | `/api/files/download/{filename}` | Streams stored file directly to requesting peer |
| **Backup** | `GET` | `/api/backup/export` | Exports all messages as `.json` or `.zip` (with media) |
| **Backup** | `GET` | `/api/backup/export/peer/{peerId}` | Exports only the conversation with a specific peer |
| **Backup** | `POST` | `/api/backup/inspect` | Previews uploaded backup file metadata before restoring |
| **Backup** | `POST` | `/api/backup/restore` | Restores backup with `MERGE` (safe deduplication) or `OVERWRITE` |
| **Updates** | `GET` | `/api/update/check` | Queries for latest version release |
| **Updates** | `POST` | `/api/update/apply` | Downloads update and spawns atomic restart process |

---

<a id="features"></a>
## 🌟 Key Features

* **Self-Excluding Peer List**: The sidebar lists all other discovered computers on your network while strictly hiding yourself.
* **Peer Context Menu**: Right-click or click the 3-dots button (`⋮`) on any peer to:
  * 🗑️ **Delete Chat History**: Wipes the conversation with that peer from your local database.
  * 💾 **Backup Chat (JSON)**: Exports that individual conversation to a standalone JSON file.
  * 💬 **Open Chat**: Jump directly to messaging that peer.
* **Browser Pop-Up Notifications & Sound**:
  * Native desktop pop-up alerts notify you when someone messages you or shares a file.
  * Web Audio API provides an instant offline two-tone chime when messages arrive.
* **Mobile-Responsive UI**:
  * On mobile screens, the sidebar slides in as an off-canvas drawer with backdrop blur.
  * iOS Safari auto-zoom prevention and dynamic viewport height (`100dvh`).
* **Auto-Update Engine**:
  * Checks for releases against GitHub Releases or a custom endpoint.
  * One-click update triggers [`scripts/update-runner.bat`](file:///d:/works/FlickLAN/scripts/update-runner.bat) to swap the binary and restart smoothly.
