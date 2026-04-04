package com.mirror.target.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Unified transport manager for the Target (server) side.
 * Handles both TCP (always active) and WebRTC (for cross-network fallback).
 */
class TransportManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TransportManager"
    }

    enum class TransportType {
        NONE,
        TCP,
        WEBRTC
    }
    
    enum class ConnectionState {
        DISCONNECTED,
        WAITING_TCP,
        WAITING_WEBRTC,
        CONNECTED_TCP,
        CONNECTED_WEBRTC
    }

    private val tcpManager = TcpServerManager(scope)
    private val webRtcManager = WebRtcServerManager(context, scope)
    
    private var currentTransport = TransportType.NONE
    private var _connectionState = ConnectionState.DISCONNECTED
    val connectionState: ConnectionState get() = _connectionState
    
    private var webRtcInitialized = false
    
    // Callbacks
    var onClientConnected: ((TransportType) -> Unit)? = null
    var onClientDisconnected: (() -> Unit)? = null
    var onSignalingData: ((String) -> Unit)? = null  // For WebRTC SDP/ICE to display as QR

    init {
        // Set up TCP callbacks
        tcpManager.onClientConnected = {
            if (currentTransport == TransportType.NONE) {
                currentTransport = TransportType.TCP
                _connectionState = ConnectionState.CONNECTED_TCP
                onClientConnected?.invoke(TransportType.TCP)
                Timber.i("Client connected via TCP")
            }
        }
        
        // Set up WebRTC callbacks
        webRtcManager.onConnectionStateChanged = { connected ->
            if (connected) {
                if (currentTransport == TransportType.NONE) {
                    currentTransport = TransportType.WEBRTC
                    _connectionState = ConnectionState.CONNECTED_WEBRTC
                    onClientConnected?.invoke(TransportType.WEBRTC)
                    Timber.i("Client connected via WebRTC")
                }
            } else {
                handleDisconnection()
            }
        }
        
        webRtcManager.onSignalingData = { data ->
            onSignalingData?.invoke(data)
        }
    }

    /**
     * Start the TCP server (always runs for local connections).
     */
    fun start() {
        tcpManager.startServer()
        _connectionState = ConnectionState.WAITING_TCP
        Timber.i("Transport manager started (TCP)")
    }
    
    /**
     * Initialize WebRTC for cross-network connections.
     * Call this when user selects "Cross-network mode" in UI.
     */
    fun initializeWebRtc() {
        if (webRtcInitialized) return
        
        scope.launch(Dispatchers.IO) {
            webRtcManager.initialize()
            delay(500)
            webRtcInitialized = true
            
            if (_connectionState == ConnectionState.WAITING_TCP) {
                _connectionState = ConnectionState.WAITING_WEBRTC
            }
            Timber.i("WebRTC initialized")
        }
    }
    
    /**
     * Create WebRTC answer from scanned offer.
     * Call this after scanning the Host's QR code containing their offer.
     */
    fun createWebRtcAnswer(offerSdp: String) {
        if (!webRtcInitialized) {
            initializeWebRtc()
        }
        
        scope.launch(Dispatchers.IO) {
            delay(500)  // Ensure initialization is complete
            Timber.i("Creating WebRTC answer...")
            webRtcManager.createAnswer(offerSdp)
        }
    }
    
    /**
     * Add ICE candidate received from Host.
     */
    fun addWebRtcIceCandidate(candidateJson: String) {
        webRtcManager.addIceCandidate(candidateJson)
    }

    /**
     * Send data through the active transport.
     */
    fun send(data: ByteArray): Boolean {
        return when (currentTransport) {
            TransportType.TCP -> tcpManager.sendToClient(data)
            TransportType.WEBRTC -> webRtcManager.sendBinary(data)
            else -> false
        }
    }

    fun stop() {
        tcpManager.stopServer()
        webRtcManager.dispose()
        
        currentTransport = TransportType.NONE
        _connectionState = ConnectionState.DISCONNECTED
        webRtcInitialized = false
        
        Timber.i("Transport manager stopped")
    }
    
    private fun handleDisconnection() {
        currentTransport = TransportType.NONE
        _connectionState = if (tcpManager.isClientConnected()) {
            ConnectionState.CONNECTED_TCP
        } else {
            ConnectionState.WAITING_TCP
        }
        onClientDisconnected?.invoke()
        Timber.i("Client disconnected")
    }
    
    fun isClientConnected(): Boolean {
        return currentTransport == TransportType.TCP || 
               currentTransport == TransportType.WEBRTC
    }
    
    fun getCurrentTransport(): TransportType = currentTransport
    
    fun getTcpPort(): Int = TcpServerManager.PORT
}
