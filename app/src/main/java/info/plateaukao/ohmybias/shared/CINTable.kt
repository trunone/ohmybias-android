package info.plateaukao.ohmybias.shared

import org.json.JSONObject
import java.io.File
import java.util.UUID

/// 嘸蝦米二進位字表（CINM 格式）— 以 mmap 載入（零複製）。
///
/// 執行緒安全：字表狀態全收在 [Snap] 裡，載入端在區域變數上組好一整份才發佈
/// （@Volatile 單次指派），讀取端一開始就把 snap 抓成區域變數、之後只讀那一份。
class CINTable {

    companion object {
        /// 字表世代 —— 鍵盤與設定頁同 process，service 據此在重載時識別世代。
        @Volatile
        var generation: Int = 0
            private set

        fun bumpGeneration() {
            generation += 1
        }
    }

    // MARK: - 不可變快照

    /// 一份完整的字表。只有載入期間（還沒發佈、只有一條執行緒看得到）才寫欄位，
    /// 發佈給 [snap] 之後一律只讀 —— 所以下面的查詢方法在任何執行緒上都安全。
    private class Snap {
        // mmap 二進位（liu.bin）
        var binData: BinData? = null
        var entryCount = 0
        var codesOff = 0
        var valsOff = 0
        var stringsOff = 0
        var charsOff = 0

        /// 已載入 .bin 的 CINM 格式版本（見 CINCompiler.FORMAT_VERSION）
        var binFormatVersion = 0

        // 文字 fallback + overlay（extras — 小型 dict）
        var overlay: MutableMap<String, MutableList<String>> = HashMap()

        /// 常用語自訂組字碼（UserPhrases）— 與字表分開存：查詢時排在既有候選之後，
        /// 不參與字頻排序，使用者設了跟字表撞碼的捷徑也不會把原本的字擠掉
        var shortcuts: Map<String, List<String>> = emptyMap()

        var t2s: Map<String, String> = emptyMap()
        var s2t: Map<String, String> = emptyMap()
        var selKeys: List<Char> = "1234567890".toList()
        var cinName: String = ""
        var maxCodeLength: Int = 4

        val isEmpty: Boolean get() = entryCount == 0 && overlay.isEmpty()

        // MARK: 反查建表

        fun buildReverseTable(): Map<String, List<String>> {
            val r = HashMap<String, MutableList<String>>()
            val d = binData
            if (d != null) {
                for (i in 0 until entryCount) {
                    val code = readCode(d, i)
                    for (ch in readChars(d, i)) r.getOrPut(ch) { mutableListOf() }.add(code)
                }
            }
            for ((code, chars) in overlay) for (c in chars) r.getOrPut(c) { mutableListOf() }.add(code)
            return r
        }

        // MARK: 二進位 helpers

        fun readCode(d: BinData, i: Int): String {
            val off = d.u32(codesOff + i * 6).toInt()
            val len = d.u16(codesOff + i * 6 + 4)
            val start = stringsOff + off
            if (start < 0 || start + len > d.count) return ""
            return d.asciiString(start, len)
        }

        fun readChars(d: BinData, i: Int): List<String> {
            val entryOff = valsOff + i * 4
            if (entryOff < 0 || entryOff + 4 > d.count) return emptyList()
            // offset 是 u24：低 16 位在前兩 byte、高 8 位在第 4 byte（見 CINCompiler）
            val vOff = d.u16(entryOff) or (d.u8(entryOff + 3) shl 16)
            val vCnt = d.u8(entryOff + 2)
            if (vCnt <= 0) return emptyList()
            val firstOff = charsOff + vOff * 4
            val lastOff = charsOff + (vOff + vCnt - 1) * 4 + 4
            if (firstOff < charsOff || lastOff > d.count) return emptyList()
            val r = ArrayList<String>(vCnt)
            for (j in 0 until vCnt) {
                val cp = d.u32(charsOff + (vOff + j) * 4).toInt()
                if (Character.isValidCodePoint(cp)) r.add(String(Character.toChars(cp)))
            }
            return r
        }

        private fun binSearch(code: String): Int {
            val d = binData ?: return -1
            if (entryCount <= 0) return -1
            val target = code.toByteArray(Charsets.UTF_8)
            var lo = 0; var hi = entryCount - 1
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                val cmp = compareCode(d, mid, target)
                when {
                    cmp == 0 -> return mid
                    cmp < 0 -> lo = mid + 1
                    else -> hi = mid - 1
                }
            }
            return -1
        }

