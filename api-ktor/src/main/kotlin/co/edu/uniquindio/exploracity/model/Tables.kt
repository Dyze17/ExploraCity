package co.edu.uniquindio.exploracity.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// Tablas de Exposed (ADR-05). El esquema lo crean las migraciones de Flyway (db/migration); aquí solo se mapea. Las
// consultas espaciales usan places.location, que no se mapea: es una columna generada a partir de latitud y longitud.

private const val ENUM_LENGTH = 32

object Users : Table("users") {
    val id = javaUUID("id").autoGenerate()
    val email = text("email")
    val pendingEmail = text("pending_email").nullable()
    val passwordHash = text("password_hash")
    val name = text("name")
    val bio = text("bio").nullable()
    val residency = enumerationByName<Residency>("residency", ENUM_LENGTH)
    val photoUrl = text("photo_url").nullable()
    val photoPublicId = text("photo_public_id").nullable()
    val role = enumerationByName<Role>("role", ENUM_LENGTH).default(Role.USER)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = javaUUID("id").autoGenerate()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val tokenHash = text("token_hash")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object AccountLinks : Table("account_links") {
    val id = javaUUID("id").autoGenerate()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val purpose = enumerationByName<LinkPurpose>("purpose", ENUM_LENGTH)
    val tokenHash = text("token_hash")
    val email = text("email")
    val expiresAt = timestampWithTimeZone("expires_at")
    val usedAt = timestampWithTimeZone("used_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object Places : Table("places") {
    val id = javaUUID("id").autoGenerate()
    val authorId = optReference("author_id", Users.id, onDelete = ReferenceOption.SET_NULL)
    val title = text("title")
    val description = text("description")
    val category = enumerationByName<Category>("category", ENUM_LENGTH)
    val categoryOrigin = enumerationByName<CategoryOrigin>("category_origin", ENUM_LENGTH)
    val status = enumerationByName<PublicationStatus>("status", ENUM_LENGTH).default(PublicationStatus.PENDING)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val address = text("address").nullable()
    val price = enumerationByName<PriceRange>("price", ENUM_LENGTH).nullable()

    /** Días de atención como bits: lunes = 1 … domingo = 64. */
    val hoursDays = short("hours_days").nullable()
    val hoursOpens = time("hours_opens").nullable()
    val hoursCloses = time("hours_closes").nullable()
    val possibleDuplicate = bool("possible_duplicate").default(false)
    val duplicateNote = text("duplicate_note").nullable()
    val pointsEarned = integer("points_earned").default(0)
    val submittedAt = timestampWithTimeZone("submitted_at").defaultExpression(CurrentTimestampWithTimeZone)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object Photos : Table("photos") {
    val id = javaUUID("id").autoGenerate()
    val ownerId = reference("owner_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val placeId = optReference("place_id", Places.id, onDelete = ReferenceOption.CASCADE)
    val position = short("position").nullable()
    val url = text("url")
    val publicId = text("public_id")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object PlaceSimilar : Table("place_similar") {
    val placeId = reference("place_id", Places.id, onDelete = ReferenceOption.CASCADE)
    val similarId = reference("similar_id", Places.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(placeId, similarId)
}

object ModerationDecisions : Table("moderation_decisions") {
    val id = javaUUID("id").autoGenerate()
    val placeId = reference("place_id", Places.id, onDelete = ReferenceOption.CASCADE)
    val moderatorId = optReference("moderator_id", Users.id, onDelete = ReferenceOption.SET_NULL)
    val action = enumerationByName<DecisionAction>("action", ENUM_LENGTH)
    val note = text("note").nullable()
    val rejectionReason = enumerationByName<RejectionReason>("rejection_reason", ENUM_LENGTH).nullable()
    val rejectionMessage = text("rejection_message").nullable()
    val canResubmit = bool("can_resubmit").nullable()
    val duplicateOf = optReference("duplicate_of", Places.id, onDelete = ReferenceOption.SET_NULL)
    val finalizeReason = enumerationByName<FinalizeReason>("finalize_reason", ENUM_LENGTH).nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object Votes : Table("votes") {
    val id = long("id").autoIncrement()
    val placeId = reference("place_id", Places.id, onDelete = ReferenceOption.CASCADE)
    val userId = optReference("user_id", Users.id, onDelete = ReferenceOption.SET_NULL)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object Visits : Table("visits") {
    val placeId = reference("place_id", Places.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val recommends = bool("recommends").nullable()
    val text = text("text").nullable()
    val showName = bool("show_name").default(false)
    val pointsAwarded = integer("points_awarded").default(0)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(placeId, userId)
}

object Comments : Table("comments") {
    val id = javaUUID("id").autoGenerate()
    val placeId = reference("place_id", Places.id, onDelete = ReferenceOption.CASCADE)
    val authorId = optReference("author_id", Users.id, onDelete = ReferenceOption.SET_NULL)
    val clientId = javaUUID("client_id").nullable()
    val text = text("text")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object Badges : Table("badges") {
    val id = text("id")
    val position = short("position")
    val name = text("name")
    val metric = enumerationByName<BadgeMetric>("metric", ENUM_LENGTH)
    val category = enumerationByName<Category>("category", ENUM_LENGTH).nullable()
    val target = integer("target")
    val howTo = text("how_to")
    val tip = text("tip").nullable()

    override val primaryKey = PrimaryKey(id)
}

object UserBadges : Table("user_badges") {
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val badgeId = reference("badge_id", Badges.id, onDelete = ReferenceOption.CASCADE)
    val unlockedAt = timestampWithTimeZone("unlocked_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(userId, badgeId)
}

object Notifications : Table("notifications") {
    val id = javaUUID("id").autoGenerate()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val type = enumerationByName<NotificationType>("type", ENUM_LENGTH)
    val placeId = optReference("place_id", Places.id, onDelete = ReferenceOption.CASCADE)
    val placeTitle = text("place_title").nullable()
    val points = integer("points").nullable()
    val reason = text("reason").nullable()
    val commentId = optReference("comment_id", Comments.id, onDelete = ReferenceOption.CASCADE)
    val existingPlaceId = optReference("existing_place_id", Places.id, onDelete = ReferenceOption.SET_NULL)
    val existingTitle = text("existing_title").nullable()
    val achievement = text("achievement").nullable()
    val badgeId = optReference("badge_id", Badges.id, onDelete = ReferenceOption.SET_NULL)
    val remaining = integer("remaining").nullable()
    val readAt = timestampWithTimeZone("read_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object UserReports : Table("user_reports") {
    val id = javaUUID("id").autoGenerate()
    val reportedId = reference("reported_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val reporterId = optReference("reporter_id", Users.id, onDelete = ReferenceOption.SET_NULL)
    val reason = enumerationByName<ReportReason>("reason", ENUM_LENGTH)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

/** Vista de solo lectura: los puntos de cada persona (26). */
object UserPoints : Table("user_points") {
    val userId = javaUUID("user_id")
    val points = integer("points")
}
