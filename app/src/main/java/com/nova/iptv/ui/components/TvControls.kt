package com.nova.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.iptv.core.isTvLayout
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.TvInterface

@Composable
private fun TvControl(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val foreground = when {
        !enabled -> NovaColors.TextSecondary.copy(alpha = 0.5f)
        focused -> Color.White
        else -> NovaColors.TextPrimary
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 48.dp)
            .focusProperties { canFocus = enabled }
            .semantics { role = Role.Button; this.selected = selected },
        shape = RoundedCornerShape(LocalTvInterface.current.cardRadius.dp),
        color = when {
            focused -> if (LocalTvInterface.current == TvInterface.PLUS) Color(0xFF393939) else Color(0xFF334155)
            else -> NovaColors.Card
        },
        contentColor = foreground,
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            when {
                focused -> if (NovaColors.palette.isLight) Color.Black else Color.White
                selected -> NovaColors.Cyan
                else -> NovaColors.Border
            },
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected) Icon(Icons.Rounded.Check, "Actif", modifier = Modifier.size(18.dp))
            content()
        }
    }
}

@Composable
fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    TvControl(onClick, modifier, enabled, selected) {
        icon?.let { Icon(it, null, modifier = Modifier.size(20.dp)) }
        Text(
            label,
            color = if (destructive && enabled) NovaColors.Error else LocalContentColor.current,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TvSettingToggle(
    title: String,
    description: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvControl({ onChange(!checked) }, modifier.fillMaxWidth(), enabled = true) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            description?.let { Text(it, fontSize = 13.sp, color = LocalContentColor.current.copy(alpha = 0.75f)) }
        }
        Text(if (checked) "Activé" else "Désactivé", color = if (checked) NovaColors.Cyan else LocalContentColor.current)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun NovaButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    if (isTvLayout()) TvControl(onClick, modifier, enabled, content = content)
    else Button(onClick, modifier, enabled, content = content)
}

@Composable
fun NovaTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    if (isTvLayout()) TvControl(onClick, modifier, enabled, content = content)
    else TextButton(onClick, modifier, enabled, content = content)
}

@Composable
fun NovaOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    if (isTvLayout()) TvControl(onClick, modifier, enabled, content = content)
    else OutlinedButton(onClick, modifier, enabled, content = content)
}

@Composable
fun NovaFilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    if (isTvLayout()) TvControl(onClick, modifier, enabled, content = content)
    else FilledTonalButton(onClick, modifier, enabled, content = content)
}

@Composable
fun NovaIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (isTvLayout()) {
        TvControl(onClick, modifier.widthIn(min = 48.dp), enabled) { content() }
    } else IconButton(onClick, modifier, enabled, content = content)
}

@Composable
fun NovaFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    if (isTvLayout()) {
        TvControl(onClick, modifier, enabled, selected) {
            leadingIcon?.invoke()
            label()
        }
    } else FilterChip(selected, onClick, label, modifier, enabled, leadingIcon = leadingIcon)
}
