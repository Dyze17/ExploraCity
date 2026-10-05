package co.edu.uniquindio.exploracity.navigation

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
