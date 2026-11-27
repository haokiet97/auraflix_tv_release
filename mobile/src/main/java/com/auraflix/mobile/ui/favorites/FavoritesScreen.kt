package com.auraflix.mobile.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraflix.mobile.MobileGraph
import com.auraflix.mobile.ui.components.EmptyState
import com.auraflix.mobile.ui.components.MoviePosterCard
import com.auraflix.mobile.ui.theme.AuraColors

@Composable
fun FavoritesScreen(
    onOpenMovie: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val favorites by MobileGraph.userData.favorites.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .statusBarsPadding(),
    ) {
        Text(
            text = "Phim yêu thích",
            color = AuraColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )

        Box(Modifier.weight(1f)) {
            if (favorites.isEmpty()) {
                EmptyState("Chưa có phim yêu thích.\nNhấn \"Yêu thích\" ở trang chi tiết phim.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(favorites, key = { it.slug }) { movie ->
                        MoviePosterCard(movie = movie, onClick = { onOpenMovie(movie.slug) })
                    }
                }
            }
        }
    }
}
