package co.edu.uniquindio.exploracity.domain.model

import java.time.Instant

/** 14 · Comentario publicado sobre un lugar. [mine] si lo escribió la persona de la sesión. */
data class Comment(
    val id: String,
    val author: Author,
    val text: String,
    val createdAt: Instant,
    val mine: Boolean = false,
) {
    companion object {
        /** README 14: máximo 300 caracteres, con contador desde 250. */
        const val MAX_LENGTH = 300
        const val COUNTER_FROM = 250
    }
}
