package info.plateaukao.ohmybias.android

import android.app.Application
import info.plateaukao.ohmybias.keyboard.CollectionData
import info.plateaukao.ohmybias.shared.AppEnv
import java.io.File

/// Application：初始化共享目錄、複製 assets 資料檔、掛上偏好與剪貼簿橋接。
/// IME service 與設定 Activity 同一 process — 這裡是唯一入口。
class OhMyBiasApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppEnv.sharedDir = File(filesDir, "shared").path
        AppEnv.ensureDirs()
        copyAssetsIfNeeded()
        Prefs.install(this)
        // 首次啟動：語言模式歸零成嘸蝦米 — 偏好可能從自動備份還原帶著舊的英文狀態
        if (!Prefs.firstLaunchDone) {
            Prefs.resetToChineseMode()
            Prefs.firstLaunchDone = true
        }
        ClipboardProcessor.install(this)
        CollectionData.install(assets)   // 面板資料改讀 assets/collections.txt（省 ~90 KB dex）
    }

    /// assets → sharedDir（引擎層統一走檔案路徑）。
    /// assets 只會隨 APK 安裝/更新而變 — 記住 lastUpdateTime，未變且檔案都在就整段跳過，
    /// 不必每次啟動把六個資料檔全部讀進 heap 比對（取法 sweetlime 的延遲/一次性資料庫複製）
    private fun copyAssetsIfNeeded() {
        val names = listOf(
            "liu.bin", "phrases.bin", "zhuyin_data.bin", "pinyin_data.bin",
            "char_freq.bin", "t2s.json", "s2t.json",
            "default_skin.json",   // 內建預設主題 — SkinSettings 未匯入時的 fallback
        )
        val stamp = try {
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        } catch (e: Exception) { 0L }
        val prefs = getSharedPreferences("asset_sync", MODE_PRIVATE)
        if (prefs.getLong("apk_stamp", -1L) == stamp &&
            names.all { File(AppEnv.sharedDir, it).exists() }
        ) return
        var allOk = true
        for (name in names) {
            try {
                val dst = File(AppEnv.sharedDir, name)
                assets.open(name).use { input ->
                    dst.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                allOk = false
            }
        }
        // 全數成功才記 stamp — 失敗的下次啟動會重試
        if (allOk) prefs.edit().putLong("apk_stamp", stamp).apply()
    }
}
