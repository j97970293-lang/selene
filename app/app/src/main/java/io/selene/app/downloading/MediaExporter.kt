package io.selene.app.downloading

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Ce que l'export a produit, ou ce qui l'a empêché. */
sealed interface ExportResult {
    data class Saved(val uri: Uri, val path: String) : ExportResult

    /** Android 9 et antérieurs : l'écriture dans le stockage partagé exige l'accord. */
    data object PermissionRequired : ExportResult
    data class Failed(val message: String) : ExportResult
}

/**
 * Copie un fichier téléchargé vers le stockage partagé, là où la galerie, le
 * lecteur de musique et le gestionnaire de fichiers le voient.
 *
 * Deux chemins, parce qu'Android a changé de règle :
 *  - API 29+ : MediaStore, sans aucune permission ;
 *  - API 24-28 : écriture directe dans `Movies/`, `Music/` ou `Download/`, qui
 *    exige `WRITE_EXTERNAL_STORAGE` — la permission n'est demandée qu'au moment
 *    de l'export, jamais au démarrage.
 *
 * Le fichier d'origine reste dans l'espace privé : l'export est une copie, pas
 * un déplacement. Une lecture depuis la bibliothèque continue de fonctionner
 * même si l'utilisateur efface la copie publique.
 */
@Singleton
class MediaExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun export(file: File, displayName: String = file.name): ExportResult =
        withContext(Dispatchers.IO) {
            if (!file.isFile || file.length() == 0L) {
                return@withContext ExportResult.Failed("Fichier introuvable ou vide")
            }

            val extension = MediaMime.extensionOf(displayName)
                .ifEmpty { MediaMime.extensionOf(file.name) }
            val family = MediaMime.familyFor(extension)
            val mime = MediaMime.mimeFor(extension)
            val relative = MediaMime.relativePath(family)
            val name = FileNamer.sanitize(
                displayName.substringBeforeLast('.', displayName),
                extension,
            )

            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    exportViaMediaStore(file, name, mime, relative)
                } else {
                    exportToPublicDirectory(file, name, mime, family)
                }
            }.getOrElse { error ->
                ExportResult.Failed(error.message ?: "Export impossible")
            }
        }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun exportViaMediaStore(
        file: File,
        name: String,
        mime: String,
        relative: String,
    ): ExportResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$relative/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: return ExportResult.Failed("MediaStore a refusé la création du fichier")

        val copied: Boolean = runCatching {
            val output = resolver.openOutputStream(uri)
            if (output == null) {
                false
            } else {
                output.use { stream -> file.inputStream().use { input -> input.copyTo(stream) } }
                true
            }
        }.getOrElse { error ->
            resolver.delete(uri, null, null)
            return ExportResult.Failed(error.message ?: "Écriture impossible")
        }

        if (!copied) {
            resolver.delete(uri, null, null)
            return ExportResult.Failed("Écriture impossible dans le stockage partagé")
        }

        // Tant que IS_PENDING vaut 1, les autres applications ne voient pas le fichier :
        // on ne le publie qu'une fois la copie terminée.
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        return ExportResult.Saved(uri, "$relative/$name")
    }

    @Suppress("DEPRECATION")
    private fun exportToPublicDirectory(
        file: File,
        name: String,
        mime: String,
        family: MediaFamily,
    ): ExportResult {
        val granted = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return ExportResult.PermissionRequired

        val base = Environment.getExternalStoragePublicDirectory(
            when (family) {
                MediaFamily.AUDIO -> Environment.DIRECTORY_MUSIC
                MediaFamily.VIDEO -> Environment.DIRECTORY_MOVIES
                MediaFamily.DOCUMENT -> Environment.DIRECTORY_DOWNLOADS
            },
        )
        val directory = File(base, "Selene").apply { mkdirs() }
        val target = FileNamer.uniqueFile(directory, name)

        file.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        // Sans indexation, le fichier existe mais aucune galerie ne le liste.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, target.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, target.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
        }
        val collection = when (family) {
            MediaFamily.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            MediaFamily.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaFamily.DOCUMENT -> MediaStore.Files.getContentUri("external")
        }
        val uri = runCatching { context.contentResolver.insert(collection, values) }.getOrNull()
        return ExportResult.Saved(uri ?: Uri.fromFile(target), target.absolutePath)
    }
}
