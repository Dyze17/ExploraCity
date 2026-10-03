package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.remote.dto.CityDto
import co.edu.uniquindio.exploracity.domain.model.City
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** GET /v1/city · La ciudad que atiende la app. */
class CityApi(private val client: HttpClient) {
    suspend fun city(): City = client.get("v1/city").body<CityDto>().toDomain()
}
