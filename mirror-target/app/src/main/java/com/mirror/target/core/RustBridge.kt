package com.mirror.target.core

object RustBridge {

    init {
        System.loadLibrary("mirror_core")
    }

    external fun nativeInit()
    external fun nativeGetVersion(): String
    external fun nativeEncryptPacket(data: ByteArray, key: ByteArray): ByteArray?
    external fun nativeDecryptPacket(data: ByteArray, key: ByteArray): ByteArray?
    external fun nativeMuxPacket(type: Int, payload: ByteArray): ByteArray?
    external fun nativeDemuxPacket(data: ByteArray): Array<ByteArray>?
    external fun nativeClearDemux()
}
