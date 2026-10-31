package io.selene.app.downloading

/** Message destiné à l'utilisateur + indication « réessayer a-t-il un sens ? ». */
data class YtDlpFailure(val message: String, val retryable: Boolean)

/**
 * Traduit les messages de yt-dlp en une phrase et en une décision.
 *
 * yt-dlp écrit des diagnostics techniques en anglais sur sa sortie d'erreur ;
 * l'application les affichait tels quels. Une erreur qui ne changera jamais
 * (« adresse non prise en charge ») ne doit pas proposer « réessayer », et une
 * coupure réseau ne doit pas ressembler à un refus définitif.
 *
 * Kotlin pur, donc testable sans appareil ni réseau.
 */
object YtDlpErrors {

    fun describe(raw: String?): YtDlpFailure {
        val message = raw.orEmpty().trim()
        if (message.isEmpty()) {
            return YtDlpFailure("yt-dlp a échoué sans message", retryable = true)
        }
        val lower = message.lowercase()

        return when {
            // Le moteur lui-même n'a pas démarré : rien à voir avec le site.
            "youtubedl exception" in lower || "python" in lower && "not found" in lower ||
                "could not find" in lower && "yt-dlp" in lower ->
                YtDlpFailure(
                    "Le moteur yt-dlp n'a pas démarré sur cet appareil (architecture non prise en charge ou installation incomplète).",
                    retryable = false,
                )

            "unsupported url" in lower || "no suitable" in lower || "is not a valid url" in lower ||
                "unable to extract" in lower ->
                YtDlpFailure(
                    "Ce site n'est pas pris en charge par la version embarquée de yt-dlp.",
                    retryable = false,
                )

            "sign in to confirm" in lower || "login required" in lower || "private video" in lower ||
                "members-only" in lower || "premium" in lower ->
                YtDlpFailure(
                    "Ce contenu exige un compte ou n'est pas public : Séléné ne contourne pas l'authentification.",
                    retryable = false,
                )

            "video unavailable" in lower || "not available in your country" in lower ||
                "removed by the uploader" in lower ->
                YtDlpFailure("Ce contenu n'est pas disponible.", retryable = false)

            "http error 403" in lower ->
                YtDlpFailure("Le site refuse l'accès (403) : le média a peut-être expiré.", retryable = true)

            "http error 429" in lower || "too many requests" in lower ->
                YtDlpFailure("Le site limite le débit (429) : réessayer dans quelques minutes.", retryable = true)

            "http error 5" in lower || "internal server error" in lower ||
                "bad gateway" in lower || "service unavailable" in lower ->
                YtDlpFailure("Le site est en erreur de son côté : réessayer plus tard.", retryable = true)

            "timed out" in lower || "timeout" in lower || "connection reset" in lower ||
                "connection refused" in lower || "network is unreachable" in lower ||
                "temporary failure" in lower ->
                YtDlpFailure("Connexion interrompue : réessayer quand le réseau revient.", retryable = true)

            "ffmpeg" in lower || "merging" in lower ->
                YtDlpFailure(
                    "La fusion des pistes vidéo et audio a échoué ; le fichier peut être incomplet.",
                    retryable = true,
                )

            "no space left" in lower ->
                YtDlpFailure("Espace de stockage insuffisant.", retryable = false)

            // Message non reconnu : on le garde, tronqué, plutôt que de le perdre.
            else -> YtDlpFailure(message.take(240), retryable = true)
        }
    }
}
