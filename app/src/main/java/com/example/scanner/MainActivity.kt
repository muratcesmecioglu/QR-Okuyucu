package com.example.scanner

import android.Manifest
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
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
import org.json.JSONArray
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private var camera: Camera? = null
    private var isTorchOn = false
    private var isZoomed = false
    private var hasScanned = false
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var gestureDetector: GestureDetector
    private lateinit var previewView: PreviewView
    private lateinit var permissionContainer: LinearLayout
    private lateinit var historyListLayout: LinearLayout
    private lateinit var historyWrapper: LinearLayout

    // Karenin ekran genişliğine oranı (%80)
    private val RETICLE_RATIO = 0.80f

    @Volatile
    private var viewWidth = 1
    @Volatile
    private var viewHeight = 1

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

        previewView = PreviewView(this).apply {
            addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                viewWidth = max(1, right - left)
                viewHeight = max(1, bottom - top)
            }
        }
        root.addView(previewView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Karenin dışını karartan ve ortada %80'lik net alan bırakan katman
        val overlayView = object : View(this) {
            private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#88000000")
            }
            private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            private val rect = RectF()

            init {
                setLayerType(LAYER_TYPE_SOFTWARE, null)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
                val size = width * RETICLE_RATIO
                val left = (width - size) / 2f
                val top = (height - size) / 2f
                rect.set(left, top, left + size, top + size)
                canvas.drawRoundRect(rect, 36f, 36f, clearPaint)
                canvas.drawRoundRect(rect, 36f, 36f, borderPaint)
            }
        }
        root.addView(overlayView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

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

        // Alt Kısım: Son 3 Okuma Paneli
        historyWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 28, 36, 40)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC121212"))
                cornerRadii = floatArrayOf(48f, 48f, 48f, 48f, 0f, 0f, 0f, 0f)
            }
            visibility = View.GONE
        }

        val historyTitle = TextView(this).apply {
            text = "SON OKUNANLAR (İşlem için dokun)"
            textSize = 12f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(8, 0, 8, 12)
        }
        historyListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        historyWrapper.addView(historyTitle)
        historyWrapper.addView(historyListLayout)

        root.addView(historyWrapper, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))

        // Kamera izni reddedildiğinde gösterilecek ekran
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

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                isZoomed = !isZoomed
                camera?.cameraControl?.setZoomRatio(if (isZoomed) 2.5f else 1.0f)
                return true
            }
        })
        previewView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }

        updateHistoryUI()
    }

    private fun createTopButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(32, 18, 32, 18)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#99000000"))
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
        hasScanned = false
        updateHistoryUI()
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
                    val cropped = extractReticleSquare(imageProxy)
                    if (cropped != null) {
                        val (bytes, side) = cropped
                        val result = ScannerBridge.decodeFrame(bytes, side, side)
                        if (result.isNotEmpty()) {
                            hasScanned = true
                            runOnUiThread { handleResult(result) }
                        }
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

    // Sadece ekrandaki karenin içine denk gelen pikselleri kırpar
    private fun extractReticleSquare(imageProxy: ImageProxy): Pair<ByteArray, Int>? {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val imgW = imageProxy.width
        val imgH = imageProxy.height

        val rot = imageProxy.imageInfo.rotationDegrees
        val uprightW = if (rot == 90 || rot == 270) imgH else imgW
        val uprightH = if (rot == 90 || rot == 270) imgW else imgH

        val vw = max(1, viewWidth).toFloat()
        val vh = max(1, viewHeight).toFloat()

        // PreviewView FILL_CENTER ölçekleme hesabı
        val scale = max(vw / uprightW, vh / uprightH)
        val reticleScreenPx = vw * RETICLE_RATIO
        val cropSide = (reticleScreenPx / scale).toInt().coerceIn(64, min(imgW, imgH))

        val startX = (imgW - cropSide) / 2
        val startY = (imgH - cropSide) / 2

        val out = ByteArray(cropSide * cropSide)
        for (row in 0 until cropSide) {
            val offset = (startY + row) * rowStride + startX
            if (offset + cropSide <= buffer.limit()) {
                buffer.position(offset)
                buffer.get(out, row * cropSide, cropSide)
            } else {
                return null
            }
        }
        return Pair(out, cropSide)
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

    // Son 3 okumayı kaydetme ve güncelleme
    private fun saveToHistory(raw: String) {
        val prefs = getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
        val current = getHistoryList().toMutableList()
        current.remove(raw)
        current.add(0, raw)
        val top3 = current.take(3)

        val json = JSONArray()
        top3.forEach { json.put(it) }
        prefs.edit().putString("history_json", json.toString()).apply()

        updateHistoryUI()
    }

    private fun getHistoryList(): List<String> {
        val prefs = getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
        val str = prefs.getString("history_json", "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val json = JSONArray(str)
            for (i in 0 until json.length()) {
                list.add(json.getString(i))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun updateHistoryUI() {
        val items = getHistoryList()
        if (items.isEmpty()) {
            historyWrapper.visibility = View.GONE
            return
        }

        historyWrapper.visibility = View.VISIBLE
        historyListLayout.removeAllViews()

        for (raw in items) {
            val parts = raw.split("|", limit = 2)
            val content = parts.getOrNull(1) ?: raw

            val isWifi = content.startsWith("WIFI:", ignoreCase = true)
            val isUrl = content.startsWith("http://", ignoreCase = true) || content.startsWith("https://", ignoreCase = true)

            val icon = when {
                isUrl -> "🔗"
                isWifi -> "📶"
                else -> "📝"
            }

            val displayText = if (isWifi) {
                val (ssid, _, _) = parseWifiQr(content)
                "Wi-Fi: $ssid"
            } else {
                content.replace("\n", " ")
            }

            val itemView = TextView(this).apply {
                text = "$icon  $displayText"
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(28, 22, 28, 22)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#33FFFFFF"))
                    cornerRadius = 24f
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 8) }
                layoutParams = params

                setOnClickListener {
                    onHistoryItemClicked(raw)
                }
            }
            historyListLayout.addView(itemView)
        }
    }

    // Alt listedeki son 3 okumadan birine tıklandığında çalışır
    private fun onHistoryItemClicked(raw: String) {
        val prefs = getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("vibrate", true)) triggerHaptic()
        val parts = raw.split("|", limit = 2)
        val content = parts.getOrNull(1) ?: raw

        val isWifi = content.startsWith("WIFI:", ignoreCase = true)
        val isUrl = content.startsWith("http://", ignoreCase = true) || content.startsWith("https://", ignoreCase = true)

        when {
            isUrl -> {
                hasScanned = true
                AlertDialog.Builder(this)
                    .setTitle("Bağlantı İşlemi")
                    .setMessage("$content\n\nBu bağlantı açılsın mı yoksa kopyalansın mı?")
                    .setPositiveButton("Linki Aç") { _, _ -> openExternalUrl(content) }
                    .setNeutralButton("Kopyala") { _, _ ->
                        copyToClipboard(content, showToast = true)
                        hasScanned = false
                    }
                    .setNegativeButton("İptal") { _, _ -> hasScanned = false }
                    .setOnCancelListener { hasScanned = false }
                    .show()
            }
            isWifi -> {
                hasScanned = true
                val (ssid, pass, authType) = parseWifiQr(content)
                AlertDialog.Builder(this)
                    .setTitle("Wi-Fi Ağı")
                    .setMessage("Ağ Adı (SSID): $ssid\nŞifre: ${if (pass.isEmpty()) "(Şifresiz)" else pass}\n\nBu ağa bağlanılsın mı?")
                    .setPositiveButton("Ağa Bağlan") { _, _ -> connectToWifi(ssid, pass, authType) }
                    .setNeutralButton("Şifreyi Kopyala") { _, _ ->
                        copyToClipboard(pass, showToast = true)
                        hasScanned = false
                    }
                    .setNegativeButton("İptal") { _, _ -> hasScanned = false }
                    .setOnCancelListener { hasScanned = false }
                    .show()
            }
            else -> {
                // Sadece yazı ise soru sormadan direkt kopyala
                copyToClipboard(content, showToast = true)
            }
        }
    }

    // Kamera yeni bir kod okuduğunda çalışır
    private fun handleResult(raw: String) {
        saveToHistory(raw)

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

        if (autoCopy) {
            val textToCopy = wifiData?.second ?: content
            copyToClipboard(textToCopy, showToast = autoClose)
        }

        if (autoClose) {
            if (!autoCopy) {
                copyToClipboard(wifiData?.second ?: content, showToast = true)
            }
            finish()
            return
        }

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

            Toast.makeText(this, "Şifre kopyalandı! '$ssid' ağına dokunup yapıştırın.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            finish()
        }
    }

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
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }

            if (!vibrator.hasVibrator()) return

            // 100ms net hissedilir titreşim
            val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = android.os.VibrationAttributes.createForUsage(
                    android.os.VibrationAttributes.USAGE_NOTIFICATION
                )
                vibrator.vibrate(effect, attrs)
            } else {
                vibrator.vibrate(effect)
            }
        } catch (_: Exception) {}
    }
}
