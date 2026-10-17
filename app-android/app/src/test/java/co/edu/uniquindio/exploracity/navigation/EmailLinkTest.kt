package co.edu.uniquindio.exploracity.navigation

import co.edu.uniquindio.exploracity.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los enlaces del correo de la API (docs/api): 6.b y «Cambiar correo». */
class EmailLinkTest {

    @Test
    fun `reconoce los dos enlaces con su token`() {
        assertEquals(EmailLink.ResetPassword("abc123"), EmailLink.parse("exploracity://enlace/restablecer?token=abc123"))
        assertEquals(EmailLink.ConfirmEmail("x-y_z"), EmailLink.parse("exploracity://enlace/confirmar-correo?token=x-y_z"))
    }

    @Test
    fun `el token llega decodificado aunque haya otros parámetros`() {
        assertEquals(EmailLink.ResetPassword("a b+c"), EmailLink.parse("exploracity://enlace/restablecer?origen=correo&token=a%20b%2Bc"))
    }

    @Test
    fun `los enlaces https de la API en Cloud Run también son del correo`() {
        val host = BuildConfig.APP_LINK_HOST

        assertEquals(EmailLink.ResetPassword("abc123"), EmailLink.parse("https://$host/enlace/restablecer?token=abc123"))
        assertEquals(EmailLink.ConfirmEmail("x-y_z"), EmailLink.parse("https://$host/enlace/confirmar-correo?token=x-y_z"))
        assertEquals(EmailLink.ResetPassword("abc123"), EmailLink.parse("https://${host.uppercase()}/enlace/restablecer?token=abc123"))
    }

    @Test
    fun `un enlace https de otro dominio, sin https o fuera de enlace no es del correo`() {
        val host = BuildConfig.APP_LINK_HOST

        assertNull(EmailLink.parse("https://otro.run.app/enlace/restablecer?token=abc123"))
        assertNull(EmailLink.parse("http://$host/enlace/restablecer?token=abc123"))
        assertNull(EmailLink.parse("https://$host/restablecer?token=abc123"))
        assertNull(EmailLink.parse("https://$host/enlaces/restablecer?token=abc123"))
        assertNull(EmailLink.parse("https://$host/enlace/restablecer"))
        assertNull(EmailLink.parse("https://$host/enlace/otra-cosa?token=abc123"))
    }

    @Test
    fun `otro esquema, otra ruta o sin token no es un enlace del correo`() {
        assertNull(EmailLink.parse(null))
        assertNull(EmailLink.parse("https://exploracity.co/enlace/restablecer?token=abc"))
        assertNull(EmailLink.parse("exploracity://enlace/otra-cosa?token=abc"))
        assertNull(EmailLink.parse("exploracity://otro/restablecer?token=abc"))
        assertNull(EmailLink.parse("exploracity://enlace/restablecer"))
        assertNull(EmailLink.parse("exploracity://enlace/restablecer?token="))
        assertNull(EmailLink.parse("no es un enlace"))
    }
}
