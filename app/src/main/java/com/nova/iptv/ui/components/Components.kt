package com.nova.iptv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.*
import com.nova.iptv.ui.components.NovaButton as Button
import com.nova.iptv.ui.components.NovaIconButton as IconButton
import com.nova.iptv.ui.components.NovaFilterChip as FilterChip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.nova.iptv.R
import com.nova.iptv.core.isTvLayout
import com.nova.iptv.data.db.GroupCount
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.LocalTvInterface

/** Clickable card with a clearly visible D-pad focus state (cyan border + slight zoom). */
@Composable
fun NovaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LocalTvInterface.current.cardRadius.dp),
    color: androidx.compose.ui.graphics.Color = NovaColors.Card,
    focusScale: Float = 1.05f,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val tv = isTvLayout()
    val scale by animateFloatAsState(if (focused) focusScale else 1f, tween(150), label = "focusScale")
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(if (tv) Modifier.focusProperties { canFocus = true } else Modifier)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = shape,
        color = color,
        border = if (focused) BorderStroke(if (tv) 3.dp else 2.dp, if (tv) NovaColors.TextPrimary else NovaColors.Cyan)
            else BorderStroke(1.dp, NovaColors.Border),
        interactionSource = interaction,
        content = content,
    )
}

/** Remote image with an initial-letter placeholder (hidden once the image is loaded). */
@Composable
fun ContentImage(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderSize: Int = 22,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.background(NovaColors.Background2), contentAlignment = Alignment.Center) {
        if (!loaded) {
            Text(
                name.trim().take(1).uppercase(),
                color = NovaColors.TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = placeholderSize.sp,
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isFavorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    focusModifier: Modifier = Modifier,
) {
    val tv = isTvLayout()
    val posterFocus = remember { FocusRequester() }
    val favoriteFocus = remember { FocusRequester() }
    Column(modifier) {
        NovaCard(
            onClick = onClick,
            modifier = focusModifier.fillMaxWidth()
                .aspectRatio(if (tv) LocalTvInterface.current.posterAspectRatio else 2f / 3f).focusRequester(posterFocus)
                .then(if (tv && isFavorite != null && onToggleFavorite != null) Modifier.focusProperties { down = favoriteFocus } else Modifier),
        ) {
            ContentImage(imageUrl, title, Modifier.fillMaxSize())
        }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = NovaColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
                }
            }
            if (isFavorite != null && onToggleFavorite != null) {
                FavoriteButton(
                    isFavorite,
                    onToggleFavorite,
                    size = if (tv) 48.dp else 32.dp,
                    modifier = if (tv) Modifier.focusRequester(favoriteFocus).focusProperties {
                        up = posterFocus
                        left = posterFocus
                    } else Modifier,
                )
            }
        }
    }
}

@Composable
fun RatingButtons(rating: Int?, onRate: (Int) -> Unit) {
    IconButton(onClick = { onRate(1) }) {
        Icon(
            if (rating == 1) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
            contentDescription = if (rating == 1) "Retirer J'aime" else "J'aime",
            tint = if (rating == 1) NovaColors.Cyan else NovaColors.TextSecondary,
        )
    }
    IconButton(onClick = { onRate(-1) }) {
        Icon(
            if (rating == -1) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
            contentDescription = if (rating == -1) "Retirer Je n'aime pas" else "Je n'aime pas",
            tint = if (rating == -1) NovaColors.Violet else NovaColors.TextSecondary,
        )
    }
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val targetSize = if (isTvLayout()) size.coerceAtLeast(48.dp) else size
    IconButton(onClick = onToggle, modifier = modifier.size(targetSize)) {
        Icon(
            if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
            tint = if (isFavorite) NovaColors.Cyan else NovaColors.TextSecondary,
        )
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (!subtitle.isNullOrBlank()) Text(subtitle, color = NovaColors.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 20.dp),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
) {
    if (isTvLayout()) {
        var editing by remember { mutableStateOf(false) }
        var draft by remember(value) { mutableStateOf(value) }
        val keyboard = LocalSoftwareKeyboardController.current
        TvActionButton(
            label = if (value.isBlank()) placeholder else "Recherche : $value",
            onClick = { draft = value; editing = true },
            icon = Icons.Rounded.Search,
            modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        )
        if (editing) {
            val inputFocus = remember { FocusRequester() }
            AlertDialog(
                onDismissRequest = { editing = false },
                title = { Text(placeholder) },
                text = {
                    OutlinedTextField(
                        modifier = Modifier.focusRequester(inputFocus),
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            onValueChange(draft)
                            keyboard?.hide()
                            editing = false
                        }),
                    )
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        inputFocus.requestFocus()
                    }
                },
                confirmButton = {
                    NovaTextButton(onClick = { onValueChange(draft); editing = false }) { Text("Rechercher") }
                },
                dismissButton = {
                    NovaTextButton(onClick = { onValueChange(""); editing = false }) { Text("Effacer la recherche") }
                },
            )
        }
        return
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Effacer") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

