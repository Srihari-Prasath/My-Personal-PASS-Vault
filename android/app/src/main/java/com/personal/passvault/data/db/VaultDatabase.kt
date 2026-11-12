package com.personal.passvault.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

@Database(
    entities = [
        EntryEntity::class,
        PasswordHistoryEntity::class,
        RecoveryCodeEntity::class,
        LinkedTargetEntity::class,
        WebIconEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun history(): HistoryDao
    abstract fun codes(): RecoveryCodeDao
    abstract fun targets(): LinkedTargetDao
    abstract fun webIcons(): WebIconDao
}

class VaultLockedException : IllegalStateException("Vault is locked")

/** v1 -> v2: type-specific JSON (2FA secret, card, Wi-Fi). Existing entries keep everything. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN payload TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * The database only exists while the vault is unlocked. It's opened with the Vault Key as a
 * SQLCipher *raw key* (x'…' form → no extra PBKDF2 on every open) and closed on lock.
 */
@Singleton
class DatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var db: VaultDatabase? = null

    /** [decoy] opens the separate decoy vault file (same schema, its own key). */
    fun open(vaultKey: ByteArray, decoy: Boolean = false) {
        close()
        val rawKey = rawKeyBytes(vaultKey)
        // SupportOpenHelperFactory wipes the key bytes after the first open
        val factory = SupportOpenHelperFactory(rawKey)
        db = Room.databaseBuilder(context, VaultDatabase::class.java, if (decoy) DECOY_DB_NAME else DB_NAME)
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2)
            .build()
            .also { it.openHelper.writableDatabase } // open now, so a wrong key fails here, not later
    }

    fun close() {
        db?.close()
        db = null
    }

    fun require(): VaultDatabase = db ?: throw VaultLockedException()

    fun deleteDatabaseFile() {
        close()
        context.deleteDatabase(DB_NAME)
        context.deleteDatabase(DECOY_DB_NAME)
    }

    /** Only while the REAL vault (or nothing) is open. */
    fun deleteDecoyDatabase() {
        context.deleteDatabase(DECOY_DB_NAME)
    }

    /** Builds x'<hex>' straight into a ByteArray (no immutable String copy of the key in memory). */
    private fun rawKeyBytes(key: ByteArray): ByteArray {
        val hex = "0123456789abcdef".toByteArray(Charsets.US_ASCII)
        val out = ByteArray(key.size * 2 + 3)
        out[0] = 'x'.code.toByte()
        out[1] = '\''.code.toByte()
        key.forEachIndexed { i, b ->
            val v = b.toInt() and 0xFF
            out[2 + i * 2] = hex[v ushr 4]
            out[3 + i * 2] = hex[v and 0x0F]
        }
        out[out.size - 1] = '\''.code.toByte()
        return out
    }

    private companion object {
        const val DB_NAME = "vault.db"
        const val DECOY_DB_NAME = "vault2.db" // neutral name: doesn't advertise that a decoy exists
    }
}
