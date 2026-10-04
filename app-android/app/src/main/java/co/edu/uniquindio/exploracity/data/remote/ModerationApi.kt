package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.remote.dto.CandidateDto
import co.edu.uniquindio.exploracity.data.remote.dto.FinalizeRequest
import co.edu.uniquindio.exploracity.data.remote.dto.ModerationSummaryDto
import co.edu.uniquindio.exploracity.data.remote.dto.ModerationWorkDto
import co.edu.uniquindio.exploracity.data.remote.dto.RejectRequest
import co.edu.uniquindio.exploracity.data.remote.dto.ReopenRequest
import co.edu.uniquindio.exploracity.data.remote.dto.ResolvedDto
import co.edu.uniquindio.exploracity.data.remote.dto.ReviewItemDto
import co.edu.uniquindio.exploracity.data.remote.dto.ReviewQueueDto
import co.edu.uniquindio.exploracity.data.remote.dto.VerifyRequest
import co.edu.uniquindio.exploracity.data.repository.ModerationSummary
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.encodeURLPathPart

/**
 * /v1/moderation · La cola, las decisiones y «Resueltas» (32–37). Solo con el rol de moderador: con otro, ApiException
 * 403. Los errores de cada decisión los traduce ApiModerationRepository.
 */
class ModerationApi(private val client: HttpClient) {
    suspend fun summary(): ModerationSummary = client.get("v1/moderation/summary").body<ModerationSummaryDto>().toDomain()

    /** 37 · Desde la medianoche de la ciudad. */
    suspend fun today(): ModerationWork = client.get("v1/moderation/today").body<ModerationWorkDto>().toDomain()

    /** 32 · De la más antigua a la más reciente; nunca las del propio moderador. */
    suspend fun queue(): List<ReviewItem> = client.get("v1/moderation/queue").body<ReviewQueueDto>().items.map { it.toDomain() }

    suspend fun item(id: String): ReviewItem = client.get(pending(id)).body<ReviewItemDto>().toDomain()

    suspend fun verify(id: String, note: String?) {
        client.post("${pending(id)}/verify") { jsonBody(VerifyRequest(note?.trim()?.ifEmpty { null })) }
    }

    suspend fun duplicateOptions(id: String): List<DuplicateCandidate> =
        client.get("${pending(id)}/duplicate-options").body<List<CandidateDto>>().map { it.toDomain() }

    suspend fun reject(id: String, decision: RejectDecision) {
        client.post("${pending(id)}/reject") { jsonBody(RejectRequest.of(decision)) }
    }

    suspend fun resolved(): List<ResolvedPublication> = client.get("v1/moderation/resolved").body<List<ResolvedDto>>().map { it.toDomain() }

    suspend fun resolvedItem(id: String): ResolvedPublication = client.get(resolved(id)).body<ResolvedDto>().toDomain()

    suspend fun finalize(id: String, reason: FinalizeReason) {
        client.post("${resolved(id)}/finalize") { jsonBody(FinalizeRequest(reason)) }
    }

    suspend fun reopen(id: String, reason: String) {
        client.post("${resolved(id)}/reopen") { jsonBody(ReopenRequest(reason.trim())) }
    }

    private fun pending(id: String) = "v1/moderation/queue/${id.encodeURLPathPart()}"

    private fun resolved(id: String) = "v1/moderation/resolved/${id.encodeURLPathPart()}"
}
