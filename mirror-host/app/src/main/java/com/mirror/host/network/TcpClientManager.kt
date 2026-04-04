package com.mirror.host.network

import com.mirror.host.core.RustBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP client to connect to Target app's server.
 * Connects to port 8080 on the Target device's IP.
 * Protocol: [4-byte length (big-endian)][data]
 */
class TcpClientManager(
    private val scope: CoroutineScope
) {
    companion object {
        const val PORT = 8080
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
    var onFrameReceived: ((ByteArray) -> Unit)? = null  // Called with demuxed video frames

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

                    // Start receiving length-prefixed frames
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
            val lengthBuffer = ByteArray(4)

            while (kotlin.coroutines.coroutineContext.isActive && sock.isConnected && !sock.isClosed) {
                // Read 4-byte length header
                if (!readFully(input, lengthBuffer)) {
                    Timber.i("Connection closed while reading length")
                    break
                }
                
                // Parse length (big-endian)
                val length = ((lengthBuffer[0].toInt() and 0xFF) shl 24) or
                             ((lengthBuffer[1].toInt() and 0xFF) shl 16) or
                             ((lengthBuffer[2].toInt() and 0xFF) shl 8) or
                             (lengthBuffer[3].toInt() and 0xFF)
                
                if (length < 0 || length > 10_000_000) { // Sanity check: max 10MB
                    Timber.e("Invalid packet length: $length")
                    break
                }
                
                // Read exactly 'length' bytes
                val dataBuffer = ByteArray(length)
                if (!readFully(input, dataBuffer)) {
                    Timber.i("Connection closed while reading data")
                    break
                }
                
                // Process the complete packet
                processReceivedData(dataBuffer)
                onDataReceived?.invoke(dataBuffer)
            }
        } catch (e: IOException) {
            if (kotlin.coroutines.coroutineContext.isActive) Timber.e(e, "Receive error")
        } finally {
            cleanupConnection()
        }
    }
    
    /**
     * Read exactly 'buffer.size' bytes from input stream.
     * Returns false if stream ends before buffer is filled.
     */
    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val bytesRead = input.read(buffer, offset, buffer.size - offset)
            if (bytesRead == -1) return false
            offset += bytesRead
        }
        return true
    }

    private fun processReceivedData(data: ByteArray) {
        // Pass raw encrypted data to callback.
        // The consumer (LiveCameraActivity) will: decrypt → demux → route by type
        // This unifies the data flow with WebRTC which also delivers raw encrypted bytes.
        try {
            onDataReceived?.invoke(data)
        } catch (e: Exception) {
            Timber.e(e, "Error processing received data")
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
