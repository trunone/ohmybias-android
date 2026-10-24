package info.plateaukao.ohmybias.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/// CINM 編譯／讀取 — 重點在真實字表規模（chars 區遠超過舊 u16 offset 上限）。
class CINCompilerTest {

    init { TestEnv.touch() }

    private fun codeFor(i: Int) = "" + ('a' + i / 676) + ('a' + (i / 26) % 26) + ('a' + i % 26)

    private fun charsFor(i: Int, n: Int) =
        (0 until n).map { j -> String(Character.toChars(0x4E00 + (i * 7 + j) % 20000)) }

    private fun writeCin(entries: Int, charsPer: Int): File {
        val sb = StringBuilder("%cname 測試\n%selkey 1234567890\n%chardef begin\n")
        for (i in 0 until entries) {
            for (c in charsFor(i, charsPer)) sb.append(codeFor(i)).append('\t').append(c).append('\n')
        }
        sb.append("%chardef end\n")
        val f = File.createTempFile("cintest", ".cin")
        f.writeText(sb.toString(), Charsets.UTF_8)
        return f
    }

    /// 真正的嘸蝦米字表 chars 區有數十萬個 code point。val index 的 offset 舊格式只有
    /// u16，超過 65,535 之後整條尾巴都讀到別的字 —— 而且照樣回報「編譯成功」。
    @Test
    fun charOffsetBeyondU16() {
        val entries = 800
        val charsPer = 100  // 80,000 code point，遠超 u16 上限
        val cin = writeCin(entries, charsPer)
        try {
            val table = CINTable()
            table.load(cin.path)
            assertTrue("字表應載入", !table.isEmpty)
            // 頭、中、尾都要對 —— 尾端正是舊格式溢位後讀到錯字的區段
            for (i in listOf(0, 1, entries / 2, entries - 2, entries - 1)) {
                assertEquals("第 $i 筆（碼 ${codeFor(i)}）", charsFor(i, charsPer), table.lookup(codeFor(i)))
            }
        } finally {
            cin.delete()
        }
    }

    /// 小表（offset 存得下 u16）行為不變 —— 保留舊檔／iOS 產出的 .bin 相容性
    @Test
    fun smallTableUnchanged() {
        val cin = writeCin(entries = 20, charsPer = 3)
        try {
            val table = CINTable()
            table.load(cin.path)
            for (i in 0 until 20) {
                assertEquals(charsFor(i, 3), table.lookup(codeFor(i)))
            }
        } finally {
            cin.delete()
        }
    }


    /// 反查表（查碼提示／注音同音字）走同一條 readChars —— 尾端也不能讀到錯字
    @Test
    fun reverseLookupAcrossOverflowBoundary() {
        val entries = 800
        val cin = writeCin(entries, charsPer = 100)
        try {
            val table = CINTable()
            table.load(cin.path)
            val last = entries - 1
            val lastChar = charsFor(last, 100).first()
            assertTrue("尾端的字要能反查回自己的碼", codeFor(last) in table.reverseLookup(lastChar))
        } finally {
            cin.delete()
        }
    }
}
