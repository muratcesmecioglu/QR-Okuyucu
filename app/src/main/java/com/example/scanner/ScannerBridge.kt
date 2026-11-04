package com.example.scanner

object ScannerBridge {
    init {
        System.loadLibrary("rust_core")
    }
    external fun decodeFrame(yBuffer: ByteArray, width: Int, height: Int): String
}