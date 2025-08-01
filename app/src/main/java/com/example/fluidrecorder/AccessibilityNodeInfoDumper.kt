package com.example.fluidrecorder

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import java.io.IOException
import java.io.StringWriter
import org.xmlpull.v1.XmlSerializer

class AccessibilityNodeInfoDumper {
    companion object {
        private const val TAG = "XMLDumper"

        fun dumpWindow(
                root: AccessibilityNodeInfo?,
                nodeMap: HashMap<Int, AccessibilityNodeInfo>
        ): String? {
            if (root == null) {
                Log.d(TAG, "root is null!!!")
                return null
            }

            val startTime = SystemClock.uptimeMillis()
            return try {
                val serializer = Xml.newSerializer()
                val stringWriter = StringWriter()
                serializer.setOutput(stringWriter)
                serializer.startDocument("UTF-8", true)
                serializer.startTag("", "hierarchy")

                dumpNodeRec(root, serializer, nodeMap, nodeMap.size)

                serializer.endTag("", "hierarchy")
                serializer.endDocument()

                val endTime = SystemClock.uptimeMillis()
                Log.w(TAG, "Fetch time: ${endTime - startTime}ms")

                stringWriter.toString()
            } catch (e: IOException) {
                Log.e(TAG, "failed to dump window to string", e)
                null
            }
        }

        @Throws(IOException::class)
        private fun dumpNodeRec(
                node: AccessibilityNodeInfo,
                serializer: XmlSerializer,
                nodeMap: HashMap<Int, AccessibilityNodeInfo>,
                index: Int
        ) {
            nodeMap[index] = node
            serializer.startTag("", "node")
            node.window
            serializer.attribute("", "resource-id", safeCharSeqToString(node.viewIdResourceName))
            serializer.attribute("", "important", node.isImportantForAccessibility.toString())
            serializer.attribute("", "index", index.toString())
            serializer.attribute("", "text", safeCharSeqToString(node.text))
            serializer.attribute("", "class", safeCharSeqToString(node.className))
            serializer.attribute("", "content-desc", safeCharSeqToString(node.contentDescription))
            serializer.attribute("", "checkable", node.isCheckable.toString())
            serializer.attribute("", "checked", node.isChecked.toString())
            serializer.attribute("", "clickable", node.isClickable.toString())
            serializer.attribute("", "enabled", node.isEnabled.toString())
            serializer.attribute("", "scrollable", node.isScrollable.toString())
            serializer.attribute("", "long-clickable", node.isLongClickable.toString())
            serializer.attribute("", "selected", node.isSelected.toString())
            serializer.attribute("", "bounds", getVisibleBoundsInScreen(node).toShortString())

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i)
                if (child != null) {
                    dumpNodeRec(child, serializer, nodeMap, nodeMap.size)
                }
            }
            serializer.endTag("", "node")
        }

        private fun getVisibleBoundsInScreen(node: AccessibilityNodeInfo?): Rect {
            if (node == null) {
                return Rect()
            }
            // targeted node's bounds
            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)
            return nodeRect
        }

        private fun safeCharSeqToString(cs: CharSequence?): String {
            return if (cs == null) {
                ""
            } else {
                stripInvalidXMLChars(cs)
            }
        }

        private fun stripInvalidXMLChars(cs: CharSequence): String {
            val ret = StringBuilder()
            var ch: Char

            for (i in cs.indices) {
                ch = cs[i]

                if ((ch.code in 0x1..0x8) ||
                                (ch.code in 0xB..0xC) ||
                                (ch.code in 0xE..0x1F) ||
                                (ch.code in 0x7F..0x84) ||
                                (ch.code in 0x86..0x9f) ||
                                (ch.code in 0xFDD0..0xFDDF)
                ) {
                    ret.append(".")
                } else {
                    ret.append(ch)
                }
            }
            return ret.toString()
        }

        fun getXPath(
                targetNode: AccessibilityNodeInfo?,
                rootNode: AccessibilityNodeInfo?
        ): String? {
            if (targetNode == null || rootNode == null) {
                return null
            }

            val path = mutableListOf<String>()
            var currentNode: AccessibilityNodeInfo? = targetNode

            // 루트 노드까지 거슬러 올라가며 경로 구성
            while (currentNode != null && currentNode != rootNode) {
                val parent = currentNode.parent
                if (parent != null) {
                    val className = currentNode.className?.toString() ?: "node"
                    val index = getChildIndex(parent, currentNode)
                    path.add(0, "$className[$index]")
                    currentNode = parent
                } else {
                    break
                }
            }

            // 루트 노드 추가
            val rootClassName = rootNode.className?.toString() ?: "hierarchy"
            path.add(0, rootClassName)

            return "/" + path.joinToString("/")
        }

        private fun getChildIndex(
                parent: AccessibilityNodeInfo,
                child: AccessibilityNodeInfo
        ): Int {
            val childCount = parent.childCount
            var index = 1 // XPath는 1부터 시작
            val childClassName = child.className?.toString()

            for (i in 0 until childCount) {
                val currentChild = parent.getChild(i)
                if (currentChild != null) {
                    if (currentChild == child) {
                        return index
                    }
                    // 같은 클래스명을 가진 형제 노드만 카운트
                    if (currentChild.className?.toString() == childClassName) {
                        index++
                    }
                }
            }
            return 1
        }
    }
}
