<div align="center">

# NexorA

### Offline. Direct. Private. Peer-to-Peer.

<p align="center">
  <b>A local-first Android communication system for secure messaging and file sharing without internet, cellular data, or central servers.</b>
</p>

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Android SDK](https://img.shields.io/badge/Android-SDK%2024%2B%20%7C%20Target%2036-3DDC84?style=flat-square&logo=android)](https://developer.android.com)
[![Nearby Connections](https://img.shields.io/badge/API-Google%20Nearby%20Connections%20v19.3.0-4285F4?style=flat-square&logo=google)](https://developers.google.com/nearby/connections/overview)
[![Architecture](https://img.shields.io/badge/Topology-Single--Hop%20P2P__STAR-blueviolet?style=flat-square)](https://developers.google.com/nearby/connections/strategies)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

</div>

---

## 🛰️ What is NexorA?

**NexorA** is an offline-first peer-to-peer (P2P) communication engine built for Android devices. It operates directly over local radio hardware—combining **Bluetooth Low Energy (BLE)** for discovery and high-bandwidth **Wi-Fi Direct / Wi-Fi Aware** for data transmission via the Google Nearby Connections API (`P2P_STAR` strategy).

```
NEXORA P2P AD-HOC NETWORK
┌───────────────────────────┐                ┌───────────────────────────┐
│     Android Device A      │  Direct Radio  │     Android Device B      │
│  (Role: Advertiser / Host)│◄──────────────►│   (Role: Discoverer / Client)│
│                           │   Bluetooth /  │                           │
│  - SQLite Room Storage    │   Wi-Fi Direct │  - SQLite Room Storage    │
│  - AES-256 Encryption     │   (Zero Cloud) │  - AES-256 Encryption     │
└───────────────────────────┘                └───────────────────────────┘
```

> **Zero Cloud. Zero Servers. Zero Data Usage.**  
> All communications remain isolated to the local radio range of the participating physical devices.

---

## ⚡ The Problem & Solution

Traditional mobile messaging applications depend on a complex central architecture:

```
[ Sender Device ] ──► (Cellular / Internet) ──► [ Cloud Servers ] ──► (Cellular / Internet) ──► [ Receiver Device ]
```

When network infrastructure fails—during grid blackouts, remote expeditions, natural disasters, or in strict privacy environments—cloud-dependent messaging halts.

**NexorA bypasses infrastructure completely:**

```
[ Device A ] ═════════════════ (Direct P2P Link via BLE / Wi-Fi) ═════════════════► [ Device B ]
```

*Communication range is governed strictly by device radio hardware power and physical line-of-sight conditions (typically 10–100 meters).*

---

## 📊 Feature Matrix

| Capability | NexorA | Cloud Messengers | Standard Bluetooth |
| :--- | :---: | :---: | :---: |
| **Internet / Cellular Required** | **No** | Yes | No |
| **Centralized Server Dependencies** | **None** | Mandatory | None |
| **Peer Discovery Engine** | **Auto (6-10s role toggle) + Manual** | Central Directory | Manual Pairing |
| **Text Messaging** | **Yes (Offline Queuing)** | Yes | Basic |
| **Binary Stream File Transfer** | **Yes (Images & Documents)** | Yes | Slow |
| **Local Data Persistence** | **Yes (SQLite Room DB)** | Cloud Sync / Local Cache | None |
| **Payload Encryption** | **AES-256 Local Cipher** | TLS / E2EE | Transport Only |
| **Minimum Supported Android Version** | **Android 7.0 (API 24)** | Varies | Varies |

---

## 🔄 How It Works

```
┌─────────────────┐       ┌──────────────────┐       ┌──────────────────┐
│  Peer Discovery │ ────► │ Connection Handshake │ ──► │ AES-256 Cipher   │
│  (BLE / Wi-Fi)  │       │ (P2P_STAR Strategy)  │       │ (SecurityHelper) │
└─────────────────┘       └──────────────────┘       └──────────────────┘
                                                              │
┌─────────────────┐       ┌──────────────────┐                │
│ SQLite Storage  │ ◄──── │ Payload Handler  │ ◄──────────────┘
│ (Room Database) │       │ (Bytes / Streams)│
└─────────────────┘       └──────────────────┘
```

1. **Discovery & Auto-Connection Engine**:
   - Devices periodically toggle roles between **Advertising** (Host) and **Scanning** (Client) every 6–10 seconds using a background loop handler (`roleSwitchRunnable`).
   - Alternatively, users can launch **Manual Pairing Mode** to select specific discovered endpoints from an interactive modal dialog.
2. **Connection Handshake**:
   - Nearby Connections initializes a high-performance `P2P_STAR` topology connection.
   - Credentials and endpoint IDs are exchanged automatically.
3. **Payload Processing & Serialization**:
   - Text payloads are encrypted via `SecurityHelper` (AES-256-CBC) prior to transmission.
   - Image and Document files are streamed via `Payload.fromFile` / `Payload.fromStream`.
4. **Resilient Local Persistence**:
   - Messages sent while disconnected are assigned `isSent = false` in the local Room database (`MessageEntity`).
   - Once a P2P connection with the targeted peer is re-established, queued messages are automatically flushed.

---

## 🏗️ Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        NexorA Android App                              │
├────────────────────────────────────────────────────────────────────────┤
│  UI Layer (Material Design 3 ViewBinding Architecture)                 │
│  ├── MainActivity (4-Tab Navigation Surface)                           │
│  ├── RecyclerView Adapters (PeerAdapter, ChatAdapter, AlertAdapter)   │
│  └── Custom Dialogs (PairingMode, AlertCreation)                       │
├────────────────────────────────────────────────────────────────────────┤
│  Logic & Security Layer                                                │
│  ├── SecurityHelper (AES-256-CBC Encryption Engine)                    │
│  └── UserManager (Identity & Preference State)                         │
├────────────────────────────────────────────────────────────────────────┤
│  Persistence Layer (Android Room DB)                                   │
│  ├── AppDatabase                                                       │
│  ├── Entities: MessageEntity, PeerEntity, AlertEntity                 │
│  └── DAOs: MessageDao, PeerDao, AlertDao                              │
├────────────────────────────────────────────────────────────────────────┤
│  Connectivity & Radio Layer (Google Play Services)                     │
│  ├── Nearby Connections API (Strategy: P2P_STAR)                       │
│  ├── Location Services API (GPS / BLE Scanning Enforcer)              │
│  └── Hardware Transceivers (Wi-Fi Direct / Wi-Fi Aware / BLE)          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🌐 Network Topology & Technology

### Single-Hop P2P_STAR Strategy

NexorA currently employs a **Single-Hop Star Topology** provided by Google Nearby Connections:

```
                  ┌──────────────────────┐
                  │    Host / Advertiser │
                  │  (Central P2P Node)  │
                  └──────────┬───────────┘
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
┌──────────────────────┐           ┌──────────────────────┐
│   Client / Discoverer│           │   Client / Discoverer│
│       (Peer A)       │           │       (Peer B)       │
└──────────────────────┘           └──────────────────────┘
```

- **Host Node**: Accepts incoming P2P connections and coordinates bandwidth allocation.
- **Client Node**: Scans for active advertisements and requests explicit connection pairing.
- **Direct Transmission**: Communication occurs directly between connected nodes over local radio channels without intermediate multi-hop routing.

---

## 🔒 Security Architecture

NexorA applies local cryptographic protection to all transmitted text payloads:

```
Plaintext Message ──► [ SecurityHelper.encrypt() ] ──► Base64 Ciphertext ──► P2P Radio Transfer
                                                                                   │
Plaintext Display ◄── [ SecurityHelper.decrypt() ] ◄── Base64 Ciphertext ◄────────┘
```

- **Algorithm**: `AES/CBC/PKCS5Padding` (AES-256).
- **Encryption Scope**: Text payload bytes are encrypted on the sending device before being passed to Nearby Connections `Payload.fromBytes()`.
- **Decryption Scope**: Received bytes are decrypted locally prior to insertion into the Room database and `ChatAdapter` UI rendering.
- **File Transfer Security**: Files are transferred via raw binary streams (`Payload.Type.FILE` / `Payload.Type.STREAM`) and stored directly into Android `Downloads` using system `MediaStore` APIs.

---

## 📱 Interface Architecture

NexorA features a persistent 4-tab operational navigation layout:

| Tab | Functionality |
| :--- | :--- |
| 1. **Home** | Network Command Surface displaying real-time connectivity state (`● LIVE` / `● SEARCHING`), active peer counters, recent communication timeline, and quick-pairing triggers. |
| 2. **Messages** | Contact list with online status indicators and multi-view chat room supporting Text messages, Document cards (`DOCUMENT`), and Image thumbnail previews. |
| 3. **Alerts** | Operational event log storing network lifecycle events (`CONNECTION`, `DISCONNECTION`, `TRANSFER`, `QUEUED`, `DISCOVERY`) in Room DB across application restarts. |
| 4. **Settings** | Configuration hub (`PROFILE`, `NETWORK`, `STORAGE`, `ABOUT`) with inline user identity customization. |

---

## 🛠️ Tech Stack & Dependencies

| Category | Component / Library | Version |
| :--- | :--- | :--- |
| **Language** | Kotlin | `2.0.21` |
| **Build Tooling** | Android Gradle Plugin (AGP) / Gradle | `8.13.2` |
| **Target / Compile SDK** | Android SDK | `API 36` |
| **Minimum Supported SDK** | Android SDK | `API 24` (Android 7.0 Nougat) |
| **P2P Networking** | Google Play Services Nearby Connections | `19.3.0` |
| **Location Services** | Google Play Services Location | `21.0.1` |
| **Local Database** | Android Room DB | `2.7.0-alpha13` |
| **UI Framework** | Material Design 3 / AndroidX AppCompat | `1.13.0` / `1.7.1` |
| **Concurrency** | Kotlin Coroutines & Lifecycle KTX | `2.11.0` |

---

## 📁 Repository Structure

```
NexorA/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/fury/peerconnect/
│   │   │   │   ├── data/          # Room Database (AppDatabase, Entities, DAOs)
│   │   │   │   ├── logic/         # Encryption (SecurityHelper), User State (UserManager)
│   │   │   │   └── ui/            # MainActivity, RecyclerView Adapters, Dialogs
│   │   │   ├── res/               # Layout XMLs, Color Tokens, Vector Drawables, Menus
│   │   │   └── AndroidManifest.xml # Permissions (BLE, Wi-Fi Direct, Location)
│   │   └── test/                  # Unit Tests
│   └── build.gradle.kts           # App-level Dependencies & SDK Specifications
├── gradle/                        # Version Catalog (libs.versions.toml) & Wrapper
├── build.gradle.kts               # Root Build Script
├── settings.gradle.kts            # Gradle Project Configuration
├── LICENSE                        # MIT License
└── README.md                      # Project Documentation
```

---

## 🚀 Getting Started

### Prerequisites

1. **Android Studio**: Android Studio Ladybug (2024.2.1) or newer.
2. **Java Development Kit (JDK)**: JDK 11 or higher configured in Gradle.
3. **Physical Android Devices**: Minimum **2 physical Android devices** running Android 7.0 (API 24) or higher with Bluetooth and Wi-Fi enabled.
   > *Note: Android Emulators cannot emulate physical BLE / Wi-Fi Direct radio hardware.*

### Installation & Build

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/prem-000/NexorA.git
   cd NexorA
   ```

2. **Open in Android Studio**:
   - Open Android Studio and choose **Open an Existing Project**.
   - Select the `NexorA` root folder.
   - Allow Gradle sync to complete automatically.

3. **Deploy to Devices**:
   - Connect two Android devices via USB debugging or Wireless Debugging.
   - Select Device 1 and click **Run 'app'**.
   - Select Device 2 and click **Run 'app'**.

---

## 🧪 Testing P2P Connectivity

```
[ Device 1 ]                                                [ Device 2 ]
  │                                                           │
  ├─ 1. Launch NexorA App                                     ├─ 1. Launch NexorA App
  ├─ 2. Grant Location & Nearby Permissions                   ├─ 2. Grant Location & Nearby Permissions
  │                                                           │
  ├─── (Automatic 6-10s Role Switch or Manual Pairing) ───────┤
  │                                                           │
  ├─ 3. Peer Discovered & Connected (LIVE Status)             ├─ 3. Peer Discovered & Connected (LIVE Status)
  ├─ 4. Send Message / Share Image                            ├─ 4. Receive Encrypted Message / File
  │                                                           │
```

1. **Permissions Setup**: Upon first launch, grant **Location Services**, **Bluetooth**, and **Nearby Devices** permissions on both devices.
2. **Automatic Pairing**: Keep both devices in close proximity (within 5 meters). The auto-connect engine will discover and pair the devices automatically within 10–20 seconds.
3. **Manual Pairing**: Tap **+ ADD NEW CONTACT** on the Home tab to open the explicit device discovery dialog, then tap a peer to initiate direct connection.
4. **Exchanging Data**: Open a conversation thread in the **Messages** tab, type a message or select a file/image attachment, and verify instantaneous local delivery.

---

## ⚠️ Current Limitations

- **Physical Hardware Requirement**: Cannot be fully tested on Android Studio Emulators due to radio hardware virtualization limits.
- **Single-Hop Radius**: Operates as a direct single-hop `P2P_STAR` network; multi-hop mesh message forwarding is not currently implemented.
- **Location Permission Requirement**: Android OS requires active Location permissions and GPS toggles to perform BLE and Wi-Fi Direct discovery scans.
- **Line-of-Sight Range**: Connection quality and bandwidth are constrained by physical obstacles (walls, interference) and device radio power.

---

## 🗺️ Roadmap

- [x] **Direct P2P Messaging** (Google Nearby Connections `P2P_STAR`)
- [x] **Local Text Payload Encryption** (AES-256-CBC)
- [x] **Binary Stream File & Image Transfer** (`MediaStore` integration)
- [x] **Offline Message Queuing** (SQLite Room DB persistence)
- [x] **Automatic & Manual Discovery Engine** (Role toggle loop + Selection dialog)
- [ ] **Multi-Hop Mesh Message Routing**
- [ ] **Dynamic Key Exchange** (Diffie-Hellman Key Agreement)
- [ ] **Group P2P Chat Rooms**

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

```text
Copyright (c) 2026 Prem
```
