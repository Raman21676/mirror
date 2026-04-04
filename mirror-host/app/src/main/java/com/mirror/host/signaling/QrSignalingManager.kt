package com.mirror.host.signaling

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
 * QR Code signaling manager for WebRTC SDP/ICE exchange.
 * 
 * Flow for Host (initiator):
 * 1. Create WebRTC offer
 * 2. Display offer as QR code (large, chunked if needed)
 * 3. Target scans offer, creates answer
 * 4. Host scans answer QR from Target
 * 5. Connection established
 * 
 * For large SDPs, we use chunked QR codes displayed in sequence.
 */
class QrSignalingManager {
    companion object {
        private const val QR_SIZE = 600
        private const val MAX_QR_BYTES = 2500  // Conservative limit for reliable scanning
        private const val CHUNK_PREFIX = "MIRROR_CHUNK_"
    }

    /**
     * Generate QR code bitmap from signaling data.
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
     * Check if data needs to be chunked for QR code.
     */
    fun needsChunking(data: String): Boolean {
        return data.toByteArray(Charsets.UTF_8).size > MAX_QR_BYTES
    }
    
    /**
     * Chunk large signaling data into multiple QR codes.
     * Returns list of data chunks to be displayed sequentially.
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
            
            // Add header: MIRROR_CHUNK_index_total_data
            val header = "${CHUNK_PREFIX}${i}_${totalChunks}_"
            val chunkData = header + chunk.toString(Charsets.UTF_8)
            chunks.add(chunkData)
        }
        
        return chunks
    }
    
    /**
     * Reassemble chunked data from received chunks.
     * Returns null if incomplete.
     */
    fun reassembleChunks(chunks: List<String>): String? {
        if (chunks.isEmpty()) return null
        
        // Parse chunks
        val parsedChunks = chunks.mapNotNull { parseChunk(it) }
        if (parsedChunks.isEmpty()) return null
        
        val totalChunks = parsedChunks.first().total
        if (parsedChunks.size < totalChunks) return null  // Missing chunks
        
        // Sort by index and reassemble
        val sortedChunks = parsedChunks.sortedBy { it.index }
        return sortedChunks.joinToString("") { it.data }
    }
    
    private fun parseChunk(chunk: String): ChunkData? {
        if (!chunk.startsWith(CHUNK_PREFIX)) {
            // Not a chunked message, return as-is as index 0 of 1
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
    
    /**
     * Compress SDP to reduce QR code size.
     * Removes unnecessary whitespace and line breaks.
     */
    fun compressSdp(sdp: String): String {
        return sdp
            .replace("\r\n", "\n")
            .replace("\n\n", "\n")
            .trim()
    }
    
    /**
     * Create a simple JSON wrapper for signaling data.
     */
    fun wrapSignaling(type: String, data: String): String {
        return org.json.JSONObject().apply {
            put("type", type)  // "offer", "answer", "ice"
            put("data", data)
            put("ts", System.currentTimeMillis())
        }.toString()
    }
}
