package io.selene.app.resolver

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

/**
 * État d'un pont d'extraction auto-hébergé, tel que l'application peut le
 * constater en une requête. Kotlin pur : le verdict se décide sans réseau, donc
 * il se teste, et l'écran de réglages n'a plus à deviner pourquoi ça ne marche pas.
 */
sealed interface BridgeStatus {
    data class Ok(val schema: String?, val version: String?) : BridgeStatus
    data object Unauthorized : BridgeStatus
    data object NotConfigured : BridgeStatus
    data class Unavailable(val message: String) : BridgeStatus
}

@JsonClass(generateAdapter = false)
data class BridgeHealthPayload(
    val status: String? = null,
    val schema: String? = null,
    val version: String? = null,
    val error: String? = null,
)

object BridgeHealth {

    const val EXPECTED_SCHEMA = "selene.external/1"

    private val adapter = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
        .adapter(BridgeHealthPayload::class.java)

    /**
     * Traduit une réponse HTTP en diagnostic utilisable. Un pont qui répond 200
     * avec un autre schéma n'est pas « joignable » : il est incompatible, et
     * l'application doit le dire avant de lui envoyer une adresse.
     */
    fun parse(httpCode: Int, body: String?): BridgeStatus = when (httpCode) {
        // Un 204 est une réponse valide : le pont existe et accepte le jeton.
        204 -> BridgeStatus.Ok(schema = null, version = null)

        200 -> {
            val payload = body
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
            when {
                payload == null -> BridgeStatus.Unavailable("réponse vide ou illisible")
                !payload.error.isNullOrBlank() -> BridgeStatus.Unavailable(payload.error)
                payload.schema != null && payload.schema != EXPECTED_SCHEMA ->
                    BridgeStatus.Unavailable("schéma ${payload.schema} au lieu de $EXPECTED_SCHEMA")
                else -> BridgeStatus.Ok(schema = payload.schema, version = payload.version)
            }
        }

        401, 403 -> BridgeStatus.Unauthorized
        404 -> BridgeStatus.Unavailable("aucune route /health sur ce service")
        429 -> BridgeStatus.Unavailable("trop de requêtes (HTTP 429)")
        else -> BridgeStatus.Unavailable("HTTP $httpCode")
    }

    /** Phrase affichée dans les réglages. */
    fun describe(status: BridgeStatus): String = when (status) {
        is BridgeStatus.Ok -> buildString {
            append("Pont joignable")
            status.version?.takeIf { it.isNotBlank() }?.let { append(" — version $it") }
        }
        BridgeStatus.Unauthorized -> "Jeton refusé par le pont (401/403)"
        BridgeStatus.NotConfigured -> "Adresse ou jeton manquant"
        is BridgeStatus.Unavailable -> "Pont injoignable : ${status.message}"
    }
}
