package co.edu.uniquindio.exploracity.domain.repository

import co.edu.uniquindio.exploracity.domain.model.Municipality
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.ProfileUpdate
import co.edu.uniquindio.exploracity.domain.model.PublicProfile
import co.edu.uniquindio.exploracity.domain.model.ReportReason

/** Personas (SAD: componente de Usuarios y Reputación, aparte de Puntos de Interés). */
interface UserRepository {
    /** 31 · Perfil público; null si la persona ya no existe. Lanza excepción si falla la red. */
    suspend fun publicProfile(userId: String): PublicProfile?

    /** 31A · Reporte anónimo: lo revisa un moderador. Lanza excepción si falla la red. */
    suspend fun reportUser(userId: String, reason: ReportReason)

    /** 26 y 27 · El perfil de la persona de la sesión. Lanza excepción si falla la red y no hay nada guardado. */
    suspend fun ownProfile(): OwnProfile

    /** 28 · Guarda nombre, «Sobre mí», cómo se presenta y la foto (que sube si es nueva). Lanza excepción si falla la red. */
    suspend fun updateProfile(update: ProfileUpdate): OwnProfile

    /**
     * v1.3.0 · La ciudad que supo la app (la de la ubicación o la elegida a mano) llena la del perfil, solo si la
     * persona nunca tuvo una. Devuelve el perfil como quedó. Lanza excepción si falla la red.
     */
    suspend fun fillTown(town: Municipality): OwnProfile
}
