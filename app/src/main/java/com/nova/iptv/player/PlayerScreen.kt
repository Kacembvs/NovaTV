package com.nova.iptv.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.nova.iptv.core.formatDuration
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.components.ContentImage
import com.nova.iptv.ui.components.NovaCard
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.delay

private val PanelColor = Color(0xE6101829)
private val ChipColor = Color(0x99000000)

@Composable
fun PlayerScreen(state: PlayerUiState, onViewCreated: (PlayerView) -> Unit, actions: PlayerActions) {
    BackHandler(enabled = state.nextUp != null) { actions.cancelNextUp() }
    var topBarHasFocus by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { context -> PlayerView(context).also(onViewCreated) }, modifier = Modifier.fillMaxSize())
        if (state.inPip) return@Box

        val blocking = state.error != null || state.endScreen != null || state.sleepStopped
        // Gestures only while the default controls are hidden, so their buttons stay tappable.
        if (!state.isTv && !state.controlsVisible && !blocking) {
            if (state.locked) LockedLayer(actions) else GestureLayer(state, actions)
        }

        AnimatedVisibility(
            visible = !state.locked && (state.controlsVisible || state.bannerVisible || topBarHasFocus),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            TopBar(state, actions, onFocusChanged = {
                topBarHasFocus = it
                actions.onTopBarFocused(it)
            })
        }

        state.gesture?.let { GestureIndicatorView(it, Modifier.align(Alignment.Center)) }
        state.seekFeedback?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center).background(ChipColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        state.resumeFromMs?.let { position ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 20.dp)
                    .background(ChipColor, RoundedCornerShape(12.dp))
                    .padding(start = 14.dp),
            ) {
                Text("Reprise à ${formatDuration(position)}", color = Color.White, fontSize = 14.sp)
                TextButton(onClick = actions::restart) { Text("Recommencer") }
            }
        }

        AnimatedVisibility(
            visible = state.nextUp != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 110.dp),
        ) {
            state.nextUp?.let { NextUpCard(it, actions) }
        }

        state.endScreen?.let { EndCard(state, it, actions) }
        state.castDevice?.let { CastOverlay(state, it, actions) }
        state.error?.let { ErrorCard(state, it, actions) }
        if (state.sleepStopped) SleepStoppedScreen(actions)
        state.panel?.let { PanelDialog(it, state, actions) }
    }
}

// region Top bar

