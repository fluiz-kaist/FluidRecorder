package com.example.fluidrecorder

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var infoView: View? = null
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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

        val params =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                layoutFlag,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.TOP or Gravity.END
                            x = 0
                            y = 100
                        }

        floatingView?.let { view ->
            val recordButton = view.findViewById<Button>(R.id.recordButton)
            recordButton.setOnClickListener { toggleRecording() }

            // 드래그 기능 추가
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            recordButton.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        params.x = initialX - (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(view, params)
        }
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

        infoView?.let { windowManager.addView(it, params) }
    }

    private fun showNodeInfo(info: String) {
        infoView?.let { view ->
            val textView = view.findViewById<TextView>(R.id.nodeInfoText)
            textView?.text = info
        }
    }

    private fun removeInfoView() {
        infoView?.let {
            windowManager.removeView(it)
            infoView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager.removeView(it) }
        removeInfoView()
    }
}
