package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.CitySettings
import co.edu.uniquindio.exploracity.config.query
import co.edu.uniquindio.exploracity.integration.MailDeliveryException
import co.edu.uniquindio.exploracity.integration.MediaStore
import co.edu.uniquindio.exploracity.model.AccountResponse
import co.edu.uniquindio.exploracity.model.EmailChangeRequest
import co.edu.uniquindio.exploracity.model.ExportAccount
import co.edu.uniquindio.exploracity.model.ExportBadge
import co.edu.uniquindio.exploracity.model.ExportComment
import co.edu.uniquindio.exploracity.model.ExportFile
import co.edu.uniquindio.exploracity.model.ExportHours
import co.edu.uniquindio.exploracity.model.ExportLabels
import co.edu.uniquindio.exploracity.model.ExportPublication
import co.edu.uniquindio.exploracity.model.ExportReputation
import co.edu.uniquindio.exploracity.model.ExportVisit
import co.edu.uniquindio.exploracity.model.LinkPurpose
import co.edu.uniquindio.exploracity.model.UserLevel
import co.edu.uniquindio.exploracity.model.iso
import co.edu.uniquindio.exploracity.plugins.ApiException
import co.edu.uniquindio.exploracity.repository.AccountLinkRepository
import co.edu.uniquindio.exploracity.repository.EmailTakenException
import co.edu.uniquindio.exploracity.repository.OwnPlace
import co.edu.uniquindio.exploracity.repository.PersonalDataRepository
import co.edu.uniquindio.exploracity.repository.UserRecord
import co.edu.uniquindio.exploracity.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** «Descargar mis datos» (29): el nombre que se propone al guardarlo y el JSON. */
class DataExportFile(val fileName: String, val content: String)

/**
 * Componente de Usuarios (SAD) · La cuenta de la sesión: el correo y su cambio, el archivo con los datos personales y
 * la eliminación (Ley 1581).
 */