        private fun lowerBound(prefix: String): Int {
            val d = binData ?: return 0
            if (entryCount <= 0) return 0
            val target = prefix.toByteArray(Charsets.UTF_8)
            var lo = 0; var hi = entryCount
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (comparePrefixCode(d, mid, target) < 0) lo = mid + 1 else hi = mid
            }
            return lo
        }

        private fun compareCode(d: BinData, i: Int, target: ByteArray): Int {
            val idxOff = codesOff + i * 6
            if (idxOff < 0 || idxOff + 6 > d.count) return -1
            val off = stringsOff + d.u32(idxOff).toInt()
            val len = d.u16(idxOff + 4)
            if (off < 0 || off + len > d.count) return -1
            for (j in 0 until len) {
                if (j >= target.size) return 1
                val a = d.u8(off + j)
                val b = target[j].toInt() and 0xFF
                if (a != b) return a - b
            }
            if (len < target.size) return -1
            return 0
        }

        private fun comparePrefixCode(d: BinData, i: Int, prefix: ByteArray): Int {
            val idxOff = codesOff + i * 6
            if (idxOff < 0 || idxOff + 6 > d.count) return -1
            val off = stringsOff + d.u32(idxOff).toInt()
            val len = d.u16(idxOff + 4)
            if (off < 0 || off + len > d.count) return -1
            val n = minOf(len, prefix.size)
            for (j in 0 until n) {
                val a = d.u8(off + j)
                val b = prefix[j].toInt() and 0xFF
                if (a != b) return a - b
            }
            if (len < prefix.size) return -1
            return 0
        }

        private fun codeHasPrefix(d: BinData, i: Int, prefix: ByteArray): Boolean {
            val idxOff = codesOff + i * 6
            if (idxOff < 0 || idxOff + 6 > d.count) return false
            val off = stringsOff + d.u32(idxOff).toInt()
            val len = d.u16(idxOff + 4)
            if (off < 0 || off + len > d.count) return false
            if (len < prefix.size) return false
            for (j in prefix.indices) {
                if (d.u8(off + j) != (prefix[j].toInt() and 0xFF)) return false
            }
            return true
        }

        // MARK: 查詢

        fun lookup(code: String): List<String> {
            val c = code.lowercase()
            var result: List<String> = emptyList()
            val idx = binSearch(c)
            val d = binData
            if (idx >= 0 && d != null) result = readChars(d, idx)
            overlay[c]?.let { extra -> result = extra + result }
            return result
        }

        /// 常用語捷徑 — 不含字表本身的候選（見 [shortcuts]）
        fun shortcutLookup(code: String): List<String> = shortcuts[code.lowercase()] ?: emptyList()

        fun hasPrefix(prefix: String): Boolean {
            val p = prefix.lowercase()
            val d = binData
            if (d != null) {
                val i = lowerBound(p)
                if (i < entryCount && codeHasPrefix(d, i, p.toByteArray(Charsets.UTF_8))) return true
            }
            return overlay.keys.any { it.startsWith(p) } || shortcuts.keys.any { it.startsWith(p) }
        }

        fun validNextKeys(after: String): Set<Char> {
            val p = after.lowercase()
            val result = HashSet<Char>()
            val pBytes = p.toByteArray(Charsets.UTF_8)
            val pLen = pBytes.size
            val d = binData
            if (d != null) {
                val start = lowerBound(p)
                for (i in start until entryCount) {
                    if (!codeHasPrefix(d, i, pBytes)) break
                    val codeLen = d.u16(codesOff + i * 6 + 4)
                    if (codeLen > pLen) {
                        val off = stringsOff + d.u32(codesOff + i * 6).toInt()
                        if (off + pLen >= d.count) continue
                        result.add(d.u8(off + pLen).toInt().toChar())
                    }
                }
            }
            for (key in overlay.keys) {
                if (key.startsWith(p) && key.length > p.length) result.add(key[p.length])
            }
            for (key in shortcuts.keys) {
                if (key.startsWith(p) && key.length > p.length) result.add(key[p.length])
            }
            return result
        }

        fun wildcardLookup(pattern: String): List<String> {
            val pat = pattern.lowercase()
            if (!pat.contains("*")) return lookup(pat)
            // 逐字元 escape、* → .+（對應 iOS 的 escapedPattern + replacingOccurrences）
            val sb = StringBuilder("^")
            for (ch in pat) {
                if (ch == '*') sb.append(".+")
                else if (ch.isLetterOrDigit()) sb.append(ch)
                else sb.append(Regex.escape(ch.toString()))
            }
            sb.append("$")
            val re = try { Regex(sb.toString()) } catch (e: Exception) { return emptyList() }
            val fix = pat.takeWhile { it != '*' }
            val results = ArrayList<String>()
            val seen = HashSet<String>()
            val d = binData
            if (d != null) {
                val start = if (fix.isEmpty()) 0 else lowerBound(fix)
                val fixBytes = fix.toByteArray(Charsets.UTF_8)
                for (i in start until entryCount) {
                    if (fix.isNotEmpty() && !codeHasPrefix(d, i, fixBytes)) break
                    val code = readCode(d, i)
                    if (re.matches(code)) {
                        for (c in readChars(d, i)) if (seen.add(c)) results.add(c)
                    }
                }
            }
            for ((code, chars) in overlay) {
                if (fix.isNotEmpty() && !code.startsWith(fix)) continue
                if (re.matches(code)) {
                    for (c in chars) if (seen.add(c)) results.add(c)
                }
            }
            return results
        }
    }

    @Volatile private var snap = Snap()

    // MARK: - 反查快取（lazy；可由背景執行緒預熱）
    //
    // 建表要走遍整張表，成本高 —— 只用 buildLock 序列化建表，發佈快照的 reload()
    // 完全不碰這把鎖，主執行緒不會卡在背景預熱後面（IME 主執行緒卡住＝ANR）。
    // 快取以「當時那份 Snap 的參考」認親：字表換掉就整組作廢，慢一步算完的舊結果
    // 也不會被寫上去（否則會一直給舊表的字根提示）。
    private val buildLock = Any()

    @Volatile private var cacheOwner: Snap? = null
    @Volatile private var _reverseTable: Map<String, List<String>>? = null
    @Volatile private var _shortestCodes: Map<String, Set<String>>? = null
    @Volatile private var _longestCodes: Map<String, Set<String>>? = null

    private val reverseTable: Map<String, List<String>>
        get() {
            val s = snap
            if (cacheOwner === s) _reverseTable?.let { return it }
            if (!MemoryBudget.canAfford(MemoryBudget.reverseTable)) return emptyMap()
            synchronized(buildLock) {
                val cur = snap
                if (cacheOwner !== cur) resetCaches(cur)
                _reverseTable?.let { return it }
                val r = cur.buildReverseTable()
                if (snap === cur) _reverseTable = r
                return r
            }
        }

    /// 整表反查建立成本高（走遍所有 entry）— service 啟動後在背景執行緒呼叫，
    /// 免得第一次用到（查碼提示/注音同音字/,,SC）的那個按鍵卡住
    fun warmUpReverseCache() {
        reverseTable
    }

    val shortestCodesTable: Map<String, Set<String>>
        get() {
            if (cacheOwner === snap) _shortestCodes?.let { return it }
            val rev = reverseTable
            synchronized(buildLock) {
                val cur = snap
                if (cacheOwner !== cur) resetCaches(cur)
                _shortestCodes?.let { return it }
                val r = HashMap<String, Set<String>>()
                for ((ch, codes) in rev) {
                    val m = codes.minOfOrNull { it.length } ?: 0
                    r[ch] = codes.filter { it.length == m }.toSet()
                }
                if (snap === cur) _shortestCodes = r
                return r
            }
        }

    val longestCodesTable: Map<String, Set<String>>
        get() {
            if (cacheOwner === snap) _longestCodes?.let { return it }
            val rev = reverseTable
            synchronized(buildLock) {
                val cur = snap
                if (cacheOwner !== cur) resetCaches(cur)
                _longestCodes?.let { return it }
                val r = HashMap<String, Set<String>>()
                for ((ch, codes) in rev) {
                    val m = codes.maxOfOrNull { it.length } ?: 0
                    r[ch] = codes.filter { it.length == m }.toSet()
                }
                if (snap === cur) _longestCodes = r
                return r
            }
        }

    private fun resetCaches(owner: Snap) {
        _reverseTable = null
        _shortestCodes = null
        _longestCodes = null
        cacheOwner = owner
    }

    val t2s: Map<String, String> get() = snap.t2s
    val s2t: Map<String, String> get() = snap.s2t
    val selKeys: List<Char> get() = snap.selKeys
    val cinName: String get() = snap.cinName
    val isEmpty: Boolean get() = snap.isEmpty
    val maxCodeLength: Int get() = snap.maxCodeLength

    fun releaseOptionalCaches() {
        resetCaches(snap)
    }

    /// 整份原子替換 —— 讀取端要嘛看到舊的完整快照、要嘛看到新的完整快照，沒有中間態。
    private fun publish(s: Snap) {
        snap = s
        resetCaches(s)
    }

    // MARK: - 載入

    fun reload() {
        val s = Snap()

        // 1. sharedDir 的 mmap 二進位（liu.bin）
        val userBin = AppEnv.sharedDir + "/liu.bin"
        if (File(userBin).exists()) loadBin(userBin, s)

        // 2. Extras
        loadExtras(s)
        // 3. 簡繁對照表
        loadCharMaps(s)
        // 4. 常用語自訂組字碼
        s.shortcuts = UserPhrases.shared.shortcuts
        // 5. maxCodeLength
        recomputeMaxCodeLength(s)

        publish(s)
    }

    /// 從 .bin 二進位檔載入 — 測試與特化需求
    fun loadBinFile(binPath: String) {
        val s = Snap()
        if (File(binPath).exists()) loadBin(binPath, s)
        s.shortcuts = UserPhrases.shared.shortcuts
        recomputeMaxCodeLength(s)
        publish(s)
    }

    /// 從 .cin 文字檔編譯並載入 — 測試輔助
    fun load(cinPath: String) {
        val s = Snap()
        val tmp = (System.getProperty("java.io.tmpdir") ?: "/tmp") + "/cin_" + UUID.randomUUID() + ".bin"
        CINCompiler.compile(cinPath, tmp)
        val f = File(tmp)
        if (f.exists()) {
            BinData.of(f)?.let { parseBinData(it, s) }
            f.delete()
        }
        s.shortcuts = UserPhrases.shared.shortcuts
        recomputeMaxCodeLength(s)
        publish(s)
    }

    private fun recomputeMaxCodeLength(s: Snap) {
        var maxLen = 4
        val d = s.binData
        if (d != null) {
            for (i in 0 until s.entryCount) {
                val len = d.u16(s.codesOff + i * 6 + 4)
                if (len > maxLen) maxLen = len
            }
        }
        for (k in s.overlay.keys) if (k.length > maxLen) maxLen = k.length
        for (k in s.shortcuts.keys) if (k.length > maxLen) maxLen = k.length
        s.maxCodeLength = maxLen
    }

    // MARK: - 二進位載入

    private fun loadBin(path: String, s: Snap) {
        val d = BinData.mapped(path) ?: return
        parseBinData(d, s)
    }

    private fun parseBinData(d: BinData, s: Snap) {
        if (d.count < 128 || d.u8(0) != 0x43 || d.u8(1) != 0x49 || d.u8(2) != 0x4E || d.u8(3) != 0x4D) return
        s.entryCount = d.u32(4).toInt()
        val skLen = d.u8(8)
        if (skLen in 1..20 && 9 + skLen <= d.count) {
            s.selKeys = (0 until skLen).map { d.u8(9 + it).toInt().toChar() }
        }
        val cnLen = d.u16(20)
        if (cnLen > 0 && 22 + cnLen <= d.count) s.cinName = d.utf8String(22, cnLen)
        s.binFormatVersion = d.u32(88).toInt()
        s.codesOff = d.u32(96).toInt()
        s.valsOff = d.u32(100).toInt()
        s.stringsOff = d.u32(104).toInt()
        s.charsOff = d.u32(108).toInt()
        if (s.codesOff < 128 || s.codesOff >= s.valsOff || s.valsOff >= s.stringsOff ||
            s.stringsOff >= s.charsOff || s.charsOff > d.count
        ) {
            s.entryCount = 0; return
        }
        s.binData = d
    }


    // MARK: - Extras + 簡繁對照

    private fun loadExtras(s: Snap) {
        val dir = File(AppEnv.tablesDir)
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (!file.name.endsWith(".txt")) continue
            val content = try { file.readText(Charsets.UTF_8) } catch (e: Exception) { continue }
            for (line in content.lineSequence()) {
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("#")) continue
                val parts = t.split("\t", limit = 2)
                if (parts.size != 2) continue
                s.overlay.getOrPut(parts[0].lowercase()) { mutableListOf() }.add(parts[1])
            }
        }
    }

    private fun loadCharMaps(s: Snap) {
        s.t2s = loadCharMap("t2s") ?: emptyMap()
        s.s2t = loadCharMap("s2t") ?: emptyMap()
    }

    private fun loadCharMap(name: String): Map<String, String>? {
        val f = File(AppEnv.sharedDir, "$name.json")
        if (!f.exists()) return null
        return try {
            val json = JSONObject(f.readText(Charsets.UTF_8))
            val r = HashMap<String, String>()
            for (key in json.keys()) r[key] = json.getString(key)
            r
        } catch (e: Exception) {
            null
        }
    }

    // MARK: - 查詢（公開 API）

    fun lookup(code: String): List<String> = snap.lookup(code)

    /// 常用語自訂組字碼的詞（不含字表候選）。撞碼時引擎把這些排在字表候選之後。
    fun shortcutLookup(code: String): List<String> = snap.shortcutLookup(code)

    fun hasPrefix(prefix: String): Boolean = snap.hasPrefix(prefix)

    fun validNextKeys(after: String): Set<Char> = snap.validNextKeys(after)

    fun wildcardLookup(pattern: String): List<String> = snap.wildcardLookup(pattern)

    fun reverseLookup(char: String): List<String> = reverseTable[char] ?: emptyList()

    fun convert(char: String, map: Map<String, String>): String = map[char] ?: char
}
