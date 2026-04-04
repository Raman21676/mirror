package com.mirror.target.signaling

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import timber.log.Timber
import java.util.EnumMap

/**
 * QR Code signaling manager for WebRTC SDP/ICE exchange (Target side).
 * 
 * Flow for Target (responder):
 * 1. Display "waiting" QR with local IP (for TCP connection)
 * 2. If Host scans TCP QR and connects via TCP → done
 * 3. If Host scans WebRTC offer QR → create answer, display as QR
 * 4. Host scans answer QR → connection established
 */
class QrSignalingManager {
    companion object {
        private const val QR_SIZE = 600
        private const val MAX_QR_BYTES = 2500
        private const val CHUNK_PREFIX = "MIRROR_CHUNK_"
    }

    /**
     * Generate QR code bitmap from data.
     */
    fun generateQrCode(data: String): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.MARGIN, 2)
            }
            
            val writer = MultiFormatWriter()
            val bitMatrix: BitMatrix = writer.encode(
                data,
                BarcodeFormat.QR_CODE,
                QR_SIZE,
                QR_SIZE,
                hints
            )
            
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate QR code")
            null
        }
    }
    
    /**
     * Generate connection info QR for TCP (same-network) mode.
     */
    fun generateConnectionInfoQr(localIp: String, port: Int): Bitmap? {
        val info = org.json.JSONObject().apply {
            put("mode", "tcp")
            put("ip", localIp)
            put("port", port)
            put("type", "target_info")
        }.toString()
        return generateQrCode(info)
    }
    
    /**
     * Check if data needs chunking.
     */
    fun needsChunking(data: String): Boolean {
        return data.toByteArray(Charsets.UTF_8).size > MAX_QR_BYTES
    }
    
    /**
     * Chunk large data into multiple QR codes.
     */
    fun chunkData(data: String, chunkSize: Int = MAX_QR_BYTES): List<String> {
        val bytes = data.toByteArray(Charsets.UTF_8)
        if (bytes.size <= chunkSize) {
            return listOf(data)
        }
        
        val chunks = mutableListOf<String>()
        val totalChunks = (bytes.size + chunkSize - 1) / chunkSize
        
        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, bytes.size)
            val chunk = bytes.copyOfRange(start, end)
            
            val header = "${CHUNK_PREFIX}${i}_${totalChunks}_"
            val chunkData = header + chunk.toString(Charsets.UTF_8)
            chunks.add(chunkData)
        }
        
        return chunks
    }
    
    /**
     * Reassemble chunked data.
     */
    fun reassembleChunks(chunks: List<String>): String? {
        if (chunks.isEmpty()) return null
        
        val parsedChunks = chunks.mapNotNull { parseChunk(it) }
        if (parsedChunks.isEmpty()) return null
        
        val totalChunks = parsedChunks.first().total
        if (parsedChunks.size < totalChunks) return null
        
        val sortedChunks = parsedChunks.sortedBy { it.index }
        return sortedChunks.joinToString("") { it.data }
    }
    
    private fun parseChunk(chunk: String): ChunkData? {
        if (!chunk.startsWith(CHUNK_PREFIX)) {
            return ChunkData(0, 1, chunk)
        }
        
        return try {
            val withoutPrefix = chunk.removePrefix(CHUNK_PREFIX)
            val parts = withoutPrefix.split("_", limit = 3)
            if (parts.size < 3) return null
            
            val index = parts[0].toInt()
            val total = parts[1].toInt()
            val data = parts[2]
            
            ChunkData(index, total, data)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse chunk")
            null
        }
    }
    
    private data class ChunkData(
        val index: Int,
        val total: Int,
        val data: String
    )
}
