package co.edu.uniquindio.exploracity.viewmodel

import kotlinx.coroutines.CancellationException

/** Como runCatching, pero deja pasar la cancelación de la corrutina: null si [block] falla. */
internal suspend fun <T> runCatchingNonCancellation(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/** Como [runCatchingNonCancellation], pero conserva el fallo: sirve para distinguir la falta de red de otros errores. */
internal suspend fun <T> catchingNonCancellation(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
