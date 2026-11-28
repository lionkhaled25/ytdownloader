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

/**
 * بيدوّر على كلمات الأغنية في أكتر من مصدر مجاني من غير مفاتيح:
 * LRCLIB (متزامنة)، وDeezer لتوحيد اسم الأغنية/الفنان، وlyrics.ovh، وNetEase.
 * للأغاني العربي بيفضّل الكلمات المكتوبة بالحروف العربي لو لقاها.
 */
object LyricsFetcher {

    data class Result(val plain: String?, val synced: String?)

    private const val LRCLIB = "https://lrclib.net/api/search"
    private const val UA = "YTDownloader/6.5 (Android)"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun arabicRatio(s: String): Double {
        var ar = 0
        var letters = 0
        for (ch in s) {
            if (ch.isLetter()) {
                letters++
                if (ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F') ar++
            }
        }
        return if (letters == 0) 0.0 else ar.toDouble() / letters
    }

    private fun isArabicText(s: String?) = s != null && arabicRatio(s) > 0.5
    private fun hasArabic(s: String?) = s != null && arabicRatio(s) > 0.0

    private fun get(url: String, headers: Map<String, String> = emptyMap(), post: String? = null): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 7000
            c.readTimeout = 7000
            c.setRequestProperty("User-Agent", UA)
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (post != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(post.toByteArray(Charsets.UTF_8)) }
            }
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            c.disconnect()
        }
    }

    private fun getJson(url: String): JSONArray? =
        try { get(url)?.let { JSONArray(it) } } catch (_: Exception) { null }

    fun find(title: String, artist: String?, track: String?, uploader: String?, duration: Double): Result? {
        val deadline = System.currentTimeMillis() + 45_000
        fun timeLeft() = System.currentTimeMillis() < deadline

        val cleaned = cleanTitle(title)
        val cleanArtist = artist?.let { cleanTitle(it) }?.takeIf { it.isNotBlank() }
        val cleanTrack = track?.let { cleanTitle(it) }?.takeIf { it.isNotBlank() }
        val upl = uploader?.let { cleanUploader(it) }?.takeIf { it.isNotBlank() }

        // أزواج (فنان، أغنية) محتملة
        val pairs = LinkedHashSet<Pair<String, String>>()
        if (cleanArtist != null && cleanTrack != null) pairs.add(cleanArtist to cleanTrack)
        for (sep in listOf(" - ", " – ", " — ", " ـ ")) {
            val i = cleaned.indexOf(sep)
            if (i > 0) {
                val a = cleaned.substring(0, i).trim()
                val t = cleaned.substring(i + sep.length).trim()
                if (a.isNotBlank() && t.isNotBlank()) {
                    pairs.add(a to t)
                    pairs.add(t to a)
                }
                break
            }
        }
        if (upl != null && cleaned.isNotBlank()) pairs.add(upl to cleaned)

        val arabic = hasArabic(title) || hasArabic(artist) || hasArabic(track)
        val found = mutableListOf<Result>()

        fun consider(r: Result?): Boolean {
            if (r == null) return false
            found.add(r)
            return !arabic || isArabicText(r.plain)
        }

        // 1) LRCLIB بالبيانات الأصلية
        for (q in lrclibQueries(pairs, cleaned, upl)) {
            if (!timeLeft()) break
            val arr = getJson("$LRCLIB?$q") ?: continue
            if (consider(pickLrclib(arr, duration))) return found.last()
        }

        // 2) Deezer: بيوحّد اسم الأغنية والفنان (بيفهم العربي والإنجليزي)
        val names = if (timeLeft()) deezerNames(listOf(cleaned) + pairs.map { "${it.first} ${it.second}" }, duration) else emptyList()
        for ((a, t) in names) {
            if (!timeLeft()) break
            val arr = getJson("$LRCLIB?track_name=${enc(t)}&artist_name=${enc(a)}")
            if (arr != null && consider(pickLrclib(arr, duration))) return found.last()
        }

        // 3) lyrics.ovh بالأسماء الموحّدة (غالبًا بيرجّع العربي بالحروف العربي)
        val ovhPairs = LinkedHashSet<Pair<String, String>>()
        ovhPairs.addAll(names)
        ovhPairs.addAll(pairs)
        var ovhCalls = 0
        for ((a, t) in ovhPairs) {
            if (!timeLeft() || ovhCalls >= 6) break
            ovhCalls++
            if (consider(ovh(a, t))) return found.last()
        }

        // 4) NetEase (بحث بالأسماء الموحّدة لأنه ضعيف مع الحروف العربي)
        val neteaseQueries = (names.map { "${it.first} ${it.second}" } + listOf(cleaned)).distinct().take(3)
        for (q in neteaseQueries) {
            if (!timeLeft()) break
            if (consider(netease(q, duration))) return found.last()
        }

        // مفيش نتيجة عربي: نرجّع أحسن حاجة لقيناها (حتى لو بحروف لاتينية)
        return found.firstOrNull { it.synced != null } ?: found.firstOrNull()
    }

    private fun lrclibQueries(pairs: Set<Pair<String, String>>, cleaned: String, uploader: String?): List<String> {
        val out = LinkedHashSet<String>()
        for ((a, t) in pairs) {
            out.add("track_name=${enc(t)}&artist_name=${enc(a)}")
            out.add("q=${enc("$a $t")}")
        }
        if (cleaned.isNotBlank()) out.add("q=${enc(cleaned)}")
        if (uploader != null && cleaned.isNotBlank()) out.add("q=${enc("$uploader $cleaned")}")
        return out.take(7)
    }

    private fun pickLrclib(arr: JSONArray, duration: Double): Result? {
        var best: JSONObject? = null
        var bestScore = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("instrumental", false)) continue
            if (o.str("plainLyrics") == null && o.str("syncedLyrics") == null) continue
            val d = o.optDouble("duration", Double.NaN)
            val known = !duration.isNaN() && !d.isNaN()
            val diff = if (known) Math.abs(d - duration) else 0.0
            if (known && diff > 8.0) continue   // غالبًا أغنية تانية
            // الأفضلية للعربي بالحروف العربي، وبعدين الأقرب في المدة
            val arabicBonus = if (isArabicText(o.str("plainLyrics") ?: o.str("syncedLyrics"))) -100.0 else 0.0
            val score = diff + arabicBonus
            if (score < bestScore) {
                best = o
                bestScore = score
            }
        }
        val b = best ?: return null
        val synced = b.str("syncedLyrics")
        val plain = b.str("plainLyrics") ?: synced?.let { stripTimestamps(it) }
        return Result(plain, synced)
    }

    /** بيرجّع أزواج (فنان، أغنية) من Deezer للأغاني اللي مدتها قريبة */
    private fun deezerNames(queries: List<String>, duration: Double): List<Pair<String, String>> {
        val out = LinkedHashSet<Pair<String, String>>()
        for (q in queries.filter { it.isNotBlank() }.distinct().take(3)) {
            val body = get("https://api.deezer.com/search?limit=5&q=${enc(q)}") ?: continue
            try {
                val data = JSONObject(body).optJSONArray("data") ?: continue
                for (i in 0 until data.length()) {
                    val o = data.optJSONObject(i) ?: continue
                    val d = o.optDouble("duration", Double.NaN)
                    if (!duration.isNaN() && !d.isNaN() && Math.abs(d - duration) > 8.0) continue
                    val t = o.optString("title_short", "").ifBlank { o.optString("title", "") }.trim()
                    val a = o.optJSONObject("artist")?.optString("name", "")?.trim().orEmpty()
                    if (t.isNotBlank() && a.isNotBlank()) out.add(a to t)
                    if (out.size >= 3) return out.toList()
                }
            } catch (_: Exception) {
            }
            if (out.isNotEmpty()) break
        }
        return out.toList()
    }

    private fun ovh(artist: String, track: String): Result? {
        val body = get("https://api.lyrics.ovh/v1/${enc(artist).replace("+", "%20")}/${enc(track).replace("+", "%20")}") ?: return null
        return try {
            var l = JSONObject(body).str("lyrics") ?: return null
            l = l.replace("\r\n", "\n").replace(Regex("""^Paroles de la chanson[^\n]*\n+"""), "").trim()
            if (l.length < 40) null else Result(l, null)
        } catch (_: Exception) {
            null
        }
    }

    private fun netease(query: String, duration: Double): Result? {
        val hdr = mapOf("Referer" to "https://music.163.com")
        val sb = get(
            "https://music.163.com/api/search/get", hdr,
            "s=${enc(query)}&type=1&offset=0&limit=5"
        ) ?: return null
        try {
            val songs = JSONObject(sb).optJSONObject("result")?.optJSONArray("songs") ?: return null
            for (i in 0 until songs.length()) {
                val o = songs.optJSONObject(i) ?: continue
                val d = o.optDouble("duration", Double.NaN) / 1000.0
                if (duration.isNaN() || d.isNaN() || Math.abs(d - duration) > 5.0) continue
                val id = o.optLong("id", 0)
                if (id == 0L) continue
                val lb = get("https://music.163.com/api/song/lyric?id=$id&lv=1&kv=1&tv=-1", hdr) ?: continue
                val lrc = JSONObject(lb).optJSONObject("lrc")?.str("lyric") ?: continue
                val clean = lrc.lines().filterNot {
                    Regex("""^\[[\d:.]+]\s*(作词|作曲|编曲|制作|混音|Lyrics by|Composer)""").containsMatchIn(it)
                }.joinToString("\n")
                if (clean.count { it == '\n' } < 4) continue
                val plain = stripTimestamps(clean).trim()
                if (plain.length < 40) continue
                return Result(plain, if (Regex("""\[\d+:\d+""").containsMatchIn(clean)) clean else null)
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun stripTimestamps(s: String): String =
        s.replace(Regex("""^\[[^\]]*]\s*""", RegexOption.MULTILINE), "")

    private fun cleanUploader(u: String): String =
        u.replace(Regex("""(?i)\s*-\s*topic$|vevo$|\s*official(\s*channel)?$|\s*رسمي$"""), "").trim()

    fun cleanTitle(t: String): String {
        var s = t.substringBefore(" | ")
        // نشيل التشكيل والتطويل
        s = s.replace(Regex("""[\u064B-\u065F\u0670\u0640]"""), "")
        s = s.replace(Regex("""[\(\[\{][^\)\]\}]*[\)\]\}]"""), " ")
        s = s.replace(
            Regex(
                """(?i)\b(official\s*(music\s*)?(video|audio)|lyric(s)?\s*video|lyrics?|audio|hd|hq|4k|mv|video\s*clip|full\s*song|ft\.?|feat\.?|featuring)\b"""
            ),
            " "
        )
        // كلمات عربي شائعة في عناوين اليوتيوب
        s = s.replace(
            Regex(
                """(فيديو\s*كليب|كليب\s*رسمي|الكليب\s*الرسمي|كليب|الفيديو\s*الرسمي|النسخة\s*الاصلية|النسخه\s*الاصليه|حصريا|حصريًا|بالكلمات|مع\s*الكلمات|كلمات|بدون\s*موسيقى|اغنية|أغنية|الاغنية|الأغنية)"""
            ),
            " "
        )
        s = s.replace(Regex("""[\"'“”«»]"""), " ")
        return s.replace(Regex("""\s+"""), " ").trim().trim('-', ' ', '–', '—')
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
        // نقل ذري قدر الإمكان: الأصل ميتمسحش غير بعد ما النقل ينجح
        return try {
            try {
                java.nio.file.Files.move(
                    tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                java.nio.file.Files.move(
                    tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
            true
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }
}
