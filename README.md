<p align="center">
  <img src="app/src/main/play/listings/zh-TW/graphics/icon/1.png" width="128" alt="OhMyBias 米">
</p>

# OhMyBias 米 Android

Android 嘸蝦米（Boshiamy）鍵盤 — [ohmybias-ios](https://github.com/plateaukao/ohmybias-ios) 的 Android 移植版
（引擎層一對一對應，源自 [Yabomish](https://github.com/plateaukao/yabomish)）。
純 Kotlin、零第三方執行期依賴。

<p align="center">
  <img src="docs/keyboard.png" width="560" alt="鍵盤字母頁 — 鍵帽角標、滑動提示與工具列">
</p>

## 特色

### 輸入引擎

- **嘸蝦米輸入**：內建嘸蝦米二進位字表（`liu.bin`，由 [cin-tables](https://github.com/chinese-opendesktop/cin-tables) 的 `boshiamy.cin` 編譯，採用 CINM 高效 mmap 格式內嵌）
- **基本聯想詞**：commit 後即出現詞組聯想（萌典詞組，CC0，僅 687KB）＋自訂詞（user_phrases.txt）；聯想列開頭有 ✕ 可一鍵關閉
- **`,,` 指令**：`,,T/S/J` 切模式、`,,ZH` 注音查碼、`,,TO` 同音字、`,,PYS/PYT` 拼音查碼、`,,SG` 聯想開關、`,,PIN` 固定同碼字排序、`,,V/VT/VS` 剪貼簿（ICU 簡繁轉換）、`,,H` 說明
- **極簡資料**：不含專業詞典／語料 binary（<2MB）

### 鍵盤介面（以 sweetlime 皮膚為藍本，與 iOS 版一致）

- **滑動手勢**：鍵帽角標提示 — 上滑出符號、下滑出數字；`n`/`m` 次選、三選直接上屏；空白上滑切中英、水平拖曳移動游標；Enter 上滑跳注音頁；`z`/`m` 下滑句首句尾、`v` 下滑貼上
- **長按選單**：字母大小寫／變音變體、逗號句號長按插入日期時間（中文／民國／日本／英文／農曆／時區，android.icu 曆法）
- **工具列與面板**：設定、♥ 常用語面板、符號／Emoji／顏文字分類面板、語言鍵顯示目前模式（米／英）、游標左右、貼上、收折、**浮動鍵盤**（鍵盤浮在畫面上，底部把手拖曳移動、四角縮放，再按一次停回底部）；cskin 可配置的編輯按鈕全支援 — 全選／複製／剪下／復原／重做／簡繁切換（iOS 版因 extension API 缺失無法支援的部分，Android 依原始定義實作）。設定頁「主題 ▸ 自訂工具列」可直接在手機上排 10 格
- **多頁鍵盤**：字母、九宮格數字、Row 數字半形符號、全形符號、注音查碼
- **主題**：淺色／深色調色盤；可匯入 `.cskin` 皮膚設定（工具列、調色盤、字級、版面選項）
- **記住中英模式**：鍵盤重啟後還原上次輸入模式
- **常用語組字碼**：常用語設定頁可為每句設自訂組字碼，直接打碼叫出、不必開 ♥ 面板；碼已被字表使用時本句排在既有候選之後（設定時即時提示）。密碼類欄位自動暫切英文直通
- **鍵盤高度調整**：設定頁滑桿 85–140%（鍵面字級連動放大，上限 1.2×）— 大螢幕手機可調大

## 安裝

1. `./gradlew assembleDebug` 後安裝 `app/build/outputs/apk/debug/app-debug.apk`
2. 系統設定 → 螢幕鍵盤 → 啟用「OhMyBias 米」，並切換為目前輸入法

## 開發

```bash
./gradlew assembleDebug        # 建置 APK
./gradlew testDebugUnitTest    # 引擎測試（JVM，無需模擬器）
```

## 專案結構

```
app/src/main/java/info/plateaukao/ohmybias/
  shared/      # 平台無關引擎（對應 iOS Shared/；禁止 Android API）
  keyboard/    # IME service、鍵盤五頁、候選列、面板、主題、手勢
  android/     # SharedPreferences、剪貼簿（ICU）、Application
  MainActivity # 設定頁（匯入字表/皮膚、偏好、自訂詞、測試輸入）
app/src/main/assets/   # liu.bin（嘸蝦米）、phrases.bin（萌典）、s2t/t2s、zhuyin/pinyin/char_freq（mmap 二進位）
tools/                 # 資料檔產生腳本（JSON → mmap 二進位）
app/src/test/          # JUnit 引擎測試（移植自 iOS Tests/）
```

## 嘸蝦米字表二進位格式 (CINM)

本 App 將 `boshiamy.cin` 文字表格預先編譯為 CINM 二進位格式檔（`liu.bin`），透過 memory-mapped file (mmap) 實現零拷貝、極速載入與 $O(\log N)$ 查詢。

### 檔案結構 (128-byte Header + 3 區塊)
- **Header（128 bytes）**：
  - `+0` (4 bytes ASCII): Magic `"CINM"`
  - `+4` (u32 LE): 條目總數 $N$
  - `+8` (u8): 選字鍵長度 $K$（如 `1234567890`）
  - `+9` ($K$ bytes ASCII): 選字鍵字元
  - `+20` (u16 LE): 字表名稱長度
  - `+22` (UTF-8): 字表名稱（`cname`）
  - `+88` (u32 LE): 格式版本（1）
  - `+96` / `+100` / `+104` / `+108` (u32 LE): `codesOff`, `valsOff`, `stringsOff`, `charsOff` 位移
- **Code Index 區 ($N \times 6$ bytes)**：每筆存 `u32 LE` 位移（於 Strings 區）與 `u16 LE` 字碼長度。條目按 ASCII 字典序排序。
- **Val Index 區 ($N \times 4$ bytes)**：每筆存 `u16 LE` 低 16 位位移、`u8` 字數 $C$、`u8` 高 8 位位移（組合為 u24 位移，於 Chars 區）。
- **Strings 區**：ASCII 鍵字串串接。
- **Chars 區**：UTF-32 LE Unicode Code Points 陣列。

### 查詢演算法
1. **字碼搜尋**：對 Code Index 區進行二分搜尋（Binary Search），以 ASCII 序比對 Strings 區的字碼。
2. **候選字讀取**：命中後由 Val Index 區取得 24-bit 偏移量與字數 $C$，直接從 Chars 區以 4-byte 步進讀出對應的 UTF-32 Code Point。

## 資料來源與授權

| 檔案 | 來源 | 授權 |
|------|------|------|
| `liu.bin` | [cin-tables (boshiamy.cin)](https://github.com/chinese-opendesktop/cin-tables) | Free for non-commercial use |
| `phrases.bin` | [萌典](https://www.moedict.tw/)（教育部國語辭典） | CC0 |
| `zhuyin_data.bin` / `pinyin_data.bin` / `char_freq.bin` | Yabomish 上游整理（tools/gen_data_bins.py 轉製） | 同上游 |
| `s2t.json` / `t2s.json` | Yabomish 上游整理 | 同上游 |
| 鍵盤版面／符號分類 | Hamster 2 皮膚「蝦米輸入法」（sweetlime.cskin，作者 Ryan）移植 | 見原皮膚 |
