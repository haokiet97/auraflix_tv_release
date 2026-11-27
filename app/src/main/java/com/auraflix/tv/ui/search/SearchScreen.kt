package com.auraflix.tv.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auraflix.tv.Graph
import com.auraflix.tv.ui.components.EmptyScreen
import com.auraflix.tv.ui.components.PosterCard
import com.auraflix.tv.ui.components.TvButton
import com.auraflix.tv.ui.components.TvFocusable
import com.auraflix.tv.ui.factoryOf
import com.auraflix.tv.ui.theme.AuraColors

private val KEYBOARD_ROWS = listOf(
    "1234567890",
    "qwertyuiop",
    "asdfghjkl",
    "zxcvbnm",
)

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenMovie: (String) -> Unit,
) {
    val vm: SearchViewModel = viewModel(factory = factoryOf { SearchViewModel(Graph.movies) })
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .onKeyEvent { event -> handleHardwareKey(event, vm) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.5f)
                    .verticalScroll(rememberScrollState())
                    .padding(end = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvButton(text = "‹", onClick = onBack)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Tìm kiếm",
                        color = AuraColors.TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(18.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(AuraColors.Surface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = state.query.ifBlank { "Nhập tên phim…" },
                        color = if (state.query.isBlank()) AuraColors.TextMuted else AuraColors.TextPrimary,
                        fontSize = 18.sp,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Dùng remote, chuột hoặc bàn phím USB/Bluetooth để nhập.",
                    color = AuraColors.TextMuted,
                    fontSize = 12.sp,
                )

                Spacer(Modifier.height(12.dp))

                TvKeyboard(
                    onKey = { char ->
                        if (state.query.length < 40) vm.onQueryChange(state.query + char)
                    },
                    onBackspace = { vm.onQueryChange(state.query.dropLast(1)) },
                    onClear = { vm.onQueryChange("") },
                    onSearch = { vm.searchNow() },
                )
            }

            Spacer(Modifier.width(24.dp))

            Box(Modifier.fillMaxSize()) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AuraColors.Accent)
                    }

                    state.results.isEmpty() && state.searched ->
                        EmptyScreen("Không tìm thấy phim \"${state.query}\".")
                    state.results.isEmpty() -> EmptyScreen("Dùng remote, chuột hoặc bàn phím để tìm phim.")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(state.results, key = { it.slug }) { movie ->
                            PosterCard(movie = movie, onClick = { onOpenMovie(movie.slug) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Nhận ký tự từ bàn phím USB/Bluetooth. Đặt ở cấp màn hình để gõ được bất kể
 * tiêu điểm đang ở phím ảo, poster hay nút — remote vẫn hoạt động bình thường.
 * Trả về true để "nuốt" sự kiện, tránh phím ảo nhận trùng.
 */
private fun handleHardwareKey(event: KeyEvent, vm: SearchViewModel): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    when (event.key) {
        Key.Backspace -> {
            vm.onQueryChange(vm.state.value.query.dropLast(1))
            return true
        }
        Key.Spacebar -> {
            vm.onQueryChange(vm.state.value.query + " ")
            return true
        }
        Key.Enter, Key.NumPadEnter -> {
            vm.searchNow()
            return true
        }
    }

    val code = event.utf16CodePoint
    if (code in 32..0xFFFF) {
        val char = code.toChar()
        if (!char.isISOControl()) {
            vm.onQueryChange(vm.state.value.query + char)
            return true
        }
    }
    return false
}

@Composable
private fun TvKeyboard(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        KEYBOARD_ROWS.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEachIndexed { colIndex, char ->
                    KeyBox(
                        label = char.toString(),
                        onClick = { onKey(char.toString()) },
                        modifier = if (rowIndex == 0 && colIndex == 0) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        },
                    )
                }
                if (rowIndex == KEYBOARD_ROWS.lastIndex) {
                    Spacer(Modifier.width(4.dp))
                    KeyBox(label = "Space", onClick = { onKey(" ") }, width = 70.dp)
                    Spacer(Modifier.width(4.dp))
                    KeyBox(label = "⌫", onClick = onBackspace, width = 56.dp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(text = "Xoá hết", onClick = onClear)
            TvButton(text = "Tìm phim", onClick = onSearch, primary = true)
        }
    }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }
}

@Composable
private fun KeyBox(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 38.dp,
) {
    TvFocusable(
        onClick = onClick,
        modifier = modifier.size(width = width, height = 44.dp),
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.10f,
        containerColor = AuraColors.Surface,
        focusedContainerColor = AuraColors.Accent,
        focusedBorderColor = AuraColors.Accent,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = AuraColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
