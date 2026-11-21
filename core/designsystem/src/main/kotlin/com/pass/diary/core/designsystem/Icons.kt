package com.pass.diary.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The 시안's line icons (24 grid, round caps). Same path data as _source/lib.py so they match 1:1. */
enum class OdIcon(val d: String) {
    Pen("M4 20h4L19 9l-4-4L4 16v4z M13.5 6.5l4 4"), Cal("M4 6.5h16V20H4z M4 10.5h16 M8.5 3.5v4 M15.5 3.5v4"),
    Chart("M5 20v-7 M12 20V5 M19 20v-11"), User("M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4.5 20.5c1.4-3.6 4.2-5.5 7.5-5.5s6.1 1.9 7.5 5.5"),
    Search("M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M20 20l-4-4"), Close("M6 6l12 12 M18 6L6 18"), Back("M15 5l-7 7 7 7"),
    ChevR("M9.5 6l6 6-6 6"), ChevL("M14.5 6l-6 6 6 6"), ChevD("M6 9.5l6 6 6-6"),
    Mic("M12 14.5a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5.5a3 3 0 0 0 3 3z M6.5 11a5.5 5.5 0 0 0 11 0 M12 16.5V20"),
    Chat("M20 11.5a8 8 0 0 1-11.6 7.1L4 20l1.3-4A8 8 0 1 1 20 11.5z"), Image("M4 5h16v14H4z M4 16l5-5 4 4 2.5-2.5L20 17 M15.5 9.5h.01"),
    Tag("M4 4h7.5l8.5 8.5-7.5 7.5L4 11.5V4z M8 8h.01"), Flag("M5.5 21V4 M5.5 4.5h11l-2 4 2 4h-11"), Check("M5 12.5l4.5 4.5L19 7.5"),
    Sparkle("M12 3.5l1.7 5L19 10.2l-5.3 1.7L12 17l-1.7-5.1L5 10.2l5.3-1.7z"),
    Shield("M12 3l7.5 3v5.5c0 4.8-3.3 8-7.5 9.5-4.2-1.5-7.5-4.7-7.5-9.5V6L12 3z M8.6 12l2.4 2.4 4.4-4.6"),
    Download("M12 4v11 M7 10.5l5 5 5-5 M5 20h14"), Bell("M6.5 16v-5a5.5 5.5 0 0 1 11 0v5l1.5 2h-14l1.5-2z M10 21h4"),
    Lock("M6 11h12v9.5H6z M8.5 11V8a3.5 3.5 0 0 1 7 0v3"), Plus("M12 5v14 M5 12h14"),
    Phone("M6 4h3.5l1.8 4.5-2.3 1.4a10.5 10.5 0 0 0 5.1 5.1l1.4-2.3L20 14.5V18a2 2 0 0 1-2 2A15 15 0 0 1 4 6a2 2 0 0 1 2-2z"),
    Heart("M12 19.5s-7-4.3-7-9.7A3.9 3.9 0 0 1 12 7.6a3.9 3.9 0 0 1 7 2.2c0 5.4-7 9.7-7 9.7z"), Mail("M4 6h16v12H4z M4.5 6.5l7.5 6 7.5-6"),
    Share("M12 15V4 M8 8l4-4 4 4 M5 13v7h14v-7"), Doc("M7 3.5h7l4.5 4.5v12.5H7z M14 3.5v4.5h4.5"),
    Trash("M5 7h14 M10 7V4.5h4V7 M7 7l1 13h8l1-13"),
    Sun("M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M12 2.5v2 M12 19.5v2 M4.5 12h-2 M21.5 12h-2 M5.3 5.3l1.4 1.4 M17.3 17.3l1.4 1.4 M5.3 18.7l1.4-1.4 M17.3 6.7l1.4-1.4"),
    Palette("M12 3.5a8.5 8.5 0 1 0 0 17c1.1 0 1.7-.7 1.7-1.6 0-1.1-.9-1.5-.9-2.5 0-.9.7-1.6 1.7-1.6h2a4 4 0 0 0 4-4c0-4.2-3.8-7.3-8.5-7.3z M7.5 12h.01 M9.5 8h.01 M14.5 8h.01"),
    Pause("M9 6v12 M15 6v12"), Wifi("M4 9.5a12 12 0 0 1 16 0 M7 12.8a7.5 7.5 0 0 1 10 0 M10 16a3 3 0 0 1 4 0 M12 19h.01"),
    Finger("M8 20c-1-2-1.5-4-1.5-7a5.5 5.5 0 0 1 11 0v1 M12 13v2.5c0 1.8.6 3.4 1.5 4.5 M9.5 18.5c-.3-1.2-.5-3-.5-5.5a3 3 0 0 1 6 0v.5 M17.5 17c-.2-.8-.3-1.6-.3-2.5"),
    More("M12 6h.01 M12 12h.01 M12 18h.01"), Backspace("M9 6h11v12H9l-5-6 5-6z M12.5 9.5l5 5 M17.5 9.5l-5 5"),
    Crown("M4 8l4 4 4-6 4 6 4-4-1.5 10h-13z"), Send("M5 12h12 M12 6l6 6-6 6"), Refresh("M19 8a8 8 0 1 0 1 6 M19 3.5V8h-4.5");

    private val cache = HashMap<Float, ImageVector>()

    fun vector(stroke: Float): ImageVector = cache.getOrPut(stroke) {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(d).toNodes(), fill = null, stroke = SolidColor(Color.Black),
            strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()
    }
}

@Composable
fun OdIcon(icon: OdIcon, modifier: Modifier = Modifier, size: Dp = 22.dp, stroke: Float = 1.7f, tint: Color = Od.c.ink, contentDescription: String? = null) =
    Icon(icon.vector(stroke), contentDescription, modifier = modifier.size(size), tint = tint)

/** 「오늘」 tab: Oni's silhouette (final/oni_mono_silhouette.svg). */
val OniMono: ImageVector by lazy {
    ImageVector.Builder("oniMono", 24.dp, 24.dp, 512f, 512f)
        .addPath(
            PathParser().parsePathString(
                "M66 285C56 152.5 136 40 256 40C376 40 456 152.5 446 285C443.5 315 366 332.5 256 332.5C146 332.5 68.5 315 66 285Z" +
                    "M174 203.8A24 33 0 1 0 222 203.8A24 33 0 1 0 174 203.8Z M290 203.8A24 33 0 1 0 338 203.8A24 33 0 1 0 290 203.8Z",
            ).toNodes(),
            fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd,
        )
        .addPath(
            PathParser().parsePathString("M216 366.5h80v62h-80Z M137 410.5H375A31 31 0 0 1 375 472.5H137A31 31 0 0 1 137 410.5Z").toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}
