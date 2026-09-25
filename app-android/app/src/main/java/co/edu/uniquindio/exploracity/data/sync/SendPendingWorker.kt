package co.edu.uniquindio.exploracity.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Envía la cola cuando hay red; si la red vuelve a fallar a mitad de camino, pide reintentar más tarde. */
class SendPendingWorker(
    context: Context,
    params: WorkerParameters,
    private val sender: PendingSender,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (sender.flush()) Result.success() else Result.retry()
}

/** Pide enviar la cola. Separado de WorkManager para probar el repositorio sin él. */
fun interface PendingScheduler {
    fun schedule()
}

class WorkManagerScheduler(private val context: Context) : PendingScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<SendPendingWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        // Después de la que esté en curso: si esa ya iba a terminar, lo recién encolado no se queda sin enviar.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "enviar-pendientes"
    }
}

/** Crea los workers con las dependencias de la app (inyección manual, sin Hilt): ver ExploraApplication. */
class ExploraWorkerFactory(private val sender: () -> PendingSender) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        when (workerClassName) {
            SendPendingWorker::class.java.name -> SendPendingWorker(appContext, workerParameters, sender())
            else -> null
        }
}
