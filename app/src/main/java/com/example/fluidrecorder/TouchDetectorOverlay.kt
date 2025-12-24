package com.example.fluidrecorder

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * 전체 화면을 덮는 투명 오버레이로 터치 좌표를 캡처합니다.
 * 터치 이벤트를 가로채지 않고 좌표만 기록한 후 아래 뷰로 전달합니다.
 */
class TouchDetectorOverlay(private val context: Context) {
    
    companion object {
        private const val TAG = "TouchDetectorOverlay"
    }
    
    private var windowManager: WindowManager? = null
    private var touchView: View? = null
    private var onTouchCallback: ((Int, Int) -> Unit)? = null
    private var isEnabled = false
    
    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    /**
     * 터치 감지 콜백 설정
     * @param callback 터치 좌표 (x, y)를 전달받는 콜백
     */
    fun setOnTouchCallback(callback: (Int, Int) -> Unit) {
        onTouchCallback = callback
    }
    
    /**
     * 터치 감지 오버레이 표시
     */
    fun show() {
        if (touchView != null) {
            Log.d(TAG, "Touch overlay already shown")
            return
        }
        
        touchView = object : View(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (!isEnabled) return false
                
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val x = event.rawX.toInt()
                        val y = event.rawY.toInt()
                        Log.d(TAG, "Touch detected at: ($x, $y)")
                        onTouchCallback?.invoke(x, y)
                    }
                    MotionEvent.ACTION_UP -> {
                        // 터치 종료 시점도 로깅
                        Log.d(TAG, "Touch released")
                    }
                }
                // false를 반환하여 이벤트를 아래 뷰로 전달
                return false
            }
        }.apply {
            // 투명하게 설정
            alpha = 0f
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // FLAG_NOT_FOCUSABLE: 포커스 안 받음
            // FLAG_NOT_TOUCH_MODAL: 터치 이벤트를 다른 윈도우로도 전달
            // FLAG_WATCH_OUTSIDE_TOUCH: 외부 터치도 감지
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        
        try {
            windowManager?.addView(touchView, params)
            Log.d(TAG, "Touch overlay added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add touch overlay", e)
        }
    }
    
    /**
     * 터치 감지 오버레이 숨기기
     */
    fun hide() {
        touchView?.let {
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "Touch overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove touch overlay", e)
            }
            touchView = null
        }
    }
    
    /**
     * 터치 감지 활성화/비활성화
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d(TAG, "Touch detection enabled: $enabled")
    }
    
    /**
     * 리소스 해제
     */
    fun destroy() {
        hide()
        onTouchCallback = null
        windowManager = null
    }
}

