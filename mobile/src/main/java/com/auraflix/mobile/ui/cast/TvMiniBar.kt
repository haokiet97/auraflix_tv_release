package com.auraflix.mobile.ui.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.auraflix.mobile.ui.theme.AuraColors
import com.auraflix.tv.core.cast.CastPlaybackState

/**
 * Thanh "đang phát trên TV" hiển thị trong app khi điện thoại đã ghép cặp và TV đang chiếu phim.
 * Bấm vào mở [RemoteScreen]. Đặt [modifier] ở nơi cố định (trên thanh điều hướng dưới).
 */
@Composable
fun TvMiniBar(
    playback: CastPlaybackState?,
    isPaired: Boolean,
    onOpenRemote: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isPaired || playback?.active != true) return
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AuraColors.Surface)
            .clickable(onClick = onOpenRemote)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 64.dp, height = 36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(AuraColors.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (playback.posterUrl != null) {
                AsyncImage(
                    model = playback.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.Tv, contentDescription = null, tint = AuraColors.TextMuted)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playback.movieName,
                color = AuraColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    "Đang phát trên TV",
                    playback.episodeLabel.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                color = AuraColors.AccentSoft,
                fontSize = 10.sp,
                maxLines = 1,
            )
            if (playback.durationMs > 0) {
                val ratio = (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp),
                    color = AuraColors.Accent,
                    trackColor = AuraColors.SurfaceVariant,
                )
            }
        }
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Phát/Tạm dừng trên TV",
                tint = AuraColors.TextPrimary,
            )
        }
    }
}
