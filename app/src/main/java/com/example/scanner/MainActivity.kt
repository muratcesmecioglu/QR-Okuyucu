package com.example.scanner

import android.Manifest
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    private lateinit var previewView: PreviewView
    private lateinit var permissionContainer: LinearLayout

    // Kamera izni isteme başlatıcısı
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            permissionContainer.visibility = View.GONE
            startCamera()
        } else {
            permissionContainer.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setupUI()

        // Paylaş menüsünden görsel geldiyse
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) {
                decodeImageUri(uri)
                return
            }
        }

        checkCameraPermission()
    }

    private fun setupUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        previewView = PreviewView(this)
        root.addView(previewView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Ortadaki sade vizör çerçevesi
        val reticle = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(4, Color.parseColor("#88FFFFFF"))
                cornerRadius = 32f
            }
        }
        val reticleSize = (resources.displayMetrics.widthPixels * 0.65).toInt()
        root.addView(reticle, FrameLayout.LayoutParams(reticleSize, reticleSize).apply {
            gravity = Gravity.CENTER
        })

        // Sağ üst kontrol butonları (Flaş ve Ayarlar)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(32, 64, 32, 32)
        }

        val flashBtn = createTopButton("🔦 Flaş") { toggleTorch() }
        val settingsBtn = createTopButton("⚙️ Ayarlar") {
            runUnlocked {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        topBar.addView(flashBtn)
        topBar.addView(settingsBtn)
        root.addView(topBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))

        // İzin verilmediğinde gösterilecek ekran
        permissionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(64, 64, 64, 64)
            visibility = View.GONE

            val msg = TextView(context).apply {
                text = "Barkod ve QR kodları okuyabilmek için kamera izni gerekiyor."
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 32)
            }
            val btn = Button(context).apply {
                text = "Kamera İzni Ver"
                setOnClickListener {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        // Kalıcı reddedildiyse uygulama ayarlarını aç
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                }
            }
            addView(msg)
            addView(btn)
        }
        root.addView(permissionContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)

        // Çift dokunuşla 2.5x Zoom
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                isZoomed = !isZoomed
                camera?.cameraControl?.setZoomRatio(if (isZoomed) 2.5f else 1.0f)
                return true
            }
        })
        previewView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }
    }

    private fun createTopButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(32, 18, 32, 18)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#88000000"))
                cornerRadius = 50f
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 0, 0, 0) }
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            permissionContainer.visibility = View.GONE
            startCamera()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
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

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleTorch() {
        isTorchOn = !isTorchOn
        camera?.cameraControl?.enableTorch(isTorchOn)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            toggleTorch()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleResult(raw: String) {
        val prefs = getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
        val autoCopy = prefs.getBoolean("auto_copy", true)
        val autoClose = prefs.getBoolean("auto_close", false)
        val vibrate = prefs.getBoolean("vibrate", true)

        if (vibrate) triggerHaptic()

        val parts = raw.split("|", limit = 2)
        val format = parts.getOrNull(0) ?: "KOD"
        val content = parts.getOrNull(1) ?: ""

        val isWifi = content.startsWith("WIFI:", ignoreCase = true)
        val isUrl = content.startsWith("http://", ignoreCase = true) || content.startsWith("https://", ignoreCase = true)
        val isProductBarcode = format.contains("EAN") || format.contains("UPC") || format.contains("CODE_")

        val wifiData = if (isWifi) parseWifiQr(content) else null

        // 1. Otomatik Kopyalama Açıksa sormadan kopyala
        if (autoCopy) {
            val textToCopy = wifiData?.second ?: content
            copyToClipboard(textToCopy, showToast = autoClose)
        }

        // 2. Otomatik Kapanma Açıksa
        if (autoClose) {
            if (!autoCopy) {
                copyToClipboard(wifiData?.second ?: content, showToast = true)
            }
            finish()
            return
        }

        // 3. Otomatik Kapanma Kapalıysa: Kullanıcıya Sor
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setCancelable(false)

        val copyNote = if (autoCopy) "\n\n✓ Otomatik olarak panoya kopyalandı." else ""

        when {
            isUrl -> {
                dialogBuilder.setTitle("Bağlantı (Link) Okundu")
                dialogBuilder.setMessage("$content$copyNote\n\nBu bağlantı açılsın mı?")
                dialogBuilder.setPositiveButton("Linki Aç") { _, _ ->
                    openExternalUrl(content)
                }
                if (!autoCopy) {
                    dialogBuilder.setNeutralButton("Kopyala") { _, _ ->
                        copyToClipboard(content, showToast = true)
                        hasScanned = false
                    }
                }
                dialogBuilder.setNegativeButton("Yeni Tarama") { _, _ -> hasScanned = false }
            }

            isWifi && wifiData != null -> {
                val (ssid, pass, authType) = wifiData
                val passCopyNote = if (autoCopy && pass.isNotEmpty()) "\n\n✓ Şifre panoya kopyalandı." else ""
                dialogBuilder.setTitle("Wi-Fi Ağı Algılandı")
                dialogBuilder.setMessage("Ağ Adı (SSID): $ssid\nŞifre: ${if (pass.isEmpty()) "(Şifresiz)" else pass}$passCopyNote\n\nBu ağa bağlanılsın mı?")
                dialogBuilder.setPositiveButton("Ağa Bağlan") { _, _ ->
                    connectToWifi(ssid, pass, authType)
                }
                if (!autoCopy && pass.isNotEmpty()) {
                    dialogBuilder.setNeutralButton("Şifreyi Kopyala") { _, _ ->
                        copyToClipboard(pass, showToast = true)
                        hasScanned = false
                    }
                }
                dialogBuilder.setNegativeButton("İptal") { _, _ -> hasScanned = false }
            }

            isProductBarcode -> {
                dialogBuilder.setTitle("Barkod Okundu ($format)")
                dialogBuilder.setMessage("$content$copyNote")
                dialogBuilder.setPositiveButton("Google'da Ara") { _, _ ->
                    openExternalUrl("https://www.google.com/search?q=${Uri.encode(content)}")
                }
                if (!autoCopy) {
                    dialogBuilder.setNeutralButton("Kopyala") { _, _ ->
                        copyToClipboard(content, showToast = true)
                        hasScanned = false
                    }
                }
                dialogBuilder.setNegativeButton("Yeni Tarama") { _, _ -> hasScanned = false }
            }

            else -> {
                dialogBuilder.setTitle("Kod Okundu ($format)")
                dialogBuilder.setMessage("$content$copyNote")
                if (!autoCopy) {
                    dialogBuilder.setPositiveButton("Kopyala") { _, _ ->
                        copyToClipboard(content, showToast = true)
                        hasScanned = false
                    }
                } else {
                    dialogBuilder.setPositiveButton("Paylaş") { _, _ ->
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, content)
                            type = "text/plain"
                        }
                        runUnlocked { startActivity(Intent.createChooser(sendIntent, "Paylaş")) }
                        hasScanned = false
                    }
                }
                dialogBuilder.setNegativeButton("Yeni Tarama") { _, _ -> hasScanned = false }
            }
        }

        dialogBuilder.show()
    }

    private fun copyToClipboard(text: String, showToast: Boolean) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Scanned Code", text))
        if (showToast) {
            Toast.makeText(this, "Panoya kopyalandı: $text", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExternalUrl(url: String) {
        runUnlocked {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show()
                hasScanned = false
            }
        }
    }

    // WIFI:S:AğAdı;T:WPA;P:Şifre;; formatını ayrıştırır
    private fun parseWifiQr(raw: String): Triple<String, String, String> {
        var ssid = ""
        var pass = ""
        var type = "WPA"
        val body = raw.substringAfter("WIFI:", "")
        val tokens = body.split(";")
        for (token in tokens) {
            when {
                token.startsWith("S:", ignoreCase = true) -> ssid = token.substring(2)
                token.startsWith("P:", ignoreCase = true) -> pass = token.substring(2)
                token.startsWith("T:", ignoreCase = true) -> type = token.substring(2)
            }
        }
        return Triple(ssid, pass, type)
    }

    private fun connectToWifi(ssid: String, pass: String, type: String) {
        if (pass.isNotEmpty()) copyToClipboard(pass, showToast = false)

        runUnlocked {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ Doğrudan Sistem Wi-Fi Ekleme Penceresi
                val suggestionBuilder = WifiNetworkSuggestion.Builder().setSsid(ssid)
                if (pass.isNotEmpty()) {
                    if (type.contains("WPA3", ignoreCase = true)) {
                        suggestionBuilder.setWpa3Passphrase(pass)
                    } else {
                        suggestionBuilder.setWpa2Passphrase(pass)
                    }
                }
                val suggestions = arrayListOf(suggestionBuilder.build())
                val bundle = Bundle().apply {
                    putParcelableArrayList(Settings.EXTRA_WIFI_NETWORK_LIST, suggestions)
                }
                val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                    putExtras(bundle)
                }
                try {
                    startActivity(intent)
                    finish()
                    return@runUnlocked
                } catch (_: Exception) {}
            }

            // Eski sürümler için şifreyi kopyalayıp Wi-Fi ayarlarını aç
            Toast.makeText(this, "Şifre kopyalandı! '$ssid' ağına dokunup yapıştırın.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            finish()
        }
    }

    // Kilit ekranındayken dış uygulama açılacaksa önce kilidi açtırır
    private fun runUnlocked(action: () -> Unit) {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    action()
                }
            })
        } else {
            action()
        }
    }

    private fun decodeImageUri(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
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
            if (result.isNotEmpty()) {
                handleResult(result)
            } else {
                Toast.makeText(this, "Görselde kod bulunamadı", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Görsel okunamadı", Toast.LENGTH_SHORT).show()
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