package com.example.fluidrecorder

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        private val floatingViewBounds = Rect()

        /**
         * Check if a point is inside the floating overlay buttons.
         */
        fun isPointInsideOverlay(x: Int, y: Int): Boolean {
            return floatingViewBounds.contains(x, y)
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var infoView: View? = null
    private var isRecording = false
    private var isExpanded = false
    private var screenWidth = 0
    private var floatingParams: WindowManager.LayoutParams? = null
    private var buttonSize = 0
    private var expandedHeight = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 오버레이 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "오버레이 권한이 없습니다. 플로팅 버튼을 생성할 수 없습니다.")
                return
            }
        }
        
        createFloatingView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW_NODE_INFO" -> {
                val info = intent.getStringExtra("info")
                showNodeInfo(info ?: "")
            }
        }
        return START_STICKY
    }

    /**
     * Update the floating view bounds for touch detection.
     * Called when the view is created, dragged, or expanded/collapsed.
     */
    private fun updateFloatingViewBounds() {
        val params = floatingParams ?: return
        // With Gravity.END, x is offset from right edge
        // So actual left = screenWidth - x - viewWidth
        val left = screenWidth - params.x - buttonSize
        val top = params.y
        val right = screenWidth - params.x
        // When collapsed, only main button is visible (60dp height)
        // When expanded, full height
        val visibleHeight = if (isExpanded) expandedHeight else buttonSize
        val bottom = top + visibleHeight
        floatingViewBounds.set(left, top, right, bottom)
        Log.d(TAG, "Updated floating view bounds: $floatingViewBounds (expanded: $isExpanded)")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingView() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_button, null)

        val layoutFlag =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }

        // Calculate height to fit all buttons when expanded
        // Main button (60dp) + gap + record button (60dp) + gap + dump button (60dp)
        val density = resources.displayMetrics.density
        expandedHeight = ((60 + 80 + 60 + 80 + 60) * density).toInt()  // ~340dp total
        buttonSize = (60 * density).toInt()

        // Get screen dimensions
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels

        val params =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                expandedHeight,
                                layoutFlag,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.TOP or Gravity.END
                            x = 0
                            y = 100
                        }
        floatingParams = params

        floatingView?.let { view ->
            val mainButton = view.findViewById<Button>(R.id.mainButton)
            val recordButton = view.findViewById<Button>(R.id.recordButton)
            val dumpButton = view.findViewById<Button>(R.id.dumpButton)

            // 녹화 버튼 클릭
            recordButton.setOnClickListener { toggleRecording() }

            // 화면 덤프 버튼 클릭
            dumpButton.setOnClickListener { 
                RecorderAccessibilityService.instance?.dumpCurrentScreenToServer()
            }

            // 드래그 기능 추가 (메인 버튼에만 적용)
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false

            mainButton.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaX = Math.abs(event.rawX - initialTouchX)
                        val deltaY = Math.abs(event.rawY - initialTouchY)
                        if (deltaX > 10 || deltaY > 10) {
                            isDragging = true
                            params.x = initialX - (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(view, params)
                            true
                        } else {
                            false
                        }
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            isDragging = false
                            updateFloatingViewBounds()
                            true
                        } else {
                            // 클릭으로 인식 - 펼치기/접기 토글
                            toggleExpand()
                            true
                        }
                    }
                    else -> false
                }
            }

            windowManager.addView(view, params)
            updateFloatingViewBounds()
        }
    }

    private fun toggleExpand() {
        floatingView?.let { view ->
            val recordButton = view.findViewById<Button>(R.id.recordButton)
            val dumpButton = view.findViewById<Button>(R.id.dumpButton)
            val mainButton = view.findViewById<Button>(R.id.mainButton)

            isExpanded = !isExpanded
            updateFloatingViewBounds()

            if (isExpanded) {
                // 펼치기
                mainButton.text = "×"
                
                // 녹화 버튼 보이기
                recordButton.visibility = View.VISIBLE
                recordButton.alpha = 0f
                recordButton.translationY = 0f
                recordButton.isClickable = true
                
                ObjectAnimator.ofFloat(recordButton, "translationY", 0f, 80f).apply {
                    duration = 300
                    start()
                }
                ObjectAnimator.ofFloat(recordButton, "alpha", 0f, 1f).apply {
                    duration = 300
                    start()
                }

                // 덤프 버튼 보이기
                dumpButton.visibility = View.VISIBLE
                dumpButton.alpha = 0f
                dumpButton.translationY = 0f
                dumpButton.isClickable = true
                
                ObjectAnimator.ofFloat(dumpButton, "translationY", 0f, 160f).apply {
                    duration = 300
                    start()
                }
                ObjectAnimator.ofFloat(dumpButton, "alpha", 0f, 1f).apply {
                    duration = 300
                    start()
                }
                
                Log.d(TAG, "버튼 펼침")
            } else {
                // 접기
                mainButton.text = "☰"
                
                // 버튼 클릭 비활성화
                recordButton.isClickable = false
                dumpButton.isClickable = false

                // 녹화 버튼 숨기기
                ObjectAnimator.ofFloat(recordButton, "translationY", 80f, 0f).apply {
                    duration = 300
                    start()
                }
                ObjectAnimator.ofFloat(recordButton, "alpha", 1f, 0f).apply {
                    duration = 300
                    start()
                }.also {
                    it.doOnEnd { 
                        recordButton.visibility = View.GONE
                    }
                }

                // 덤프 버튼 숨기기
                ObjectAnimator.ofFloat(dumpButton, "translationY", 160f, 0f).apply {
                    duration = 300
                    start()
                }
                ObjectAnimator.ofFloat(dumpButton, "alpha", 1f, 0f).apply {
                    duration = 300
                    start()
                }.also {
                    it.doOnEnd { 
                        dumpButton.visibility = View.GONE
                    }
                }
                
                Log.d(TAG, "버튼 접음")
            }
        }
    }

    private fun ObjectAnimator.doOnEnd(action: () -> Unit) {
        addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                action()
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        val recordButton = floatingView?.findViewById<Button>(R.id.recordButton)

        if (isRecording) {
            recordButton?.text = "중지"
            RecorderAccessibilityService.instance?.startRecording()
            createInfoView()
        } else {
            recordButton?.text = "녹화"
            RecorderAccessibilityService.instance?.stopRecording()
            removeInfoView()
        }
    }

    private fun createInfoView() {
        // 오버레이 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "오버레이 권한이 없어서 info view를 생성할 수 없습니다.")
                return
            }
        }
        
        val inflater = LayoutInflater.from(this)
        infoView = inflater.inflate(R.layout.node_info_view, null)

        val layoutFlag =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }

        val params =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                layoutFlag,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.BOTTOM
                            y = 100
                        }

        infoView?.let { 
            try {
                windowManager.addView(it, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add info view", e)
            }
        }
    }

    private fun showNodeInfo(info: String) {
        infoView?.let { view ->
            val textView = view.findViewById<TextView>(R.id.nodeInfoText)
            textView?.text = info
        }
    }

    private fun removeInfoView() {
        infoView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove info view", e)
            } finally {
                infoView = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { 
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove floating view", e)
            }
        }
        removeInfoView()
    }
}
