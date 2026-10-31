package io.selene.app.converting

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Conversion d'une vidéo téléchargée en fichier audio, avec Media3 Transformer.
 *
 * **Limite factuelle, à connaître avant de demander du MP3** : Media3 sait encoder en **AAC**
 * (conteneur `.m4a`), AMR et Opus. Il ne sait **pas** encoder en MP3 : cela demande un encodeur
 * natif (LAME) qui n'est pas fourni par Android. Le fichier produit est donc un `.m4a`, lisible
 * partout, et non un faux `.mp3` renommé — un MP4 renommé en .mp3 serait refusé par la moitié des
 * lecteurs, ce qui est pire que de ne rien faire.
 *
 * La conversion se fait hors du thread principal, dans le cache de l'application, puis le fichier
 * est déplacé dans le dossier média.
 */
class AudioExporter(
    private val context: Context,
) {

    suspend fun exportToM4a(source: File, target: File): File = withContext(Dispatchers.IO) {
        if (!source.exists()) throw ConversionException("Fichier source introuvable : ${source.name}")
        target.parentFile?.mkdirs()

        val outputFile = if (target.exists()) {
            target.delete()
            target
        } else {
            target
        }

        suspendCancellableCoroutine { continuation ->
            // `setRemoveVideo` suffit : la piste vidéo est écartée, la piste audio réencodée.
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(source.toURI().toString()))
                .setRemoveVideo(true)
                .build()

            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (continuation.isActive) continuation.resume(outputFile)
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    ConversionException(
                                        "Conversion impossible : ${exception.message ?: "erreur d'encodage"}",
                                        exception,
                                    ),
                                )
                            }
                        }
                    },
                )
                .build()

            continuation.invokeOnCancellation { transformer.cancel() }
            transformer.start(edited, outputFile.absolutePath)
        }

        outputFile
    }

    companion object {
        /** Nom de sortie cohérent avec la source : `film.mkv` → `film.m4a`. */
        fun targetFor(source: File, directory: File): File =
            File(directory, source.nameWithoutExtension + ".m4a")
    }
}

class ConversionException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
