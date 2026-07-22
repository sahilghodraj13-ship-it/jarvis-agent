package com.jarvis.agent

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            val voiceIntent = Intent(this, VoiceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(voiceIntent)
            else startService(voiceIntent)
            startService(Intent(this, OverlayService::class.java))
            findViewById<TextView>(R.id.tv_status).text = "Jarvis is running"
            findViewById<Button>(R.id.btn_start).isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        val a11yEnabled = isAccessibilityServiceEnabled()
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        findViewById<TextView>(R.id.tv_a11y_status).text = if (a11yEnabled) "Accessibility: ENABLED" else "Accessibility: DISABLED"
        findViewById<TextView>(R.id.tv_overlay_status).text = if (overlayGranted) "Overlay: GRANTED" else "Overlay: NEEDED"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/.JarvisAccessibilityService"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(service)
    }
}