@Composable
private fun TopBar(state: PlayerUiState, actions: PlayerActions, onFocusChanged: (Boolean) -> Unit) {
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(state.topBarFocusRequest) {
        if (state.topBarFocusRequest > 0) runCatching { firstButton.requestFocus() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        BarButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", actions::back, Modifier.focusRequester(firstButton))
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isLive) {
                    Text(
                        "EN DIRECT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.background(NovaColors.Error, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(state.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val secondLine = if (state.isLive) state.programme ?: state.subtitle else state.subtitle
            secondLine?.let { Text(it, color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (state.isLive) {
                state.nextProgramme?.let {
                    Text(it, color = NovaColors.TextSecondary.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        sleepLabel(state.sleep, state.kind)?.let {
            Text(it, color = NovaColors.Cyan, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
        }
        if (state.audioTracks.size > 1) BarButton(Icons.Rounded.Audiotrack, "Audio", { actions.openPanel(PlayerPanel.AUDIO) })
        if (state.textTracks.isNotEmpty()) BarButton(Icons.Rounded.ClosedCaption, "Sous-titres", { actions.openPanel(PlayerPanel.SUBTITLES) })
        if (!state.isLive) BarButton(Icons.Rounded.Speed, "Vitesse", { actions.openPanel(PlayerPanel.SPEED) })
        if (state.videoTracks.size > 1) BarButton(Icons.Rounded.HighQuality, "Qualité", { actions.openPanel(PlayerPanel.QUALITY) })
        BarButton(
            Icons.Rounded.Bedtime,
            "Minuteur de sommeil",
            { actions.openPanel(PlayerPanel.SLEEP) },
            tint = if (state.sleep == SleepMode.Off) Color.White else NovaColors.Cyan,
        )
        BarButton(Icons.Rounded.Info, "Informations techniques", { actions.openPanel(PlayerPanel.INFO) })
        if (state.castAvailable && !state.isTv) {
            AndroidView(
                factory = { ctx -> MediaRouteButton(ctx).also { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, it) } },
                modifier = Modifier.size(48.dp),
            )
        }
        if (!state.isTv) BarButton(Icons.Rounded.Lock, "Verrouiller l'écran", actions::toggleLock)
        BarButton(Icons.AutoMirrored.Rounded.OpenInNew, "Lecteur externe", actions::openExternal)
    }
}

@Composable
private fun BarButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.background(NovaColors.Cyan.copy(alpha = 0.3f), CircleShape) else Modifier),
    ) {
        Icon(icon, description, tint = tint)
    }
}

@Composable
private fun sleepLabel(sleep: SleepMode, kind: ContentKind): String? = when (sleep) {
    SleepMode.Off -> null
    SleepMode.EndOfItem -> if (kind == ContentKind.MOVIE) "Arrêt fin du film" else "Arrêt fin d'épisode"
    is SleepMode.Timer -> {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(sleep) {
            while (true) {
                now = System.currentTimeMillis()
                delay(15_000)
            }
        }
        val minutesLeft = ((sleep.deadlineMs - now) / 60_000 + 1).coerceAtLeast(1)
        "Arrêt dans $minutesLeft min"
    }
}

// endregion

// region Gestures and lock

@Composable
private fun GestureLayer(state: PlayerUiState, actions: PlayerActions) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state.isLive) {
                detectTapGestures(
                    onTap = { actions.showControls() },
                    onDoubleTap = { offset ->
                        if (!state.isLive) actions.seekBy(if (offset.x < size.width / 2) -10_000 else 10_000)
                    },
                )
            }
            .then(
                if (state.gesturesEnabled) {
                    Modifier.pointerInput(Unit) {
                        var leftSide = false
                        detectVerticalDragGestures(
                            onDragStart = { offset -> leftSide = offset.x < size.width / 2 },
                            onDragEnd = { actions.endGesture() },
                            onDragCancel = { actions.endGesture() },
                        ) { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount / (size.height * 0.8f)
                            if (leftSide) actions.adjustBrightness(delta) else actions.adjustVolume(delta)
                        }
                    }
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun LockedLayer(actions: PlayerActions) {
    var showUnlock by remember { mutableStateOf(true) }
    LaunchedEffect(showUnlock) {
        if (showUnlock) {
            delay(3_000)
            showUnlock = false
        }
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { showUnlock = true } }) {
        if (showUnlock) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
                    .clip(RoundedCornerShape(24.dp)).background(ChipColor),
            ) {
                IconButton(onClick = actions::toggleLock) { Icon(Icons.Rounded.LockOpen, "Déverrouiller", tint = Color.White) }
                Text("Déverrouiller", color = Color.White, modifier = Modifier.padding(end = 16.dp))
            }
        }
    }
}

@Composable
private fun GestureIndicatorView(indicator: GestureIndicator, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.width(84.dp).background(ChipColor, RoundedCornerShape(20.dp)).padding(vertical = 16.dp),
    ) {
        Icon(
            if (indicator.kind == GestureKind.BRIGHTNESS) Icons.Rounded.LightMode else Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = Color.White,
        )
        Box(Modifier.width(6.dp).height(110.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f))) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(indicator.level.coerceIn(0f, 1f))
                    .background(NovaColors.Cyan),
            )
        }
        Text("${(indicator.level * 100).toInt()} %", color = Color.White, fontSize = 13.sp)
    }
}

// endregion

// region Panels

