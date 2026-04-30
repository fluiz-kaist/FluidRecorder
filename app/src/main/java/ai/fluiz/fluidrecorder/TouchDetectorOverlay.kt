package ai.fluiz.fluidrecorder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * 전체 화면을 덮는 투명 오버레이로 터치 좌표를 캡처합니다.
 * 캡처 후 dispatchGesture로 터치를 재현하여 아래 앱에서도 클릭이 동작합니다.
 */
class TouchDetectorOverlay(
    private val context: Context,
    private val accessibilityService: AccessibilityService
) {
    
    companion object {
        private const val TAG = "TouchDetectorOverlay"
        private const val CLICK_DURATION_MS = 100L
        private const val FLAG_SETTLE_DELAY_MS = 100L  // Time for FLAG_NOT_TOUCHABLE to take effect
        private const val MAX_RECAPTURE_WINDOW_MS = 2000L  // Max time to check for recapture (generous for slow devices)
        private const val COORDINATE_TOLERANCE = 10f       // Pixels tolerance for coordinate matching
    }
    
    private var windowManager: WindowManager? = null
    private var touchView: View? = null
    private var onTouchCallback: ((Int, Int) -> Unit)? = null
    private var isEnabled = false
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isOverlayVisible = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Track the last dispatched gesture to detect recapture
    private var lastDispatchedX = -1f
    private var lastDispatchedY = -1f
    private var lastDispatchTime = 0L
    private var isDispatching = false
    
    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    fun setOnTouchCallback(callback: (Int, Int) -> Unit) {
        onTouchCallback = callback
    }
    
    fun show() {
        if (touchView != null) {
            Log.d(TAG, "Touch overlay already shown")
            return
        }
        
        touchView = object : View(context) {
            private var downX = 0f
            private var downY = 0f
            private var downTime = 0L
            
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (!isEnabled) {
                    Log.d(TAG, "Touch ignored - not enabled")
                    return false
                }

                // While dispatching, consume but ignore all touches
                if (isDispatching) {
                    Log.d(TAG, "Touch consumed during dispatch at (${event.rawX}, ${event.rawY})")
                    return true  // Consume to maintain touch sequence
                }

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        downTime = System.currentTimeMillis()
                        Log.d(TAG, "Touch DOWN at: (${downX.toInt()}, ${downY.toInt()})")
                    }
                    MotionEvent.ACTION_UP -> {
                        val upX = event.rawX
                        val upY = event.rawY
                        val duration = System.currentTimeMillis() - downTime
                        
                        Log.d(TAG, "Touch UP at: (${upX.toInt()}, ${upY.toInt()}), duration: ${duration}ms")
                        
                        // 이동 거리가 작으면 클릭으로 판단
                        val deltaX = kotlin.math.abs(upX - downX)
                        val deltaY = kotlin.math.abs(upY - downY)
                        
                        // Set non-touchable and dispatch
                        isDispatching = true
                        setTouchPassthrough(true)
                        Log.d(TAG, "Starting dispatch sequence")

                        if (deltaX < 50 && deltaY < 50) {
                            // 콜백으로 노드 정보 캡처
                            onTouchCallback?.invoke(upX.toInt(), upY.toInt())

                            // dispatchGesture로 실제 클릭 재현
                            mainHandler.postDelayed({
                                dispatchClick(upX, upY)
                            }, FLAG_SETTLE_DELAY_MS)
                        } else {
                            // 스와이프 재현
                            mainHandler.postDelayed({
                                dispatchSwipe(downX, downY, upX, upY, duration)
                            }, FLAG_SETTLE_DELAY_MS)
                        }
                    }
                }
                // true를 반환하여 이벤트 시퀀스를 계속 받음
                return true
            }
        }.apply {
            alpha = 0f
        }
        
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        
        try {
            windowManager?.addView(touchView, layoutParams)
            isOverlayVisible = true
            Log.d(TAG, "Touch overlay added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add touch overlay", e)
        }
    }
    
    /**
     * 오버레이를 일시적으로 숨깁니다 (dispatchGesture 전용)
     */
    private fun hideTemporarily() {
        if (!isOverlayVisible) {
            Log.d(TAG, "Touch overlay already hidden")
            return
        }
        touchView?.let {
            try {
                windowManager?.removeView(it)
                isOverlayVisible = false
                Log.d(TAG, "Touch overlay hidden temporarily")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide touch overlay temporarily", e)
            }
        }
    }
    
    /**
     * 일시적으로 숨긴 오버레이를 다시 표시합니다
     */
    private fun showAgain() {
        if (isOverlayVisible) {
            Log.d(TAG, "Touch overlay already visible")
            return
        }
        mainHandler.post {
            touchView?.let { view ->
                layoutParams?.let { params ->
                    try {
                        windowManager?.addView(view, params)
                        isOverlayVisible = true
                        Log.d(TAG, "Touch overlay shown again")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to show touch overlay again", e)
                    }
                }
            }
        }
    }
    
    /**
     * 오버레이를 터치 통과 모드로 전환 (dispatchGesture가 아래 앱으로 전달되도록)
     */
    private fun setTouchPassthrough(passthrough: Boolean) {
        layoutParams?.let { params ->
            if (passthrough) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            touchView?.let { view ->
                try {
                    windowManager?.updateViewLayout(view, params)
                    Log.d(TAG, "Touch passthrough set to: $passthrough")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update touch passthrough", e)
                }
            }
        }
    }
    
    /**
     * 클릭 제스처를 재현합니다.
     */
    private fun dispatchClick(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires API 24+")
            return
        }
        
        Log.d(TAG, "Dispatching click at ($x, $y), overlay visible: $isOverlayVisible")

        // Track dispatched coordinates for recapture detection
        lastDispatchedX = x
        lastDispatchedY = y
        lastDispatchTime = System.currentTimeMillis()

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, CLICK_DURATION_MS))
            .build()

        Log.d(TAG, "Calling dispatchGesture...")
        val result = accessibilityService.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "onCompleted: Click dispatched successfully at ($x, $y)")
                // Delay re-enabling to ensure gesture is fully processed
                mainHandler.postDelayed({
                    setTouchPassthrough(false)
                    isDispatching = false
                    Log.d(TAG, "Dispatch complete, ready for next touch")
                }, CLICK_DURATION_MS + 50)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "onCancelled: Click dispatch cancelled at ($x, $y)")
                mainHandler.postDelayed({
                    setTouchPassthrough(false)
                    isDispatching = false
                    Log.d(TAG, "Dispatch cancelled, ready for next touch")
                }, 50)
            }
        }, null)

        if (result) {
            Log.d(TAG, "dispatchGesture accepted the gesture")
        } else {
            Log.e(TAG, "dispatchGesture REJECTED the gesture!")
        }
    }

    /**
     * 스와이프 제스처를 재현합니다.
     */
    private fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires API 24+")
            return
        }

        Log.d(TAG, "Dispatching swipe from ($startX, $startY) to ($endX, $endY)...")

        // Track dispatched coordinates for recapture detection (use start position)
        lastDispatchedX = startX
        lastDispatchedY = startY
        lastDispatchTime = System.currentTimeMillis()

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val swipeDuration = duration.coerceIn(100, 1000)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, swipeDuration))
            .build()

        val result = accessibilityService.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe dispatched successfully")
                mainHandler.postDelayed({
                    setTouchPassthrough(false)
                    isDispatching = false
                    Log.d(TAG, "Swipe dispatch complete, ready for next touch")
                }, swipeDuration + 50)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Swipe dispatch cancelled")
                mainHandler.postDelayed({
                    setTouchPassthrough(false)
                    isDispatching = false
                    Log.d(TAG, "Swipe dispatch cancelled, ready for next touch")
                }, 50)
            }
        }, null)
        
        Log.d(TAG, "dispatchGesture (swipe) returned: $result")
    }
    
    fun hide() {
        touchView?.let {
            try {
                if (isOverlayVisible) {
                    windowManager?.removeView(it)
                    isOverlayVisible = false
                }
                Log.d(TAG, "Touch overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove touch overlay", e)
            }
            touchView = null
        }
    }
    
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d(TAG, "Touch detection enabled: $enabled")
    }
    
    fun destroy() {
        hide()
        onTouchCallback = null
        windowManager = null
    }
}
