package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.PlaceReportReason
import co.edu.uniquindio.exploracity.model.PlaceReports
import co.edu.uniquindio.exploracity.model.Places
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.ReportResolution
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** v1.4.0 · Un reporte abierto de un lugar, sin quién lo hizo: es anónimo. */
data class PlaceReportRow(val placeId: UUID, val reason: PlaceReportReason, val detail: String?, val createdAt: Instant)

/** v1.4.0 · Reportes de lugares (13 y «Reportados»). Corre en la transacción de quien llama. */
class PlaceReportRepository {

    /**
     * Reporta [placeId] o, si [reporterId] ya tenía un reporte abierto ahí, lo cambia. Bloquea el lugar hasta el final
     * de la transacción, para que dos envíos seguidos de la misma persona no choquen.
     */
    fun report(placeId: UUID, reporterId: UUID, reason: PlaceReportReason, detail: String?, at: Instant) {
        Places.select(Places.id).where { Places.id eq placeId }.forUpdate(ForUpdateOption.ForUpdate).toList()
        val changed = PlaceReports.update({ openBy(placeId, reporterId) }) {
            it[PlaceReports.reason] = reason
            it[PlaceReports.detail] = detail
            it[createdAt] = at.atOffset(ZoneOffset.UTC)
        }
        if (changed > 0) return
        PlaceReports.insert {
            it[PlaceReports.placeId] = placeId
            it[PlaceReports.reporterId] = reporterId
            it[PlaceReports.reason] = reason
            it[PlaceReports.detail] = detail
            it[createdAt] = at.atOffset(ZoneOffset.UTC)
        }
    }

    /** Los reportes abiertos de [placeIds], del más reciente al más antiguo. */
    fun open(placeIds: Collection<UUID>): List<PlaceReportRow> {
        if (placeIds.isEmpty()) return emptyList()
        return PlaceReports.selectAll()
            .where { (PlaceReports.placeId inList placeIds) and PlaceReports.resolvedAt.isNull() }
            .orderBy(PlaceReports.createdAt to SortOrder.DESC, PlaceReports.id to SortOrder.ASC)
            .map { PlaceReportRow(it[PlaceReports.placeId], it[PlaceReports.reason], it[PlaceReports.detail], it[PlaceReports.createdAt].toInstant()) }
    }

    /** «Reportados» · Los lugares públicos con reportes abiertos, sin los de [excludeAuthor] (F1). */
    fun reportedPlaces(excludeAuthor: UUID): List<UUID> = PlaceReports
        .join(Places, JoinType.INNER, PlaceReports.placeId, Places.id)
        .select(PlaceReports.placeId)
        .withDistinct()
        .where {
            PlaceReports.resolvedAt.isNull() and
                (Places.status inList PUBLIC) and
                (Places.authorId.isNull() or (Places.authorId neq excludeAuthor))
        }
        .map { it[PlaceReports.placeId] }

    /** Cierra los reportes abiertos de [placeId] con [resolution]. Devuelve cuántos cerró. */
    fun resolve(placeId: UUID, moderatorId: UUID, resolution: ReportResolution, at: Instant): Int =
        PlaceReports.update({ (PlaceReports.placeId eq placeId) and PlaceReports.resolvedAt.isNull() }) {
            it[resolvedAt] = at.atOffset(ZoneOffset.UTC)
            it[resolvedBy] = moderatorId
            it[PlaceReports.resolution] = resolution
        }

    private fun openBy(placeId: UUID, reporterId: UUID) =
        (PlaceReports.placeId eq placeId) and (PlaceReports.reporterId eq reporterId) and PlaceReports.resolvedAt.isNull()

    private companion object {
        val PUBLIC = listOf(PublicationStatus.VERIFIED, PublicationStatus.FINALIZED)
    }
}