@Composable
private fun PanelDialog(panel: PlayerPanel, state: PlayerUiState, actions: PlayerActions) {
    Dialog(
        onDismissRequest = { actions.openPanel(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PanelColor,
            border = BorderStroke(1.dp, NovaColors.Border),
            modifier = Modifier.widthIn(min = 300.dp, max = 440.dp).heightIn(max = 520.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (panel) {
                    PlayerPanel.OPTIONS -> OptionsPanel(state, actions)
                    PlayerPanel.AUDIO -> ChoicePanel(
                        title = "Langue audio",
                        empty = "Une seule piste audio disponible.",
                        choices = state.audioTracks.map { Choice(it.label, it.selected) { actions.selectAudio(it) } },
                    )
                    PlayerPanel.SUBTITLES -> ChoicePanel(
                        title = "Sous-titres",
                        empty = null,
                        choices = listOf(Choice("Désactivés", state.textTracks.none { it.selected }) { actions.selectSubtitles(null) }) +
                            state.textTracks.map { Choice(it.label, it.selected) { actions.selectSubtitles(it) } },
                    )
                    PlayerPanel.SPEED -> ChoicePanel(
                        title = "Vitesse de lecture",
                        empty = null,
                        choices = TrackLabels.SPEEDS.map { Choice(TrackLabels.speed(it), it == state.speed) { actions.setSpeed(it) } },
                    )
                    PlayerPanel.QUALITY -> ChoicePanel(
                        title = "Qualité",
                        empty = "Ce flux ne propose qu'une seule qualité.",
                        choices = if (state.videoTracks.size < 2) emptyList() else
                            listOf(Choice("Automatique", state.qualityAuto) { actions.selectQuality(null) }) +
                                state.videoTracks.map { Choice(it.label, !state.qualityAuto && it.selected) { actions.selectQuality(it) } },
                    )
                    PlayerPanel.SLEEP -> SleepChoices(state, actions)
                    PlayerPanel.INFO -> InfoPanel(state)
                }
            }
        }
    }
}

private data class Choice(val label: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
private fun PanelTitle(text: String, subtitle: String? = null) {
    Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    subtitle?.let { Text(it, color = NovaColors.TextSecondary, fontSize = 12.sp) }
}

@Composable
private fun ChoicePanel(title: String, empty: String?, choices: List<Choice>) {
    PanelTitle(title)
    if (choices.isEmpty()) {
        Text(empty.orEmpty(), color = NovaColors.TextSecondary, fontSize = 14.sp)
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val selectedIndex = choices.indexOfFirst { it.selected }.coerceAtLeast(0)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(choices.size) { index ->
            val choice = choices[index]
            PanelRow(
                label = choice.label,
                selected = choice.selected,
                onClick = choice.onClick,
                modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
}

@Composable
private fun PanelRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: String? = null,
    chevron: Boolean = false,
) {
    NovaCard(
        onClick = onClick,
        color = if (selected) NovaColors.Card else Color.Transparent,
        focusScale = 1.02f,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, tint = NovaColors.Cyan) }
            Text(label, color = Color.White, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            trailing?.let { Text(it, color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = 1) }
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Sélectionné", tint = NovaColors.Cyan)
            if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = NovaColors.TextSecondary)
        }
    }
}

@Composable
private fun OptionsPanel(state: PlayerUiState, actions: PlayerActions) {
    PanelTitle("Options de lecture")
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val audio = state.audioTracks.firstOrNull { it.selected }?.label ?: "—"
    val text = state.textTracks.firstOrNull { it.selected }?.label ?: "Désactivés"
    val quality = if (state.qualityAuto) "Auto" else state.videoTracks.firstOrNull { it.selected }?.label ?: "—"
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            PanelRow("Audio", false, { actions.openPanel(PlayerPanel.AUDIO) }, Modifier.focusRequester(focus), Icons.Rounded.Audiotrack, audio, true)
        }
        item { PanelRow("Sous-titres", false, { actions.openPanel(PlayerPanel.SUBTITLES) }, icon = Icons.Rounded.ClosedCaption, trailing = text, chevron = true) }
        if (!state.isLive) {
            item { PanelRow("Vitesse", false, { actions.openPanel(PlayerPanel.SPEED) }, icon = Icons.Rounded.Speed, trailing = TrackLabels.speed(state.speed), chevron = true) }
        }
        item { PanelRow("Qualité", false, { actions.openPanel(PlayerPanel.QUALITY) }, icon = Icons.Rounded.HighQuality, trailing = quality, chevron = true) }
        item {
            PanelRow(
                "Minuteur de sommeil",
                false,
                { actions.openPanel(PlayerPanel.SLEEP) },
                icon = Icons.Rounded.Bedtime,
                trailing = if (state.sleep == SleepMode.Off) "Désactivé" else "Actif",
                chevron = true,
            )
        }
        item { PanelRow("Informations techniques", false, { actions.openPanel(PlayerPanel.INFO) }, icon = Icons.Rounded.Info, chevron = true) }
        item { PanelRow("Ouvrir dans un lecteur externe", false, actions::openExternal, icon = Icons.AutoMirrored.Rounded.OpenInNew) }
    }
}

