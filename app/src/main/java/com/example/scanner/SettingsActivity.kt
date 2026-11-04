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
        root.addView(createSwitchRow(
            title = "Okuma sonrası otomatik kopyala",
            subtitle = "Açık olduğunda okunan kodu sormadan panoya kopyalar",
            checked = prefs.getBoolean("auto_copy", true)
        ) { isChecked ->
            prefs.edit().putBoolean("auto_copy", isChecked).apply()
        })

        // 2. Okuma sonrası kapansın
        root.addView(createSwitchRow(
            title = "Okuma sonrası kapansın",
            subtitle = "Açık olduğunda soru sormadan işlemi yapıp uygulamayı kapatır",
            checked = prefs.getBoolean("auto_close", false)
        ) { isChecked ->
            prefs.edit().putBoolean("auto_close", isChecked).apply()
        })

        // 3. Titreşim
        root.addView(createSwitchRow(
            title = "Okumada titreşim",
            subtitle = "Kod okunduğunda hafif titreşim verir",
            checked = prefs.getBoolean("vibrate", true)
        ) { isChecked ->
            prefs.edit().putBoolean("vibrate", isChecked).apply()
        })

        setContentView(root)
    }

    private fun createSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
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
        return row
    }
}