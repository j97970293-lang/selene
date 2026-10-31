package io.selene.app.data.db

import io.selene.app.model.MediaKind
import io.selene.app.model.Variant
import org.json.JSONObject

/**
 * Conversion entité → modèle.
 *
 * La base stocke les en-têtes HTTP sous forme de JSON (colonne `headers_json`) ;
 * l'objet métier manipulé par le moteur de téléchargement attend une `Map`.
 * Cette conversion vit ici pour qu'il n'y ait qu'un seul endroit à corriger.
 */
fun VariantEntity.toVariant(): Variant = Variant(
    id = id,
    kind = runCatching { MediaKind.valueOf(kind.uppercase()) }.getOrDefault(MediaKind.VIDEO),
    container = container,
    qualityLabel = qualityLabel,
    bitrateKbps = bitrateKbps,
    audioLanguage = audioLanguage,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    url = url,
    headers = decodeHeaders(headersJson),
)

/** Décode `headers_json` ; une valeur illisible est traitée comme « aucun en-tête ». */
internal fun decodeHeaders(json: String?): Map<String, String> {
    if (json.isNullOrBlank()) return emptyMap()
    return runCatching {
        val root = JSONObject(json)
        buildMap {
            for (key in root.keys()) {
                put(key, root.optString(key))
            }
        }
    }.getOrDefault(emptyMap())
}
