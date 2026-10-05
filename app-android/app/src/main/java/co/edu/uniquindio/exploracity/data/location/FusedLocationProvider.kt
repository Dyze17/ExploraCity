package co.edu.uniquindio.exploracity.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * La ubicación del teléfono con FusedLocationProvider (ADR-08). Si la última conocida es reciente, esa; si no, pide una
 * nueva con precisión equilibrada, como mucho [timeout], y si no llega se queda con la última. Lanza excepción sin el
 * permiso o sin ninguna ubicación: quien la pide sigue sin ella (la API mide desde el centro de la ciudad).
 */
class FusedLocationProvider(
    context: Context,
    private val timeout: Duration = 3.seconds,
    private val maxAge: Duration = 2.minutes,
) : LocationProvider {

    private val context = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(this.context)

    // Las dos llamadas a Play Services van después de comprobar el permiso.
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeoPoint {
        if (!hasPermission()) throw SecurityException("Sin permiso de ubicación")
        val last = client.lastLocation.awaitOrNull()
        val location = if (last != null && last.age() <= maxAge) last else fresh() ?: last
        return location?.let { GeoPoint(it.latitude, it.longitude) } ?: throw IllegalStateException("Sin ubicación")
    }

    @SuppressLint("MissingPermission")
    private suspend fun fresh(): Location? {
        val cancel = CancellationTokenSource()
        return try {
            withTimeoutOrNull(timeout) { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token).awaitOrNull() }
        } finally {
            cancel.cancel()
        }
    }

    private fun hasPermission(): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private fun Location.age(): Duration = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos).nanoseconds

    /** El resultado de la tarea; null si falla. */
    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resume(null) }
        addOnCanceledListener { continuation.resume(null) }
    }
}
