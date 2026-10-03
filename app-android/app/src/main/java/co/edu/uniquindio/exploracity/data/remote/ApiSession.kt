package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.local.AccountStore
import co.edu.uniquindio.exploracity.data.local.AuthTokens
import co.edu.uniquindio.exploracity.data.local.SessionAccount
import co.edu.uniquindio.exploracity.data.local.TokenStore
import co.edu.uniquindio.exploracity.data.remote.dto.RefreshRequest
import co.edu.uniquindio.exploracity.data.remote.dto.SessionDto
import co.edu.uniquindio.exploracity.domain.model.Account
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * D1 · La sesión con la API en el teléfono: guarda lo que llega al entrar, registrarse o renovar (los tokens y la
 * cuenta) y lo borra al terminar. El cliente de [sessionHttpClient] lee de aquí el token de cada petición.
 */
class ApiSession(private val tokens: TokenStore, private val accounts: AccountStore) {
    private var client: HttpClient? = null

    /** El cliente que guarda los tokens en memoria: al cambiar la sesión, los vuelve a leer de aquí. */
    internal fun attach(client: HttpClient) {
        this.client = client
    }

    suspend fun tokens(): AuthTokens? = tokens.tokens()

    /** null: no hay sesión. */
    val account: Flow<SessionAccount?> get() = accounts.account

    /** La cuenta cambió (un correo pendiente o confirmado): la sesión sigue siendo la misma. */
    suspend fun updateAccount(account: Account) {
        val current = accounts.account.first() ?: return
        accounts.save(current.copy(account = account))
    }

    suspend fun start(session: SessionDto) {
        tokens.save(AuthTokens(session.accessToken, session.refreshToken))
        accounts.save(SessionAccount(session.userId, session.account))
        forgetCachedTokens()
    }

    /** La renovación ya la guardó el cliente en memoria; aquí queda para la próxima vez que se abra la app. */
    internal suspend fun renewed(session: SessionDto) {
        tokens.save(AuthTokens(session.accessToken, session.refreshToken))
        accounts.save(SessionAccount(session.userId, session.account))
    }

    suspend fun end() {
        clearStored()
        forgetCachedTokens()
    }

    /** Durante una renovación rechazada: el cliente ya descarta los tokens que tenía en memoria. */
    internal suspend fun clearStored() {
        tokens.clear()
        accounts.clear()
    }

    private fun forgetCachedTokens() {
        client?.authProvider<BearerAuthProvider>()?.clearToken()
    }
}

/**
 * El cliente de la API con la sesión: cada petición lleva el token de acceso y, con un 401, lo renueva una sola vez
 * con el de renovación (que también cambia) y repite la petición. Si la API rechaza la renovación, la sesión terminó:
 * se borra y se avisa con [onSessionEnded]; la petición original falla con su 401.
 */
fun sessionHttpClient(
    baseUrl: String,
    engine: HttpClientEngine,
    session: ApiSession,
    onSessionEnded: suspend () -> Unit = {},
): HttpClient = apiHttpClient(baseUrl, engine) {
    install(Auth) {
        bearer {
            loadTokens { session.tokens()?.let { BearerTokens(it.access, it.refresh) } }
            refreshTokens {
                val refresh = oldTokens?.refreshToken ?: return@refreshTokens null
                val renewed = try {
                    client.post("v1/auth/refresh") {
                        markAsRefreshTokenRequest()
                        jsonBody(RefreshRequest(refresh))
                    }.body<SessionDto>()
                } catch (e: ApiException) {
                    if (e.status == HttpStatusCode.Unauthorized.value) {
                        session.clearStored()
                        onSessionEnded()
                    }
                    return@refreshTokens null
                }
                session.renewed(renewed)
                BearerTokens(renewed.accessToken, renewed.refreshToken)
            }
        }
    }
}.also(session::attach)
