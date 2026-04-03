package com.mirror.host.core

object RustBridge {
    
    init {
        System.loadLibrary("mirror_core")
    }
    
    external fun nativeInit()
    external fun nativeGetVersion(): String
}
