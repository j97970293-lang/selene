package io.selene.app.ui.player

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.MoreExecutors
import io.selene.app.playback.PlaybackService

/**
 * Lecteur plein écran, branché sur [PlaybackService] par un `MediaController`.
 *
 * Passer par une session média plutôt qu'un `ExoPlayer` local donne deux choses : la lecture
 * survit à la sortie de l'écran (le service garde la main et la notification système propose
 * pause/suivant), et la fenêtre flottante n'interrompt pas le son quand l'activité est réduite.
 */
@Composable
fun PlayerScreen(uri: String) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uri) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()

        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        controller = mediaController
                        mediaController.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                        mediaController.prepare()
                        mediaController.play()
                    }
                    .onFailure { error = it.message ?: "lecture impossible" }
            },
            MoreExecutors.directExecutor(),
        )

        onDispose {
            controller?.stop()
            controller = null
            MediaController.releaseFuture(future)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = true
                }
            },
            update = { view -> view.player = controller },
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                TextButton(onClick = { context.findActivity()?.enterPip() }) {
                    Text("Fenêtre flottante")
                }
            }
        }
    }
}

/** `enterPictureInPictureMode` n'existe qu'à partir d'Android 8.0 (API 26). */
private fun ComponentActivity.enterPip() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val params = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .build()
    runCatching { enterPictureInPictureMode(params) }
}

private fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}
