package com.pass.diary.core.billing

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject

/** AI_GROWTH §5: the four moments premium is suggested. [say] is Oni (반말), [body] the app (존댓말). */
enum class NudgeReason(val say: String, val body: String) {
    STREAK7("벌써 7일째 불 켜 줬네. 고마워!", "프리미엄은 한 번 결제로 평생이에요. 광고 없이, 특별 팔레트와 글꼴, 올해의 빛까지 다 열려요."),
    TRIAL_END("오늘 써 본 빛, 마음에 들었어?", "프리미엄이면 특별 팔레트를 언제든 쓸 수 있어요. 구독이 아니라 한 번 결제로 평생이에요."),
    SHARE("카드 예쁘게 나왔다!", "프리미엄이면 워터마크 없이, 다른 카드 테마로도 만들 수 있어요."),
    YEAR_END("올해도 빛을 많이 모았네.", "365일의 빛을 한 장에 담은 「올해의 빛」은 프리미엄에서 열려요. 한 번 결제로 평생이에요."),
}

/**
 * At most one suggestion a day, each one-time moment once ([onceKey]), and never again after "다시 보지 않기".
 * Premium users never see one (callers check). SharedPreferences: this phone's choice.
 */
class NudgeGate @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("nudge", Context.MODE_PRIVATE)

    fun canShow(onceKey: String?, today: LocalDate = LocalDate.now()): Boolean =
        !prefs.getBoolean(NEVER, false) && prefs.getLong(DAY, Long.MIN_VALUE) != today.toEpochDay() &&
            (onceKey == null || !prefs.getBoolean("once_$onceKey", false))

    fun shown(onceKey: String?, today: LocalDate = LocalDate.now()) {
        prefs.edit().putLong(DAY, today.toEpochDay()).apply { if (onceKey != null) putBoolean("once_$onceKey", true) }.apply()
    }

    fun never() = prefs.edit().putBoolean(NEVER, true).apply()

    private companion object {
        const val NEVER = "never"
        const val DAY = "last_day"
    }
}

/**
 * The soft suggestion: Oni's line, one sentence, 프리미엄 보기 / [dismissLabel] / 다시 보지 않기. Ads never appear here.
 * [onPremium] replaces [onDismiss] for the first button (the caller hides the sheet and opens the paywall).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumNudgeSheet(reason: NudgeReason, gate: NudgeGate, onPremium: () -> Unit, onDismiss: () -> Unit, dismissLabel: String = "다음에 볼게요") {
    val c = Od.c
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OniFigure(null, OniExpression.CHEER, 96.dp)
            Text(reason.say, style = OdType.say, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Text(reason.body, style = OdType.body2, color = c.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            OdButton("프리미엄 보기", onPremium, Modifier.fillMaxWidth().padding(top = 18.dp))
            OdButton(dismissLabel, onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp), kind = ButtonKind.Ghost)
            Text(
                "다시 보지 않기", style = OdType.caption2, color = c.ink3,
                modifier = Modifier.padding(top = 4.dp).clickable { gate.never(); onDismiss() }.padding(8.dp),
            )
        }
    }
}
