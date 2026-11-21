package com.pass.diary.core.designsystem

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 위기 안내 (PLAN §3.2): 109 · 1577-0199, no ads, no share, no warning colors.
 * [when] is "오늘" for one entry (답장) and "요즘" for a few days (체크인·편지).
 */
@Composable
fun CrisisCard(modifier: Modifier = Modifier, `when`: String = "오늘") {
    val c = Od.c
    val context = LocalContext.current
    fun dial(n: String) = runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) }
    OdCard(modifier, padding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp)) {
        Text("$`when` 정말 많이 무거웠구나. 혼자 다 견디지 않아도 돼. 지금 이 마음을 같이 들어 줄 사람이 있어.", style = OdType.reply)
        Text("— 온이가", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 10.dp))
        Divider(Modifier.padding(top = 16.dp, bottom = 6.dp))
        CallRow("109", "자살예방 상담 · 24시간") { dial("109") }
        CallRow("1577-0199", "정신건강 위기상담 · 24시간") { dial("15770199") }
        Text("문자·카카오톡 상담도 109 누리집에서 할 수 있어요.", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
    }
}

@Composable
private fun CallRow(number: String, desc: String, onCall: () -> Unit) {
    val c = Od.c
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF7F9FD1).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { OdIcon(OdIcon.Phone, size = 20.dp) }
        Column(Modifier.weight(1f)) {
            Text(number, style = OdType.title3.copy(fontSize = 16.sp))
            Text(desc, style = OdType.caption2, color = c.ink2)
        }
        OdButton("전화", onCall, small = true, height = 36.dp, modifier = Modifier.width(64.dp))
    }
}
