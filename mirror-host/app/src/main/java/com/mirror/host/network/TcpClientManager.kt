package com.mirror.host.network

import com.mirror.host.core.RustBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP client to connect to Target app's server.
 * Connects to port 8080 on the Target device's IP.
 */
class TcpClientManager(
    private val scope: CoroutineScope
) {
    companion object {
        const val PORT = 8080
        const val BUFFER_SIZE = 8192
        const val RECONNECT_DELAY_MS = 3000L
    }

    private var socket: Socket? = null
    private var clientJob: Job? = null
    private val ioScope = CoroutineScope(Dispatchers.IO + scope.coroutineContext)
    
    @Volatile
    var isConnected = false
        private set

    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onDataReceived: ((ByteArray) -> Unit)? = null

    fun connect(targetIp: String) {
        if (isConnected || clientJob != null) return

        clientJob = ioScope.launch {
            while (isActive) {
                try {
                    Timber.i("Connecting to $targetIp:$PORT...")
                    socket = Socket().apply {
                        connect(InetSocketAddress(targetIp, PORT), 5000)
                    }
                    
                    isConnected = true
                    onConnectionStateChanged?.invoke(true)
                    Timber.i("Connected to Target at $targetIp:$PORT")

                    // Start receiving data
                    receiveLoop()

                } catch (e: IOException) {
                    Timber.e(e, "Connection failed")
                    cleanupConnection()
                    
                    // Wait before retry
                    Timber.d("Reconnecting in ${RECONNECT_DELAY_MS}ms...")
                    delay(RECONNECT_DELAY_MS)
                }
            }
        }
    }

    fun disconnect() {
        clientJob?.cancel()
        clientJob = null
        cleanupConnection()
        Timber.i("Disconnected from Target")
    }

    private suspend fun receiveLoop() {
        val sock = socket ?: return
        
        try {
            val input = sock.getInputStream()
            val buffer = ByteArray(BUFFER_SIZE)

            while (isActive && sock.isConnected && !sock.isClosed) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) {
                    Timber.i("Server closed connection")
                    break
                }
                if (bytesRead > 0) {
                    val data = buffer.copyOf(bytesRead)
                    processReceivedData(data)
                    onDataReceived?.invoke(data)
                }
            }
        } catch (e: IOException) {
            if (isActive) Timber.e(e, "Receive error")
        } finally {
            cleanupConnection()
        }
    }

    private fun processReceivedData(data: ByteArray) {
        // Pass through Rust demux and log payload count
        try {
            val payloads = RustBridge.nativeDemuxPacket(data)
            if (payloads != null && payloads.isNotEmpty()) {
                Timber.d("Demuxed ${payloads.size} payload(s)")
            } else {
                Timber.d("No complete payloads yet (buffered ${data.size} bytes)")
            }
        } catch (e: Exception) {
            Timber.e(e, "Demux error")
        }
    }

    /**
     * Send data to the Target (for future use: control commands, etc.)
     */
    fun send(data: ByteArray): Boolean {
        val sock = socket ?: return false
        return try {
            sock.getOutputStream().write(data)
            sock.getOutputStream().flush()
            Timber.d("Sent ${data.size} bytes to Target")
            true
        } catch (e: IOException) {
            Timber.e(e, "Failed to send")
            false
        }
    }

    /**
     * Send a test muxed packet to verify the pipeline
     */
    fun sendTestPacket(): Boolean {
        val testPayload = "Hello from Host!".toByteArray()
        val key = ByteArray(32) { 0x42 } // Test key (all 0x42)
        
        return try {
            // Mux the payload
            val muxed = RustBridge.nativeMuxPacket(0x01, testPayload) // 0x01 = Video type
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
            
            Timber.i("Sending test packet: ${encrypted.size} bytes (payload: ${testPayload.size} bytes)")
            send(encrypted)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create test packet")
            false
        }
    }

    private fun cleanupConnection() {
        isConnected = false
        onConnectionStateChanged?.invoke(false)
        try {
            socket?.close()
        } catch (_: IOException) {}
        socket = null
    }
}
