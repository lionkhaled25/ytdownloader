package com.example.ytdl

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** قيمة نصية من JSON أو null لو مش موجودة / فاضية */
internal fun JSONObject.str(key: String): String? {
    if (isNull(key)) return null
    return optString(key, "").trim().takeIf { it.isNotEmpty() }
}

/** بيدوّر على كلمات الأغنية في LRCLIB (مجاني ومن غير مفتاح) */
object LyricsFetcher {

    data class Result(val plain: String?, val synced: String?)

    private const val BASE = "https://lrclib.net/api/search"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun find(title: String, artist: String?, track: String?, uploader: String?, duration: Double): Result? {
        val queries = mutableListOf<String>()
        fun add(q: String) {
            if (q.isNotBlank() && q !in queries) queries.add(q)
        }

        if (artist != null && track != null) {
            add("track_name=${enc(track)}&artist_name=${enc(artist)}")
        }
        val cleaned = cleanTitle(title)
        val idx = cleaned.indexOf(" - ")
        if (idx > 0) {
            val a = cleaned.substring(0, idx).trim()
            val tr = cleaned.substring(idx + 3).trim()
            add("track_name=${enc(tr)}&artist_name=${enc(a)}")
            add("q=${enc("$a $tr")}")
        }
        add("q=${enc(cleaned)}")
        if (uploader != null) add("q=${enc("$uploader $cleaned")}")

        for (q in queries) {
            val arr = try {
                getJson("$BASE?$q")
            } catch (_: Exception) {
                null
            } ?: continue
            val r = pick(arr, duration)
            if (r != null) return r
        }
        return null
    }

    private fun getJson(url: String): JSONArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "YTDownloader/5.0 (Android)")
        return try {
            if (c.responseCode != 200) null
            else JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    private fun pick(arr: JSONArray, duration: Double): Result? {
        var best: JSONObject? = null
        var bestDiff = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("instrumental", false)) continue
            if (o.str("plainLyrics") == null && o.str("syncedLyrics") == null) continue
            val d = o.optDouble("duration", Double.NaN)
            val known = !duration.isNaN() && !d.isNaN()
            val diff = if (known) Math.abs(d - duration) else 0.0
            if (known && diff > 8.0) continue   // غالبًا أغنية تانية
            if (diff < bestDiff) {
                best = o
                bestDiff = diff
            }
        }
        val b = best ?: return null
        val synced = b.str("syncedLyrics")
        val plain = b.str("plainLyrics") ?: synced?.let { stripTimestamps(it) }
        return Result(plain, synced)
    }

    private fun stripTimestamps(s: String): String =
        s.replace(Regex("""^\[[^\]]*]\s*""", RegexOption.MULTILINE), "")

    fun cleanTitle(t: String): String {
        var s = t.substringBefore(" | ")
        s = s.replace(Regex("""[\(\[\{][^\)\]\}]*[\)\]\}]"""), " ")
        s = s.replace(
            Regex(
                """(?i)\b(official\s*(music\s*)?(video|audio)|lyric(s)?\s*video|lyrics?|audio|hd|hq|4k|mv|video\s*clip)\b"""
            ),
            " "
        )
        return s.replace(Regex("""\s+"""), " ").trim().trim('-', ' ')
    }
}

/** بيكتب الكلمات جوه ملف MP3 (إطار USLT) من غير ما يمس الغلاف أو باقي البيانات */
object Id3 {

    private fun ss2i(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun i2ss(v: Int): ByteArray = byteArrayOf(
        ((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(),
        ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte()
    )

    private fun be(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun i2be(v: Int): ByteArray =
        byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    fun writeLyrics(file: File, lyrics: String): Boolean {
        val tmp = File(file.parentFile, file.name + ".tmp")
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(10)
            var hasTag = false
            if (raf.length() >= 10) {
                raf.readFully(header)
                hasTag = header[0] == 'I'.code.toByte() &&
                    header[1] == 'D'.code.toByte() &&
                    header[2] == '3'.code.toByte()
            }

            var ver = 3
            var region = ByteArray(0)
            var audioStart = 0L
            if (hasTag) {
                ver = header[3].toInt()
                if (ver != 3 && ver != 4) return false
                if ((header[5].toInt() and 0xF0) != 0) return false
                val size = ss2i(header, 6)
                region = ByteArray(size)
                raf.readFully(region)
                audioStart = 10L + size
            }

            // نحتفظ بكل الإطارات الموجودة (الغلاف، الاسم، الفنان...) ما عدا USLT القديم
            val keep = ByteArrayOutputStream()
            var p = 0
            while (p + 10 <= region.size) {
                if (region[p].toInt() == 0) break // بداية الـ padding
                val id = String(region, p, 4, Charsets.ISO_8859_1)
                val sz = if (ver == 4) ss2i(region, p + 4) else be(region, p + 4)
                val frameLen = 10 + sz
                if (sz < 0 || p + frameLen > region.size) return false
                if (id != "USLT") keep.write(region, p, frameLen)
                p += frameLen
            }

            val text = lyrics.replace("\r\n", "\n")
            val body = ByteArrayOutputStream()
            if (ver == 4) {
                body.write(3) // UTF-8
                body.write("eng".toByteArray(Charsets.ISO_8859_1))
                body.write(0)
                body.write(text.toByteArray(Charsets.UTF_8))
            } else {
                body.write(1) // UTF-16 مع BOM
                body.write("eng".toByteArray(Charsets.ISO_8859_1))
                body.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0))
                body.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
                body.write(text.toByteArray(Charsets.UTF_16LE))
            }
            val bodyBytes = body.toByteArray()

            val frame = ByteArrayOutputStream()
            frame.write("USLT".toByteArray(Charsets.ISO_8859_1))
            frame.write(if (ver == 4) i2ss(bodyBytes.size) else i2be(bodyBytes.size))
            frame.write(byteArrayOf(0, 0))
            frame.write(bodyBytes)
            val frameBytes = frame.toByteArray()

            val tagSize = keep.size() + frameBytes.size
            FileOutputStream(tmp).use { out ->
                out.write("ID3".toByteArray(Charsets.ISO_8859_1))
                out.write(byteArrayOf(ver.toByte(), 0, 0))
                out.write(i2ss(tagSize))
                keep.writeTo(out)
                out.write(frameBytes)
                raf.seek(audioStart)
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = raf.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            return tmp.renameTo(file)
        }
        return true
    }
}
