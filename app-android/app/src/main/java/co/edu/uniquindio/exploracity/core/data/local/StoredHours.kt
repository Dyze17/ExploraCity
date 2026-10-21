package co.edu.uniquindio.exploracity.core.data.local

import co.edu.uniquindio.exploracity.domain.model.HoursBlock
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * v1.3.0 · Un horario guardado en el teléfono (lugares para ver sin conexión, cola de envío y moderación): siempre
 * abierto o sus tramos. Días de 1 (lunes) a 7 (domingo) y horas en minutos desde la medianoche: sin tipos de java.time
 * en el JSON.
 */
@Serializable
internal data class StoredHours(val alwaysOpen: Boolean = false, val blocks: List<StoredBlock> = emptyList()) {
    fun toDomain(): OpeningHours? = when {
        alwaysOpen -> OpeningHours.AlwaysOpen
        blocks.isEmpty() -> null
        else -> OpeningHours.Blocks(blocks.map { it.toDomain() })
    }

    fun encode(): String = storedHoursJson.encodeToString(this)

    companion object {
        fun of(hours: OpeningHours): StoredHours = when (hours) {
            OpeningHours.AlwaysOpen -> StoredHours(alwaysOpen = true)
            is OpeningHours.Blocks -> StoredHours(blocks = hours.blocks.map(StoredBlock::of))
        }

        /** null si no se puede leer. */
        fun decode(json: String): OpeningHours? = runCatching { storedHoursJson.decodeFromString<StoredHours>(json) }.getOrNull()?.toDomain()
    }
}

@Serializable
internal data class StoredBlock(val days: List<Int>, val opensMinute: Int, val closesMinute: Int) {
    fun toDomain() = HoursBlock(days.map(DayOfWeek::of).toSet(), opensMinute.toTime(), closesMinute.toTime())

    companion object {
        fun of(block: HoursBlock) = StoredBlock(block.days.map { it.value }.sorted(), block.opens.toMinute(), block.closes.toMinute())
    }
}

private val storedHoursJson = Json { ignoreUnknownKeys = true }

private fun LocalTime.toMinute() = hour * 60 + minute

private fun Int.toTime(): LocalTime = LocalTime.of(this / 60, this % 60)
