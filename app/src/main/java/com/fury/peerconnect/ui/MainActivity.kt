package com.fury.peerconnect.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fury.peerconnect.R
import com.fury.peerconnect.data.AlertEntity
import com.fury.peerconnect.data.AppDatabase
import com.fury.peerconnect.data.ChatMessage
import com.fury.peerconnect.data.MessageEntity
import com.fury.peerconnect.data.PeerEntity
import com.fury.peerconnect.logic.SecurityHelper
import com.fury.peerconnect.logic.UserManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class MainActivity : AppCompatActivity() {

    // --- CONFIGURATION ---
    private val STRATEGY = Strategy.P2P_STAR
    private val SERVICE_ID = "com.fury.peerconnect_v2"
    private val TAG = "NexoraDebug"

    // --- STATE VARIABLES ---
    private var isPairingMode = false
    private var isHost = false
    private var myNickName: String = ""

    private var isConnected = false

    // CHAT STATE
    private var currentChatPeerName: String? = null
    private var currentChatEndpointId: String? = null

    // Track discovered devices for manual selection
    private val discoveredEndpoints = mutableMapOf<String, String>()
    private var selectionDialog: AlertDialog? = null

    // TRACKING
    private val pendingConnections = mutableMapOf<String, String>()
    private val pendingPayloads = mutableMapOf<Long, Long>()

    // Track active file transfers
    private val incomingFilePayloads = mutableMapOf<Long, Payload>()

    // Deterministic payload tracking maps
    private data class PendingFileMetadata(
        val messageId: Long? = null,
        val alertId: String? = null,
        val fileName: String,
        val senderName: String,
        val endpointId: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val pendingFileMetadataMap = java.util.Collections.synchronizedMap(mutableMapOf<Long, PendingFileMetadata>())

    private data class CompletedReceivedFile(
        val endpointId: String,
        val file: File,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val pendingCompletedFilesMap = java.util.Collections.synchronizedMap(mutableMapOf<Long, CompletedReceivedFile>())

    // AUTO-LOOP HANDLER
    private val handler = Handler(Looper.getMainLooper())
    private val roleSwitchRunnable = Runnable { switchRoles() }

    // Track internal delayed runnable
    private var pendingRadioSwitch: Runnable? = null

    // --- DATA & ADAPTERS ---
    private lateinit var db: AppDatabase
    private lateinit var peerAdapter: PeerAdapter
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var alertAdapter: AlertAdapter
    private lateinit var recentActivityAdapter: RecentActivityAdapter

    // --- UI ELEMENTS ---
    private lateinit var statusText: TextView
    private lateinit var chatStatusText: TextView
    private lateinit var btnAddContact: Button

    private lateinit var layoutConnection: View
    private lateinit var layoutDashboard: View
    private lateinit var layoutAlerts: View
    private lateinit var layoutSettings: View
    private lateinit var layoutChat: ConstraintLayout
    private lateinit var bottomNavigation: BottomNavigationView

    // Dashboard UI
    private lateinit var dashConnStatus: TextView
    private lateinit var dashConnDesc: TextView
    private lateinit var dashActiveCount: TextView
    private lateinit var dashKnownCount: TextView
    private lateinit var dashRecentRecyclerView: RecyclerView
    private lateinit var dashEmptyRecentText: TextView
    private lateinit var btnDashAddContact: Button
    private lateinit var btnDashSendSos: Button

    // Alerts & Alert Thread UI
    private lateinit var alertsRecyclerView: RecyclerView
    private lateinit var btnClearAlerts: Button
    private lateinit var btnCreateAlert: Button
    private lateinit var textEmptyAlerts: View

    private lateinit var layoutAlertThread: ConstraintLayout
    private lateinit var alertThreadHeader: TextView
    private lateinit var alertThreadSenderText: TextView
    private lateinit var btnExitAlertThread: Button
    private lateinit var alertThreadDescription: TextView
    private lateinit var alertThreadAttachmentCard: View
    private lateinit var alertThreadAttachmentName: TextView
    private lateinit var alertThreadAttachmentImage: ImageView
    private lateinit var alertThreadRecyclerView: RecyclerView
    private lateinit var editAlertThreadReply: EditText
    private lateinit var btnSendAlertThreadReply: Button
    private lateinit var alertThreadAdapter: ChatAdapter

    private var currentAlertThreadId: String? = null
    private var pendingAlertAttachmentUri: Uri? = null
    private var pendingAlertAttachmentName: String? = null
    private var pendingAlertFileNameText: TextView? = null

    // Settings UI
    private lateinit var settingsDisplayName: TextView
    private lateinit var btnEditIdentity: Button

    private lateinit var peersRecyclerView: RecyclerView
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnAttach: Button
    private lateinit var btnExitChat: Button

    // --- PERMISSIONS ---
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            Toast.makeText(this, "Permissions Granted!", Toast.LENGTH_SHORT).show()
            checkLocationAndRun {
                resetRadio()
                startAutoMode()
            }
        } else {
            Toast.makeText(this, "Permissions Denied. App won't work.", Toast.LENGTH_LONG).show()
        }
    }

    // --- LOCATION ENFORCER ---
    private val locationResolutionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Location Enabled!", Toast.LENGTH_SHORT).show()
            resetRadio()
            startAutoMode()
        } else {
            updateStatus("Error: Location is Required!")
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val uri = result.data?.data
            if (uri != null) {
                val fileName = getFileNameFromUri(uri) ?: "Unknown_File"
                sendFile(uri, fileName)
            }
        }
    }

    private val alertFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val uri = result.data?.data
            if (uri != null) {
                pendingAlertAttachmentUri = uri
                pendingAlertAttachmentName = getFileNameFromUri(uri) ?: "Attached_File"
                pendingAlertFileNameText?.text = pendingAlertAttachmentName
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val mainHeaderCard = findViewById<View>(R.id.mainHeaderCard)
        if (mainHeaderCard != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainHeaderCard) { view, insets ->
                val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.setPadding(0, statusBarHeight, 0, 0)
                insets
            }
        }

        db = AppDatabase.getDatabase(this)

        lifecycleScope.launch(Dispatchers.IO) {
            db.peerDao().setAllOffline()
            loadPeersFromDb()
            loadAlertsFromDb()
            loadRecentActivityFromDb()
        }

        // Initialize UI Views
        statusText = findViewById(R.id.statusText)
        chatStatusText = findViewById(R.id.chatStatusText)
        btnAddContact = findViewById(R.id.btnHost)

        layoutDashboard = findViewById(R.id.layoutDashboard)
        layoutConnection = findViewById(R.id.layoutConnection)
        layoutAlerts = findViewById(R.id.layoutAlerts)
        layoutSettings = findViewById(R.id.layoutSettings)
        layoutChat = findViewById(R.id.layoutChat)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        btnExitChat = findViewById(R.id.btnExitChat)

        // Dashboard Elements
        dashConnStatus = findViewById(R.id.dashConnStatus)
        dashConnDesc = findViewById(R.id.dashConnDesc)
        dashActiveCount = findViewById(R.id.dashActiveCount)
        dashKnownCount = findViewById(R.id.dashKnownCount)
        dashRecentRecyclerView = findViewById(R.id.dashRecentRecyclerView)
        dashEmptyRecentText = findViewById(R.id.dashEmptyRecentText)
        btnDashAddContact = findViewById(R.id.btnDashAddContact)
        btnDashSendSos = findViewById(R.id.btnDashSendSos)

        // Alerts Elements
        alertsRecyclerView = findViewById(R.id.alertsRecyclerView)
        btnClearAlerts = findViewById(R.id.btnClearAlerts)
        btnCreateAlert = findViewById(R.id.btnCreateAlert)
        textEmptyAlerts = findViewById(R.id.textEmptyAlerts)

        // Alert Thread Elements
        layoutAlertThread = findViewById(R.id.layoutAlertThread)
        alertThreadHeader = findViewById(R.id.alertThreadHeader)
        alertThreadSenderText = findViewById(R.id.alertThreadSenderText)
        btnExitAlertThread = findViewById(R.id.btnExitAlertThread)
        alertThreadDescription = findViewById(R.id.alertThreadDescription)
        alertThreadAttachmentCard = findViewById(R.id.alertThreadAttachmentCard)
        alertThreadAttachmentName = findViewById(R.id.alertThreadAttachmentName)
        alertThreadAttachmentImage = findViewById(R.id.alertThreadAttachmentImage)
        alertThreadRecyclerView = findViewById(R.id.alertThreadRecyclerView)
        alertThreadRecyclerView.layoutManager = LinearLayoutManager(this)
        editAlertThreadReply = findViewById(R.id.editAlertThreadReply)
        btnSendAlertThreadReply = findViewById(R.id.btnSendAlertThreadReply)

        // Settings Elements
        settingsDisplayName = findViewById(R.id.settingsDisplayName)
        btnEditIdentity = findViewById(R.id.btnEditIdentity)

        peersRecyclerView = findViewById(R.id.peersRecyclerView)
        peersRecyclerView.layoutManager = LinearLayoutManager(this)

        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        chatRecyclerView.layoutManager = LinearLayoutManager(this)

        dashRecentRecyclerView.layoutManager = LinearLayoutManager(this)
        alertsRecyclerView.layoutManager = LinearLayoutManager(this)

        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)
        btnAttach = findViewById(R.id.btnAttach)

        val appBarCard = findViewById<View>(R.id.appBarCard)
        if (appBarCard != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBarCard) { view, insets ->
                val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.setPadding(view.paddingLeft, statusBarHeight, view.paddingRight, view.paddingBottom)
                insets
            }
        }

        val alertThreadAppBar = findViewById<View>(R.id.alertThreadAppBar)
        if (alertThreadAppBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(alertThreadAppBar) { view, insets ->
                val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.setPadding(view.paddingLeft, statusBarHeight, view.paddingRight, view.paddingBottom)
                insets
            }
        }

        if (bottomNavigation != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation) { view, insets ->
                val navBarsHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBarsHeight)
                insets
            }
        }

        val inputCard = findViewById<View>(R.id.inputCard)
        if (layoutChat != null && inputCard != null) {
            ViewCompat.setOnApplyWindowInsetsListener(layoutChat) { _, insets ->
                val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val navBarsHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val bottomMargin = if (imeHeight > 0) imeHeight else navBarsHeight
                val params = inputCard.layoutParams as android.view.ViewGroup.MarginLayoutParams
                val baseMargin = (12 * resources.displayMetrics.density).toInt()
                params.bottomMargin = bottomMargin + baseMargin
                inputCard.layoutParams = params

                if (imeHeight > 0 && ::chatAdapter.isInitialized && chatAdapter.itemCount > 0) {
                    chatRecyclerView.post { chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1) }
                }
                insets
            }
        }

        val alertThreadInputCard = findViewById<View>(R.id.alertThreadInputCard)
        if (layoutAlertThread != null && alertThreadInputCard != null) {
            ViewCompat.setOnApplyWindowInsetsListener(layoutAlertThread) { _, insets ->
                val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val navBarsHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val bottomMargin = if (imeHeight > 0) imeHeight else navBarsHeight
                val params = alertThreadInputCard.layoutParams as android.view.ViewGroup.MarginLayoutParams
                val baseMargin = (12 * resources.displayMetrics.density).toInt()
                params.bottomMargin = bottomMargin + baseMargin
                alertThreadInputCard.layoutParams = params

                if (imeHeight > 0 && ::alertThreadAdapter.isInitialized && alertThreadAdapter.itemCount > 0) {
                    alertThreadRecyclerView.post { alertThreadRecyclerView.scrollToPosition(alertThreadAdapter.itemCount - 1) }
                }
                insets
            }
        }

        // Adapters
        alertAdapter = AlertAdapter { alert ->
            openAlertThread(alert)
        }
        alertsRecyclerView.adapter = alertAdapter

        btnCreateAlert.setOnClickListener { showCreateAlertDialog() }
        btnDashSendSos.setOnClickListener { showCreateAlertDialog() }
        btnExitAlertThread.setOnClickListener { closeAlertThread() }
        btnSendAlertThreadReply.setOnClickListener { sendAlertThreadReply() }

        recentActivityAdapter = RecentActivityAdapter { peerName ->
            openChat(peerName, null)
        }
        dashRecentRecyclerView.adapter = recentActivityAdapter

        peerAdapter = PeerAdapter { peer ->
            openChat(peer.name, if (peer.isOnline) peer.endpointId else null)
        }
        peersRecyclerView.adapter = peerAdapter

        checkIdentity()

        // Persistent Bottom Navigation Handler
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showTab(layoutDashboard)
                    loadRecentActivityFromDb()
                    true
                }
                R.id.nav_messages -> {
                    showTab(layoutConnection)
                    loadPeersFromDb()
                    true
                }
                R.id.nav_alerts -> {
                    showTab(layoutAlerts)
                    loadAlertsFromDb()
                    true
                }
                R.id.nav_settings -> {
                    showTab(layoutSettings)
                    updateSettingsUI()
                    true
                }
                else -> false
            }
        }

        // Add Contact triggers existing pairing dialog safely
        val addContactClickListener = View.OnClickListener {
            checkLocationAndRun {
                if (isPairingMode) {
                    isPairingMode = false
                    btnAddContact.text = "+ Add New Contact"
                    btnAddContact.setBackgroundColor(Color.parseColor("#6D28D9"))
                    startAutoMode()
                } else {
                    showPairingDialog()
                }
            }
        }
        btnAddContact.setOnClickListener(addContactClickListener)
        btnDashAddContact.setOnClickListener(addContactClickListener)

        btnClearAlerts.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                db.alertDao().clearAlerts()
                loadAlertsFromDb()
            }
        }

        btnEditIdentity.setOnClickListener {
            showNameInputDialog()
        }

        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            if (text.isNotEmpty()) sendMessage(text)
        }

        btnAttach.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            filePickerLauncher.launch(intent)
        }

        btnExitChat.setOnClickListener { closeChat() }

        Nearby.getConnectionsClient(this).stopAllEndpoints()

        // Startup Logic
        if (hasPermissions()) {
            checkLocationAndRun {
                resetRadio()
                startAutoMode()
            }
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun showTab(activeLayout: View) {
        layoutDashboard.visibility = if (activeLayout == layoutDashboard) View.VISIBLE else View.GONE
        layoutConnection.visibility = if (activeLayout == layoutConnection) View.VISIBLE else View.GONE
        layoutAlerts.visibility = if (activeLayout == layoutAlerts) View.VISIBLE else View.GONE
        layoutSettings.visibility = if (activeLayout == layoutSettings) View.VISIBLE else View.GONE
    }

    // --- NAVIGATION ---
    private fun openChat(peerName: String, endpointId: String?) {
        currentChatPeerName = peerName
        currentChatEndpointId = endpointId
        lifecycleScope.launch(Dispatchers.IO) {
            val history = db.messageDao().getChatHistory(myNickName, peerName)
            withContext(Dispatchers.Main) { updateChatUI(history) }
        }
        layoutChat.visibility = View.VISIBLE
        updateStatus(if (endpointId != null) "CONNECTED" else "OFFLINE")
        findViewById<TextView>(R.id.chatHeader).text = peerName
    }

    private fun closeChat() {
        currentChatPeerName = null
        currentChatEndpointId = null
        layoutChat.visibility = View.GONE
        updateStatus("AUTO: DISCOVERING")
    }

    // --- ENGINE: AUTO-CONNECT LOOP ---
    private fun startAutoMode() {
        handler.removeCallbacks(roleSwitchRunnable)
        switchRoles()
    }

    private fun switchRoles() {
        if (currentChatEndpointId != null || isConnected) return

        resetRadio()
        isHost = !isHost

        pendingRadioSwitch = Runnable {
            if (!isConnected) {
                if (isHost) startAdvertising() else startDiscovery()
            }
        }
        handler.postDelayed(pendingRadioSwitch!!, 600)

        val randomDelay = (6000..10000).random().toLong()
        handler.postDelayed(roleSwitchRunnable, randomDelay)
    }

    private fun resetRadio() {
        if (!hasPermissions()) return
        Nearby.getConnectionsClient(this).stopAdvertising()
        Nearby.getConnectionsClient(this).stopDiscovery()
        if (!isConnected) {
            Nearby.getConnectionsClient(this).stopAllEndpoints()
        }
        if (pendingRadioSwitch != null) {
            handler.removeCallbacks(pendingRadioSwitch!!)
        }
    }

    private fun startAdvertising() {
        if (!hasPermissions()) return
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).setLowPower(false).build()
        Nearby.getConnectionsClient(this)
            .startAdvertising(myNickName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
            .addOnSuccessListener { updateStatus("AUTO: ADVERTISING") }
            .addOnFailureListener { e -> Log.e(TAG, "Adv fail", e) }
    }

    private fun startDiscovery() {
        if (!hasPermissions()) return
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(this)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { updateStatus("AUTO: DISCOVERING") }
            .addOnFailureListener { e -> Log.e(TAG, "Disc fail", e) }
    }

    // --- DISCONNECTION HELPER ---
    private fun handleExplicitDisconnect(endpointId: String) {
        if (!pendingConnections.containsKey(endpointId) && currentChatEndpointId != endpointId) return

        isConnected = false

        runOnUiThread {
            if (endpointId == currentChatEndpointId) {
                currentChatEndpointId = null
                updateStatus("OFFLINE")
            }
        }

        pendingConnections.remove(endpointId)
        Nearby.getConnectionsClient(this).disconnectFromEndpoint(endpointId)

        lifecycleScope.launch(Dispatchers.IO) {
            val allPeers = db.peerDao().getAllPeers()
            val disconnectedPeer = allPeers.find { it.endpointId == endpointId }

            if (disconnectedPeer != null) {
                db.peerDao().insertPeer(disconnectedPeer.copy(isOnline = false))
                emitAlert("DISCONNECTION", "NETWORK", "Connection with ${disconnectedPeer.name} was lost", disconnectedPeer.name)
            } else {
                emitAlert("DISCONNECTION", "NETWORK", "Connection with peer was lost")
            }

            loadPeersFromDb()
        }

        startAutoMode()
    }

    // --- CALLBACKS ---
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val foundName = info.endpointName

            lifecycleScope.launch(Dispatchers.IO) {
                val isKnown = db.peerDao().isKnownPeer(foundName)

                withContext(Dispatchers.Main) {
                    if (isKnown) {
                        emitAlert("DISCOVERY", "DEVICE DETECTED", "Known peer $foundName is nearby", foundName)
                        Nearby.getConnectionsClient(this@MainActivity)
                            .requestConnection(myNickName, endpointId, connectionLifecycleCallback)
                        handler.removeCallbacks(roleSwitchRunnable)
                    } else if (isPairingMode) {
                        if (!discoveredEndpoints.containsKey(endpointId)) {
                            discoveredEndpoints[endpointId] = foundName
                            showDeviceSelectionDialog()
                        }
                    }
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            if (isPairingMode) {
                discoveredEndpoints.remove(endpointId)
                showDeviceSelectionDialog()
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val incomingName = info.endpointName
            pendingConnections[endpointId] = incomingName
            Nearby.getConnectionsClient(this@MainActivity).acceptConnection(endpointId, payloadCallback)
            lifecycleScope.launch(Dispatchers.IO) {
                val isKnown = db.peerDao().isKnownPeer(incomingName)
                if (!isKnown && !isPairingMode) {
                    Nearby.getConnectionsClient(this@MainActivity).disconnectFromEndpoint(endpointId)
                } else {
                    handler.removeCallbacks(roleSwitchRunnable)
                }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val peerName = pendingConnections[endpointId] ?: return

            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                isConnected = true
                handler.removeCallbacks(roleSwitchRunnable)
                if (pendingRadioSwitch != null) handler.removeCallbacks(pendingRadioSwitch!!)

                if (isPairingMode) {
                    isPairingMode = false
                    runOnUiThread {
                        btnAddContact.text = "+ Add New Contact"
                        btnAddContact.setBackgroundColor(Color.parseColor("#6D28D9"))
                    }
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    db.peerDao().insertPeer(
                        PeerEntity(
                            name = peerName,
                            endpointId = endpointId,
                            lastSeenTimestamp = System.currentTimeMillis(),
                            isOnline = true
                        )
                    )

                    emitAlert("CONNECTION", "CONNECTED", "$peerName is now connected", peerName)

                    val updatedPeers = db.peerDao().getAllPeers()

                    withContext(Dispatchers.Main) {
                        if (::peerAdapter.isInitialized) {
                            peerAdapter.updateList(updatedPeers)
                        }

                        if (currentChatPeerName == peerName) {
                            currentChatEndpointId = endpointId
                            updateStatus("CONNECTED")
                        }
                    }

                    // Handle Offline Messages
                    val pendingMsgs = db.messageDao().getUnsentMessages(peerName)
                    if (pendingMsgs.isNotEmpty()) {
                        withContext(Dispatchers.Main) { updateStatus("Sending ${pendingMsgs.size} offline messages...") }
                        for (msg in pendingMsgs) {
                            val encryptedText = SecurityHelper.encrypt(msg.text)
                            val chatPayload = ChatMessage(myNickName, encryptedText, msg.timestamp)

                            try {
                                val payload = Payload.fromBytes(serialize(chatPayload))
                                pendingPayloads[payload.id] = msg.id.toLong()
                                Nearby.getConnectionsClient(this@MainActivity).sendPayload(endpointId, payload)
                                    .addOnFailureListener { pendingPayloads.remove(payload.id) }
                            } catch (e: Exception) {
                                Log.e(TAG, "Offline Msg Fail", e)
                            }
                        }
                    }
                }

                if (currentChatPeerName == null) {
                    startAutoMode()
                }

            } else {
                isConnected = false
                emitAlert("NETWORK", "CONNECTION REJECTED", "Connection with $peerName failed", peerName)
                startAutoMode()
            }
        }

        override fun onDisconnected(endpointId: String) { handleExplicitDisconnect(endpointId) }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(TAG, "[PAYLOAD_RX] endpointId=$endpointId payloadId=${payload.id} type=${payload.type}")
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val receivedBytes = payload.asBytes()!!
                    try {
                        val msg = deserialize(receivedBytes)
                        lifecycleScope.launch(Dispatchers.IO) {
                            val decryptedBody = SecurityHelper.decrypt(msg.messageBody)
                            when {
                                decryptedBody.startsWith("[ALERT]:") -> {
                                    val alertContent = decryptedBody.removePrefix("[ALERT]:")
                                    val parts = alertContent.split("|")
                                    if (parts.size >= 4) {
                                        val rAlertId = parts[0]
                                        val rSender = parts[1]
                                        val rTitle = parts[2]
                                        val rDesc = parts[3]
                                        val rAttachRaw = if (parts.size > 4) parts.subList(4, parts.size).joinToString("|") else null

                                        var finalAttachPath: String? = null
                                        var filePayloadId: Long? = null

                                        if (!rAttachRaw.isNullOrEmpty()) {
                                            val cleanAttach = when {
                                                rAttachRaw.startsWith("[FILE]:") -> rAttachRaw.removePrefix("[FILE]:")
                                                rAttachRaw.startsWith("📄 Shared a file: ") -> rAttachRaw.removePrefix("📄 Shared a file: ")
                                                rAttachRaw.startsWith("📄 Shared a file:") -> rAttachRaw.removePrefix("📄 Shared a file:")
                                                else -> rAttachRaw
                                            }
                                            val attachParts = cleanAttach.split("|")
                                            val attachFileName = attachParts[0]
                                            val senderPath = if (attachParts.size > 1) attachParts[1] else null
                                            filePayloadId = if (attachParts.size > 2) attachParts[2].toLongOrNull() else null

                                            val completedFile = if (filePayloadId != null) pendingCompletedFilesMap.remove(filePayloadId) else null
                                            if (completedFile != null && completedFile.file.exists()) {
                                                val finalFile = prepareDestinationFile(completedFile.file, attachFileName)
                                                finalAttachPath = "[FILE]:${finalFile.name}|${finalFile.absolutePath}"
                                                emitAlert("TRANSFER", "TRANSFER COMPLETE", "${finalFile.name} received successfully")
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(this@MainActivity, "File Saved!", Toast.LENGTH_LONG).show()
                                                }
                                            } else if (!senderPath.isNullOrEmpty() && File(senderPath).exists()) {
                                                finalAttachPath = "[FILE]:$attachFileName|$senderPath"
                                            } else {
                                                finalAttachPath = "[FILE]:$attachFileName"
                                            }
                                        }

                                        val existing = db.alertDao().getAlertByAlertId(rAlertId)
                                        if (existing == null) {
                                            val newAlert = AlertEntity(
                                                alertId = rAlertId,
                                                type = "SOS",
                                                title = rTitle,
                                                description = rDesc,
                                                peerName = rSender,
                                                timestamp = msg.time,
                                                attachmentPath = finalAttachPath
                                            )
                                            db.alertDao().insertAlert(newAlert)
                                            loadAlertsFromDb()

                                            if (filePayloadId != null && (finalAttachPath == null || !finalAttachPath.contains("|/"))) {
                                                val attachFileName = rAttachRaw?.split("|")?.firstOrNull()?.removePrefix("[FILE]:") ?: "attachment"
                                                pendingFileMetadataMap[filePayloadId] = PendingFileMetadata(
                                                    alertId = rAlertId,
                                                    fileName = attachFileName,
                                                    senderName = rSender,
                                                    endpointId = endpointId
                                                )
                                            }
                                        }
                                    }
                                }
                                decryptedBody.startsWith("[ALERT_REPLY]:") -> {
                                    val replyContent = decryptedBody.removePrefix("[ALERT_REPLY]:")
                                    val parts = replyContent.split("|")
                                    if (parts.size >= 2) {
                                        val rAlertId = parts[0]
                                        val rReplyText = parts.subList(1, parts.size).joinToString("|")

                                        db.messageDao().insertMessage(MessageEntity(
                                            senderId = msg.senderName,
                                            receiverId = myNickName,
                                            text = rReplyText,
                                            timestamp = msg.time,
                                            isSent = true,
                                            alertId = rAlertId
                                        ))

                                        if (currentAlertThreadId == rAlertId) {
                                            val threadMessages = db.messageDao().getAlertThreadMessages(rAlertId)
                                            withContext(Dispatchers.Main) { updateAlertThreadUI(threadMessages) }
                                        }
                                    }
                                }
                                else -> {
                                    val isFileMsg = decryptedBody.startsWith("[FILE]:") || decryptedBody.startsWith("📄 Shared a file:")
                                    val storedBody = if (isFileMsg) {
                                        val cleanBody = when {
                                            decryptedBody.startsWith("[FILE]:") -> decryptedBody.removePrefix("[FILE]:")
                                            decryptedBody.startsWith("📄 Shared a file: ") -> decryptedBody.removePrefix("📄 Shared a file: ")
                                            else -> decryptedBody
                                        }
                                        val parts = cleanBody.split("|")
                                        val fileName = parts[0]
                                        val filePayloadId = if (parts.size > 2) parts[2].toLongOrNull() else null

                                        val now = System.currentTimeMillis()
                                        pendingCompletedFilesMap.values.removeAll { now - it.timestamp > 300_000 }
                                        pendingFileMetadataMap.values.removeAll { now - it.timestamp > 300_000 }

                                        val completedFile = if (filePayloadId != null) pendingCompletedFilesMap.remove(filePayloadId) else null
                                        if (completedFile != null && completedFile.file.exists()) {
                                            val finalFile = prepareDestinationFile(completedFile.file, fileName)
                                            emitAlert("TRANSFER", "TRANSFER COMPLETE", "${finalFile.name} received successfully")
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(this@MainActivity, "File Saved!", Toast.LENGTH_LONG).show()
                                            }
                                            "[FILE]:${finalFile.name}|${finalFile.absolutePath}"
                                        } else {
                                            "[FILE]:$fileName"
                                        }
                                    } else {
                                        decryptedBody
                                    }

                                    val cleanBody = when {
                                        decryptedBody.startsWith("[FILE]:") -> decryptedBody.removePrefix("[FILE]:")
                                        decryptedBody.startsWith("📄 Shared a file: ") -> decryptedBody.removePrefix("📄 Shared a file: ")
                                        else -> decryptedBody
                                    }
                                    val parts = cleanBody.split("|")
                                    val filePayloadId = if (parts.size > 2) parts[2].toLongOrNull() else null

                                    val insertedMsgId = db.messageDao().insertMessage(MessageEntity(
                                        senderId = msg.senderName, receiverId = myNickName,
                                        text = storedBody, timestamp = msg.time, isSent = true
                                    ))

                                    if (isFileMsg && filePayloadId != null && !storedBody.contains("|/")) {
                                        pendingFileMetadataMap[filePayloadId] = PendingFileMetadata(
                                            messageId = insertedMsgId,
                                            fileName = parts[0],
                                            senderName = msg.senderName,
                                            endpointId = endpointId
                                        )
                                    }

                                    loadRecentActivityFromDb()
                                    if (currentChatPeerName == msg.senderName) {
                                        val history = db.messageDao().getChatHistory(myNickName, msg.senderName)
                                        withContext(Dispatchers.Main) { updateChatUI(history) }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Deserialization failed", e) }
                }
                Payload.Type.FILE -> {
                    incomingFilePayloads[payload.id] = payload
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            Log.d(TAG, "[TRANSFER_UPDATE] endpointId=$endpointId payloadId=${update.payloadId} status=${update.status} bytes=${update.bytesTransferred}/${update.totalBytes}")
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val dbMsgId = pendingPayloads[update.payloadId]
                if (dbMsgId != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.messageDao().markAsSent(dbMsgId.toInt())
                        pendingPayloads.remove(update.payloadId)
                        if (pendingPayloads.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                if (currentChatEndpointId == endpointId) {
                                    updateStatus("CONNECTED")
                                }
                            }
                        }
                    }
                }
                val filePayload = incomingFilePayloads.remove(update.payloadId)
                if (filePayload != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val pendingMeta = pendingFileMetadataMap.remove(update.payloadId)
                            val targetName = pendingMeta?.fileName
                            val savedFile = saveReceivedPayloadFile(filePayload, targetName)

                            if (savedFile != null && savedFile.exists()) {
                                val fileName = savedFile.name
                                val savedPath = savedFile.absolutePath

                                if (pendingMeta != null) {
                                    val updatedText = "[FILE]:$fileName|$savedPath"
                                    if (pendingMeta.messageId != null) {
                                        val existingMsg = db.messageDao().getMessageById(pendingMeta.messageId)
                                        if (existingMsg != null) {
                                            db.messageDao().updateMessage(existingMsg.copy(text = updatedText))
                                        }
                                        emitAlert("TRANSFER", "TRANSFER COMPLETE", "$fileName received successfully")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "File Saved!", Toast.LENGTH_LONG).show()
                                            val activeChatPeer = currentChatPeerName
                                            if (activeChatPeer != null && activeChatPeer == pendingMeta.senderName) {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    val updatedHistory = db.messageDao().getChatHistory(myNickName, activeChatPeer)
                                                    withContext(Dispatchers.Main) { updateChatUI(updatedHistory) }
                                                }
                                            }
                                        }
                                    } else if (pendingMeta.alertId != null) {
                                        val existingAlert = db.alertDao().getAlertByAlertId(pendingMeta.alertId)
                                        if (existingAlert != null) {
                                            db.alertDao().updateAlert(existingAlert.copy(attachmentPath = updatedText))
                                        }
                                        emitAlert("TRANSFER", "TRANSFER COMPLETE", "$fileName received successfully")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "Alert File Saved!", Toast.LENGTH_LONG).show()
                                            loadAlertsFromDb()
                                            if (currentAlertThreadId == pendingMeta.alertId && existingAlert != null) {
                                                openAlertThread(existingAlert.copy(attachmentPath = updatedText))
                                            }
                                        }
                                    }
                                } else {
                                    val allFileMessages = db.messageDao().getAllFileMessages()
                                    val candidate = allFileMessages.firstOrNull { msg ->
                                        val clean = when {
                                            msg.text.startsWith("[FILE]:") -> msg.text.removePrefix("[FILE]:")
                                            msg.text.startsWith("📄 Shared a file: ") -> msg.text.removePrefix("📄 Shared a file: ")
                                            else -> msg.text
                                        }
                                        val parts = clean.split("|")
                                        val hasValidLocalFile = parts.size > 1 && File(parts[1]).exists()
                                        !hasValidLocalFile
                                    }
                                    if (candidate != null) {
                                        val updatedText = "[FILE]:$fileName|$savedPath"
                                        db.messageDao().updateMessage(candidate.copy(text = updatedText))
                                        emitAlert("TRANSFER", "TRANSFER COMPLETE", "$fileName received successfully")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@MainActivity, "File Saved!", Toast.LENGTH_LONG).show()
                                            val activeChatPeer = currentChatPeerName
                                            if (activeChatPeer != null && (activeChatPeer == candidate.senderId)) {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    val updatedHistory = db.messageDao().getChatHistory(myNickName, activeChatPeer)
                                                    withContext(Dispatchers.Main) { updateChatUI(updatedHistory) }
                                                }
                                            }
                                        }
                                    } else {
                                        pendingCompletedFilesMap[update.payloadId] = CompletedReceivedFile(endpointId, savedFile)
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "Save Failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) { Log.e(TAG, "File Save Crash", e) }
                    }
                }
            }
        }
    }

    // --- MESSAGING ---
    private fun sendMessage(messageText: String) {
        val peerName = currentChatPeerName ?: return
        val endpointId = currentChatEndpointId
        val canTrySending = endpointId != null

        lifecycleScope.launch(Dispatchers.IO) {
            val msgEntity = MessageEntity(
                senderId = myNickName,
                receiverId = peerName,
                text = messageText,
                timestamp = System.currentTimeMillis(),
                isSent = false
            )
            val newMsgId = db.messageDao().insertMessage(msgEntity)

            val history = db.messageDao().getChatHistory(myNickName, peerName)
            withContext(Dispatchers.Main) { updateChatUI(history) }

            if (canTrySending) {
                val encryptedText = SecurityHelper.encrypt(messageText)
                val chatMessage = ChatMessage(myNickName, encryptedText, System.currentTimeMillis())

                try {
                    val payload = Payload.fromBytes(serialize(chatMessage))
                    pendingPayloads[payload.id] = newMsgId

                    Nearby.getConnectionsClient(this@MainActivity)
                        .sendPayload(endpointId!!, payload)
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Send Payload Failed", e)
                            pendingPayloads.remove(payload.id)

                            launch(Dispatchers.IO) {
                                emitAlert("QUEUED", "MESSAGE QUEUED", "Message waiting for peer connection", peerName)
                            }
                            launch(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Message queued (Offline)", Toast.LENGTH_SHORT).show()
                            }
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Serialization Failed", e)
                }
            } else {
                emitAlert("QUEUED", "MESSAGE QUEUED", "Message waiting for peer connection", peerName)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Saved offline. Will send automatically.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        editMessage.setText("")
    }

    // --- ALERTS EMITTER ---
    private fun emitAlert(type: String, title: String, description: String, peerName: String? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            val alert = AlertEntity(
                type = type,
                title = title,
                description = description,
                peerName = peerName,
                timestamp = System.currentTimeMillis()
            )
            db.alertDao().insertAlert(alert)
            loadAlertsFromDb()
        }
    }

    private fun loadAlertsFromDb() {
        lifecycleScope.launch(Dispatchers.IO) {
            val alerts = db.alertDao().getAllAlerts()
            withContext(Dispatchers.Main) {
                if (::alertAdapter.isInitialized) {
                    alertAdapter.setAlerts(alerts)
                    textEmptyAlerts.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun loadRecentActivityFromDb() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allPeers = db.peerDao().getAllPeers()
            val recentMessages = mutableListOf<ChatMessage>()
            for (peer in allPeers) {
                val history = db.messageDao().getChatHistory(myNickName, peer.name)
                if (history.isNotEmpty()) {
                    val lastMsg = history.last()
                    recentMessages.add(ChatMessage(peer.name, lastMsg.text, lastMsg.timestamp))
                }
            }
            recentMessages.sortByDescending { it.time }
            withContext(Dispatchers.Main) {
                if (::recentActivityAdapter.isInitialized) {
                    recentActivityAdapter.setItems(recentMessages.take(5))
                    dashEmptyRecentText.visibility = if (recentMessages.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun updateSettingsUI() {
        settingsDisplayName.text = myNickName
    }

    // --- HELPERS ---
    private fun checkLocationAndRun(action: () -> Unit) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1000
        ).build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)

        client.checkLocationSettings(builder.build())
            .addOnSuccessListener { action() }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val request = IntentSenderRequest.Builder(exception.resolution).build()
                        locationResolutionLauncher.launch(request)
                    } catch (e: Exception) { }
                }
            }
    }

    private fun showPairingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pairing_mode, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnReceiveHost = dialogView.findViewById<View>(R.id.btnReceiveHost)
        val btnSendJoin = dialogView.findViewById<View>(R.id.btnSendJoin)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancel)

        btnReceiveHost.setOnClickListener {
            dialog.dismiss()
            startManualHost()
        }

        btnSendJoin.setOnClickListener {
            dialog.dismiss()
            startManualJoin()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            startAutoMode()
        }

        dialog.setOnCancelListener {
            startAutoMode()
        }

        dialog.show()
    }

    private fun startManualHost() {
        handler.removeCallbacks(roleSwitchRunnable)
        if (pendingRadioSwitch != null) handler.removeCallbacks(pendingRadioSwitch!!)

        Nearby.getConnectionsClient(this).stopAdvertising()
        Nearby.getConnectionsClient(this).stopDiscovery()
        Nearby.getConnectionsClient(this).stopAllEndpoints()

        isConnected = false
        isPairingMode = true
        isHost = true

        updateStatus("PAIRING MODE")
        btnAddContact.text = "Please Wait..."
        btnAddContact.setBackgroundColor(Color.DKGRAY)

        handler.postDelayed({
            updateStatus("PAIRING MODE")
            btnAddContact.text = "Hosting... (Tap to Cancel)"
            btnAddContact.setBackgroundColor(Color.RED)
            val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).setLowPower(false).build()
            Nearby.getConnectionsClient(this)
                .startAdvertising(myNickName, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Manual Host Failed", e)
                    updateStatus("Error: Radio Failed")
                    btnAddContact.text = "Retry"
                }
        }, 1000)
    }

    private fun startManualJoin() {
        handler.removeCallbacks(roleSwitchRunnable)
        if (pendingRadioSwitch != null) handler.removeCallbacks(pendingRadioSwitch!!)

        Nearby.getConnectionsClient(this).stopAdvertising()
        Nearby.getConnectionsClient(this).stopDiscovery()
        Nearby.getConnectionsClient(this).stopAllEndpoints()

        isConnected = false
        isPairingMode = true
        isHost = false

        discoveredEndpoints.clear()

        updateStatus("PAIRING MODE")
        btnAddContact.text = "Please Wait..."
        btnAddContact.setBackgroundColor(Color.DKGRAY)

        handler.postDelayed({
            updateStatus("PAIRING MODE")
            btnAddContact.text = "Scanning... (Tap to Cancel)"
            btnAddContact.setBackgroundColor(Color.BLUE)
            val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

            Nearby.getConnectionsClient(this)
                .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Manual Join Failed", e)
                    updateStatus("Error: Radio Failed")
                    btnAddContact.text = "Retry"
                }
        }, 1000)
    }

    private fun updateStatus(text: String) {
        val upperText = text.uppercase()

        val isLive = upperText.contains("CONNECTED")
        val isSearching = upperText.contains("DISCOVERING") || upperText.contains("ADVERTISING") || upperText.contains("PAIRING")

        // Clean Header Status Dot Pill Badge
        statusText.text = when {
            isLive -> "● LIVE"
            isSearching -> "● SEARCHING"
            else -> "● OFFLINE"
        }
        statusText.setTextColor(Color.parseColor(when {
            isLive -> "#047857"
            isSearching -> "#B45309"
            else -> "#64748B"
        }))

        // Clean Header Dynamic Subtitle Descriptor
        dashConnDesc.text = when {
            isLive -> if (currentChatPeerName != null) "Connected to $currentChatPeerName" else "Connected to 1 nearby device"
            upperText.contains("PAIRING") -> "Pairing mode active. Select a device to pair."
            upperText.contains("ADVERTISING") -> "Advertising presence to nearby devices"
            else -> "Looking for nearby peers"
        }

        // Chat Header status text
        chatStatusText.text = text
        if (isLive) {
            chatStatusText.setTextColor(Color.parseColor("#10B981"))
        } else {
            chatStatusText.setTextColor(Color.parseColor("#64748B"))
        }

        // Network Command Surface status badge
        if (::dashConnStatus.isInitialized) {
            dashConnStatus.text = when {
                isLive -> "● CONNECTED"
                upperText.contains("PAIRING") -> "● PAIRING MODE"
                upperText.contains("ADVERTISING") -> "● ADVERTISING"
                upperText.contains("DISCOVERING") -> "● DISCOVERING"
                else -> "● OFFLINE"
            }
            dashConnStatus.setTextColor(Color.parseColor(when {
                isLive -> "#10B981"
                isSearching -> "#6D28D9"
                else -> "#64748B"
            }))
        }
    }

    private fun updateChatUI(history: List<MessageEntity>) {
        val chatMessages = history.map { entity ->
            ChatMessage(
                senderName = entity.senderId,
                messageBody = entity.text,
                time = entity.timestamp
            )
        }
        chatAdapter.setMessages(chatMessages)
        if (chatAdapter.itemCount > 0) chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun checkIdentity() {
        val userManager = UserManager(this)
        if (userManager.hasIdentity()) {
            myNickName = userManager.getUsername()!!
            updateStatus("AUTO: DISCOVERING")
            updateSettingsUI()
            chatAdapter = ChatAdapter(myNickName) { fileName, pathOrUri ->
                openAttachment(fileName, pathOrUri)
            }
            chatRecyclerView.adapter = chatAdapter

            alertThreadAdapter = ChatAdapter(myNickName) { fileName, pathOrUri ->
                openAttachment(fileName, pathOrUri)
            }
            alertThreadRecyclerView.adapter = alertThreadAdapter
        } else {
            showNameInputDialog()
        }
    }

    private fun showNameInputDialog() {
        val input = EditText(this)
        input.hint = "Enter your unique ID/Name"
        val dialog = AlertDialog.Builder(this)
            .setTitle("Welcome to Nexora")
            .setView(input).setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString()
                if (name.isNotEmpty()) {
                    UserManager(this).saveUsername(name)
                    checkIdentity()
                } else {
                    showNameInputDialog()
                }
            }.create()
        dialog.show()
    }

    // --- ALERT THREAD & SOS FEATURE ---
    private fun showCreateAlertDialog() {
        pendingAlertAttachmentUri = null
        pendingAlertAttachmentName = null

        val dialogView = layoutInflater.inflate(R.layout.dialog_create_alert, null)
        val editAlertDesc = dialogView.findViewById<EditText>(R.id.editAlertDesc)
        val btnAttachAlertFile = dialogView.findViewById<View>(R.id.btnAttachAlertFile)
        val textAttachedFileName = dialogView.findViewById<TextView>(R.id.textAttachedFileName)
        val btnSendAlert = dialogView.findViewById<View>(R.id.btnSendAlert)
        val btnCancelCreateAlert = dialogView.findViewById<View>(R.id.btnCancelCreateAlert)

        pendingAlertFileNameText = textAttachedFileName

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnAttachAlertFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            alertFilePickerLauncher.launch(intent)
        }

        btnSendAlert.setOnClickListener {
            val desc = editAlertDesc.text.toString().trim()
            if (desc.isEmpty()) {
                Toast.makeText(this, "Please enter an alert description", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            createAndBroadcastAlert(desc, pendingAlertAttachmentUri, pendingAlertAttachmentName)
        }

        btnCancelCreateAlert.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createAndBroadcastAlert(desc: String, uri: Uri?, fileName: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val alertId = "ALERT_" + System.currentTimeMillis()
            var attachmentPathStr: String? = null
            var alertFilePayload: Payload? = null
            var alertFilePayloadId: Long? = null

            if (uri != null && fileName != null) {
                val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val localFile = File(downloadsDir, fileName)
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(localFile).use { output -> input.copyTo(output) }
                    }
                    if (localFile.exists()) {
                        attachmentPathStr = "[FILE]:$fileName|${localFile.absolutePath}"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving alert attachment local file", e)
                }

                try {
                    val pfd = contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val fp = Payload.fromFile(pfd)
                        alertFilePayload = fp
                        alertFilePayloadId = fp.id
                        if (attachmentPathStr != null) {
                            attachmentPathStr = "$attachmentPathStr|$alertFilePayloadId"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating alert binary file payload", e)
                }
            }

            val newAlert = AlertEntity(
                alertId = alertId,
                type = "SOS",
                title = "SOS Alert",
                description = desc,
                peerName = myNickName,
                timestamp = System.currentTimeMillis(),
                attachmentPath = attachmentPathStr
            )
            db.alertDao().insertAlert(newAlert)
            loadAlertsFromDb()

            val alertPayloadStr = if (!attachmentPathStr.isNullOrEmpty()) {
                "[ALERT]:$alertId|$myNickName|SOS Alert|$desc|$attachmentPathStr"
            } else {
                "[ALERT]:$alertId|$myNickName|SOS Alert|$desc"
            }

            val encryptedText = SecurityHelper.encrypt(alertPayloadStr)
            val chatMsg = ChatMessage(myNickName, encryptedText, System.currentTimeMillis())

            try {
                val payloadBytes = serialize(chatMsg)
                val onlinePeers = db.peerDao().getAllPeers().filter { it.isOnline && it.endpointId.isNotEmpty() }
                Log.d(TAG, "[ALERT_TX] connectedPeerCount=${onlinePeers.size}")
                for (peer in onlinePeers) {
                    try {
                        val payload = Payload.fromBytes(payloadBytes)
                        Log.d(TAG, "[ALERT_TX] sending BYTES endpoint=${peer.endpointId} peer=${peer.name}")
                        Nearby.getConnectionsClient(this@MainActivity).sendPayload(peer.endpointId, payload)

                        if (alertFilePayload != null) {
                            Log.d(TAG, "[ALERT_TX] sending FILE payload endpoint=${peer.endpointId} payloadId=$alertFilePayloadId")
                            Nearby.getConnectionsClient(this@MainActivity).sendPayload(peer.endpointId, alertFilePayload)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[ALERT_TX] Error sending to endpoint=${peer.endpointId}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Broadcast alert fail", e)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "SOS Alert Broadcasted!", Toast.LENGTH_SHORT).show()
                openAlertThread(newAlert)
            }
        }
    }

    private fun openAlertThread(alert: AlertEntity) {
        val alertId = alert.alertId ?: return
        currentAlertThreadId = alertId

        alertThreadHeader.text = alert.title
        alertThreadSenderText.text = "From: ${alert.peerName ?: "Unknown"}"
        alertThreadDescription.text = alert.description

        if (!alert.attachmentPath.isNullOrEmpty()) {
            val clean = alert.attachmentPath.removePrefix("[FILE]:")
            val parts = clean.split("|")
            val fileName = parts[0]
            val pathOrUri = if (parts.size > 1) parts[1] else null

            alertThreadAttachmentName.text = fileName
            alertThreadAttachmentCard.visibility = View.VISIBLE
            alertThreadAttachmentCard.setOnClickListener {
                if (!pathOrUri.isNullOrEmpty()) {
                    openAttachment(fileName, pathOrUri)
                }
            }
        } else {
            alertThreadAttachmentCard.visibility = View.GONE
        }

        layoutAlertThread.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val history = db.messageDao().getAlertThreadMessages(alertId)
            withContext(Dispatchers.Main) { updateAlertThreadUI(history) }
        }
    }

    private fun closeAlertThread() {
        currentAlertThreadId = null
        layoutAlertThread.visibility = View.GONE
    }

    private fun updateAlertThreadUI(history: List<MessageEntity>) {
        val chatMessages = history.map { entity ->
            ChatMessage(
                senderName = entity.senderId,
                messageBody = entity.text,
                time = entity.timestamp
            )
        }
        alertThreadAdapter.setMessages(chatMessages)
        if (alertThreadAdapter.itemCount > 0) {
            alertThreadRecyclerView.scrollToPosition(alertThreadAdapter.itemCount - 1)
        }
    }

    private fun sendAlertThreadReply() {
        val alertId = currentAlertThreadId ?: return
        val replyText = editAlertThreadReply.text.toString().trim()
        if (replyText.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            val targetAlert = db.alertDao().getAlertByAlertId(alertId)
            val receiverName = targetAlert?.peerName ?: "All"

            val msgEntity = MessageEntity(
                senderId = myNickName,
                receiverId = receiverName,
                text = replyText,
                timestamp = System.currentTimeMillis(),
                isSent = true,
                alertId = alertId
            )
            db.messageDao().insertMessage(msgEntity)

            val replyPayloadStr = "[ALERT_REPLY]:$alertId|$replyText"
            val encryptedText = SecurityHelper.encrypt(replyPayloadStr)
            val chatMsg = ChatMessage(myNickName, encryptedText, System.currentTimeMillis())

            try {
                val payloadBytes = serialize(chatMsg)
                val onlinePeers = db.peerDao().getAllPeers().filter { it.isOnline && it.endpointId.isNotEmpty() }
                Log.d(TAG, "[ALERT_REPLY_TX] connectedPeerCount=${onlinePeers.size}")
                for (peer in onlinePeers) {
                    try {
                        val payload = Payload.fromBytes(payloadBytes)
                        Log.d(TAG, "[ALERT_REPLY_TX] sending endpoint=${peer.endpointId} peer=${peer.name}")
                        Nearby.getConnectionsClient(this@MainActivity).sendPayload(peer.endpointId, payload)
                        Log.d(TAG, "[ALERT_REPLY_TX] sendPayload invoked endpoint=${peer.endpointId}")
                    } catch (e: Exception) {
                        Log.e(TAG, "[ALERT_REPLY_TX] Error sending to endpoint=${peer.endpointId}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Broadcast alert reply fail", e)
            }

            val updatedHistory = db.messageDao().getAlertThreadMessages(alertId)
            withContext(Dispatchers.Main) {
                updateAlertThreadUI(updatedHistory)
                editAlertThreadReply.setText("")
            }
        }
    }

    private fun loadPeersFromDb() {
        lifecycleScope.launch(Dispatchers.IO) {
            val savedPeers = db.peerDao().getAllPeers()
            val activeOnlineCount = savedPeers.count { it.isOnline }

            withContext(Dispatchers.Main) {
                if (::peerAdapter.isInitialized) peerAdapter.updateList(savedPeers)
                if (::dashActiveCount.isInitialized) {
                    dashActiveCount.text = String.format("%02d", activeOnlineCount)
                }
                if (::dashKnownCount.isInitialized) {
                    dashKnownCount.text = String.format("%02d", savedPeers.size)
                }
            }
        }
    }

    private fun serialize(message: ChatMessage): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val objectStream = ObjectOutputStream(outputStream)
        objectStream.writeObject(message)
        return outputStream.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): ChatMessage {
        val inputStream = ByteArrayInputStream(bytes)
        val objectStream = ObjectInputStream(inputStream)
        return objectStream.readObject() as ChatMessage
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }

    private fun sendFile(uri: Uri, fileName: String) {
        if (currentChatEndpointId == null) return
        try {
            val pfd = contentResolver.openFileDescriptor(uri, "r") ?: return
            val filePayload = Payload.fromFile(pfd)
            val payloadId = filePayload.id
            Nearby.getConnectionsClient(this).sendPayload(currentChatEndpointId!!, filePayload)

            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val localFile = File(downloadsDir, fileName)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(localFile).use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying local file for sender", e)
            }

            val localPath = if (localFile.exists()) localFile.absolutePath else uri.toString()
            val metaText = "[FILE]:$fileName|$localPath|$payloadId"
            sendMessage(metaText)
            Toast.makeText(this, "Sending $fileName...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "File Error", e)
            Toast.makeText(this, "Error sending file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveReceivedPayloadFile(filePayload: Payload, targetFileName: String? = null): File? {
        return try {
            val payloadFile = filePayload.asFile() ?: return null
            val pfd = payloadFile.asParcelFileDescriptor()
            val javaFile = payloadFile.asJavaFile()

            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val name = targetFileName ?: javaFile?.name ?: "Nexora_${System.currentTimeMillis()}"
            var destinationFile = File(downloadsDir, name)
            var counter = 1
            while (destinationFile.exists()) {
                val nameWithoutExt = name.substringBeforeLast(".")
                val ext = name.substringAfterLast(".", "")
                val newName = if (ext.isNotEmpty() && ext != name) "$nameWithoutExt($counter).$ext" else "$name($counter)"
                destinationFile = File(downloadsDir, newName)
                counter++
            }

            if (javaFile != null && javaFile.exists()) {
                javaFile.copyTo(destinationFile, overwrite = true)
            } else if (pfd != null) {
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Log.d(TAG, "[FILE_SAVE] payloadId=${filePayload.id} filename=$name destination=${destinationFile.absolutePath} exists=${destinationFile.exists()} size=${destinationFile.length()}")
            destinationFile
        } catch (e: Exception) {
            Log.e(TAG, "Error saving received payload file", e)
            null
        }
    }

    private fun prepareDestinationFile(tempFile: File, targetName: String): File {
        return try {
            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            var destinationFile = File(downloadsDir, targetName)
            if (tempFile.absolutePath == destinationFile.absolutePath) {
                return tempFile
            }

            var counter = 1
            while (destinationFile.exists() && destinationFile.absolutePath != tempFile.absolutePath) {
                val nameWithoutExt = targetName.substringBeforeLast(".")
                val ext = targetName.substringAfterLast(".", "")
                val newName = if (ext.isNotEmpty() && ext != targetName) "$nameWithoutExt($counter).$ext" else "$targetName($counter)"
                destinationFile = File(downloadsDir, newName)
                counter++
            }

            if (tempFile.renameTo(destinationFile)) {
                destinationFile
            } else {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
                destinationFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing destination file", e)
            tempFile
        }
    }

    private fun openAttachment(fileName: String, pathOrUri: String) {
        Log.d("FILE_DEBUG", "fileName=$fileName")
        Log.d("FILE_DEBUG", "pathOrUri=$pathOrUri")

        if (pathOrUri.isEmpty()) {
            Toast.makeText(this, "File transfer is still in progress...", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri: Uri = if (pathOrUri.startsWith("content://")) {
                Uri.parse(pathOrUri)
            } else {
                val file = File(pathOrUri)
                Log.d("FILE_DEBUG", "absolutePath=${file.absolutePath}")
                Log.d("FILE_DEBUG", "exists=${file.exists()}")
                Log.d("FILE_DEBUG", "isFile=${file.isFile}")
                Log.d("FILE_DEBUG", "size=${file.length()}")

                if (!file.exists() || file.length() == 0L) {
                    Toast.makeText(this, "File not found on this device.", Toast.LENGTH_SHORT).show()
                    return
                }
                FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            }

            val extension = fileName.substringAfterLast('.', "").lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: contentResolver.getType(uri)
                ?: when {
                    extension in listOf("jpg", "jpeg", "png", "webp", "gif") -> "image/*"
                    extension == "pdf" -> "application/pdf"
                    extension in listOf("txt", "log") -> "text/plain"
                    else -> "*/*"
                }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                clipData = android.content.ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val pkg = resolveInfo.activityInfo.packageName
                grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Open $fileName").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No application found to open $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening attachment", e)
            Toast.makeText(this, "Unable to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeviceSelectionDialog() {
        if (discoveredEndpoints.isEmpty()) {
            selectionDialog?.dismiss()
            return
        }

        val endpointIds = discoveredEndpoints.keys.toList()
        val names = discoveredEndpoints.values.toTypedArray()

        if (selectionDialog != null && selectionDialog!!.isShowing) {
            selectionDialog!!.dismiss()
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Found Devices — Nexora")
        builder.setItems(names) { _, which ->
            val selectedEndpointId = endpointIds[which]
            val selectedName = names[which]

            Toast.makeText(this, "Connecting to $selectedName...", Toast.LENGTH_SHORT).show()

            Nearby.getConnectionsClient(this)
                .requestConnection(myNickName, selectedEndpointId, connectionLifecycleCallback)

            Nearby.getConnectionsClient(this).stopDiscovery()
            handler.removeCallbacks(roleSwitchRunnable)
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
            startAutoMode()
        }

        selectionDialog = builder.create()
        selectionDialog!!.show()
    }
}