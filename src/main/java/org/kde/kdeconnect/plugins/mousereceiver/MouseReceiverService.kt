/*
 * SPDX-FileCopyrightText: 2021 SohnyBohny <sohny.bean@streber24.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.mousereceiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.annotation.SuppressLint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.plugins.inputdevicesreceiver.InputDevicesReceiverPlugin
import org.kde.kdeconnect_tp.R
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.sign

@SuppressLint("AccessibilityPolicy")
class MouseReceiverService : AccessibilityService() {
    private lateinit var cursorView: View
    private lateinit var cursorLayout: WindowManager.LayoutParams
    private var windowManager: WindowManager? = null
    private val runHandler: Handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    private var swipeStroke: StrokeDescription? = null
    private var scrollSum = 0.0

    override fun onCreate() {
        instanceRef = WeakReference(this)
        Log.i("MouseReceiverService", "created")
    }

    override fun onServiceConnected() {
        windowManager = ContextCompat.getSystemService(this, WindowManager::class.java)
        cursorView = View.inflate(baseContext, R.layout.mouse_receiver_cursor, null)
        cursorLayout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS),
            PixelFormat.TRANSLUCENT
        )

        // Create an overlay and display the cursor
        val (height, width) = getHeightWidth()

        // allow cursor to move over status bar on devices having a display cutout
        // https://developer.android.com/guide/topics/display-cutout/#render_content_in_short_edge_cutout_areas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cursorLayout.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        cursorLayout.gravity = Gravity.START or Gravity.TOP
        cursorLayout.x = width / 2
        cursorLayout.y = height / 2

        windowManager!!.addView(cursorView, cursorLayout)

        hideRunnable = Runnable {
            cursorView.visibility = View.GONE
            Log.i("MouseReceiverService", "Hiding pointer due to inactivity")
        }

        cursorView.visibility = View.GONE
    }

    private fun getHeightWidth(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = windowManager!!.maximumWindowMetrics
        metrics.bounds.height() to metrics.bounds.width()
    } else {
        val metrics = DisplayMetrics()
        windowManager!!.defaultDisplay.getMetrics(metrics)
        metrics.heightPixels to metrics.widthPixels
    }

    private fun hideAfter5Seconds() {
        hide(5000)
    }

    fun hide(delayMillis: Int) {
        runHandler.removeCallbacks(hideRunnable!!)
        runHandler.postDelayed(hideRunnable!!, delayMillis.toLong())
    }

    val x: Int
        get() = cursorLayout.x + cursorView.width / 2

    val y: Int
        get() = cursorLayout.y + cursorView.height / 2

    fun moveView(dx: Int, dy: Int) {
        val (height, width) = getHeightWidth()

        cursorLayout.x += dx
        cursorLayout.y += dy

        if (this.x > width) cursorLayout.x = width - cursorView.width / 2
        if (this.y > height) cursorLayout.y = height - cursorView.height / 2
        if (this.x < 0) cursorLayout.x = -cursorView.width / 2
        if (this.y < 0) cursorLayout.y = -cursorView.height / 2

        InputDevicesReceiverPlugin.Cursor.x = this.x
        InputDevicesReceiverPlugin.Cursor.y = this.y

        runHandler.post {
            // Log.i("MouseReceiverService", "performing move");
            try {
                windowManager!!.updateViewLayout(
                    cursorView,
                    cursorLayout
                )
                cursorView.visibility = View.VISIBLE
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }
    }

    private val isSwiping: Boolean
        get() = swipeStroke != null

    private fun startSwipe(): Boolean {
        val path = Path()
        path.moveTo(this.x.toFloat(), this.y.toFloat())
        swipeStroke = StrokeDescription(path, 0, 1, true)
        val builder = GestureDescription.Builder()
        builder.addStroke(swipeStroke!!)
        (cursorView.findViewById<View?>(R.id.mouse_cursor) as ImageView).setImageResource(R.drawable.mouse_pointer_clicked)
        return dispatchGesture(builder.build(), null, null)
    }

    private fun continueSwipe(fromX: Int, fromY: Int): Boolean {
        val path = Path()
        path.moveTo(fromX.toFloat(), fromY.toFloat())
        path.lineTo(this.x.toFloat(), this.y.toFloat())
        swipeStroke = swipeStroke!!.continueStroke(path, 0, 5, true)
        val builder = GestureDescription.Builder()
        builder.addStroke(swipeStroke!!)
        return dispatchGesture(builder.build(), null, null)
    }

    fun stopSwipe(): Boolean {
        val path = Path()
        path.moveTo(this.x.toFloat(), this.y.toFloat())
        if (swipeStroke == null) {
            return true
        }
        swipeStroke = swipeStroke!!.continueStroke(path, 0, 1, false)
        val builder = GestureDescription.Builder()
        builder.addStroke(swipeStroke!!)
        swipeStroke = null
        (cursorView.findViewById<View?>(R.id.mouse_cursor) as ImageView).setImageResource(R.drawable.mouse_pointer)
        return dispatchGesture(builder.build(), null, null)
    }


    // https://codelabs.developers.google.com/codelabs/developing-android-a11y-service/#6
    private fun findNodeByAction(
        root: AccessibilityNodeInfo?,
        action: AccessibilityNodeInfo.AccessibilityAction?
    ): AccessibilityNodeInfo? {
        val deque: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        root?.let { deque.add(it) }
        while (!deque.isEmpty()) {
            val node = deque.removeFirst()
            if (node.actionList.contains(action)) {
                return node
            }
            for (i in 0..<node.childCount) {
                deque.addLast(node.getChild(i))
            }
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceRef?.clear()
        instanceRef = null

        windowManager?.removeView(cursorView)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    companion object {
        private var instanceRef: WeakReference<MouseReceiverService>? = null
        val instance: MouseReceiverService?
            get() = instanceRef?.get()

        fun move(dx: Int, dy: Int): Boolean {
            val service = instance ?: return false
            val fromX: Int = service.x
            val fromY: Int = service.y

            service.moveView(dx, dy)

            service.hideAfter5Seconds()

            if (service.isSwiping) {
                return service.continueSwipe(fromX, fromY)
            }

            return true
        }

        fun setPos(x: Int, y: Int): Boolean {
            val service = instance ?: return false
            return move(x - service.x, y - service.y)
        }

        private fun createClick(x: Int, y: Int, duration: Int): GestureDescription? {
            val clickPath = Path()
            clickPath.moveTo(x.toFloat(), y.toFloat())
            val clickStroke =
                StrokeDescription(clickPath, 0, duration.toLong())
            val clickBuilder = GestureDescription.Builder()
            clickBuilder.addStroke(clickStroke)
            return clickBuilder.build()
        }

        fun click(): Boolean {
            val service = instance ?: return false

            // Log.i("MouseReceiverService", "x: " + instance.getX() + " y:" + instance.getY());
            if (service.isSwiping) {
                return service.stopSwipe()
            }

            return click(service.x, service.y)
        }

        fun click(x: Int, y: Int): Boolean {
            return instance?.dispatchGesture(createClick(x, y, 1 /*ms*/)!!, null, null) ?: false
        }

        fun longClick(): Boolean {
            val service = instance ?: return false
            return service.dispatchGesture(
                createClick(
                    service.x,
                    service.y,
                    android.view.ViewConfiguration.getLongPressTimeout()
                )!!, null, null
            )
        }

        fun longClickSwipe(): Boolean {
            val service = instance ?: return false

            return if (service.isSwiping) {
                service.stopSwipe()
            } else {
                service.startSwipe()
            }
        }

        fun scroll(dy: Int): Boolean {
            val service = instance ?: return false

            service.scrollSum += dy.toDouble()
            if (sign(dy.toFloat()).toDouble() != sign(service.scrollSum))
                service.scrollSum = dy.toDouble()
            if (abs(service.scrollSum) < 500) return false
            service.scrollSum = 0.0

            val scrollable: AccessibilityNodeInfo? = service.findNodeByAction(
                service.rootInActiveWindow,
                if (dy > 0)
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
                else
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
            )

            if (scrollable == null) return false

            return scrollable.performAction(
                if (dy > 0)
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id
                else
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id
            )
        }

        fun backButton(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        }

        fun homeButton(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
        }

        fun recentButton(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false
        }
    }
}
