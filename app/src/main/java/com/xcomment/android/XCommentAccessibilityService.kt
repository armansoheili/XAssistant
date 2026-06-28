package com.xcomment.android

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import androidx.core.content.ContextCompat
import com.xcomment.android.ai.AiClient
import com.xcomment.android.ai.AiResult
import com.xcomment.android.data.SettingsStore
import com.xcomment.android.data.Tone
import java.util.concurrent.Executors

/**
 * Accessibility service that shows a floating AI bubble when the user is in X (Twitter).
 * When tapped, it reads the visible post, generates reply ideas via AI, and inserts the
 * selected idea into the focused reply box (or copies to clipboard if no box is focused).
 */
class XCommentAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var store: SettingsStore

    private var bubble: View? = null
    private var panel: LinearLayout? = null
    private var requestId = 0
    private var isInXApp = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        store = SettingsStore(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg.isBlank()) return
        // Ignore events from our own overlay or system UI
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return
        val inX = isXApp(pkg)
        if (inX) {
            isInXApp = true
            showBubble()
        } else {
            isInXApp = false
            hideBubbleAndPanel()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        hideBubbleAndPanel()
        executor.shutdown()
    }

    /** Returns true if [pkg] is X/Twitter. */
    private fun isXApp(pkg: String): Boolean =
        pkg == "com.twitter.android" || pkg == "com.x.android"

    // ————————————————————————————————————————————————————————————————————————————————————————————
    // Bubble
    // ————————————————————————————————————————————————————————————————————————————————————————————

    private fun showBubble() {
        if (bubble != null) return
        val view = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bubble_bg)
            elevation = dp(8f)
            val icon = ImageView(this@XCommentAccessibilityService).apply {
                setImageResource(R.drawable.ic_ai_sparkle)
                setColorFilter(ContextCompat.getColor(context, android.R.color.white))
            }
            addView(icon, FrameLayout.LayoutParams(dp(28), dp(28)).apply { gravity = Gravity.CENTER })
            setOnClickListener { openPanel() }
        }
        val params = overlayParams(dp(56), dp(56)).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(160)
        }
        enableDragging(view, params)
        runCatching { windowManager.addView(view, params) }
        bubble = view
    }

    private fun hideBubbleAndPanel() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        bubble?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        bubble = null
    }

    // ————————————————————————————————————————————————————————————————————————————————————————————
    // Panel
    // ————————————————————————————————————————————————————————————————————————————————————————————

    private fun openPanel() {
        val thisRequest = ++requestId
        panel?.let { runCatching { windowManager.removeView(it) } }

        val settings = store.load()
        val rootText = extractVisibleText()
        val post = guessTweet(rootText)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundResource(R.drawable.panel_bg)
            elevation = dp(12f)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_ai_sparkle)
            setColorFilter(ContextCompat.getColor(context, R.color.brand_blue))
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
        val title = TextView(this).apply {
            text = getString(R.string.overlay_title)
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(10), 0, 0, 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2).apply { weight = 1f })
        container.addView(header)

        // Detected post preview
        val detected = TextView(this).apply {
            text = if (post.isBlank()) {
                getString(R.string.overlay_no_text)
            } else {
                post.take(160) + if (post.length > 160) "…" else ""
            }
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, dp(10), 0, dp(14))
        }
        container.addView(detected)

        // Tone selector
        val toneRow = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val toneChips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var selectedTone = settings.tone
        val toneButtons = mutableListOf<Button>()

        fun refreshToneButtons() {
            toneButtons.forEachIndexed { i, btn ->
                val tone = Tone.entries[i]
                btn.setBackgroundResource(
                    if (tone == selectedTone) R.drawable.chip_selected_bg else R.drawable.chip_bg
                )
                btn.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (tone == selectedTone) R.color.chip_text_selected else R.color.chip_text
                    )
                )
            }
        }

        Tone.entries.forEach { tone ->
            val btn = Button(this).apply {
                text = getString(tone.labelRes)
                textSize = 13f
                isAllCaps = false
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setOnClickListener {
                    selectedTone = tone
                    store.saveTone(tone)
                    refreshToneButtons()
                }
            }
            toneButtons.add(btn)
            toneChips.addView(btn, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        refreshToneButtons()
        toneRow.addView(toneChips)
        container.addView(toneRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        // Ideas list
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(list)

        // Status text
        val status = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, dp(6), 0, dp(12))
        }
        container.addView(status)

        // Bottom row
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val closeBtn = Button(this).apply {
            text = getString(R.string.overlay_close)
            isAllCaps = false
            setBackgroundResource(R.drawable.button_secondary_bg)
            setOnClickListener {
                panel?.let { runCatching { windowManager.removeView(it) } }
                panel = null
            }
        }
        bottomRow.addView(closeBtn, LinearLayout.LayoutParams(0, -2).apply { weight = 1f; rightMargin = dp(8) })
        val regenBtn = Button(this).apply {
            text = getString(R.string.overlay_regenerate)
            isAllCaps = false
            setBackgroundResource(R.drawable.button_primary_bg)
            setOnClickListener { openPanel() }
        }
        bottomRow.addView(regenBtn, LinearLayout.LayoutParams(0, -2).apply { weight = 1f })
        container.addView(bottomRow)

        val params = overlayParams(dp(340), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(100)
        }
        runCatching { windowManager.addView(container, params) }
        panel = container

        // Instant suggestions
        if (post.isNotBlank()) {
            renderIdeas(list, instantIdeas(post))
            status.text = getString(R.string.overlay_instant)
        }

        // AI suggestions
        if (post.isNotBlank()) {
            executor.execute {
                val result = AiClient.generate(post, settings.copy(tone = selectedTone))
                main.post {
                    if (panel !== container || thisRequest != requestId) return@post
                    when (result) {
                        is AiResult.Success -> {
                            renderIdeas(list, result.ideas)
                            status.text = getString(R.string.overlay_ready)
                            status.setTextColor(ContextCompat.getColor(this, R.color.brand_blue))
                        }
                        is AiResult.Failure -> {
                            status.text = result.message
                            status.setTextColor(ContextCompat.getColor(this, R.color.error_red))
                        }
                    }
                }
            }
        }
    }

    private fun renderIdeas(container: LinearLayout, ideas: List<String>) {
        container.removeAllViews()
        ideas.forEach { idea ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundResource(R.drawable.idea_card_bg)
                isClickable = true
                isFocusable = true
                setOnClickListener { insertIdea(idea) }
            }
            val text = TextView(this).apply {
                this.text = idea
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setLineSpacing(dp(2f), 1f)
            }
            card.addView(text)
            container.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun instantIdeas(post: String): List<String> = listOf(
        "Interesting perspective! 🤔",
        "Thanks for sharing this.",
        "I'd love to hear more about this.",
    ).shuffled().take(2)

    // ————————————————————————————————————————————————————————————————————————————————————————————
    // Insertion
    // ————————————————————————————————————————————————————————————————————————————————————————————

    private fun insertIdea(text: String) {
        val root = rootInActiveWindow ?: return
        val editText = findFocusedEditText(root)
        if (editText != null) {
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            toast(getString(R.string.overlay_inserted))
        } else {
            copyToClipboard(text)
            toast(getString(R.string.overlay_copied))
        }
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
    }

    private fun findFocusedEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isFocused && root.isEditable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findFocusedEditText(child)
            if (result != null) return result
        }
        return null
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("XComment reply", text))
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    // ————————————————————————————————————————————————————————————————————————————————————————————
    // Text extraction
    // ————————————————————————————————————————————————————————————————————————————————————————————

    private fun extractVisibleText(): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo?) {
            node ?: return
            val txt = node.text?.toString()?.trim()
            if (!txt.isNullOrBlank()) out.add(txt)
            val desc = node.contentDescription?.toString()?.trim()
            if (!desc.isNullOrBlank()) out.add(desc)
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(rootInActiveWindow)
        return out.distinct()
    }

    private fun guessTweet(texts: List<String>): String {
        val noise = setOf("reply", "repost", "like", "view", "post", "search", "home", "messages",
            "notifications", "premium", "following", "for you", "share", "close", "back", "menu")
        return texts
            .asSequence()
            .map { it.replace("\n", " ").trim() }
            .filter { it.length in 20..700 }
            .filter { t -> noise.none { t.equals(it, true) || t.contains("$it button", true) } }
            .maxByOrNull { it.length }
            .orEmpty()
    }

    // ————————————————————————————————————————————————————————————————————————————————————————————
    // Overlay helpers
    // ————————————————————————————————————————————————————————————————————————————————————————————

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )

    private fun enableDragging(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) moved = true
                    params.x = startX - dx
                    params.y = startY + dy
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}