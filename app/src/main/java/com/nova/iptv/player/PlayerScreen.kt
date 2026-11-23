package com.nova.iptv.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.ui.PlayerView
import com.nova.iptv.core.formatDuration
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.components.ContentImage
import com.nova.iptv.ui.components.NovaCard
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.delay

private val PanelColor = Color(0xF0101829)

@Composable
fun PlayerScreen(state: PlayerUiState, onViewCreated: (PlayerView) -> Unit, actions: PlayerActions) {
    BackHandler(enabled = state.nextUp != null) { actions.cancelNextUp() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { context -> PlayerView(context).also(onViewCreated) }, modifier = Modifier.fillMaxSize())

        AnimatedVisibility(
            visible = state.controlsVisible || state.bannerVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            TopBar(state, actions)
        }

        state.resumeFromMs?.let { position ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 20.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
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
        state.error?.let { ErrorCard(state, it, actions) }
        if (state.sleepStopped) SleepStoppedScreen(actions)
        if (state.sleepPanelVisible) SleepPanel(state, actions)
    }
}

@Composable
private fun TopBar(state: PlayerUiState, actions: PlayerActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        IconButton(onClick = actions::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = Color.White) }
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
                state.nextProgramme?.let { Text(it, color = NovaColors.TextSecondary.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        sleepLabel(state.sleep, state.kind)?.let {
            Text(it, color = NovaColors.Cyan, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
        }
        IconButton(onClick = { actions.showSleepPanel(true) }) {
            Icon(Icons.Rounded.Bedtime, "Minuteur de sommeil", tint = if (state.sleep == SleepMode.Off) Color.White else NovaColors.Cyan)
        }
        IconButton(onClick = actions::openExternal) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Ouvrir dans un lecteur externe", tint = Color.White) }
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
    CenterPanel {
        Text(state.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(message, color = NovaColors.TextSecondary, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions::retry) { Text("Réessayer") }
            OutlinedButton(onClick = actions::openExternal) { Text("Lecteur externe") }
            if (state.isLive && state.canZap) OutlinedButton(onClick = actions::zapNext) { Text("Chaîne suivante") }
        }
        TextButton(onClick = actions::back) { Text("Fermer") }
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

@Composable
private fun SleepPanel(state: PlayerUiState, actions: PlayerActions) {
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
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = { actions.showSleepPanel(false) }) {
        Surface(shape = RoundedCornerShape(24.dp), color = PanelColor, border = BorderStroke(1.dp, NovaColors.Border)) {
            Column(Modifier.padding(20.dp).widthIn(min = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Minuteur de sommeil", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Prioritaire sur la lecture automatique : la progression est enregistrée à l'arrêt.",
                    color = NovaColors.TextSecondary,
                    fontSize = 12.sp,
                )
                options.forEach { (choice, label) ->
                    val isSelected = choice == selected
                    NovaCard(
                        onClick = { actions.setSleep(choice) },
                        color = if (isSelected) NovaColors.Card else Color.Transparent,
                        focusScale = 1.02f,
                        modifier = Modifier.fillMaxWidth().then(if (isSelected) Modifier.focusRequester(focus) else Modifier),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = Color.White, modifier = Modifier.weight(1f))
                            if (isSelected) Icon(Icons.Rounded.Check, contentDescription = "Sélectionné", tint = NovaColors.Cyan)
                        }
                    }
                }
            }
        }
    }
}
