package com.personal.passvault.ui.kit

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import com.personal.passvault.domain.TimeText
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

data class KitCodes(val title: String, val account: String, val codes: List<Pair<String, Boolean>>) // code to used
data class KitTotp(val title: String, val label: String, val secret: String, val details: String)

data class KitData(
    val createdAt: Long,
    val botUsername: String?,
    val lastBackupAt: Long,
    val codeSets: List<KitCodes>,
    val totpKeys: List<KitTotp>,
)

/**
 * Draws the Emergency Kit as an A4 PDF entirely on the phone (no network, no third-party library).
 * The master password is NEVER printed — there is a box to write it by hand.
 */
object EmergencyKitPdf {

    fun build(data: KitData): ByteArray {
        val w = Writer()
        w.newPage()

        w.text("PassVault — Emergency Kit", w.title)
        w.text("Made ${TimeText.dateTime(data.createdAt)}", w.small)
        w.space(10f)
        w.callout(
            "Keep this paper somewhere safe at home — not in your phone, not in your photos. " +
                "Anyone who has this sheet AND your master password can open your vault.",
        )
        w.space(16f)

        // ---- Master password box ----
        w.heading("Your master password")
        w.writeBox(
            "Write it here by hand (it is never printed). Use clear capitals; note 0/O and 1/l/I.",
            lines = 2,
        )
        w.space(14f)

        // ---- Restore steps ----
        w.heading("If you lose or reset your phone")
        val bot = data.botUsername?.let { "@$it" } ?: "your PassVault bot"
        listOf(
            "Install PassVault on the new phone.",
            "On the first screen tap \"I have a backup — restore it\".",
            "Get your bot token again: in Telegram open @BotFather → /mybots → $bot → API Token. Paste it in PassVault.",
            "Tap \"Open $bot in Telegram\", press Start, then \"Latest backup from Telegram\".",
            "Type your master password. Your whole vault comes back, including password history.",
        ).forEachIndexed { i, step -> w.text("${i + 1}.  $step", w.body, indent = 6f) }
        w.space(4f)
        w.text(
            "Backups are the pinned .pvlt files in your chat with $bot." +
                (if (data.lastBackupAt > 0) " Latest backup when this kit was made: ${TimeText.dateTime(data.lastBackupAt)}." else ""),
            w.small,
        )

        // ---- Recovery codes ----
        if (data.codeSets.isNotEmpty()) {
            w.space(16f)
            w.heading("Recovery codes")
            w.text("Each code works once. Cross it out after you use it.", w.small)
            for (set in data.codeSets) {
                w.space(8f)
                w.ensure(40f)
                w.text(set.title + if (set.account.isNotBlank()) "  ·  ${set.account}" else "", w.bold)
                w.codeGrid(set.codes)
            }
        }

        // ---- 2FA keys ----
        if (data.totpKeys.isNotEmpty()) {
            w.space(16f)
            w.heading("2FA setup keys")
            w.text("Type a key into any authenticator app (\"Enter a setup key\") to get your codes back.", w.small)
            for (k in data.totpKeys) {
                w.space(6f)
                w.ensure(36f)
                w.text(k.title + if (k.label.isNotBlank()) "  ·  ${k.label}" else "", w.bold)
                w.text(k.secret.chunked(4).joinToString(" "), w.mono, indent = 8f)
                if (k.details.isNotBlank()) w.text(k.details, w.small, indent = 8f)
            }
        }
        return w.finish()
    }

    // ---------------------------------------------------------------------------------------------

    private class Writer {
        private val doc = PdfDocument()
        private var page: PdfDocument.Page? = null
        private var pageNo = 0
        var y = 0f

        private val width = 595 // A4 in points
        private val height = 842
        private val margin = 48f
        private val contentWidth = width - 2 * margin

