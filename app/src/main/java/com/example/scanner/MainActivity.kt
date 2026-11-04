package com.example.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var camera: Camera? = null
    private var isTorchOn = false
    private var isZoomed = false
    private var hasScanned = false
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                decodeImageUri(uri)
                return
            }
        }

        val previewView = PreviewView(this)
        setContentView(previewView)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                isZoomed = !isZoomed
                camera?.cameraControl?.setZoomRatio(if (isZoomed) 2.5f else 1.0f)
                return true
            }
        })
        previewView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }

        startCamera(previewView)
    }

    private fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (!hasScanned) {
                    val yBuffer = imageProxy.planes[0].buffer
                    val bytes = ByteArray(yBuffer.remaining())
                    yBuffer.get(bytes)

                    val result = ScannerBridge.decodeFrame(bytes, imageProxy.width, imageProxy.height)
                    if (result.isNotEmpty()) {
                        hasScanned = true
                        runOnUiThread { handleResult(result) }
                    }
                }
                imageProxy.close()
            }

            camera = cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            isTorchOn = !isTorchOn
            camera?.cameraControl?.enableTorch(isTorchOn)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleResult(raw: String) {
        triggerHaptic()
        val parts = raw.split("|", limit = 2)
        val format = parts.getOrNull(0) ?: ""
        val content = parts.getOrNull(1) ?: ""

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Code", content))

        when {
            content.startsWith("http://") || content.startsWith("https://") -> {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(content)))
                finish()
            }
            format.contains("EAN") || format.contains("CODE_") -> {
                val searchUrl = "https://www.google.com/search?q=$content"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))
                finish()
            }
            else -> {
                Toast.makeText(this, "Panoya Kopyalandı!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun decodeImageUri(uri: Uri) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val yBytes = ByteArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            yBytes[i] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
        }

        val result = ScannerBridge.decodeFrame(yBytes, w, h)
        if (result.isNotEmpty()) handleResult(result)
        else {
            Toast.makeText(this, "Görselde kod bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun triggerHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }
    }
}