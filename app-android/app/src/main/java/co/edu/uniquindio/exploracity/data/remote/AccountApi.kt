package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.remote.dto.AccountDto
import co.edu.uniquindio.exploracity.data.remote.dto.EmailChangeRequest
import co.edu.uniquindio.exploracity.data.remote.dto.TokenRequest
import co.edu.uniquindio.exploracity.domain.model.DataExport
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders

/** /v1/account · La cuenta de la sesión (28, 29, 30 y «Cambiar correo»). Piden el token de acceso. */
class AccountApi(private val client: HttpClient) {
    suspend fun account(): AccountDto = client.get("v1/account").body()

    /** Envía el enlace al correo nuevo, que queda pendiente. */
    suspend fun requestEmailChange(newEmail: String, password: String): AccountDto = client.post("v1/account/email") {
        jsonBody(EmailChangeRequest(newEmail, password))
    }.body()

    suspend fun resendEmailChange() {
        client.post("v1/account/email/resend")
    }

    /** El correo nuevo pasa a ser el de la cuenta. */
    suspend fun confirmEmailChange(token: String): AccountDto = client.post("v1/account/email/confirm") {
        jsonBody(TokenRequest(token))
    }.body()

    /** 29 · El archivo tal como lo arma la API, con el nombre que propone en Content-Disposition. */
    suspend fun exportData(): DataExport {
        val response = client.get("v1/account/export")
        val fileName = response.headers[HttpHeaders.ContentDisposition]
            ?.let { ContentDisposition.parse(it).parameter(ContentDisposition.Parameters.FileName) }
            ?: DEFAULT_EXPORT_NAME
        return DataExport(fileName, response.bodyAsText())
    }

    suspend fun deleteAccount() {
        client.delete("v1/account")
    }

    private companion object {
        const val DEFAULT_EXPORT_NAME = "exploracity-mis-datos.json"
    }
}
