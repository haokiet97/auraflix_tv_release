package com.auraflix.tv.ui.favorites

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraflix.tv.Graph
import com.auraflix.tv.ui.components.EmptyScreen
import com.auraflix.tv.ui.components.PosterCard
import com.auraflix.tv.ui.components.TvButton
import com.auraflix.tv.ui.theme.AuraColors

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenMovie: (String) -> Unit,
) {
    val favorites by Graph.userData.favorites.collectAsState(initial = emptyList())

    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .padding(horizontal = 56.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton(text = "‹", onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Phim yêu thích",
                color = AuraColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(20.dp))

        Box(Modifier.fillMaxSize()) {
            if (favorites.isEmpty()) {
                EmptyScreen("Chưa có phim yêu thích. Nhấn \"Yêu thích\" ở trang chi tiết phim.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(favorites, key = { it.slug }) { movie ->
                        PosterCard(movie = movie, onClick = { onOpenMovie(movie.slug) })
                    }
                }
            }
        }
    }
}
