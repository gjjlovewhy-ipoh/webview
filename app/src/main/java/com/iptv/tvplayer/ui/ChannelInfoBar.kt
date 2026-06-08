package com.iptv.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import android.net.TrafficStats

@Composable
fun ChannelInfoBar(
    channelNumber: String,
    channelName: String,
    resolution: String = "1080P",
    networkType: String = "IPV4",
    currentLineIndex: Int = 1,
    totalLines: Int = 1,
    decoderType: String = "MPV",
    epgCurrent: String = "精彩节目",
    epgNext: String = "精彩节目"
) {
    var currentTime by remember { mutableStateOf("") }
    var networkSpeed by remember { mutableStateOf("0 KB/s") }

    LaunchedEffect(Unit) {
        val formatter = SimpleDateFormat("HH:mm", Locale.CHINESE)
        var lastTotalRxBytes = TrafficStats.getTotalRxBytes()
        while (true) {
            currentTime = formatter.format(Date())
            val currentRxBytes = TrafficStats.getTotalRxBytes()
            val diffBytes = currentRxBytes - lastTotalRxBytes
            lastTotalRxBytes = currentRxBytes
            networkSpeed = if (diffBytes >= 0) "${diffBytes / 1024} KB/s" else "0 KB/s"
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .padding(bottom = 24.dp)
            .widthIn(max = 600.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xB3000000)) // 70% black background, floating pill
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Row: Number | Name -------------- Resolution [IPV4] [Line 1/1]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Number
                Text(
                    text = channelNumber,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Divider
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(20.dp)
                        .background(Color(0xFF007BFF))
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                // Name
                Text(
                    text = channelName,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Clock & Network
                Text(
                    text = "$currentTime  $networkSpeed",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )

                // Decoder Type
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = decoderType,
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(6.dp))

                // Resolution
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = resolution,
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(6.dp))

                // Network Type (IPV4/IPV6)
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = networkType,
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(6.dp))

                // Line indicator
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "线路$currentLineIndex/$totalLines",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
            
            // Horizontal Separator Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(1.dp)
                    .background(Color.DarkGray)
            )
            
            // Bottom Row: EPG
            Column {
                Row(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = "21:00 - 21:59", // Placeholder time
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.width(120.dp)
                    )
                    Text(
                        text = epgCurrent,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
                Row {
                    Text(
                        text = "22:00 - 22:59", // Placeholder time
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.width(120.dp)
                    )
                    Text(
                        text = epgNext,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
