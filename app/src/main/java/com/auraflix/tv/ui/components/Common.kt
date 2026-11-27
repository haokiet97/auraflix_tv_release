@file:OptIn(ExperimentalComposeUiApi::class)

package com.auraflix.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.auraflix.tv.core.model.Movie
import com.auraflix.tv.core.repo.HistoryItem
import com.auraflix.tv.ui.theme.AuraColors

/**
 * Khối có thể focus bằng remote. Tự xử lý DPAD_CENTER/ENTER -> onClick
 * để không phụ thuộc hành vi click của Compose trên TV.
 *
 * Ngoài remote, khối cũng nhận chuột/chuột bay: di chuột vào sẽ tự focus,
 * bấm chuột trái sẽ kích hoạt — cần cho các box có điều khiển bằng chuột.
 */
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    focusedScale: Float = 1.08f,
    containerColor: Color = Color.Transparent,
    focusedContainerColor: Color = Color.White.copy(alpha = 0.14f),
    focusedBorderColor: Color = AuraColors.Accent,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "tv-scale")
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    // Chuột bay / chuột thường: rê vào là focus, giống hành vi trên TV.
    LaunchedEffect(hovered) {
        if (hovered) runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            }
            .hoverable(interactionSource)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) focusedContainerColor else containerColor)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) focusedBorderColor else Color.White.copy(alpha = 0.10f),
                shape = shape,
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    ) {
        content(focused)
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    primary: Boolean = false,
    compact: Boolean = false,
) {
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.05f,
        containerColor = if (primary) AuraColors.Accent else Color.White.copy(alpha = 0.12f),
        focusedContainerColor = if (primary) AuraColors.Accent else Color.White,
    ) { focused ->
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 22.dp,
                vertical = if (compact) 9.dp else 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            leading?.let {
                it()
                Spacer(Modifier.width(if (compact) 7.dp else 10.dp))
            }
            Text(
                text = text,
                color = if (focused && !primary) AuraColors.Background else Color.White,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun MetaChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = AuraColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PosterCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TvFocusable(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = movie.posterUrl ?: movie.thumbUrl,
                    contentDescription = movie.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                ) {
                    Text(
                        text = movie.displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    movie.episodeCurrent?.let {
                        Text(
                            text = it,
                            color = AuraColors.AccentSoft,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = listOfNotNull(movie.year?.toString(), movie.quality).joinToString(" · "),
            color = AuraColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun WideCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    Column(modifier = modifier) {
        TvFocusable(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.5f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.9f),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            color = AuraColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
                if (progress != null && progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(AuraColors.Accent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueCard(item: HistoryItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(260.dp)) {
        WideCard(
            title = item.name,
            subtitle = listOfNotNull(item.episodeName.takeIf { it.isNotBlank() }, "${(item.progress * 100).toInt()}%")
                .joinToString(" · "),
            imageUrl = item.posterUrl,
            onClick = onClick,
            progress = item.progress,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AuraColors.Accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = AuraColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        onMore?.let { TvButton(text = "Xem tất cả", onClick = it) }
    }
}

@Composable
fun LoadingScreen(message: String = "Đang tải phim…", modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AuraColors.Accent, strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text(message, color = AuraColors.TextSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text("😕", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = AuraColors.TextSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            TvButton(text = "Thử lại", onClick = onRetry, primary = true)
        }
    }
}

@Composable
fun EmptyScreen(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = AuraColors.TextMuted, fontSize = 16.sp)
    }
}

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        focusedScale = 1.06f,
        containerColor = if (selected) AuraColors.Accent else Color.White.copy(alpha = 0.10f),
        focusedContainerColor = if (selected) AuraColors.Accent else Color.White,
        focusedBorderColor = if (selected) Color.White else AuraColors.Accent,
    ) { focused ->
        Text(
            text = label,
            color = if (focused && !selected) AuraColors.Background else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            maxLines = 1,
        )
    }
}
