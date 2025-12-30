package com.example.fluidrecorder

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class NetworkManager {
    companion object {
        private const val TAG = "NetworkManager"
        private val BASE_URL = AppGlobals.baseUrl
        
        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        /**
         * 터치 액션 정보를 서버로 전송
         */
        fun sendAction(
            xpath: String,
            name: String,
            index: Int,
            screen: String,
            sessionId: String,
            onSuccess: () -> Unit = {},
            onError: (String) -> Unit = {}
        ) {
            try {
                val json = JSONObject().apply {
                    put("xpath", xpath)
                    put("name", name)
                    put("index", index)
                    put("screen", screen)
                    put("session_id", sessionId)
                }
                
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = json.toString().toRequestBody(mediaType)
                
                val request = Request.Builder()
                    .url("$BASE_URL/api/collect/actions")
                    .post(requestBody)
                    .build()
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        val errorMsg = "액션 전송 실패: ${e.message}"
                        Log.e(TAG, errorMsg, e)
                        onError(errorMsg)
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (it.isSuccessful) {
                                val responseBody = it.body?.string() ?: ""
                                Log.d(TAG, "액션 전송 성공: $responseBody")
                                onSuccess()
                            } else {
                                val errorMsg = "서버 에러: ${it.code} - ${it.message}"
                                Log.e(TAG, errorMsg)
                                onError(errorMsg)
                            }
                        }
                    }
                })
                
            } catch (e: Exception) {
                val errorMsg = "액션 전송 중 예외 발생: ${e.message}"
                Log.e(TAG, errorMsg, e)
                onError(errorMsg)
            }
        }
        
        fun sendScreenDump(xml: String, filename: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
            try {
                val json = JSONObject().apply {
                    put("xml", xml)
                    put("filename", filename)
                }
                
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = json.toString().toRequestBody(mediaType)
                
                val request = Request.Builder()
                    .url("$BASE_URL/dump")
                    .post(requestBody)
                    .build()
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        val errorMsg = "네트워크 요청 실패: ${e.message}"
                        Log.e(TAG, errorMsg, e)
                        onError(errorMsg)
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (it.isSuccessful) {
                                val responseBody = it.body?.string() ?: ""
                                Log.d(TAG, "화면 덤프 전송 성공: $responseBody")
                                onSuccess()
                            } else {
                                val errorMsg = "서버 에러: ${it.code} - ${it.message}"
                                Log.e(TAG, errorMsg)
                                onError(errorMsg)
                            }
                        }
                    }
                })
                
            } catch (e: Exception) {
                val errorMsg = "화면 덤프 전송 중 예외 발생: ${e.message}"
                Log.e(TAG, errorMsg, e)
                onError(errorMsg)
            }
        }
    }
}

