package io.selene.app.resolver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Le diagnostic du pont externe : c'est la seule fonction par laquelle
 * l'utilisateur sait *pourquoi* sa source ne répond pas. Une erreur de
 * transport, un jeton refusé et un schéma incompatible appellent trois
 * corrections différentes.
 */
class BridgeHealthTest {

    @Test
    fun `un pont conforme est joignable`() {
        val status = BridgeHealth.parse(
            200,
            """{"status":"ok","schema":"selene.external/1","version":"1.2.0"}""",
        )
        assertThat(status).isInstanceOf(BridgeStatus.Ok::class.java)
        assertThat((status as BridgeStatus.Ok).version).isEqualTo("1.2.0")
        assertThat(BridgeHealth.describe(status)).contains("1.2.0")
    }

    @Test
    fun `un schema different n est pas un pont valide`() {
        val status = BridgeHealth.parse(200, """{"schema":"autre/9"}""")
        assertThat(status).isInstanceOf(BridgeStatus.Unavailable::class.java)
        assertThat((status as BridgeStatus.Unavailable).message).contains("autre/9")
    }

    @Test
    fun `jeton refuse`() {
        assertThat(BridgeHealth.parse(401, "")).isEqualTo(BridgeStatus.Unauthorized)
        assertThat(BridgeHealth.parse(403, "{}")).isEqualTo(BridgeStatus.Unauthorized)
        assertThat(BridgeHealth.describe(BridgeStatus.Unauthorized)).contains("refusé")
    }

    @Test
    fun `routes et erreurs serveur`() {
        assertThat(BridgeHealth.parse(404, ""))
            .isEqualTo(BridgeStatus.Unavailable("aucune route /health sur ce service"))
        assertThat(BridgeHealth.parse(503, ""))
            .isEqualTo(BridgeStatus.Unavailable("HTTP 503"))
    }

    @Test
    fun `reponse vide ou illisible`() {
        assertThat(BridgeHealth.parse(200, null))
            .isEqualTo(BridgeStatus.Unavailable("réponse vide ou illisible"))
        assertThat(BridgeHealth.parse(200, "  "))
            .isEqualTo(BridgeStatus.Unavailable("réponse vide ou illisible"))
        assertThat(BridgeHealth.parse(200, "<html>proxy</html>"))
            .isEqualTo(BridgeStatus.Unavailable("réponse vide ou illisible"))
    }

    @Test
    fun `un champ error est remonte tel quel`() {
        val status = BridgeHealth.parse(200, """{"error":"yt-dlp absent"}""")
        assertThat(status).isEqualTo(BridgeStatus.Unavailable("yt-dlp absent"))
    }

    @Test
    fun `un pont sans schema declare reste acceptable`() {
        // Tous les ponts n'annoncent pas le schéma sur /health ; l'absence n'est
        // pas une erreur, seul un schéma *différent* en est une.
        val status = BridgeHealth.parse(200, """{"status":"ok"}""")
        assertThat(status).isInstanceOf(BridgeStatus.Ok::class.java)
    }

    @Test
    fun `204 sans corps est accepte`() {
        assertThat(BridgeHealth.parse(204, null)).isInstanceOf(BridgeStatus.Ok::class.java)
    }
}
