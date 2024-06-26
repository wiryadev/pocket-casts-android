package au.com.shiftyjelly.pocketcasts.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import au.com.shiftyjelly.pocketcasts.widget.action.controlPlaybackAction
import au.com.shiftyjelly.pocketcasts.widget.data.LocalSource
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@Composable
internal fun PlaybackButton(
    isPlaying: Boolean,
    size: Dp = 36.dp,
    backgroundColor: ((ColorProviders) -> ColorProvider)? = null,
    iconColor: ((ColorProviders) -> ColorProvider)? = null,
) {
    val contentDescription = LocalContext.current.getString(if (isPlaying) LR.string.play_episode else LR.string.pause_episode)

    Box(
        modifier = GlanceModifier
            .size(size)
            .clickable(controlPlaybackAction(isPlaying, LocalSource.current))
            .semantics { this.contentDescription = contentDescription },
    ) {
        Image(
            provider = ImageProvider(IR.drawable.ic_circle),
            contentDescription = null,
            colorFilter = ColorFilter.tint(backgroundColor?.invoke(GlanceTheme.colors) ?: GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(size),
        )
        Image(
            provider = ImageProvider(if (isPlaying) IR.drawable.ic_widget_pause else IR.drawable.ic_widget_play),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconColor?.invoke(GlanceTheme.colors) ?: GlanceTheme.colors.onPrimary),
            modifier = GlanceModifier.size(size).padding(size / if (isPlaying) 8 else 5),
        )
    }
}
