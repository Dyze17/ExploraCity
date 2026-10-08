package co.edu.uniquindio.exploracity.data.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** Lo que pasó al pedir la cuenta de Google. */
sealed interface GoogleCredentialResult {
    /** La persona eligió una cuenta: el ID token para la API, que vale una hora. */
    data class Token(val idToken: String) : GoogleCredentialResult

    /** Cerró el selector: no se dice nada. */
    data object Cancelled : GoogleCredentialResult

    /** El teléfono no tiene cuentas de Google. */
    data object NoAccount : GoogleCredentialResult

    /** Credential Manager no pudo dar la cuenta (sin Google Play, un error del sistema…). */
    data object Failed : GoogleCredentialResult
}

/**
 * ADR-15 · «Sign in with Google» con Credential Manager: la hoja de Google para elegir la cuenta y el ID token para
 * [serverClientId], el cliente OAuth «Web» que la API acepta como audiencia. Google comprueba además que la app sea
 * ExploraCity con su firma: los clientes OAuth «Android» del proyecto (docs/despliegue.md).
 */
class GoogleCredentials(private val serverClientId: String) {

    /** [activity] tiene que ser la Activity: la hoja de Google se muestra sobre ella. */
    suspend fun request(activity: Context): GoogleCredentialResult {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleCredentialResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleCredentialResult.Failed
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleCredentialResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleCredentialResult.NoAccount
        } catch (e: GetCredentialException) {
            GoogleCredentialResult.Failed
        } catch (e: GoogleIdTokenParsingException) {
            GoogleCredentialResult.Failed
        }
    }
}
