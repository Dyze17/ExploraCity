package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.DecisionAction
import co.edu.uniquindio.exploracity.model.FinalizeReason
import co.edu.uniquindio.exploracity.model.ModerationDecisions
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.RejectionReason
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.UUIDColumnType
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** «Resueltas» · Una publicación decidida, con su última decisión. */
data class ResolvedRow(
    val id: UUID,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val authorName: String?,
    val decidedAt: Instant,
    val decidedBy: String?,
    val note: String?,
    val rejectionReason: RejectionReason?,
    val finalizeReason: FinalizeReason?,
    val votes: Int,
    val comments: Int,
)

/** Una decisión que se guarda (34, 35 y 36). */
data class NewDecision(
    val placeId: UUID,
    val moderatorId: UUID,
    val action: DecisionAction,
    val note: String? = null,
    val rejectionReason: RejectionReason? = null,
    val rejectionMessage: String? = null,
    val canResubmit: Boolean? = null,
    val duplicateOf: UUID? = null,
    val finalizeReason: FinalizeReason? = null,
)

/** Moderación (SAD: componente de Moderación): el historial de decisiones. Corre en la transacción de quien llama. */
class ModerationRepository {

    fun decide(decision: NewDecision, at: Instant) {
        ModerationDecisions.insert {
            it[placeId] = decision.placeId
            it[moderatorId] = decision.moderatorId
            it[action] = decision.action
            it[note] = decision.note
            it[rejectionReason] = decision.rejectionReason
            it[rejectionMessage] = decision.rejectionMessage
            it[canResubmit] = decision.canResubmit
            it[duplicateOf] = decision.duplicateOf
            it[finalizeReason] = decision.finalizeReason
            it[createdAt] = at.atOffset(ZoneOffset.UTC)
        }
    }

    /** 37 · Cuántas verificó, rechazó y finalizó [moderatorId] desde [since]. Volver a pendiente no cuenta. */
    fun work(moderatorId: UUID, since: Instant): Map<DecisionAction, Int> {
        val count = ModerationDecisions.id.count()
        return ModerationDecisions.select(ModerationDecisions.action, count)
            .where {
                (ModerationDecisions.moderatorId eq moderatorId) and
                    (ModerationDecisions.createdAt greaterEq since.atOffset(ZoneOffset.UTC)) and
                    (ModerationDecisions.action inList listOf(DecisionAction.VERIFIED, DecisionAction.REJECTED, DecisionAction.FINALIZED))
            }
            .groupBy(ModerationDecisions.action)
            .associate { it[ModerationDecisions.action] to it[count].toInt() }
    }

    /**
     * «Resueltas» · Las publicaciones ya decididas (verificadas, rechazadas o finalizadas) con su última decisión, de la
     * más reciente a la más antigua. Con [id], solo esa.
     */
    fun resolved(id: UUID? = null): List<ResolvedRow> {
        val sql = Sql()
        sql.append(
            """
            SELECT p.id, p.title, p.category, p.status, author.name AS author_name, d.created_at, moderator.name AS moderator_name,
                   d.note, d.rejection_reason, d.finalize_reason,
                   (SELECT count(*) FROM votes v WHERE v.place_id = p.id) AS votes,
                   (SELECT count(*) FROM comments c WHERE c.place_id = p.id) AS comments
            FROM places p
            JOIN LATERAL (
                SELECT * FROM moderation_decisions md WHERE md.place_id = p.id ORDER BY md.created_at DESC, md.id DESC LIMIT 1
            ) d ON true
            LEFT JOIN users author ON author.id = p.author_id
            LEFT JOIN users moderator ON moderator.id = d.moderator_id
            WHERE p.status <> 'PENDING'
            """.trimIndent(),
        )
        id?.let { sql.append(" AND p.id = ").param(UUIDColumnType(), it) }
        sql.append(" ORDER BY d.created_at DESC, p.id")
        return sql.query(::resolvedRow)
    }

    private fun resolvedRow(rs: ResultSet) = ResolvedRow(
        id = rs.getObject("id", UUID::class.java),
        title = rs.getString("title"),
        category = Category.valueOf(rs.getString("category")),
        status = PublicationStatus.valueOf(rs.getString("status")),
        authorName = rs.getString("author_name"),
        decidedAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
        decidedBy = rs.getString("moderator_name"),
        note = rs.getString("note"),
        rejectionReason = rs.getString("rejection_reason")?.let(RejectionReason::valueOf),
        finalizeReason = rs.getString("finalize_reason")?.let(FinalizeReason::valueOf),
        votes = rs.getInt("votes"),
        comments = rs.getInt("comments"),
    )
}
