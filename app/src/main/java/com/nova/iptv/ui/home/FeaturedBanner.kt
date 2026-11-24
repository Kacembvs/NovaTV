package com.nova.iptv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import com.nova.iptv.ui.components.NovaButton as Button
import com.nova.iptv.ui.components.NovaOutlinedButton as OutlinedButton
import com.nova.iptv.ui.components.NovaIconButton as IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.components.ContentImage
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.TvInterface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cinematic banner; rotates slowly (12 s) and stops rotating while the user interacts with it. */
@Composable
fun FeaturedBanner(
    items: List<ContentEntity>,
    tv: Boolean,
    onWatch: (ContentEntity) -> Unit,
    onDetails: (ContentEntity) -> Unit,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(pageCount = { items.size })
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    // On TV a single slide is composed at a time: a pager would also compose the buttons of
    // neighbouring pages, and directional focus search could jump to them and scroll forever.
    var tvPage by remember { mutableIntStateOf(0) }
    if (tvPage >= items.size) tvPage = 0
    val currentPage = if (tv) tvPage else pager.currentPage.coerceAtMost(items.size - 1)
    var focused by remember { mutableStateOf(false) }
    var manualRequest by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    fun move(direction: Int) {
        manualRequest++
        if (tv) {
            tvPage = (tvPage + direction + items.size) % items.size
        } else if (!pager.isScrollInProgress) scope.launch {
            pager.animateScrollToPage((pager.currentPage + direction + items.size) % items.size)
        }
    }
    LaunchedEffect(items, focused, dragged, pager.settledPage, tvPage, manualRequest) {
        if (!focused && !dragged && items.size > 1) {
            delay(12_000)
            if (tv) tvPage = (tvPage + 1) % items.size
            else if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .onFocusChanged { focused = it.hasFocus }
            .focusGroup(),
    ) {
        if (tv) {
            Crossfade(
                targetState = items[currentPage],
                modifier = Modifier.fillMaxWidth().testTag("featured-banner"),
                label = "featured",
            ) { item ->
                FeaturedSlide(item, true, onWatch, onDetails)
            }
        } else {
            HorizontalPager(
                state = pager,
                userScrollEnabled = items.size > 1,
                modifier = Modifier.fillMaxWidth().testTag("featured-banner"),
            ) { page ->
                FeaturedSlide(items[page], false, onWatch, onDetails)
            }
        }
        if (items.size > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    .onPreviewKeyEvent { event ->
                        if (!tv) return@onPreviewKeyEvent false
                        val direction = when (event.key) {
                            Key.DirectionRight -> 1
                            Key.DirectionLeft -> if (currentPage > 0) -1 else return@onPreviewKeyEvent false
                            else -> return@onPreviewKeyEvent false
                        }
                        if (event.type == KeyEventType.KeyDown) move(direction)
                        true
                    },
            ) {
                IconButton(onClick = { move(-1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Suggestion précédente")
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.indices.forEach { index ->
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(if (index == currentPage) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (index == currentPage) NovaColors.Cyan else NovaColors.TextSecondary.copy(alpha = 0.5f)),
                        )
                    }
                    Text(
                        "${currentPage + 1} / ${items.size}",
                        modifier = Modifier.padding(start = 12.dp),
                        color = NovaColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = { move(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Suggestion suivante")
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FeaturedSlide(
    item: ContentEntity,
    tv: Boolean,
    onWatch: (ContentEntity) -> Unit,
    onDetails: (ContentEntity) -> Unit,
) {
    val style = LocalTvInterface.current
    val cinematic = tv && style == TvInterface.CINEMA
    val compact = tv && style == TvInterface.GUIDE
    Box(
        Modifier.fillMaxWidth()
            .height(if (tv) style.heroHeight.dp else 230.dp)
            .clip(RoundedCornerShape(if (tv) style.cardRadius.dp else 22.dp))
            .background(NovaColors.Background2),
    ) {
        AsyncImage(
            model = item.logo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.45f),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(NovaColors.Background, NovaColors.Background.copy(alpha = 0.6f), Color.Transparent))
            )
        )
        Row(
            Modifier.fillMaxSize().padding(if (tv) 32.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (item.kind == ContentKind.MOVIE) "FILM" else "SÉRIE",
                    color = NovaColors.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    item.name,
                    fontSize = if (cinematic) 40.sp else if (tv) 30.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(item.year, item.groupTitle, item.rating?.takeIf { it != "0" }?.let { "★ $it" })
                Text(meta.joinToString(" · "), color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
                item.description?.takeUnless { compact }?.let {
                    Text(it, color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = if (tv) 3 else 2, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(onClick = { onWatch(item) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (item.kind == ContentKind.MOVIE) "Regarder" else "Voir la série")
                    }
                    OutlinedButton(
                        onClick = { onDetails(item) },
                        modifier = if (tv) Modifier
                            .focusProperties { right = FocusRequester.Cancel }
                            .onPreviewKeyEvent { it.key == Key.DirectionRight } else Modifier,
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Détails")
                    }
                }
            }
            if (!cinematic) {
                ContentImage(
                    item.logo,
                    item.name,
                    Modifier.width(if (compact) 100.dp else if (tv) 170.dp else 110.dp)
                        .aspectRatio(2f / 3f).clip(RoundedCornerShape(if (tv) style.cardRadius.dp else 14.dp)),
                )
            }
        }
    }
}
