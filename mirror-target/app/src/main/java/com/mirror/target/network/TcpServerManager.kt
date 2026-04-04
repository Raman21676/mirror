package com.mirror.target.network

import com.mirror.target.core.RustBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

/**
 * Simple TCP socket server for local WiFi streaming.
 * Listens on port 8080 and accepts connections from Host app.
 */
class TcpServerManager(
    private val scope: CoroutineScope
) {
    companion object {
        const val PORT = 8080
        const val BUFFER_SIZE = 8192
    }

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var serverJob: Job? = null
    private val ioScope = CoroutineScope(Dispatchers.IO + scope.coroutineContext)
    
    var onClientConnected: (() -> Unit)? = null

    fun startServer() {
        if (serverJob != null) return
        
        serverJob = ioScope.launch {
            try {
                serverSocket = ServerSocket(PORT).apply {
                    reuseAddress = true
                }
                Timber.i("TCP server started on port $PORT")

                while (isActive) {
                    try {
                        val client = serverSocket!!.accept()
                        Timber.i("Client connected: ${client.inetAddress}")
                        handleClient(client)
                    } catch (e: IOException) {
                        if (isActive) Timber.e(e, "Error accepting client")
                    }
                }
            } catch (e: IOException) {
                Timber.e(e, "Failed to start TCP server")
            }
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        serverJob = null
        
        try {
            clientSocket?.close()
            clientSocket = null
            serverSocket?.close()
            serverSocket = null
            Timber.i("TCP server stopped")
        } catch (e: IOException) {
            Timber.e(e, "Error stopping TCP server")
        }
    }

    private fun handleClient(socket: Socket) {
        // Close previous client if any (only one client at a time)
        clientSocket?.close()
        clientSocket = socket
        
        // Notify that a new client connected (send codec config)
        onClientConnected?.invoke()

        ioScope.launch {
            try {
                val input = socket.getInputStream()
                val buffer = ByteArray(BUFFER_SIZE)

                while (isActive && socket.isConnected && !socket.isClosed) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        processReceivedData(data)
                    }
                }
            } catch (e: IOException) {
                Timber.e(e, "Client connection error")
            } finally {
                Timber.i("Client disconnected")
                if (clientSocket === socket) {
                    clientSocket = null
                }
                try {
                    socket.close()
                } catch (_: IOException) {}
            }
        }
    }

    private fun processReceivedData(data: ByteArray) {
        // Pass through Rust demux and log payload count
        try {
            val payloads = RustBridge.nativeDemuxPacket(data)
            if (payloads != null && payloads.isNotEmpty()) {
                Timber.d("Demuxed ${payloads.size} payload(s)")
                payloads.forEachIndexed { index, payload ->
                    Timber.d("  Payload $index: ${payload.size} bytes")
                }
            } else {
                Timber.d("No complete payloads yet (buffered ${data.size} bytes)")
            }
        } catch (e: Exception) {
            Timber.e(e, "Demux error")
        }
    }

    /**
     * Send data to the connected client with 4-byte length header.
     * Format: [length (4 bytes, big-endian)][data]
     */
    fun sendToClient(data: ByteArray): Boolean {
        val socket = clientSocket ?: return false
        return try {
            val output = socket.getOutputStream()
            
            // Write 4-byte big-endian length header
            val lengthHeader = ByteArray(4).apply {
                this[0] = (data.size shr 24).toByte()
                this[1] = (data.size shr 16).toByte()
                this[2] = (data.size shr 8).toByte()
                this[3] = data.size.toByte()
            }
            
            output.write(lengthHeader)
            output.write(data)
            output.flush()
            true
        } catch (e: IOException) {
            Timber.e(e, "Failed to send to client")
            false
        }
    }

    fun isClientConnected(): Boolean {
        return clientSocket?.isConnected == true && clientSocket?.isClosed == false
    }
}
