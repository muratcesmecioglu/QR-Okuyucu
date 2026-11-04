package com.example.scanner

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 64, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Ayarlar"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 48)
        }
        root.addView(title)

        // 1. Okuma sonrası otomatik kopyalama
        val (autoCopyRow, _) = createSwitchRow(
            title = "Okuma sonrası otomatik kopyala",
            subtitle = "Açık olduğunda okunan kodu sormadan panoya kopyalar",
            checked = prefs.getBoolean("auto_copy", true)
        ) { isChecked ->
            prefs.edit().putBoolean("auto_copy", isChecked).apply()
        }
        root.addView(autoCopyRow)

        val isAutoCloseEnabled = prefs.getBoolean("auto_close", false)

        // 3. Alt Ayar: Linkleri otomatik aç (Okuma sonrası kapansın'a bağlı)
        val (autoOpenUrlRow, autoOpenUrlSwitch) = createSwitchRow(
            title = "↳ Linkleri otomatik aç",
            subtitle = "Açık olduğunda linkleri tarayıcıda açar, kapalıysa kopyalayıp çıkar",
            checked = prefs.getBoolean("auto_open_url", false)
        ) { isChecked ->
            prefs.edit().putBoolean("auto_open_url", isChecked).apply()
        }
        autoOpenUrlRow.setPadding(36, 24, 0, 32)

        // İlk açılışta aktiflik durumunu ayarla
        autoOpenUrlSwitch.isEnabled = isAutoCloseEnabled
        autoOpenUrlRow.alpha = if (isAutoCloseEnabled) 1.0f else 0.4f

        // 2. Okuma sonrası kapansın
        val (autoCloseRow, _) = createSwitchRow(
            title = "Okuma sonrası kapansın",
            subtitle = "Açık olduğunda soru sormadan işlemi yapıp uygulamayı kapatır",
            checked = isAutoCloseEnabled
        ) { isChecked ->
            prefs.edit().putBoolean("auto_close", isChecked).apply()
            // Alt ayarı dinamik olarak aktif/pasif yap
            autoOpenUrlSwitch.isEnabled = isChecked
            autoOpenUrlRow.alpha = if (isChecked) 1.0f else 0.4f
        }

        root.addView(autoCloseRow)
        root.addView(autoOpenUrlRow)

        // 4. Titreşim
        val (vibrateRow, _) = createSwitchRow(
            title = "Okumada titreşim",
            subtitle = "Kod okunduğunda fiziksel titreşim verir",
            checked = prefs.getBoolean("vibrate", true)
        ) { isChecked ->
            prefs.edit().putBoolean("vibrate", isChecked).apply()
        }
        root.addView(vibrateRow)

        setContentView(root)
    }

    private fun createSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): Pair<LinearLayout, SwitchCompat> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 32, 0, 32)
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(Color.WHITE)
        }

        val subView = TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, 6, 24, 0)
        }

        textContainer.addView(titleView)
        textContainer.addView(subView)

        val switchView = SwitchCompat(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        }

        row.addView(textContainer)
        row.addView(switchView)
        return Pair(row, switchView)
    }
}