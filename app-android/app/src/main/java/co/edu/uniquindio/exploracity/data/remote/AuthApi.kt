package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.remote.dto.EmailRequest
import co.edu.uniquindio.exploracity.data.remote.dto.LoginRequest
import co.edu.uniquindio.exploracity.data.remote.dto.RefreshRequest
import co.edu.uniquindio.exploracity.data.remote.dto.RegisterRequest
import co.edu.uniquindio.exploracity.data.remote.dto.ResetLinkDto
import co.edu.uniquindio.exploracity.data.remote.dto.ResetPasswordRequest
import co.edu.uniquindio.exploracity.data.remote.dto.SessionDto
import co.edu.uniquindio.exploracity.data.remote.dto.TokenRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post

/**
 * /v1/auth · Sesión (3 y 4) y recuperación de la contraseña (5, 6 y 6C). Todas son públicas: no llevan el token de la
 * sesión y su 401 es una respuesta, no la señal de renovarlo.
 */
class AuthApi(private val client: HttpClient) {
    suspend fun register(request: RegisterRequest): SessionDto = client.post("v1/auth/register") {
        withoutSession()
        jsonBody(request)
    }.body()

    suspend fun login(email: String, password: String): SessionDto = client.post("v1/auth/login") {
        withoutSession()
        jsonBody(LoginRequest(email, password))
    }.body()

    /** El token usado deja de servir: llega otro par. */
    suspend fun refresh(refreshToken: String): SessionDto = client.post("v1/auth/refresh") {
        withoutSession()
        jsonBody(RefreshRequest(refreshToken))
    }.body()

    suspend fun logout(refreshToken: String) {
        client.post("v1/auth/logout") {
            withoutSession()
            jsonBody(RefreshRequest(refreshToken))
        }
    }

    /** Responde igual con o sin cuenta. */
    suspend fun forgotPassword(email: String) {
        client.post("v1/auth/password/forgot") {
            withoutSession()
            jsonBody(EmailRequest(email))
        }
    }

    suspend fun openResetLink(token: String): ResetLinkDto = client.post("v1/auth/password/link") {
        withoutSession()
        jsonBody(TokenRequest(token))
    }.body()

    suspend fun resetPassword(token: String, password: String) {
        client.post("v1/auth/password/reset") {
            withoutSession()
            jsonBody(ResetPasswordRequest(token, password))
        }
    }
}