        val title = paint(22f, Typeface.DEFAULT_BOLD, Color.rgb(20, 22, 28))
        val headingPaint = paint(14f, Typeface.DEFAULT_BOLD, Color.rgb(79, 70, 229))
        val bold = paint(11f, Typeface.DEFAULT_BOLD, Color.rgb(20, 22, 28))
        val body = paint(10.5f, Typeface.DEFAULT, Color.rgb(30, 32, 40))
        val small = paint(8.5f, Typeface.DEFAULT, Color.rgb(100, 104, 116))
        val mono = paint(11f, Typeface.MONOSPACE, Color.rgb(20, 22, 28))
        private val line = Paint().apply { color = Color.rgb(180, 184, 196); strokeWidth = 0.8f; style = Paint.Style.STROKE }
        private val fill = Paint().apply { color = Color.rgb(255, 243, 224); style = Paint.Style.FILL }

        private fun paint(size: Float, face: Typeface, color: Int) = Paint().apply {
            textSize = size; typeface = face; this.color = color; isAntiAlias = true
        }

        private val canvas get() = page!!.canvas

        fun newPage() {
            page?.let { footer(); doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
            y = margin
        }

        fun ensure(needed: Float) {
            if (y + needed > height - margin - 24) newPage()
        }

        fun space(h: Float) { y += h }

        fun text(s: String, p: Paint, indent: Float = 0f) {
            for (l in wrap(s, p, contentWidth - indent)) {
                val h = p.textSize * 1.4f
                ensure(h)
                y += p.textSize
                canvas.drawText(l, margin + indent, y, p)
                y += h - p.textSize
            }
        }

        fun heading(s: String) {
            ensure(40f)
            text(s, headingPaint)
            y += 4f
        }

        fun callout(s: String) {
            val lines = wrap(s, body, contentWidth - 20)
            val h = lines.size * body.textSize * 1.4f + 16
            ensure(h)
            canvas.drawRoundRect(margin, y, margin + contentWidth, y + h, 8f, 8f, fill)
            var ty = y + 8
            for (l in lines) {
                ty += body.textSize
                canvas.drawText(l, margin + 10, ty, body)
                ty += body.textSize * 0.4f
            }
            y += h
        }

        fun writeBox(hint: String, lines: Int) {
            val boxH = 28f * lines + 30
            ensure(boxH)
            canvas.drawRoundRect(margin, y, margin + contentWidth, y + boxH, 8f, 8f, line)
            canvas.drawText(hint, margin + 10, y + 16, small)
            for (i in 1..lines) {
                val ly = y + 16 + 28f * i
                canvas.drawLine(margin + 10, ly, margin + contentWidth - 10, ly, line)
            }
            y += boxH
        }

        /** Codes in 2 columns, monospace, used ones struck through. */
        fun codeGrid(codes: List<Pair<String, Boolean>>) {
            val colW = contentWidth / 2
            codes.chunked(2).forEach { row ->
                val h = mono.textSize * 1.6f
                ensure(h)
                y += mono.textSize
                row.forEachIndexed { i, (code, used) ->
                    val x = margin + 8 + i * colW
                    val p = if (used) Paint(mono).apply { color = Color.rgb(160, 164, 176) } else mono
                    canvas.drawText(code, x, y, p)
                    if (used) {
                        val tw = p.measureText(code)
                        canvas.drawLine(x, y - mono.textSize / 3, x + tw, y - mono.textSize / 3, p)
                    }
                }
                y += h - mono.textSize
            }
        }

        private fun footer() {
            canvas.drawText("PassVault Emergency Kit · page $pageNo", margin, height - margin / 2, small)
        }

        fun finish(): ByteArray {
            page?.let { footer(); doc.finishPage(it) }
            page = null
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            doc.close()
            return out.toByteArray()
        }

        private fun wrap(s: String, p: Paint, maxWidth: Float): List<String> {
            val result = mutableListOf<String>()
            for (paragraph in s.split('\n')) {
                var current = ""
                for (word in paragraph.split(' ')) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (p.measureText(candidate) <= maxWidth) {
                        current = candidate
                    } else {
                        if (current.isNotEmpty()) result += current
                        current = word
                    }
                }
                result += current
            }
            return result
        }
    }
}

/** Hands the finished PDF to Android's print system (which also offers "Save as PDF"). */
class PdfPrintAdapter(private val bytes: ByteArray, private val name: String) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),
            true,
        )
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        try {
            FileOutputStream(destination.fileDescriptor).use { it.write(bytes) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
