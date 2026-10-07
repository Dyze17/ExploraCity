package co.edu.uniquindio.exploracity.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePhotoTest {

    @Test
    fun `las URL del servidor se descargan`() {
        assertTrue(isRemotePhoto("https://res.cloudinary.com/exploracity/image/upload/lugares/abc.jpg"))
        // En desarrollo la API sirve la carpeta local por HTTP (adb reverse).
        assertTrue(isRemotePhoto("http://localhost:8080/media/lugares/abc.jpg"))
    }

    @Test
    fun `los archivos del teléfono y la ruta vacía no`() {
        assertFalse(isRemotePhoto("/data/user/0/co.edu.uniquindio.exploracity/files/fotos/1.jpg"))
        assertFalse(isRemotePhoto(""))
        assertFalse(isRemotePhoto("httpfoto.jpg"))
    }
}
