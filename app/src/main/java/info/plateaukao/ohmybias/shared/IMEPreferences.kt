package info.plateaukao.ohmybias.shared

/// 對應 iOS IMEPreferences — 引擎層與具體偏好儲存（SharedPreferences）解耦，
/// 測試可注入 test-double。
interface IMEPreferences {
    val suggestEnabled: Boolean
    val autoCommit: Boolean
    val overflowAutoCommit: Boolean
    val fuzzyMatch: Boolean
    val showCodeHint: Boolean
    val suggestStrategy: String
    val wordCorpus: String
    val charSuggest: Boolean
    val regionVariant: String
    fun domainEnabled(key: String): Boolean
    fun domainPriority(key: String): Int
    val punctuationPairing: Boolean
    /// 同音字查詢包含多音字的罕見讀音（iOS 為 OhMyBiasPrefs 靜態存取；此處併入協定）
    val homophoneMultiReading: Boolean
    /// 中文（米）模式字母鍵與組字區是否顯示大寫
    val uppercaseLettersInChinese: Boolean
}

/// 預設偏好單例 — Android 啟動時由 Prefs 層替換 backing；JVM 測試用預設值。
object DefaultPreferences : IMEPreferences {
    /// Android 端啟動時指向 SharedPreferences 實作；null 時用內建預設
    var backing: IMEPreferences? = null

    override val suggestEnabled get() = backing?.suggestEnabled ?: true
    override val autoCommit get() = backing?.autoCommit ?: false
    override val overflowAutoCommit get() = backing?.overflowAutoCommit ?: false
    override val fuzzyMatch get() = backing?.fuzzyMatch ?: true
    override val showCodeHint get() = backing?.showCodeHint ?: false
    override val suggestStrategy get() = backing?.suggestStrategy ?: "general"
    override val wordCorpus get() = backing?.wordCorpus ?: "moedict"
    override val charSuggest get() = backing?.charSuggest ?: true
    override val regionVariant get() = backing?.regionVariant ?: "tw"
    override fun domainEnabled(key: String) = backing?.domainEnabled(key) ?: (key == "domain_phrases")
    override fun domainPriority(key: String) = backing?.domainPriority(key) ?: 0
    override val punctuationPairing get() = backing?.punctuationPairing ?: true
    override val homophoneMultiReading get() = backing?.homophoneMultiReading ?: false
    override val uppercaseLettersInChinese get() = backing?.uppercaseLettersInChinese ?: false

    /// 聯想總開關寫入（`,,SG` 指令）— Android 層掛上 setter
    var setSuggestEnabled: (Boolean) -> Unit = {}
}
