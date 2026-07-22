package com.jarvis.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisA11y"
        var instance: JarvisAccessibilityService? = null
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Jarvis Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
    }

    fun executeCommand(command: String, callback: (String) -> Unit) {
        scope.launch {
            try {
                val result = processCommand(command)
                callback(result)
            } catch (e: Exception) {
                callback("Error: ${e.message}")
            }
        }
    }

    fun tapByText(text: String): Boolean {
        val node = findNodeByText(text) ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        return true
    }

    fun tapByContentDesc(desc: String): Boolean {
        val node = findNodeByContentDesc(desc) ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        return true
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = findFocusedEditable(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        field.recycle()
        root.recycle()
        return result
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300) {
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun swipeUp() {
        val d = resources.displayMetrics
        swipe(d.widthPixels / 2f, d.heightPixels * 0.7f, d.widthPixels / 2f, d.heightPixels * 0.3f)
    }

    fun swipeDown() {
        val d = resources.displayMetrics
        swipe(d.widthPixels / 2f, d.heightPixels * 0.3f, d.widthPixels / 2f, d.heightPixels * 0.7f)
    }

    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "Cannot read screen"
        val sb = StringBuilder()
        collectText(root, sb, 0)
        root.recycle()
        return sb.toString().take(500)
    }

    private suspend fun processCommand(cmd: String): String {
        val lower = cmd.lowercase().trim()
        return when {
            lower.contains("open") || lower.contains("launch") -> {
                val app = cmd.replace(Regex("open|launch|start", RegexOption.IGNORE_CASE), "").trim()
                launchApp(app)
            }
            lower.contains("tap") || lower.contains("click") || lower.contains("press") -> {
                val target = cmd.replace(Regex("tap|click|press|on|the", RegexOption.IGNORE_CASE), "").trim()
                if (tapByText(target)) "Tapped '$target'"
                else if (tapByContentDesc(target)) "Tapped '$target' by label"
                else "Could not find '$target' on screen"
            }
            lower.contains("type") || lower.contains("write") || lower.contains("enter") -> {
                val text = cmd.replace(Regex("type|write|enter", RegexOption.IGNORE_CASE), "").trim()
                if (typeText(text)) "Typed '$text'"
                else "Could not type - no text field focused"
            }
            lower.contains("scroll down") || lower.contains("swipe up") -> { swipeUp(); "Scrolled down" }
            lower.contains("scroll up") || lower.contains("swipe down") -> { swipeDown(); "Scrolled up" }
            lower.contains("go back") || lower == "back" -> { if (pressBack()) "Went back" else "Could not go back" }
            lower.contains("go home") || lower == "home" -> { if (pressHome()) "Went home" else "Could not go home" }
            lower.contains("recents") -> { if (pressRecents()) "Opened recent apps" else "Could not open recents" }
            lower.contains("read") || lower.contains("what's on screen") -> readScreen()
            lower.contains("whatsapp") || lower.contains("message") || lower.contains("text") -> handleMessaging(cmd)
            else -> "Command understood: '$cmd'"
        }
    }

    private fun launchApp(appName: String): String {
        val pm = packageManager
        val packageName = when (appName.lowercase()) {
            "whatsapp" -> "com.whatsapp"
            "instagram" -> "com.instagram.android"
            "youtube" -> "com.google.android.youtube"
            "spotify" -> "com.spotify.music"
            "chrome" -> "com.android.chrome"
            "gmail" -> "com.google.android.gm"
            "maps" -> "com.google.android.apps.maps"
            "settings" -> "com.android.settings"
            "camera" -> "com.android.camera"
            "gallery", "photos" -> "com.google.android.apps.photos"
            "phone", "dialer" -> "com.google.android.dialer"
            "messages", "sms" -> "com.google.android.apps.messaging"
            "calculator" -> "com.google.android.calculator"
            "calendar" -> "com.google.android.calendar"
            "clock" -> "com.google.android.deskclock"
            "contacts" -> "com.google.android.contacts"
            "play store" -> "com.android.vending"
            "twitter", "x" -> "com.twitter.android"
            "facebook" -> "com.facebook.katana"
            "snapchat" -> "com.snapchat.android"
            "telegram" -> "org.telegram.messenger"
            "netflix" -> "com.netflix.mediaclient"
            "zomato" -> "com.application.zomato"
            "swiggy" -> "in.swiggy.android"
            else -> null
        }
        if (packageName != null) {
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return "Opened $appName"
            }
        }
        return try {
            val si = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra("query", appName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            startActivity(si)
            "Searching for $appName"
        } catch (e: Exception) { "Could not open $appName" }
    }

    private suspend fun handleMessaging(cmd: String): String {
        launchApp("WhatsApp")
        delay(1500)
        val m = Regex("(?:to|message|text|msg)\\s+(\\w+)", RegexOption.IGNORE_CASE).find(cmd)
        val contact = m?.groupValues?.get(1) ?: ""
        if (contact.isNotEmpty()) {
            tapByContentDesc("Search") || tapByText("Search")
            delay(500); typeText(contact); delay(800)
            tapByText(contact); delay(600)
            val body = cmd.replace(Regex("(?:send|message|text|whatsapp).*?(?:to\\s+)?\\w+\\s*", RegexOption.IGNORE_CASE), "").trim()
            if (body.isNotEmpty()) { typeText(body); delay(300); tapByContentDesc("Send") || tapByText("Send"); return "Sent '$body' to $contact" }
            return "Opened chat with $contact"
        }
        return "Opened WhatsApp"
    }

    private fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        root.recycle()
        return nodes.firstOrNull { it.isClickable || it.isEnabled }
    }

    private fun findNodeByContentDesc(desc: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val r = findNodeByCD(root, desc.lowercase())
        root.recycle()
        return r
    }

    private fun findNodeByCD(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.lowercase()?.contains(desc) == true && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val f = findNodeByCD(child, desc)
            if (f != null) return f
        }
        return null
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val f = findFocusedEditable(child)
            if (f != null) return f
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 15) return
        val t = node.text?.toString()
        val d = node.contentDescription?.toString()
        if (!t.isNullOrBlank() && t.length > 1) sb.appendLine(t)
        if (!d.isNullOrBlank() && d.length > 1 && d != t) sb.appendLine("[$d]")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb, depth + 1) }
        }
    }
}