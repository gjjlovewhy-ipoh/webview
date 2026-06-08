package com.iptv.tvplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.iptv.tvplayer.data.SettingsManager
import com.iptv.tvplayer.data.Subscription

@Composable
fun SubscriptionDialog(
    dialogType: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ipAddress = try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        if (ip == "0.0.0.0") "127.0.0.1" else ip
    } catch (e: Exception) {
        "127.0.0.1"
    }
    
    val uploadUrl = "http://$ipAddress:18890/"
    val qrCodeBitmap = remember { generateQRCode(uploadUrl) }
    
    val isListType = dialogType == "列表订阅" || dialogType == "EPG订阅"
    
    var subName by remember { mutableStateOf("") }
    var subUrl by remember { 
        mutableStateOf(
            when (dialogType) {
                "UserAgent" -> SettingsManager.userAgent
                "Headers" -> SettingsManager.headers
                else -> ""
            }
        ) 
    }
    
    val items = remember { 
        mutableStateListOf<Subscription>().apply {
            if (dialogType == "列表订阅") {
                addAll(SettingsManager.subscriptions)
            } else if (dialogType == "EPG订阅") {
                addAll(SettingsManager.epgs)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000000))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(800.dp)
                    .height(450.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF111111))
                    .clickable(enabled = false) {}
                    .padding(24.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left: QR Code
                    Column(
                        modifier = Modifier
                            .weight(0.7f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "扫码输入(点击二维码查看说明)",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = uploadUrl,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        qrCodeBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(8.dp)
                            )
                        }
                    }

                    // Right: Subscriptions List / Input
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .padding(start = 24.dp)
                    ) {
                        Text(
                            text = dialogType,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            textAlign = TextAlign.Center
                        )

                        if (isListType) {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(items.size) { index ->
                                    val item = items[index]
                                    var isFocused by remember { mutableStateOf(false) }
                                    val isActive = if (dialogType == "列表订阅") SettingsManager.activeSubscriptionUrl == item.url else SettingsManager.activeEpgUrl == item.url
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if(isFocused) Color(0xFF444444) else if (isActive) Color(0xFF1E3A8A) else Color(0xFF222222))
                                                .onFocusChanged { isFocused = it.isFocused }
                                                .focusable()
                                                .clickable { 
                                                    subName = item.name
                                                    subUrl = item.url
                                                    if (dialogType == "列表订阅") SettingsManager.activeSubscriptionUrl = item.url
                                                    if (dialogType == "EPG订阅") SettingsManager.activeEpgUrl = item.url
                                                    Toast.makeText(context, "已设为当前活跃源", Toast.LENGTH_SHORT).show()
                                                },
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                if (isActive) {
                                                    Text("✅ ", color = Color.White, fontSize = 14.sp)
                                                }
                                                Text(text = item.name, color = if (isActive) Color.White else Color.Gray, fontSize = 16.sp, maxLines = 1)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        var itemTrashFocused by remember { mutableStateOf(false) }
                                        // Delete Icon
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if(itemTrashFocused) Color(0xFF555555) else Color(0xFF222222))
                                                .onFocusChanged { itemTrashFocused = it.isFocused }
                                                .focusable()
                                                .clickable { 
                                                    items.removeAt(index)
                                                    if (dialogType == "列表订阅") SettingsManager.subscriptions = items
                                                    if (dialogType == "EPG订阅") SettingsManager.epgs = items
                                                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) { Text("🗑️", color = Color.White) }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        // Bottom Input Fields
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isListType) {
                                var nameFocused by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    value = subName,
                                    onValueChange = { subName = it },
                                    placeholder = { Text("请输入名称(选填)", fontSize = 12.sp, color = Color.Gray) },
                                    modifier = Modifier
                                        .weight(0.6f)
                                        .height(56.dp)
                                        .onFocusChanged { nameFocused = it.isFocused }
                                        .focusable(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedBorderColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedTextColor = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            
                            var urlFocused by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = subUrl,
                                onValueChange = { subUrl = it },
                                placeholder = { 
                                    if (dialogType == "Headers") Text("{\"User-Agent\":\"Player\"}", fontSize = 12.sp, color = Color.Gray) 
                                    else Text("http://...", fontSize = 12.sp, color = Color.Gray)
                                },
                                modifier = Modifier
                                    .weight(1.8f)
                                    .height(56.dp)
                                    .onFocusChanged { urlFocused = it.isFocused }
                                    .focusable(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedBorderColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedTextColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            var confirmFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .width(80.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (confirmFocused) Color(0xFF007BFF) else Color(0xFF333333))
                                    .onFocusChanged { confirmFocused = it.isFocused }
                                    .focusable()
                                    .clickable {
                                        if (isListType) {
                                            if (subUrl.isNotEmpty()) {
                                                val n = if (subName.isBlank()) "订阅 ${items.size + 1}" else subName
                                                val existing = items.indexOfFirst { it.name == n || it.url == subUrl }
                                                if (existing >= 0) {
                                                    items[existing] = Subscription(n, subUrl)
                                                } else {
                                                    items.add(Subscription(n, subUrl))
                                                }
                                                if (dialogType == "列表订阅") SettingsManager.subscriptions = items
                                                if (dialogType == "EPG订阅") SettingsManager.epgs = items
                                            }
                                        } else {
                                            if (dialogType == "UserAgent") SettingsManager.userAgent = subUrl
                                            if (dialogType == "Headers") SettingsManager.headers = subUrl
                                        }
                                        
                                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(if (isListType) "保存/更新" else "确定", color = Color.White, fontSize = 14.sp) }
                        }
                    }
                }
            }
        }
    }
}

private fun generateQRCode(content: String): Bitmap? {
    return try {
        val size = 512
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
