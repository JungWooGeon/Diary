package com.pass.diary.feature.my

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.core.designsystem.Divider
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import kotlinx.serialization.Serializable

@Serializable data object LicensesRoute

/** [file] = app assets/licenses/<file> (full license text). */
internal data class OssNotice(val name: String, val use: String, val license: String, val copyright: String, val file: String)

/** Everything bundled that asks for a notice (tools/classifier/LICENSES.md + app dependencies). */
internal val ossNotices = listOf(
    OssNotice("Pretendard", "기본 글꼴 (서브셋)", "SIL OFL 1.1", "Copyright (c) 2021, Kil Hyung-jin", "Pretendard-OFL.txt"),
    OssNotice("Gaegu", "손글씨 글꼴 (서브셋)", "SIL OFL 1.1", "Copyright 2018 The Gaegu Project Authors", "Gaegu-OFL.txt"),
    OssNotice("KOTE", "감정 분류기 학습 데이터", "MIT", "Copyright (c) 2022 Jeon Duyoung", "MIT.txt"),
    OssNotice(
        "KoELECTRA (koelectra-small-v3-discriminator)", "감정 분류기 인코더 — 감정 분류용으로 미세조정·int8 양자화해 변경함",
        "Apache-2.0", "Copyright 2020 Jangwon Park (monologg)", "Apache-2.0.txt",
    ),
    OssNotice("LiteRT (TensorFlow Lite)", "기기 안 감정 분류 실행", "Apache-2.0", "Copyright The TensorFlow Authors", "Apache-2.0.txt"),
    OssNotice("Open-Meteo", "날씨 기록 데이터", "CC BY 4.0", "Weather data by Open-Meteo.com", "CC-BY-4.0.txt"),
    OssNotice("AndroidX · Jetpack Compose · Glance · WorkManager · Room · DataStore", "앱 화면과 저장", "Apache-2.0", "Copyright The Android Open Source Project", "Apache-2.0.txt"),
    OssNotice("Kotlin · kotlinx.coroutines · kotlinx.serialization", "앱 코드", "Apache-2.0", "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors", "Apache-2.0.txt"),
    OssNotice("Dagger · Hilt", "앱 구조", "Apache-2.0", "Copyright The Dagger Authors", "Apache-2.0.txt"),
    OssNotice("OkHttp", "드라이브 백업 통신", "Apache-2.0", "Copyright Square, Inc.", "Apache-2.0.txt"),
    OssNotice("Coil", "사진 표시", "Apache-2.0", "Copyright Coil Contributors", "Apache-2.0.txt"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val c = Od.c
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<OssNotice?>(null) }
    BackHandler { if (open != null) open = null else onBack() }
    val o = open
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        OdTopBar(title = o?.name?.substringBefore(" (") ?: "오픈소스 라이선스", left = { IconBtn(OdIcon.Back, { if (open != null) open = null else onBack() }, "뒤로") })
        if (o != null) {
            val text = remember(o) { runCatching { ctx.assets.open("licenses/${o.file}").bufferedReader().use { it.readText() } }.getOrDefault("") }
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), modifier = Modifier.navigationBarsPadding()) {
                item { Text(o.copyright, style = OdType.title3, modifier = Modifier.padding(bottom = 12.dp)) }
                // Paragraph-sized items: the Apache text is long.
                items(text.split("\n\n")) { Text(it.trim('\n'), style = OdType.caption2.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp), color = c.ink2, modifier = Modifier.padding(bottom = 10.dp)) }
            }
        } else LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), modifier = Modifier.navigationBarsPadding()) {
            item {
                OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
                    ossNotices.forEachIndexed { i, n ->
                        if (i > 0) Divider()
                        Column(Modifier.fillMaxWidth().clickable { open = n }.padding(vertical = 12.dp)) {
                            Text(n.name, style = OdType.title3)
                            Text("${n.license} · ${n.use}", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
