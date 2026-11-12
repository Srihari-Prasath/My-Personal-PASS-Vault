package com.personal.passvault.data.telegram

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Telegram said no (bad token, chat not found, …). [code] is Telegram's error_code (401, 400, 409…). */
class TelegramException(message: String, val code: Int) : Exception(message)

// ---- Minimal Bot API DTOs (only the fields we use) ----
data class TgUser(val id: Long, @SerializedName("is_bot") val isBot: Boolean, val username: String?, @SerializedName("first_name") val firstName: String?)
data class TgChat(val id: Long, val type: String?, @SerializedName("pinned_message") val pinnedMessage: TgMessage?)
data class TgDocument(@SerializedName("file_id") val fileId: String, @SerializedName("file_name") val fileName: String?, @SerializedName("file_size") val fileSize: Long?)
data class TgMessage(
    @SerializedName("message_id") val messageId: Long,
    val date: Long,
    val chat: TgChat?,
    val from: TgUser?,
    val text: String?,
    val document: TgDocument?,
    val caption: String?,
)
data class TgUpdate(@SerializedName("update_id") val updateId: Long, val message: TgMessage?)
data class TgFile(@SerializedName("file_id") val fileId: String, @SerializedName("file_path") val filePath: String?)

/**
 * Talks to api.telegram.org directly — no server of our own. Only ever sends ENCRYPTED backup files.
 * The token is part of the URL path, so this client must never get a logging interceptor.
 */
@Singleton
class TelegramClient @Inject constructor(
    private val http: OkHttpClient,
    private val gson: Gson,
) {
    suspend fun getMe(token: String): TgUser = call(token, "getMe", FormBody.Builder().build())

    suspend fun getUpdates(token: String, offset: Long? = null): List<TgUpdate> {
        val body = FormBody.Builder().add("timeout", "0").add("allowed_updates", "[\"message\"]")
        if (offset != null) body.add("offset", offset.toString())
        return call<Array<TgUpdate>>(token, "getUpdates", body.build()).toList()
    }

    suspend fun sendMessage(token: String, chatId: Long, text: String): TgMessage = call(
        token, "sendMessage",
        FormBody.Builder().add("chat_id", chatId.toString()).add("text", text).build(),
    )

    suspend fun sendDocument(token: String, chatId: Long, bytes: ByteArray, fileName: String, caption: String): TgMessage {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.toString())
            .addFormDataPart("caption", caption)
            .addFormDataPart("disable_content_type_detection", "true")
            .addFormDataPart("document", fileName, bytes.toRequestBody(OCTET))
            .build()
        return call(token, "sendDocument", body)
    }

    suspend fun pinChatMessage(token: String, chatId: Long, messageId: Long) {
        call<Boolean>(
            token, "pinChatMessage",
            FormBody.Builder()
                .add("chat_id", chatId.toString())
                .add("message_id", messageId.toString())
                .add("disable_notification", "true")
                .build(),
        )
    }

    suspend fun unpinChatMessage(token: String, chatId: Long, messageId: Long) {
        call<Boolean>(
            token, "unpinChatMessage",
            FormBody.Builder().add("chat_id", chatId.toString()).add("message_id", messageId.toString()).build(),
        )
    }

    suspend fun getChat(token: String, chatId: Long): TgChat =
        call(token, "getChat", FormBody.Builder().add("chat_id", chatId.toString()).build())

    suspend fun getFile(token: String, fileId: String): TgFile =
        call(token, "getFile", FormBody.Builder().add("file_id", fileId).build())

    suspend fun downloadFile(token: String, filePath: String, maxBytes: Int = 20 * 1024 * 1024): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("$BASE/file/bot$token/$filePath").build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw TelegramException("Download failed (HTTP ${resp.code})", resp.code)
                val body = resp.body ?: throw IOException("Empty download")
                if (body.contentLength() > maxBytes) throw IOException("Backup file is unexpectedly large")
                body.bytes()
            }
        }

    // ---------------------------------------------------------------------------------------------

    private suspend inline fun <reified T> call(token: String, method: String, body: RequestBody): T =
        withContext(Dispatchers.IO) {
            require(TOKEN_SHAPE.matches(token)) { "That doesn't look like a bot token" }
            val request = Request.Builder().url("$BASE/bot$token/$method").post(body).build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
                    ?: throw TelegramException("Telegram returned an unexpected response (HTTP ${resp.code})", resp.code)
                if (json.get("ok")?.asBoolean != true) {
                    val code = json.get("error_code")?.asInt ?: resp.code
                    val desc = json.get("description")?.asString ?: "Unknown error"
                    throw TelegramException(friendly(code, desc), code)
                }
                gson.fromJson(json.get("result"), T::class.java)
            }
        }

    private fun friendly(code: Int, desc: String): String = when {
        code == 401 -> "Telegram rejected the bot token. Copy it again from @BotFather."
        code == 409 -> "Another program is reading this bot's messages (a webhook or the bot server). Enter your chat ID manually instead."
        code == 403 -> "The bot can't message you — open the bot in Telegram and press Start."
        desc.contains("chat not found", ignoreCase = true) -> "Chat not found — open the bot in Telegram and press Start first."
        else -> "Telegram: $desc"
    }

    companion object {
        private const val BASE = "https://api.telegram.org"
        private val OCTET = "application/octet-stream".toMediaType()
        /** 123456789:AAH… — checked before the token is ever put in a URL. */
        val TOKEN_SHAPE = Regex("^\\d{5,15}:[A-Za-z0-9_-]{30,50}$")
    }
}
