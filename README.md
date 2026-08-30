<div align="center">

# NexorA 📱🔗

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple?style=for-the-badge&logo=kotlin)
![Platform](https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android)
![API](https://img.shields.io/badge/API-Google_Nearby_Connections-blue?style=for-the-badge&logo=google)
![Topology](https://img.shields.io/badge/Topology-Single--Hop%20P2P_STAR-purple?style=for-the-badge)

</div>

> **Serverless. Private. Resilient. Offline P2P Communication.**

**NexorA** is an offline-first, peer-to-peer (P2P) messaging and file-sharing Android application. It enables devices to create a local peer network via **Wi-Fi Direct/Aware** and **Bluetooth Low Energy (BLE)**, allowing for secure chat and file transfer without an internet connection, cellular data, or a centralized server.

---

## 🌟 Key Features

### 📡 Network Engine & Discovery
* **100% Offline:** Operates entirely on device radio hardware (Bluetooth/Wi-Fi).
* **Auto-Connect Engine:** Toggles roles (Advertising/Host vs. Scanning/Client) every 6–10 seconds to detect and auto-reconnect known contacts.
* **Manual Pairing Mode:** Explicit device scanning and discovery presented in an interactive selection dialog to prevent unwanted connections.
* **Location Requirements Enforcer:** Automatically manages Android location permissions and GPS settings required for BLE/Wi-Fi Direct scanning.

### 💬 Messaging & Resiliency
* **End-to-End Encryption:** Messages are AES-256 encrypted locally (`SecurityHelper`) before transmission.
* **Offline Resiliency & Queuing:** Messages sent while disconnected are stored in local SQLite via Room (`isSent = false`) and auto-flushed upon reconnection.
* **File & Image Sharing:** Supports binary stream transfer for images and documents. Files are automatically saved to system `Downloads` using the `MediaStore` API.

---

## 🎨 NexorA UI/UX Architecture

NexorA features a deliberate 4-tab persistent navigation interface:

1. **Home (Network Command Surface)**: Operational overview featuring real-time connection status (`● LIVE` / `● SEARCHING`), active peer counters (`01` Active, `04` Known), recent communication timeline, and quick actions (`+ ADD NEW CONTACT`).
2. **Messages**: Contact list with online/offline indicators and multi-view type chat room supporting Text, Document cards (`DOCUMENT`), and Image thumbnail previews.
3. **Alerts**: Operational event stream timeline storing network events (`CONNECTION`, `DISCONNECTION`, `TRANSFER`, `QUEUED`, `DISCOVERY`) in Room DB across app restarts.
4. **Settings**: Structured section list (`PROFILE`, `NETWORK`, `STORAGE`, `ABOUT`) with inline identity editing.

---

## 🛠️ Tech Stack & Architecture

* **Language:** Kotlin (Target SDK 36, Min SDK 24)
* **Connectivity:** Google Nearby Connections API (Strategy: `P2P_STAR`)
* **Database:** Android Room Database (`androidx.room:room-runtime:2.7.0-alpha13`)
* **Concurrency:** Kotlin Coroutines & `lifecycleScope`
* **Security:** AES-256-CBC Encryption (`SecurityHelper`)
* **UI Architecture:** Single Activity with Persistent Bottom Navigation, `RecyclerView`, Material 3

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

Copyright (c) 2026 Prem

