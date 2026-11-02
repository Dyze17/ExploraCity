package co.edu.uniquindio.exploracity.domain.repository

import co.edu.uniquindio.exploracity.domain.model.PlaceReportReason

/** v1.4.0 · Lo que una persona aporta a un lugar publicado por otra: reportarlo (13). */
interface ContributionRepository {
    /**
     * Reporte anónimo para la moderación: el motivo y, si se quiere, qué pasa ([detail], hasta 300 caracteres; con
     * «Otro», obligatorio). Reportar otra vez el mismo lugar cambia el reporte. Sin red no se encola: lanza
     * OfflineException. Lanza excepción si falla la red.
     */
    suspend fun reportPlace(placeId: String, reason: PlaceReportReason, detail: String?)
}
