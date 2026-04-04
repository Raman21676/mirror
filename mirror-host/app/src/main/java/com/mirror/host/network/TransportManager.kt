package com.mirror.host.network

import android.content.Context
import com.mirror.host.core.RustBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Unified transport manager that intelligently switches between:
 * 1. TCP - For same-network (fast, zero overhead)
 * 2. WebRTC DataChannel - For cross-network (fallback when TCP fails)
 * 
 * The manager tries TCP first with a short timeout, then falls back to WebRTC.
 */
class TransportManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TCP_TIMEOUT_MS = 5000L  // 5 seconds to try TCP
        private const val TCP_FALLBACK_DELAY_MS = 500L  // Wait before trying WebRTC
    }

    enum class TransportType {
        NONE,
        TCP,
        WEBRTC
    }
    
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING_TCP,
        CONNECTING_WEBRTC,
        CONNECTED_TCP,
        CONNECTED_WEBRTC,
        FAILED
    }

    private val tcpManager = TcpClientManager(scope)
    private val webRtcManager = WebRtcClientManager(context, scope)
    
    private var currentTransport = TransportType.NONE
    private var _connectionState = ConnectionState.DISCONNECTED
    val connectionState: ConnectionState get() = _connectionState
    
    private var targetIp: String? = null
    private var connectionJob: Job? = null
    
    // Callbacks
    var onConnectionStateChanged: ((ConnectionState) -> Unit)? = null
    var onDataReceived: ((ByteArray) -> Unit)? = null  // Raw encrypted data (both transports)
    var onSignalingData: ((String) -> Unit)? = null  // For WebRTC SDP/ICE

    init {
        // Set up TCP callbacks
        tcpManager.onConnectionStateChanged = { connected ->
            if (connected && _connectionState == ConnectionState.CONNECTING_TCP) {
                _connectionState = ConnectionState.CONNECTED_TCP
                currentTransport = TransportType.TCP
                onConnectionStateChanged?.invoke(_connectionState)
                Timber.i("Connected via TCP")
            } else if (!connected && currentTransport == TransportType.TCP) {
                handleDisconnection()
            }
        }
        
        // TCP now delivers raw encrypted bytes (not demuxed)
        tcpManager.onDataReceived = { data ->
            if (currentTransport == TransportType.TCP) {
                onDataReceived?.invoke(data)
            }
        }
        
        // Set up WebRTC callbacks
        webRtcManager.onConnectionStateChanged = { connected ->
            if (connected && _connectionState == ConnectionState.CONNECTING_WEBRTC) {
                _connectionState = ConnectionState.CONNECTED_WEBRTC
                currentTransport = TransportType.WEBRTC
                onConnectionStateChanged?.invoke(_connectionState)
                Timber.i("Connected via WebRTC")
            } else if (!connected && currentTransport == TransportType.WEBRTC) {
                handleDisconnection()
            }
        }
        
        // WebRTC also delivers raw encrypted bytes
        webRtcManager.onDataReceived = { data ->
            if (currentTransport == TransportType.WEBRTC) {
                onDataReceived?.invoke(data)
            }
        }
        
        webRtcManager.onSignalingData = { data ->
            onSignalingData?.invoke(data)
        }
    }

    /**
     * Start connection to target with intelligent transport selection.
     * 
     * Flow:
     * 1. Try TCP first (fastest for same network)
     * 2. If TCP fails/times out, fall back to WebRTC
     * 
     * @param targetIp IP address to try for TCP connection
     */
    fun connect(targetIp: String) {
        if (_connectionState != ConnectionState.DISCONNECTED && 
            _connectionState != ConnectionState.FAILED) {
            Timber.w("Already connected or connecting")
            return
        }
        
        this.targetIp = targetIp
        
        connectionJob = scope.launch {
            _connectionState = ConnectionState.CONNECTING_TCP
            onConnectionStateChanged?.invoke(_connectionState)
            
            // Try TCP first
            Timber.i("Trying TCP connection to $targetIp...")
            tcpManager.connect(targetIp)
            
            // Wait for TCP to connect or timeout
            val tcpConnected = withTimeoutOrNull(TCP_TIMEOUT_MS) {
                while (_connectionState == ConnectionState.CONNECTING_TCP) {
                    if (tcpManager.isConnected) {
                        return@withTimeoutOrNull true
                    }
                    delay(100)
                }
                false
            } ?: false
            
            if (!tcpConnected && _connectionState == ConnectionState.CONNECTING_TCP) {
                // TCP failed, try WebRTC
                Timber.i("TCP connection failed/timed out, falling back to WebRTC")
                tryWebRtcFallback()
            }
        }
    }
    
    private suspend fun tryWebRtcFallback() {
        _connectionState = ConnectionState.CONNECTING_WEBRTC
        onConnectionStateChanged?.invoke(_connectionState)
        
        // Initialize WebRTC and create offer
        webRtcManager.initialize()
        delay(500)  // Give initialization time
        
        Timber.i("Creating WebRTC offer...")
        webRtcManager.createOffer()
        
        // The offer will be sent via onSignalingData callback
        // The UI should display this as a QR code for the Target to scan
    }
    
    /**
     * Set the remote answer from the Target (after scanning their QR code).
     */
    fun setWebRtcAnswer(answerSdp: String) {
        if (_connectionState == ConnectionState.CONNECTING_WEBRTC) {
            Timber.i("Setting WebRTC remote answer")
            webRtcManager.setRemoteAnswer(answerSdp)
        }
    }
    
    /**
     * Add ICE candidate received from Target.
     */
    fun addWebRtcIceCandidate(candidateJson: String) {
        webRtcManager.addIceCandidate(candidateJson)
    }
    
    /**
     * Connect directly via WebRTC (skip TCP attempt).
     * Use this for cross-network connections where you know TCP won't work.
     */
    fun connectWebRtcOnly() {
        if (_connectionState != ConnectionState.DISCONNECTED && 
            _connectionState != ConnectionState.FAILED) {
            Timber.w("Already connected or connecting")
            return
        }
        
        connectionJob = scope.launch {
            _connectionState = ConnectionState.CONNECTING_WEBRTC
            onConnectionStateChanged?.invoke(_connectionState)
            
            webRtcManager.initialize()
            delay(500)
            
            Timber.i("Creating WebRTC offer (WebRTC only mode)...")
            webRtcManager.createOffer()
        }
    }

    /**
     * Send data through the active transport.
     * Automatically encrypts using Rust AES-256-GCM before sending.
     */
    fun send(data: ByteArray): Boolean {
        return when (currentTransport) {
            TransportType.TCP -> tcpManager.send(data)
            TransportType.WEBRTC -> webRtcManager.sendBinary(data)
            else -> false
        }
    }
    
    /**
     * Send muxed and encrypted packet.
     * Convenience method that muxes, encrypts, and sends in one call.
     */
    fun sendPacket(streamType: Int, payload: ByteArray, key: ByteArray): Boolean {
        return try {
            // Mux the payload
            val muxed = RustBridge.nativeMuxPacket(streamType, payload)
            if (muxed == null) {
                Timber.e("Mux failed")
                return false
            }
            
            // Encrypt the muxed data
            val encrypted = RustBridge.nativeEncryptPacket(muxed, key)
            if (encrypted == null) {
                Timber.e("Encryption failed")
                return false
            }
            
            send(encrypted)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send packet")
            false
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        
        tcpManager.disconnect()
        webRtcManager.disconnect()
        
        currentTransport = TransportType.NONE
        _connectionState = ConnectionState.DISCONNECTED
        onConnectionStateChanged?.invoke(_connectionState)
        
        Timber.i("Disconnected from all transports")
    }
    
    fun dispose() {
        disconnect()
        webRtcManager.dispose()
    }
    
    private fun handleDisconnection() {
        _connectionState = ConnectionState.DISCONNECTED
        currentTransport = TransportType.NONE
        onConnectionStateChanged?.invoke(_connectionState)
        Timber.i("Transport disconnected")
    }
    
    fun isConnected(): Boolean {
        return _connectionState == ConnectionState.CONNECTED_TCP ||
               _connectionState == ConnectionState.CONNECTED_WEBRTC
    }
    
    fun getCurrentTransport(): TransportType = currentTransport
}
