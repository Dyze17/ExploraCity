package co.edu.uniquindio.exploracity.data.repository

import kotlinx.coroutines.delay

/** Resumen de la cola para el acceso rápido del moderador en el feed (7.c). */
data class ModerationSummary(val pending: Int, val oldestWaitingDays: Int)

interface ModerationRepository {
    suspend fun summary(): ModerationSummary
}

/** Temporal hasta que exista la API: los valores del lienzo 7.c. */
class FakeModerationRepository : ModerationRepository {
    override suspend fun summary(): ModerationSummary {
        delay(300)
        return ModerationSummary(pending = 7, oldestWaitingDays = 3)
    }
}
