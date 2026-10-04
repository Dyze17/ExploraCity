package co.edu.uniquindio.exploracity.support

import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.CategoryOrigin
import co.edu.uniquindio.exploracity.model.Comments
import co.edu.uniquindio.exploracity.model.Photos
import co.edu.uniquindio.exploracity.model.Places
import co.edu.uniquindio.exploracity.model.PriceRange
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.Residency
import co.edu.uniquindio.exploracity.model.Users
import co.edu.uniquindio.exploracity.model.Visits
import co.edu.uniquindio.exploracity.model.Votes
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/** Las personas y los lugares de los ejemplos del contrato (profile.json y export.json). */
object Fixtures {
    val ANA: UUID = UUID.fromString("3f6c1e2a-8b4d-4c7e-9a1f-2d5b7e9c0a14")
    val LAURA: UUID = UUID.fromString("9b2d4f6a-1c3e-4a5b-8d7f-0e2c4a6b8d10")
    val MIRADOR: UUID = UUID.fromString("c1a2b3c4-d5e6-4f70-8a9b-0c1d2e3f4a5b")
    val CAFE: UUID = UUID.fromString("d2b3c4d5-e6f7-4a81-9bac-1d2e3f4a5b6c")
    val PLAZA: UUID = UUID.fromString("e3c4d5e6-f7a8-4b92-acbd-2e3f4a5b6c7d")
    val MIRADOR_PHOTO: UUID = UUID.fromString("b4c5d6e7-f8a9-4bc0-9d1e-3f4a5b6c7d8e")

    const val ANA_EMAIL = "ana.rios@correo.com"
    const val LAURA_EMAIL = "laura.gomez@correo.com"
    const val ANA_PHOTO_ID = "perfil/ana-rios"
    const val MIRADOR_PHOTO_ID = "lugares/mirador-1"

    /**
     * Ana (desde marzo de 2026) con el Mirador verificado (35 puntos: verificada y primera publicación) y el Café
     * pendiente. Comentó, votó y visitó (+5) la Plaza de Laura, y Laura votó el Mirador. [anaPasswordHash] y
     * [lauraPasswordHash] son hashes de contraseñas generadas en la prueba.
     */
    fun anaWithActivity(database: Database, anaPasswordHash: String = randomSecret(), lauraPasswordHash: String = randomSecret()) =
        transaction(database) {
            user(ANA, ANA_EMAIL, "Ana Ríos", anaPasswordHash, Instant.parse("2026-03-14T15:00:00Z"))
            Users.insert {
                it[id] = LAURA
                it[email] = LAURA_EMAIL
                it[passwordHash] = lauraPasswordHash
                it[name] = "Laura Gómez"
                it[residency] = Residency.VISITOR
                it[createdAt] = Instant.parse("2026-01-10T15:00:00Z").atOffset(ZoneOffset.UTC)
            }
            Places.insert {
                it[id] = MIRADOR
                it[authorId] = ANA
                it[title] = "Mirador de la Secreta"
                it[description] = "Un mirador con vista a toda la ciudad, ideal al atardecer."
                it[category] = Category.NATURE
                it[categoryOrigin] = CategoryOrigin.SUGGESTED
                it[status] = PublicationStatus.VERIFIED
                it[latitude] = 4.5521
                it[longitude] = -75.6589
                it[price] = PriceRange.FREE
                it[address] = "Vía al Mirador, Armenia"
                it[hoursDays] = 0b0011111
                it[hoursOpens] = LocalTime.of(8, 0)
                it[hoursCloses] = LocalTime.of(18, 0)
                it[pointsEarned] = 35
                it[submittedAt] = Instant.parse("2026-09-01T14:00:00Z").atOffset(ZoneOffset.UTC)
            }
            Places.insert {
                it[id] = CAFE
                it[authorId] = ANA
                it[title] = "Café Las Acacias"
                it[description] = "Café de origen con tostión propia y vista al parque principal."
                it[category] = Category.GASTRONOMY
                it[categoryOrigin] = CategoryOrigin.CHOSEN
                it[latitude] = 4.5402
                it[longitude] = -75.6721
                it[submittedAt] = Instant.parse("2026-09-20T16:30:00Z").atOffset(ZoneOffset.UTC)
            }
            Places.insert {
                it[id] = PLAZA
                it[authorId] = LAURA
                it[title] = "Plaza de Bolívar"
                it[description] = "La plaza principal, con la catedral y el monumento al Esfuerzo."
                it[category] = Category.HISTORY
                it[categoryOrigin] = CategoryOrigin.SUGGESTED
                it[status] = PublicationStatus.VERIFIED
                it[latitude] = 4.5339
                it[longitude] = -75.6811
                it[pointsEarned] = 35
                it[submittedAt] = Instant.parse("2026-08-10T12:00:00Z").atOffset(ZoneOffset.UTC)
            }
            Photos.insert {
                it[id] = MIRADOR_PHOTO
                it[ownerId] = ANA
                it[placeId] = MIRADOR
                it[position] = 0
                it[url] = "https://res.cloudinary.com/exploracity/image/upload/lugares/mirador-1.jpg"
                it[publicId] = MIRADOR_PHOTO_ID
            }
            Comments.insert {
                it[placeId] = PLAZA
                it[authorId] = ANA
                it[text] = "Muy bonita de noche, con la catedral iluminada."
                it[createdAt] = Instant.parse("2026-09-25T22:10:00Z").atOffset(ZoneOffset.UTC)
            }
            Votes.insert {
                it[placeId] = PLAZA
                it[userId] = ANA
            }
            Votes.insert {
                it[placeId] = MIRADOR
                it[userId] = LAURA
            }
            Visits.insert {
                it[placeId] = PLAZA
                it[userId] = ANA
                it[recommends] = true
                it[text] = "Fui un domingo en la tarde."
                it[pointsAwarded] = 5
            }
        }

    /** Un lugar más para las pruebas de búsqueda; por omisión verificado y en el centro de Armenia. */
    fun place(
        database: Database,
        author: UUID?,
        title: String,
        category: Category = Category.CULTURE,
        status: PublicationStatus = PublicationStatus.VERIFIED,
        latitude: Double = 4.5339,
        longitude: Double = -75.6811,
        description: String = "Un lugar de la comunidad, con su historia y su gente.",
    ): UUID = transaction(database) {
        Places.insert {
            it[authorId] = author
            it[Places.title] = title
            it[Places.description] = description
            it[Places.category] = category
            it[categoryOrigin] = CategoryOrigin.CHOSEN
            it[Places.status] = status
            it[Places.latitude] = latitude
            it[Places.longitude] = longitude
        }[Places.id]
    }

    private fun user(id: UUID, email: String, name: String, passwordHash: String, createdAt: Instant) = Users.insert {
        it[Users.id] = id
        it[Users.email] = email
        it[Users.passwordHash] = passwordHash
        it[Users.name] = name
        it[bio] = "Me encanta caminar por los miradores del Quindío."
        it[residency] = Residency.RESIDENT
        it[photoUrl] = "https://res.cloudinary.com/exploracity/image/upload/perfil/ana-rios.jpg"
        it[photoPublicId] = ANA_PHOTO_ID
        it[Users.createdAt] = createdAt.atOffset(ZoneOffset.UTC)
    }
}
