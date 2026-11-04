package ai.fluiz.fluidrecorder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RecorderAccessibility"
        var isRecording = false
        var instance: RecorderAccessibilityService? = null
    }

    private var lastHoveredNode: AccessibilityNodeInfo? = null
    private var pendingClickNode: AccessibilityNodeInfo? = null
    private var overlayManager: OverlayManager? = null
    private var touchDetectorOverlay: TouchDetectorOverlay? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val nodeMap = HashMap<Int, AccessibilityNodeInfo>()
    private var currentScreenDump: String? = null
    private var rootNode: AccessibilityNodeInfo? = null
    
    // 마지막 터치 좌표
    private var lastTouchX: Int = 0
    private var lastTouchY: Int = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info =
                AccessibilityServiceInfo().apply {
                    eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                    feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                    notificationTimeout = 50
                    interactiveUiTimeoutMillis = 1000
                    flags =
                            (AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                                    AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES or
                                    AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT or
                                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                                    AccessibilityServiceInfo.FLAG_SEND_MOTION_EVENTS or
                                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS)
                }

        serviceInfo = info

        // 오버레이 매니저 초기화
        overlayManager = OverlayManager(this)
        
        // 터치 감지 오버레이 초기화 - AccessibilityService 전달하여 dispatchGesture 사용 가능
        touchDetectorOverlay = TouchDetectorOverlay(this, this).apply {
            setOnTouchCallback { x, y ->
                handleTouchAtPosition(x, y)
            }
        }

        // 오버레이 서비스 시작
        startService(Intent(this, OverlayService::class.java))

        Log.d(TAG, "Accessibility Service Connected")
        showToast("녹화 서비스가 활성화되었습니다")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isRecording) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> {
                Log.d(TAG, "onAccessibilityEvent: start${event.source}")
                // 현재 화면 Dump
                dumpCurrentScreen()
                handleHoverEnter(event)
                handleHoverExit(event)
            }
            AccessibilityEvent.TYPE_VIEW_HOVER_EXIT -> {
                Log.d(TAG, "onAccessibilityEvent: end")
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 화면이 변경되면 테두리 제거
                overlayManager?.hideRedBorder()
                // 윈도우 변경 시 약간의 딜레이 후 포커스 리프레시
                mainHandler.postDelayed({
                    refreshAccessibilityFocus()
                }, 300)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 화면이 변경되면 테두리 제거
                overlayManager?.hideRedBorder()
            }
        }
    }

    /**
     * 접근성 포커스를 강제로 리프레시하여 HOVER 이벤트가 정상 작동하도록 함
     */
    private fun refreshAccessibilityFocus() {
        try {
            val root = ensureFreshRootNode() ?: return
            Log.d(TAG, "Refreshing accessibility focus for window: ${root.packageName}")
            
            // 루트 노드에 접근성 포커스 설정 후 해제하여 강제 리프레시
            root.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            mainHandler.postDelayed({
                try {
                    root.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing accessibility focus", e)
                }
            }, 50)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing accessibility focus", e)
        }
    }

    /**
     * 캐시된 rootNode 대신 항상 새로운 rootInActiveWindow를 가져옴
     */
    private fun ensureFreshRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow?.also {
            rootNode = it
        }
    }

    private fun handleHoverEnter(event: AccessibilityEvent) {
        val source = event.source ?: return
        lastHoveredNode = source

        // 노드 정보 수집
        val nodeInfo = extractNodeInfo(source)
        Log.d(TAG, "Hover Enter - Node Info: $nodeInfo")

        // XPath 계산
        if (rootNode != null) {
            val xpath = AccessibilityNodeInfoDumper.getXPath(source, rootNode)
            Log.d(TAG, "Node XPath: $xpath")
        }

        // 녹화 중이면 정보 표시
        if (isRecording) {
            showNodeInfo(nodeInfo)
        }
    }

    private fun handleHoverExit(event: AccessibilityEvent) {
        val source = event.source ?: return

        // 마지막 hover된 노드와 같은 노드인지 확인
        //        if (lastHoveredNode != null && source == lastHoveredNode) {
        // 실제 클릭 수행
        //        if (source.isClickable) {
        pendingClickNode = source

        // 클릭할 요소에 빨간 테두리 표시
        val bounds = Rect()
        source.getBoundsInScreen(bounds)
        overlayManager?.showRedBorder(bounds)

        // Touch exploration mode에서는 double tap으로 클릭이 되므로
        // 여기서는 노드 정보만 저장
        val nodeInfo = extractNodeInfo(source)
        Log.d(TAG, "Hover Exit - Will perform click on: $nodeInfo")

        // 실제 클릭은 사용자의 double tap으로 처리됨
        val clicked = source.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "Clicked: $clicked")

        // 1초 후 테두리 제거
        mainHandler.postDelayed({ overlayManager?.hideRedBorder() }, 1000)
        //        }
        //        }

        lastHoveredNode = null
    }
    
    /**
     * 터치 좌표를 기반으로 해당 위치의 노드를 찾아 처리합니다.
     */
    private fun handleTouchAtPosition(x: Int, y: Int) {
        if (!isRecording) return

        // Skip recording if touch is on our own overlay (floating buttons)
        if (OverlayService.isPointInsideOverlay(x, y)) {
            Log.d(TAG, "Touch on overlay at ($x, $y) - skipping recording")
            return
        }

        lastTouchX = x
        lastTouchY = y
        Log.d(TAG, "Processing touch at position: ($x, $y)")
        
        // 현재 화면 덤프
        dumpCurrentScreen()
        
        // 해당 좌표에서 노드 찾기
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "Root node is null, cannot find node at position")
            return
        }
        
        val foundNode = findSmallestNodeAtPosition(rootNode, x, y)
        if (foundNode != null) {
            val nodeInfo = extractNodeInfo(foundNode)
            Log.d(TAG, "Found node at ($x, $y): $nodeInfo")
            
            // XPath 계산
            val xpath = AccessibilityNodeInfoDumper.getXPath(foundNode, rootNode)
            Log.d(TAG, "Node XPath: $xpath")
            
            // 노드 정보 표시
            showNodeInfo(nodeInfo)
            
            // 빨간 테두리 표시
            val bounds = Rect()
            foundNode.getBoundsInScreen(bounds)
            overlayManager?.showRedBorder(bounds)
            
            // 1초 후 테두리 제거
            mainHandler.postDelayed({ overlayManager?.hideRedBorder() }, 1000)
            
            // 서버로 액션 전송
            sendActionToServer(foundNode, xpath)
        } else {
            Log.w(TAG, "No node found at position ($x, $y)")
        }
    }
    
    /**
     * 클릭한 노드 정보를 서버로 전송합니다.
     */
    private fun sendActionToServer(node: AccessibilityNodeInfo, xpath: String?) {
        if (currentScreenDump == null) {
            Log.w(TAG, "Screen dump is null, cannot send action")
            return
        }
        
        val sessionId = AppGlobals.sessionId
        if (sessionId.isEmpty()) {
            Log.w(TAG, "Session ID is not set, cannot send action")
            return
        }
        
        // 노드 인덱스 찾기
        val nodeIndex = AccessibilityNodeInfoDumper.findNodeIndex(node, nodeMap)
        
        // 입력 필드인지 확인하여 name 결정
        val actionName = if (AccessibilityNodeInfoDumper.isInputField(node)) "input" else "click"
        
        Log.d(TAG, "Sending action to server - xpath: $xpath, name: $actionName, index: $nodeIndex, sessionId: $sessionId")
        
        NetworkManager.sendAction(
            xpath = xpath ?: "",
            name = actionName,
            index = nodeIndex,
            screen = currentScreenDump!!,
            sessionId = sessionId,
            onSuccess = {
                Log.d(TAG, "Action sent to server successfully")
                mainHandler.post {
                    showToast("액션 전송 완료")
                }
            },
            onError = { error ->
                Log.e(TAG, "Failed to send action: $error")
                mainHandler.post {
                    showToast("액션 전송 실패: $error")
                }
            }
        )
    }
    
    /**
     * 주어진 좌표에서 가장 작은 영역을 가진 노드를 찾습니다.
     * 전체 화면을 덮는 투명한 오버레이 뷰 대신 실제 UI 요소를 정확히 선택합니다.
     */
    private fun findSmallestNodeAtPosition(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        // 좌표가 이 노드의 bounds 안에 없으면 null 반환
        if (!bounds.contains(x, y)) {
            return null
        }
        
        // 좌표를 포함하는 모든 노드 수집
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectNodesAtPosition(node, x, y, candidates)
        
        if (candidates.isEmpty()) {
            return node
        }
        
        // 가장 작은 영역을 가진 노드 선택 (같은 영역이면 clickable 우선)
        return candidates.minWithOrNull(compareBy(
            { nodeBounds -> 
                val b = Rect()
                nodeBounds.getBoundsInScreen(b)
                b.width() * b.height()
            },
            { nodeBounds -> if (nodeBounds.isClickable) 0 else 1 }
        ))
    }
    
    /**
     * 좌표를 포함하는 모든 노드를 재귀적으로 수집합니다.
     */
    private fun collectNodesAtPosition(
        node: AccessibilityNodeInfo, 
        x: Int, 
        y: Int, 
        candidates: MutableList<AccessibilityNodeInfo>
    ) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!bounds.contains(x, y)) {
            return
        }
        
        // 유효한 bounds를 가진 노드만 후보에 추가
        if (bounds.width() > 0 && bounds.height() > 0) {
            candidates.add(node)
        }
        
        // 모든 자식 노드도 탐색
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodesAtPosition(child, x, y, candidates)
        }
    }

    private fun extractNodeInfo(node: AccessibilityNodeInfo): NodeInfo {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        return NodeInfo(
                className = node.className?.toString() ?: "Unknown",
                text = node.text?.toString() ?: "",
                contentDescription = node.contentDescription?.toString() ?: "",
                viewId =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            node.viewIdResourceName ?: ""
                        } else "",
                isClickable = node.isClickable,
                isScrollable = node.isScrollable,
                isFocusable = node.isFocusable,
                bounds = bounds,
                packageName = node.packageName?.toString() ?: ""
        )
    }

    private fun showNodeInfo(nodeInfo: NodeInfo) {
        val message = buildString {
            append("클래스: ${nodeInfo.className}\n")
            if (nodeInfo.text.isNotEmpty()) append("텍스트: ${nodeInfo.text}\n")
            if (nodeInfo.contentDescription.isNotEmpty())
                    append("설명: ${nodeInfo.contentDescription}\n")
            if (nodeInfo.viewId.isNotEmpty()) append("ID: ${nodeInfo.viewId}\n")
            append("위치: ${nodeInfo.bounds}\n")
            append("클릭가능: ${nodeInfo.isClickable}")
        }

        // OverlayService로 정보 전달
        val intent =
                Intent(this, OverlayService::class.java).apply {
                    action = "SHOW_NODE_INFO"
                    putExtra("info", message)
                }
        startService(intent)
    }

    fun startRecording() {
        isRecording = true
        
        // 터치 감지 오버레이 활성화
        touchDetectorOverlay?.show()
        touchDetectorOverlay?.setEnabled(true)

        showToast("녹화를 시작합니다 - 터치 감지 활성화")
        Log.d(TAG, "Recording started - Touch detection enabled")
    }

    fun stopRecording() {
        isRecording = false
        
        // 터치 감지 오버레이 비활성화
        touchDetectorOverlay?.setEnabled(false)
        touchDetectorOverlay?.hide()
        
        // 테두리 제거
        overlayManager?.hideRedBorder()

        showToast("녹화를 중지합니다 - 터치 감지 비활성화")
        Log.d(TAG, "Recording stopped - Touch detection disabled")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    private fun dumpCurrentScreen() {
        try {
            // 현재 활성화된 윈도우의 루트 노드 가져오기
            rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "Root node is null, cannot dump screen")
                return
            }

            // 노드맵 초기화
            nodeMap.clear()

            // 화면 Dump
            currentScreenDump = AccessibilityNodeInfoDumper.dumpWindow(rootNode, nodeMap)

            if (currentScreenDump != null) {
                Log.d(TAG, "Screen dumped successfully")
                // XML 내용의 일부만 로그에 출력 (너무 길 수 있으므로)
                Log.d(TAG, "Screen dump preview: ${currentScreenDump!!.take(500)}...")
            } else {
                Log.w(TAG, "Failed to dump screen")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dumping screen", e)
        }
    }

    fun dumpCurrentScreenToServer() {
        try {
            // 현재 활성화된 윈도우의 루트 노드 가져오기
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "Root node is null, cannot dump screen")
                showToast("화면을 가져올 수 없습니다")
                return
            }

            // 노드맵 초기화
            val tempNodeMap = HashMap<Int, AccessibilityNodeInfo>()

            // 화면 Dump
            val xmlDump = AccessibilityNodeInfoDumper.dumpWindow(rootNode, tempNodeMap)

            if (xmlDump != null) {
                Log.d(TAG, "Screen dumped successfully for server upload")
                
                // 현재 타임스탬프로 파일명 생성
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                val filename = "screen_dump_$timestamp.xml"
                
                // 서버로 전송
                NetworkManager.sendScreenDump(
                    xml = xmlDump,
                    filename = filename,
                    onSuccess = {
                        Log.d(TAG, "Screen dump sent to server successfully")
                        mainHandler.post {
                            showToast("화면 덤프가 서버로 전송되었습니다")
                        }
                    },
                    onError = { error ->
                        Log.e(TAG, "Failed to send screen dump: $error")
                        mainHandler.post {
                            showToast("서버 전송 실패: $error")
                        }
                    }
                )
            } else {
                Log.w(TAG, "Failed to dump screen")
                showToast("화면 덤프에 실패했습니다")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dumping screen for server", e)
            showToast("오류 발생: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        overlayManager?.destroy()
        overlayManager = null
        touchDetectorOverlay?.destroy()
        touchDetectorOverlay = null
        stopService(Intent(this, OverlayService::class.java))
    }

    data class NodeInfo(
            val className: String,
            val text: String,
            val contentDescription: String,
            val viewId: String,
            val isClickable: Boolean,
            val isScrollable: Boolean,
            val isFocusable: Boolean,
            val bounds: Rect,
            val packageName: String
    )
}
