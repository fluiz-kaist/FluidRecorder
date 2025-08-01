package com.example.fluidrecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fluidrecorder.ui.theme.FluidRecorderTheme

class MainActivity : ComponentActivity() {
    
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "오버레이 권한이 허용되었습니다", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "오버레이 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FluidRecorderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
    
    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Fluid Recorder",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            Text(
                text = "이 앱은 AccessibilityService를 사용하여\n다른 앱의 터치 이벤트를 녹화합니다.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            Button(
                onClick = {
                    // 접근성 설정 페이지로 이동
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                    Toast.makeText(
                        context,
                        "Fluid Recorder를 찾아서 활성화해주세요",
                        Toast.LENGTH_LONG
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("접근성 서비스 설정")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    // 오버레이 권한 확인 및 요청
                    if (!Settings.canDrawOverlays(context)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        overlayPermissionLauncher.launch(intent)
                    } else {
                        Toast.makeText(
                            context,
                            "오버레이 권한이 이미 허용되었습니다",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("오버레이 권한 설정")
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "사용 방법",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "1. 접근성 서비스를 활성화하세요\n" +
                               "2. 오버레이 권한을 허용하세요\n" +
                               "3. 플로팅 버튼으로 녹화를 시작/중지하세요\n" +
                               "4. 터치할 때 노드 정보가 표시됩니다",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}