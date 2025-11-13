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
        private const val BASE_URL = "http://192.168.225.52:3000"
        
        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
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

