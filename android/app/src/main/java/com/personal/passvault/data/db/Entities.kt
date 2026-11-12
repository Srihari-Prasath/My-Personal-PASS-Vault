package com.personal.passvault.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val id: String,
    val type: String,               // EntryType.name
    val title: String,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val url: String = "",           // primary website (first linked domain)
    val notes: String = "",
    val tags: String = "",
    val favorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val passwordChangedAt: Long,
    /** JSON of [com.personal.passvault.domain.EntryPayload] (2FA secret, card, Wi-Fi). Added in DB v2. */
    @ColumnInfo(defaultValue = "''") // SQL literal: must match the migration's DEFAULT '' exactly
    val payload: String = "",
)

/** One row per old password. Never pruned automatically. */
@Entity(
    tableName = "password_history",
    foreignKeys = [ForeignKey(
        entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("entryId")],
)
data class PasswordHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: String,
    val password: String,
    val usedFrom: Long,
    val replacedAt: Long,
    val note: String = "",
)

/** setReplacedAt == null -> current set. Non-null -> an old set kept as history. */
@Entity(
    tableName = "recovery_codes",
    foreignKeys = [ForeignKey(
        entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("entryId")],
)
data class RecoveryCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: String,
    val code: String,
    val used: Boolean = false,
    val usedAt: Long? = null,
    val position: Int,
    val setReplacedAt: Long? = null,
)

/** Apps / websites where this login is used — drives the icons. */
@Entity(
    tableName = "linked_targets",
    foreignKeys = [ForeignKey(
        entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("entryId")],
)
data class LinkedTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: String,
    val kind: String,               // "app" | "web"
    val packageName: String? = null,
    val domain: String? = null,
    val label: String = "",
    val position: Int,
)

/** Website icons live INSIDE the encrypted DB so the icon cache can't reveal which sites you use. */
@Entity(tableName = "web_icons")
data class WebIconEntity(
    @PrimaryKey val domain: String,
    val png: ByteArray?,
    val fetchedAt: Long,
    val failed: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        other is WebIconEntity && other.domain == domain && other.fetchedAt == fetchedAt && other.failed == failed

    override fun hashCode(): Int = domain.hashCode() * 31 + fetchedAt.hashCode()
}
