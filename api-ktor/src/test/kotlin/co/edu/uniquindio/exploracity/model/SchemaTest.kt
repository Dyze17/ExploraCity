package co.edu.uniquindio.exploracity.model

import co.edu.uniquindio.exploracity.support.DatabaseTest
import co.edu.uniquindio.exploracity.support.randomSecret
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.postgresql.util.PSQLException
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Las migraciones de Flyway y el mapeo de Exposed dicen lo mismo, y las reglas del esquema se cumplen. */
class SchemaTest : DatabaseTest() {

    private fun JdbcTransaction.user(email: String = "ana@ejemplo.co"): UUID = Users.insert {
        it[Users.email] = email
        it[passwordHash] = randomSecret()
        it[name] = "Ana Ríos"
        it[residency] = Residency.RESIDENT
    }[Users.id]

    private fun JdbcTransaction.place(
        author: UUID?,
        title: String = "Mirador de la Secreta",
        latitude: Double = 4.5339,
        longitude: Double = -75.6811,
    ): UUID = Places.insert {
        it[authorId] = author
        it[Places.title] = title
        it[description] = "Un mirador con vista a toda la ciudad, ideal al atardecer."
        it[category] = Category.NATURE
        it[categoryOrigin] = CategoryOrigin.SUGGESTED
        it[Places.latitude] = latitude
        it[Places.longitude] = longitude
        it[hoursDays] = 0b0011111
        it[hoursOpens] = LocalTime.of(8, 0)
        it[hoursCloses] = LocalTime.of(18, 0)
    }[Places.id]

    private fun JdbcTransaction.scalar(sql: String): String? =
        exec(sql) { rs -> if (rs.next()) rs.getString(1) else null }

    @Test
    fun `las extensiones de duplicados están instaladas`() = transaction(database) {
        assertEquals("1", scalar("SELECT count(*) FROM pg_extension WHERE extname = 'postgis'"))
        assertEquals("1", scalar("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'"))
    }

    @Test
    fun `cada tabla de Exposed se puede escribir y leer`() = transaction(database) {
        val ana = user()
        val laura = user("laura@ejemplo.co")
        val placeId = place(ana)
        val other = place(laura, title = "Mirador Secreto", latitude = 4.53395)
        val now = java.time.OffsetDateTime.now()

        val replacement = RefreshTokens.insert {
            it[userId] = ana
            it[tokenHash] = randomSecret()
            it[expiresAt] = now.plusDays(30)
        }[RefreshTokens.id]
        RefreshTokens.insert {
            it[userId] = ana
            it[tokenHash] = randomSecret()
            it[expiresAt] = now.plusDays(30)
            it[revokedAt] = now
            it[replacedBy] = replacement
        }
        LoginAttempts.insert {
            it[email] = "nadie@ejemplo.co"
            it[failures] = 1
            it[windowStartedAt] = now
        }
        AccountLinks.insert {
            it[userId] = ana
            it[purpose] = LinkPurpose.PASSWORD_RESET
            it[tokenHash] = randomSecret()
            it[email] = "ana@ejemplo.co"
            it[expiresAt] = now.plusMinutes(30)
        }
        Photos.insert {
            it[ownerId] = ana
            it[Photos.placeId] = placeId
            it[position] = 0
            it[url] = "https://res.cloudinary.com/demo/mirador.jpg"
            it[publicId] = "mirador"
        }
        PlaceSimilar.insert {
            it[PlaceSimilar.placeId] = placeId
            it[similarId] = other
        }
        ModerationDecisions.insert {
            it[ModerationDecisions.placeId] = placeId
            it[moderatorId] = laura
            it[action] = DecisionAction.REJECTED
            it[rejectionReason] = RejectionReason.DUPLICATE
            it[rejectionMessage] = "Ya existe"
            it[canResubmit] = false
            it[duplicateOf] = other
        }
        Votes.insert {
            it[Votes.placeId] = other
            it[userId] = ana
        }
        Visits.insert {
            it[Visits.placeId] = other
            it[userId] = ana
            it[recommends] = true
            it[pointsAwarded] = 5
        }
        val comment = Comments.insert {
            it[Comments.placeId] = other
            it[authorId] = ana
            it[clientId] = UUID.randomUUID()
            it[text] = "Muy bonito"
        }[Comments.id]
        UserBadges.insert {
            it[userId] = ana
            it[badgeId] = "primera-publicacion"
        }
        Notifications.insert {
            it[userId] = laura
            it[type] = NotificationType.COMMENTED
            it[Notifications.placeId] = other
            it[placeTitle] = "Mirador Secreto"
            it[commentId] = comment
        }
        UserReports.insert {
            it[reportedId] = laura
            it[reporterId] = ana
            it[reason] = ReportReason.SPAM
        }

        val read = Places.selectAll().where { Places.id eq placeId }.single()
        assertEquals(PublicationStatus.PENDING, read[Places.status])
        assertEquals(LocalTime.of(8, 0), read[Places.hoursOpens])
        assertEquals(1, Photos.selectAll().count())
        assertEquals(replacement, RefreshTokens.selectAll().where { RefreshTokens.replacedBy.isNotNull() }.single()[RefreshTokens.replacedBy])
        assertEquals(1, LoginAttempts.selectAll().single()[LoginAttempts.failures])
        assertEquals(DecisionAction.REJECTED, ModerationDecisions.selectAll().single()[ModerationDecisions.action])
        assertEquals(5, UserPoints.select(UserPoints.points).where { UserPoints.userId eq ana }.single()[UserPoints.points])
    }

