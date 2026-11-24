package com.example.fbtv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/** Draws the on-screen pointer. */
class CursorView(ctx: Context) : View(ctx) {
    var cx = 200f
    var cy = 200f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    override fun onDraw(c: Canvas) {
        c.drawCircle(cx, cy, 16f, fill)
        c.drawCircle(cx, cy, 16f, ring)
    }
}

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var cursor: CursorView
    private val startUrl = "https://m.facebook.com"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        cursor = CursorView(this)
        val root = FrameLayout(this)
        val lp = ViewGroup.LayoutParams(-1, -1)
        root.addView(web, lp)
        root.addView(cursor, lp)
        setContentView(root)

        web.isFocusable = false          // we handle the remote ourselves
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            // Pretend to be a normal mobile Chrome so Facebook allows login
            userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.webViewClient = WebViewClient()   // keep navigation inside the app
        web.webChromeClient = WebChromeClient()

        root.post {
            cursor.cx = root.width / 2f
            cursor.cy = root.height / 2f
            cursor.invalidate()
        }
        web.loadUrl(startUrl)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        // Speed up the longer a key is held
        val step = 25f + minOf(e.repeatCount, 20) * 6f
        val edge = 60f

        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) move(-step, 0f); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) move(step, 0f); return true }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (down) {
                    if (cursor.cy <= edge) web.scrollBy(0, -(step * 4).toInt()) else move(0f, -step)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (down) {
                    if (cursor.cy >= web.height - edge) web.scrollBy(0, (step * 4).toInt()) else move(0f, step)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (down && e.repeatCount == 0) click()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (down) { if (web.canGoBack()) web.goBack() else finish() }
                return true
            }
            KeyEvent.KEYCODE_MENU -> {          // Menu button = go home
                if (down) web.loadUrl(startUrl)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (down) web.pageUp(false)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (down) web.pageDown(false)
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    private fun move(dx: Float, dy: Float) {
        cursor.cx = (cursor.cx + dx).coerceIn(0f, web.width.toFloat())
        cursor.cy = (cursor.cy + dy).coerceIn(0f, web.height.toFloat())
        cursor.invalidate()
    }

    /** Sends a real tap at the cursor position. */
    private fun click() {
        val t = SystemClock.uptimeMillis()
        val d = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cursor.cx, cursor.cy, 0)
        val u = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, cursor.cx, cursor.cy, 0)
        web.dispatchTouchEvent(d)
        web.dispatchTouchEvent(u)
        d.recycle(); u.recycle()
    }

    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush() }
}