class AccountService(
    private val database: Database,
    private val users: UserRepository,
    private val links: AccountLinkRepository,
    private val personalData: PersonalDataRepository,
    private val reputation: ReputationService,
    private val passwords: PasswordHasher,
    private val mail: AccountMail,
    private val media: MediaStore,
    private val city: CitySettings,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(AccountService::class.java)

    suspend fun account(userId: UUID): AccountResponse = database.query { user(userId).toAccount() }

    /**
     * «Cambiar correo» · Envía el enlace al correo nuevo, que queda pendiente hasta confirmarlo. Otro pedido reemplaza al
     * anterior: su enlace deja de servir. Pide la contraseña actual (403 invalid_credentials si no coincide).
     */
    suspend fun requestEmailChange(userId: UUID, request: EmailChangeRequest): AccountResponse {
        val email = AccountRules.normalizeEmail(request.newEmail)
        if (!AccountRules.isValidEmail(email)) throw ApiException.badRequest("invalid_email")
        val user = database.query { user(userId) }
        if (!passwords.verify(request.password, user.passwordHash)) throw ApiException.forbidden(AuthService.INVALID_CREDENTIALS)
        if (email == user.email) throw ApiException.badRequest("same_email")
        if (database.query { users.findByEmail(email) } != null) throw AuthService.emailTaken()
        val token = SecretTokens.generate()
        deliver { mail.emailChange(email, user.name, token) }
        return database.query {
            val now = clock.instant()
            replaceLink(user.id, email, token, now)
            users.setPendingEmail(user.id, email)
            AccountResponse(user.email, email)
        }
    }

    /** Vuelve a enviar el enlace del cambio pendiente. Antes de 60 s no envía otro: el anterior sigue sirviendo. */
    suspend fun resendEmailChange(userId: UUID) {
        val now = clock.instant()
        val (user, waiting) = database.query {
            val user = user(userId)
            user to (links.lastSentAt(user.id, LinkPurpose.EMAIL_CHANGE)?.let { now.isBefore(it.plus(AccountRules.RESEND_WAIT)) } == true)
        }
        val pending = user.pendingEmail ?: throw ApiException.conflict("no_pending_email")
        if (waiting) return
        val token = SecretTokens.generate()
        deliver { mail.emailChange(pending, user.name, token) }
        database.query { replaceLink(user.id, pending, token, now) }
    }

    /**
     * Abre el enlace del correo nuevo: pasa a ser el de la cuenta. Un enlace de otra cuenta se trata como desconocido;
     * uno vencido o usado responde link_expired con el correo nuevo. Si otra cuenta se registró con ese correo mientras
     * tanto, email_taken.
     */
    suspend fun confirmEmailChange(userId: UUID, token: String): AccountResponse = database.query {
        val now = clock.instant()
        val found = links.find(SecretTokens.hash(token), LinkPurpose.EMAIL_CHANGE, lock = true)
        val link = AuthService.usable(found?.takeIf { it.userId == userId }, now)
        val owner = users.findByEmail(link.email)
        if (owner != null && owner.id != userId) throw AuthService.emailTaken()
        try {
            users.changeEmail(userId, link.email)
        } catch (e: EmailTakenException) {
            throw AuthService.emailTaken()
        }
        links.markUsed(link.id, now)
        links.expireAll(userId, LinkPurpose.EMAIL_CHANGE, now)
        AccountResponse(link.email)
    }

    /** 29 · «Descargar mis datos»: el mismo archivo, con claves en español, que armaba la app con datos de prueba. */
    suspend fun export(userId: UUID): DataExportFile = database.query {
        val user = user(userId)
        val now = clock.instant()
        val points = reputation.points(user)
        val file = ExportFile(
            generatedAt = now.iso(),
            account = ExportAccount(
                email = user.email,
                name = user.name,
                residency = ExportLabels.of(user.residency),
                city = city.name,
                memberSince = reputation.memberSince(user).toString(),
                bio = user.bio,
                photo = user.photoUrl,
            ),
            reputation = ExportReputation(
                points = points,
                level = ExportLabels.of(UserLevel.fromPoints(points)),
                badges = reputation.badges(user.id).map { ExportBadge(it.name, it.progress, it.target, it.progress >= it.target) },
            ),
            publications = personalData.places(user.id).map { it.toExport() },
            comments = personalData.comments(user.id).map { ExportComment(it.placeTitle, it.text, it.createdAt.iso()) },
            votes = personalData.votes(user.id),
            visits = personalData.visits(user.id).map { ExportVisit(it.placeTitle, it.recommends, it.text?.ifBlank { null }, it.showName) },
        )
        DataExportFile(
            fileName = "exploracity-mis-datos-${LocalDate.ofInstant(now, city.timeZone)}.json",
            content = exportJson.encodeToString(file),
        )
    }

    /**
     * 30 · Elimina la cuenta, todo o nada. Lo verificado y lo finalizado queda publicado sin autor, los comentarios
     * quedan como «Usuario eliminado» y los votos como cifras (llaves foráneas del esquema). Se borran las fotos, las
     * publicaciones pendientes y rechazadas, y lo personal. Las fotos salen del almacén después de confirmar el borrado:
     * si alguna falla, queda en el registro y la cuenta igual ya no existe.
     */
    suspend fun delete(userId: UUID) {
        val photos = database.query {
            val user = user(userId)
            // Antes de borrar nada: las fotos de las publicaciones pendientes se van con ellas.
            val ids = personalData.photoIds(user.id) + listOfNotNull(user.photoPublicId)
            personalData.deleteUnpublished(user.id)
            users.delete(user.id)
            ids
        }
        photos.forEach { publicId ->
            try {
                media.delete(publicId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("La cuenta se eliminó, pero la foto {} sigue en el almacén.", publicId, e)
            }
        }
    }

    /** Dentro de una transacción. La cuenta del token ya no existe: la sesión no vale (401). */
    private fun user(userId: UUID): UserRecord = users.findById(userId) ?: throw ApiException.unauthorized()

    /** Dentro de una transacción: el enlace nuevo reemplaza a los anteriores del cambio de correo. */
    private fun replaceLink(userId: UUID, email: String, token: String, now: Instant) {
        links.expireAll(userId, LinkPurpose.EMAIL_CHANGE, now)
        links.insert(userId, LinkPurpose.EMAIL_CHANGE, SecretTokens.hash(token), email, now.plus(AccountRules.EMAIL_CHANGE_LINK_TTL), now)
    }

    private suspend fun deliver(send: suspend () -> Unit) {
        try {
            send()
        } catch (e: MailDeliveryException) {
            log.warn("No salió el correo para confirmar el correo nuevo.", e)
            throw AuthService.deliveryFailed()
        }
    }

    private fun UserRecord.toAccount() = AccountResponse(email, pendingEmail)

    private companion object {
        val SPANISH: Locale = Locale.forLanguageTag("es-CO")

        val exportJson = Json {
            prettyPrint = true
            explicitNulls = false
        }

        fun OwnPlace.toExport() = ExportPublication(
            id = id.toString(),
            title = title,
            category = ExportLabels.of(category),
            status = ExportLabels.of(status),
            description = description,
            latitude = latitude,
            longitude = longitude,
            hours = if (hoursDays != null && opens != null && closes != null) {
                ExportHours(days = days(hoursDays), opens = opens.toString(), closes = closes.toString())
            } else {
                null
            },
            price = price?.let(ExportLabels::of),
            photos = photos,
            submittedAt = submittedAt.iso(),
            votes = votes,
            comments = comments,
            possibleDuplicate = possibleDuplicate,
        )

        /** Bits de lunes (1) a domingo (64) → «lunes», «martes»… en orden. */
        fun days(bits: Int): List<String> =
            DayOfWeek.entries.filter { bits and (1 shl it.ordinal) != 0 }.map { it.getDisplayName(TextStyle.FULL, SPANISH) }
    }
}