    @Test
    fun `la ubicación se calcula sola y se busca en metros`() = transaction(database) {
        val ana = user()
        place(ana, latitude = 4.5339, longitude = -75.6811)
        // Unos 22 m al norte.
        place(ana, title = "Mirador Secreto", latitude = 4.5341, longitude = -75.6811)

        val near = scalar(
            "SELECT count(*) FROM places WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(-75.6811, 4.5339), 4326)::geography, 50)",
        )
        val veryNear = scalar(
            "SELECT count(*) FROM places WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(-75.6811, 4.5339), 4326)::geography, 10)",
        )
        assertEquals("2", near)
        assertEquals("1", veryNear)
        assertTrue(scalar("SELECT similarity('Mirador de la Secreta', 'Mirador Secreto') > 0.3")!!.startsWith("t"))
    }

    @Test
    fun `el catálogo trae las nueve insignias en orden`() = transaction(database) {
        val badges = Badges.selectAll().orderBy(Badges.position).map { it[Badges.id] }

        assertEquals(9, badges.size)
        assertEquals("primera-publicacion", badges.first())
        assertEquals(Category.NATURE, Badges.selectAll().where { Badges.id eq "amigo-del-verde" }.single()[Badges.category])
    }

    @Test
    fun `los puntos salen de los lugares y las visitas`() = transaction(database) {
        val ana = user()
        val first = place(ana)
        val second = place(ana, title = "Café La Fogata")
        Places.update({ Places.id eq first }) { it[pointsEarned] = 35 }
        Places.update({ Places.id eq second }) { it[pointsEarned] = 15 }
        Visits.insert {
            it[placeId] = second
            it[userId] = ana
            it[pointsAwarded] = 5
        }

        assertEquals(55, UserPoints.select(UserPoints.points).where { UserPoints.userId eq ana }.single()[UserPoints.points])

        Places.deleteWhere { Places.id eq first }
        assertEquals(20, UserPoints.select(UserPoints.points).where { UserPoints.userId eq ana }.single()[UserPoints.points])
    }

    @Test
    fun `al eliminar la cuenta queda lo público sin autor y se borra lo personal`() = transaction(database) {
        val ana = user()
        val laura = user("laura@ejemplo.co")
        val placeId = place(ana)
        Photos.insert {
            it[ownerId] = ana
            it[Photos.placeId] = placeId
            it[position] = 0
            it[url] = "https://res.cloudinary.com/demo/mirador.jpg"
            it[publicId] = "mirador"
        }
        Comments.insert {
            it[Comments.placeId] = placeId
            it[authorId] = ana
            it[text] = "Muy bonito"
        }
        Votes.insert {
            it[Votes.placeId] = placeId
            it[userId] = ana
        }
        Votes.insert {
            it[Votes.placeId] = placeId
            it[userId] = laura
        }

        Users.deleteWhere { Users.id eq ana }

        assertNull(Places.selectAll().single()[Places.authorId])
        assertNull(Comments.selectAll().single()[Comments.authorId])
        assertEquals(2, Votes.selectAll().count())
        assertEquals(0, Photos.selectAll().count())
    }

    @Test
    fun `las reglas de la app también las cuida la base de datos`() {
        transaction(database) { user() }
        // Un mismo correo con otras mayúsculas.
        assertFailsWith<Exception> { transaction(database) { user("ANA@ejemplo.co") } }.assertCause<PSQLException>()
        // Un título de menos de 5 caracteres.
        assertFailsWith<Exception> { transaction(database) { place(null, title = "Café") } }.assertCause<PSQLException>()
        // Un horario a medias.
        assertFailsWith<Exception> {
            transaction(database) {
                val id = place(null)
                Places.update({ Places.id eq id }) { it[hoursOpens] = null }
            }
        }.assertCause<PSQLException>()
    }

    private inline fun <reified T : Throwable> Throwable.assertCause() {
        assertTrue(generateSequence(this) { it.cause }.any { it is T }, "Se esperaba $this causado por ${T::class.simpleName}")
    }
}
