package com.pass.diary.feature.my

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pass.diary.core.billing.PremiumState
import com.pass.diary.core.billing.PurchaseController
import com.pass.diary.core.billing.RestoreResult
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.ambient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(val premium: PremiumState, val purchases: PurchaseController) : ViewModel()

private val main = Handler(Looper.getMainLooper())

/** Play's localized price without the currency sign ("₩3,300" → "3,300원"); the planned price until Play answers. */
internal fun won(price: String?): String {
    val digits = price?.filter { it.isDigit() || it == ',' }?.trim(',')
    return if (digits.isNullOrEmpty() || price.any { it == '$' || it == '€' }) price ?: "3,300원" else "${digits}원"
}

// ======================================================================= 프리미엄 (ARCHITECTURE §7: "폰을 바꿔도 그대로" 크게)


@Composable
fun PremiumScreen(onClose: () -> Unit, vm: PremiumViewModel = hiltViewModel()) {
    val c = Od.c
    val ctx = LocalContext.current
    val activity = LocalActivity.current
    val premium by vm.premium.isPremium.collectAsStateWithLifecycle(false)
    val price by vm.purchases.price.collectAsStateWithLifecycle()
    var restoring by remember { mutableStateOf(false) }
    val toast = { msg: String -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(c.bg).ambient(OdColors.BrandGlow, 1.3f, centerY = 170.dp, radius = 460.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            OdTopBar(left = { IconBtn(OdIcon.Close, onClose, "닫기") }, right = {
                Text(if (restoring) "확인 중…" else "구매 복원", style = OdType.caption1, color = c.ink2, modifier = Modifier.clickable(enabled = !restoring) {
                    restoring = true
                    vm.purchases.restore { r ->
                        main.post {
                            restoring = false
                            toast(when (r) {
                                RestoreResult.RESTORED -> "프리미엄을 되찾았어요"
                                RestoreResult.NOTHING -> "이 Google 계정에는 구매 기록이 없어요"
                                RestoreResult.FAILED -> "Play 스토어에 연결하지 못했어요. 잠시 뒤 다시 해 주세요"
                            })
                        }
                    }
                }.padding(8.dp))
            })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                OniFigure(null, OniExpression.CHEER, 150.dp, Modifier.offset(y = (-6).dp))
                Text(if (premium) "프리미엄을\n쓰고 있어요" else "한 번 ${won(price)},\n평생 그대로예요", style = OdType.display, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                Text("구독이 아니에요. 다시 결제할 일 없어요.", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                    val inv = if (c.dark) OdColors.BrandLight to OdColors.Ink else OdColors.Ink to OdColors.Paper
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(inv.first).padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(OdColors.BrandGlow.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                            OdIcon(OdIcon.Refresh, size = 22.dp, stroke = 2f, tint = inv.second)
                        }
                        Column {
                            Text("폰을 바꿔도 그대로", style = OdType.title2.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = inv.second)
                            Text(
                                "같은 Google 계정으로 로그인하면 일기도, 프리미엄도 자동으로 돌아와요.",
                                style = OdType.caption1, color = inv.second.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    OdCard(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        listOf(
                            "광고 영구 제거" to "캘린더·리포트 하단 배너까지 모두 사라져요", "올해의 빛" to "365일 빛 모자이크와 공유 카드",
                            "특별 빛 팔레트 · 글꼴" to "겨울밤·봄 팔레트, 손글씨 글꼴로 내 취향대로", "공유 카드 워터마크 끄기 · 카드 테마" to "오늘의 빛·주간 편지·빛 캘린더 카드를 깔끔하게",
                            "PDF 일기책" to "한 달 일기를 책처럼 묶어 저장 (다음 업데이트에서 열려요)",
                        ).forEachIndexed { i, (a, b) ->
                            Row(Modifier.padding(top = if (i == 0) 0.dp else 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.padding(top = 1.dp).size(22.dp).clip(CircleShape).background(OdColors.BrandGlow.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                                    OdIcon(OdIcon.Check, size = 14.dp, stroke = 2.6f)
                                }
                                Column {
                                    Text(a, style = OdType.title3)
                                    Text(b, style = OdType.caption2, color = c.ink2)
                                }
                            }
                        }
                    }
                    Text(
                        "감정 20종 · 통계 · 백업 · 온이 답장 · AI 기능은 지금처럼 전부 무료예요.", style = OdType.caption2, color = c.ink2,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                    )
                }
            }
            OdButton(
                if (premium) "고마워요, 평생 그대로예요" else "${won(price)}으로 평생 쓰기",
                {
                    if (premium) onClose()
                    else if (activity == null || !vm.purchases.purchase(activity)) toast("결제 창을 열지 못했어요. 인터넷 연결을 확인하고 다시 해 주세요")
                },
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            )
        }
    }
}

