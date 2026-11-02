package co.edu.uniquindio.exploracity.domain.repository

import co.edu.uniquindio.exploracity.domain.model.PlaceReportReason

/** v1.4.0 · Lo que una persona aporta a un lugar publicado: reportarlo (13) y agregarle fotos. */
interface ContributionRepository {
    /**
     * Agrega al lugar las fotos de [urls], ya subidas: esperan a un moderador antes de verse (D1). Si no caben todas,
     * no se agrega ninguna: lanza [co.edu.uniquindio.exploracity.domain.model.PlaceFullException]. Sin red lanza
     * OfflineException. Lanza excepción si falla la red.
     */
    suspend fun addPhotos(placeId: String, urls: List<String>)

    /**
     * Reporte anónimo para la moderación: el motivo y, si se quiere, qué pasa ([detail], hasta 300 caracteres; con
     * «Otro», obligatorio). Reportar otra vez el mismo lugar cambia el reporte. Sin red no se encola: lanza
     * OfflineException. Lanza excepción si falla la red.
     */
    suspend fun reportPlace(placeId: String, reason: PlaceReportReason, detail: String?)
}
