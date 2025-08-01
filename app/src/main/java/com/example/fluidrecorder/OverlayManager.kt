package com.example.fluidrecorder

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class OverlayManager(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var statusBarHeight: Int = 0

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 상태바 높이 계산
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }
    }

    fun showRedBorder(bounds: Rect) {
        if (overlayView == null) {
            // 오버레이 뷰 생성
            overlayView =
                    FrameLayout(context).apply {
                        layoutParams =
                                FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                )

                        // 빨간 테두리 설정
                        background =
                                GradientDrawable().apply {
                                    setColor(Color.TRANSPARENT) // 투명한 내부
                                    setStroke(10, Color.RED) // 빨간 테두리, 두께 10px
                                }
                    }

            val params =
                    WindowManager.LayoutParams(
                                    bounds.width(),
                                    bounds.height(),
                                    bounds.left,
                                    bounds.top - statusBarHeight,
                                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                                    PixelFormat.TRANSLUCENT
                            )
                            .apply { gravity = Gravity.TOP or Gravity.LEFT }

            windowManager?.addView(overlayView, params)
        } else {
            // 기존 오버레이 위치 업데이트
            val params = overlayView?.layoutParams as WindowManager.LayoutParams
            params.width = bounds.width()
            params.height = bounds.height()
            params.x = bounds.left
            params.y = bounds.top - statusBarHeight
            windowManager?.updateViewLayout(overlayView, params)
        }
    }

    fun hideRedBorder() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }

    fun destroy() {
        hideRedBorder()
        windowManager = null
    }
}