@Composable
private fun SleepChoices(state: PlayerUiState, actions: PlayerActions) {
    val options = buildList {
        add(SleepChoice.Off to "Désactivé")
        if (!state.isLive) {
            add(SleepChoice.EndOfItem to if (state.kind == ContentKind.MOVIE) "À la fin du film" else "À la fin de l'épisode")
        }
        PlaybackRules.SLEEP_TIMER_MINUTES.forEach { add(SleepChoice.Minutes(it) to "Dans $it minutes") }
    }
    val selected: SleepChoice = when (val s = state.sleep) {
        SleepMode.Off -> SleepChoice.Off
        SleepMode.EndOfItem -> SleepChoice.EndOfItem
        is SleepMode.Timer -> SleepChoice.Minutes(s.minutes)
    }
    PanelTitle("Minuteur de sommeil", "Prioritaire sur la lecture automatique : la progression est enregistrée à l'arrêt.")
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(options) { (choice, label) ->
            val isSelected = choice == selected
            PanelRow(
                label,
                isSelected,
                { actions.setSleep(choice) },
                if (isSelected) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
}

@Composable
private fun InfoPanel(state: PlayerUiState) {
    PanelTitle("Informations techniques", "Mise à jour chaque seconde.")
    if (state.techInfo.isEmpty()) {
        Text("Informations indisponibles pour le moment.", color = NovaColors.TextSecondary)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.techInfo) { (label, value) ->
            Row {
                Text(label, color = NovaColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.width(140.dp))
                Text(value, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

// endregion

// region Overlays

@Composable
private fun NextUpCard(nextUp: NextUp, actions: PlayerActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, NovaColors.Border),
        modifier = Modifier.widthIn(max = 380.dp),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ContentImage(nextUp.logo, nextUp.title, Modifier.width(72.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Épisode suivant dans ${nextUp.secondsLeft} s", color = NovaColors.Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(nextUp.subtitle ?: nextUp.title, color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions::playNextNow, modifier = Modifier.focusRequester(focus)) { Text("Lire maintenant") }
                    OutlinedButton(onClick = actions::cancelNextUp) { Text("Annuler") }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.EndCard(state: PlayerUiState, end: EndScreen, actions: PlayerActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(end) { runCatching { focus.requestFocus() } }
    CenterPanel {
        Text(if (state.kind == ContentKind.MOVIE) "Film terminé" else "Lecture terminée", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(state.subtitle ?: state.title, color = NovaColors.TextSecondary, fontSize = 14.sp, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (end.nextLabel != null) {
                Button(onClick = actions::playNextNow, modifier = Modifier.focusRequester(focus)) { Text("Épisode suivant") }
                OutlinedButton(onClick = actions::replay) { Text("Revoir") }
            } else {
                Button(onClick = actions::replay, modifier = Modifier.focusRequester(focus)) { Text("Revoir") }
            }
            OutlinedButton(onClick = actions::back) { Text("Fermer") }
        }
        end.nextLabel?.let { Text("Suivant : $it", color = NovaColors.TextSecondary, fontSize = 12.sp, maxLines = 1) }
    }
}

@Composable
private fun BoxScope.ErrorCard(state: PlayerUiState, message: String, actions: PlayerActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(message) { runCatching { focus.requestFocus() } }
    CenterPanel {
        Text(state.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(message, color = NovaColors.TextSecondary, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions::retry, modifier = Modifier.focusRequester(focus)) { Text("Réessayer") }
            OutlinedButton(onClick = actions::openExternal) { Text("Lecteur externe") }
            if (state.isLive && state.canZap) OutlinedButton(onClick = actions::zapNext) { Text("Chaîne suivante") }
        }
        TextButton(onClick = actions::back) { Text("Fermer") }
    }
}

@Composable
private fun BoxScope.CastOverlay(state: PlayerUiState, device: String, actions: PlayerActions) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.9f)).padding(24.dp),
    ) {
        Icon(Icons.Rounded.Cast, contentDescription = null, tint = NovaColors.Cyan, modifier = Modifier.size(48.dp))
        Text("Diffusion sur $device", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(state.subtitle ?: state.title, color = NovaColors.TextSecondary, fontSize = 14.sp, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions::castTogglePlayback) { Text(if (state.castPlaying) "Pause" else "Lecture") }
            if (state.isLive && state.canZap) OutlinedButton(onClick = actions::zapNext) { Text("Chaîne suivante") }
            OutlinedButton(onClick = actions::stopCasting) { Text("Arrêter la diffusion") }
        }
        Text(
            "Si rien ne s'affiche sur le téléviseur, ce flux n'est pas compatible avec Chromecast.",
            color = NovaColors.TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun BoxScope.CenterPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .align(Alignment.Center)
            .padding(24.dp)
            .background(PanelColor, RoundedCornerShape(24.dp))
            .padding(24.dp),
        content = content,
    )
}

@Composable
private fun SleepStoppedScreen(actions: PlayerActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).padding(24.dp),
    ) {
        Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = NovaColors.Violet, modifier = Modifier.size(48.dp))
        Text("Mode sommeil", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Lecture arrêtée, progression enregistrée.", color = NovaColors.TextSecondary, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions::resumeAfterSleep, modifier = Modifier.focusRequester(focus)) { Text("Reprendre") }
            OutlinedButton(onClick = actions::back) { Text("Fermer") }
        }
    }
}

// endregion