/** Category chips: "Tout", "Favoris" then the provider groups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChips(
    groups: List<GroupCount>,
    selected: String?,
    favoritesKey: String,
    onSelect: (String?) -> Unit,
    showFavorites: Boolean = true,
    /** Extra chips (key, label) shown after "Favoris", e.g. the user's own groups. */
    extra: List<Pair<String, String>> = emptyList(),
) {
    var picking by remember { mutableStateOf(false) }
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val firstGroupIndex = 2 + (if (showFavorites) 1 else 0) + extra.size
    // Bring the chip chosen from the search panel into view.
    LaunchedEffect(selected) {
        val index = when (selected) {
            null -> 0
            favoritesKey -> 1
            else -> extra.indexOfFirst { it.first == selected }.takeIf { it >= 0 }?.let { firstGroupIndex - extra.size + it }
                ?: groups.indexOfFirst { it.name == selected }.takeIf { it >= 0 }?.let { firstGroupIndex + it }
        }
        if (index != null) rowState.animateScrollToItem(index)
    }
    Column {
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Tout") })
        }
        if (showFavorites) item(key = "fav") {
            FilterChip(
                selected = selected == favoritesKey,
                onClick = { onSelect(favoritesKey) },
                label = { Text("Favoris") },
                leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        // After "Tout"/"Favoris" so the remote's Up key from the content still lands on "Tout".
        item(key = "search") {
            FilterChip(
                selected = picking,
                onClick = { picking = !picking },
                label = { Text("Groupes  ${groups.size + extra.size}") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Rechercher un groupe", modifier = Modifier.size(16.dp)) },
            )
        }
        items(extra, key = { "x:" + it.first }) { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label, maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        items(groups, key = { "g:" + it.name }) { group ->
            FilterChip(
                selected = selected == group.name,
                onClick = { onSelect(group.name) },
                label = { Text("${group.name}  ${group.count}", maxLines = 1) },
            )
        }
    }
    if (picking) {
        val entries = buildList {
            add(GroupChoice(null, "Tout", null))
            if (showFavorites) add(GroupChoice(favoritesKey, "Favoris", null))
            extra.forEach { (key, label) -> add(GroupChoice(key, label, null)) }
            groups.forEach { add(GroupChoice(it.name, it.name, it.count)) }
        }
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val tv = isTvLayout()
        GroupPickerPanel(entries, selected, onSelect = {
            onSelect(it)
            picking = false
            // The focused "Groupes" chip would otherwise keep the row from scrolling to the choice.
            if (!tv) focusManager.clearFocus()
        }, onDismiss = { picking = false })
    }
    }
}

internal data class GroupChoice(val key: String?, val label: String, val count: Int?)

private val diacritics = Regex("\\p{Mn}+")

/** Case- and accent-insensitive group filter ("cinema" finds "Cinéma | FR"). */
internal fun filterGroups(entries: List<GroupChoice>, query: String): List<GroupChoice> {
    fun fold(text: String) = diacritics.replace(java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD), "")
    val needle = fold(query.trim())
    return if (needle.isEmpty()) entries else entries.filter { fold(it.label).contains(needle) }
}

/** Searchable list of every group, unfolded under the chips instead of scrolling a long row. */
@Composable
private fun GroupPickerPanel(
    entries: List<GroupChoice>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(entries, query) { filterGroups(entries, query) }
    val radius = LocalTvInterface.current.cardRadius.dp
        Surface(
            shape = RoundedCornerShape(radius),
            color = NovaColors.Background2,
            border = BorderStroke(1.dp, NovaColors.Border),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).heightIn(max = 420.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Groupes", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Fermer") }
                }
                val inputFocus = remember { FocusRequester() }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(inputFocus),
                    placeholder = { Text("Rechercher un groupe") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Effacer") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                if (!isTvLayout()) LaunchedEffect(Unit) { inputFocus.requestFocus() }
                if (shown.isEmpty()) {
                    Text("Aucun groupe ne correspond à « $query ».", color = NovaColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
                }
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    shown.forEach { entry ->
                        val isSelected = entry.key == selected
                        NovaCard(
                            onClick = { onSelect(entry.key) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else NovaColors.Card,
                            focusScale = 1.02f,
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    entry.label,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else NovaColors.TextPrimary,
                                )
                                entry.count?.let {
                                    Text("$it", fontSize = 12.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else NovaColors.TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
fun NovaLogo(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        if (maxWidth <= 88.dp) {
            Image(painterResource(R.drawable.nova_mark), contentDescription = "NOVA IPTV", modifier = Modifier.size(56.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.nova_mark), contentDescription = null, modifier = Modifier.size(48.dp))
                Text("NOVA", fontSize = 24.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold, color = NovaColors.TextPrimary)
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(message, color = NovaColors.TextSecondary, fontSize = 14.sp)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun SyncBanner(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isError) NovaColors.Error.copy(alpha = 0.15f) else NovaColors.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!isError) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(text, fontSize = 13.sp, color = if (isError) NovaColors.Error else NovaColors.TextSecondary)
    }
}
