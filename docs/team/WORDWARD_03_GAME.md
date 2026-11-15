# Wordward งาน 03 · กติกาการเดา คำใบ้ และเริ่มรอบวันนี้ใหม่

รายงานและเอกสารส่งมอบของสมาชิกคนที่ 3 (branch `member/03-game` · [Issue #3](https://github.com/Micosz/CS364FinalApplication/issues/3)) อ้างอิงขอบเขตจาก [ข้อตกลงทีมฉบับล่าสุด](TEAM_AGREEMENTS.md) (คลังคำหลักประมาณ **1,000 คำ** และ **31 โจทย์** ตามวัน `Asia/Bangkok` พร้อมฟีเจอร์ **เริ่มรอบวันนี้ใหม่**) และ [ใบงานคนที่ 3](03-game.md)

> **ขอบเขตไฟล์ที่แก้ในงานนี้**: `app/src/main/java/th/ac/tu/cs364/wordward/GameRound.java` และ `app/src/test/java/th/ac/tu/cs364/wordward/GameRoundTest.java` (ไม่แก้ไขไฟล์ของสมาชิกส่วนที่ 1, 2, 4, 5 และยังไม่เพิ่มฟีเจอร์ `Give up` หรือสถิติสะสมที่ทีมยังไม่ยืนยัน)

---

## 1. กลไกหลักของ `GameRound` (Rubric ข้อ 2.1)

`GameRound` ทำหน้าที่เป็นแหล่งตัดสินกติกาและสถานะของรอบเล่นประจำวัน (1 อินสแตนซ์ต่อ 1 วันที่ไทย `date`) โดยเก็บข้อมูลต้นทางใน `LinkedHashMap<String, Entry> history` และรักษาความสัมพันธ์ของยอดรวมที่ `attempts() == guesses() + hints()` เสมอ:

1. **การเดาคำ (`guess(String canonical, WordRanker.Ranking ranks)`)**
   - รับคำหลัก (`canonical`) ที่แปลงแล้วจาก `WordRanker` (หรือ `null` หากคำอยู่นอกคลัง) และตารางอันดับ `ranks` ของโจทย์วันนั้น (ผู้เรียกใช้ต้องส่ง `canonical` ที่มาจากคลังเดียวกับ `ranks` หรือ `null`)
   - ตรวจตามลำดับ: หาก `status != Status.PLAYING` คืน `GuessResult.CLOSED`; หาก `canonical == null || ranks == null` คืน `GuessResult.UNKNOWN`; หาก `seen(canonical)` เป็นจริง (เคยเดาหรือเคยได้จากคำใบ้แล้ว รวมถึงรูปพหูพจน์/alias ที่ชี้คำหลักเดียวกัน) คืน `GuessResult.REPEAT` โดยไม่เพิ่มยอด
   - หากผ่านทั้งหมด จึงเรียก `add(new Entry(canonical, ranks.rank(canonical), false))` และคืน `GuessResult.ADDED`
2. **จุดรับคำใบ้ (`boolean hint(String canonical, WordRanker.Ranking ranks)`) — ข้อเสนอสัญญาใหม่**
   - ตรวจในจุดเดียวว่า `status == Status.PLAYING`, `canonical != null`, `ranks != null`, `!seen(canonical)`, และอันดับต้องเป็น `rank > 1 && rank < best` (ห้ามเฉลยคำลับ `#1` และต้องดีกว่าอันดับดีที่สุดที่เคยเห็นในรอบนั้น; กรณีรอบว่าง `best` เริ่มที่ `Integer.MAX_VALUE` จึงรับคำใบ้แรกที่มี `rank >= 2` ได้)
   - หากไม่ผ่านเงื่อนไขใด ๆ จะคืน `false` โดยไม่แก้ประวัติ ยอด `latest` `best` หรือ `status` และหากผ่านจะเรียก `add(new Entry(canonical, rank, true))` และคืน `true`
3. **จุดเพิ่มและกู้รายการ (`void add(Entry entry)`)**
   - ตรวจสอบความถูกต้องของข้อมูลเข้า (`entry != null`, `entry.word != null`, `entry.rank >= 1`), สถานะรอบ (`status == Status.PLAYING`), ความไม่ซ้ำ (`!seen(entry.word)`), และกฎคำใบ้ (`!entry.isHint || (entry.rank > 1 && entry.rank < best)`) ก่อนบันทึก
   - อัปเดต `history.put(entry.word, entry)`, `latest = entry`, `best = Math.min(best, entry.rank)`, เพิ่ม `hints` เมื่อ `entry.isHint`, และเปลี่ยน `status = Status.WON` เมื่อ `entry.rank == 1`
   - ใช้ร่วมกันทั้งการรับคำเดา การรับคำใบ้ และการกู้ประวัติตามลำดับเวลาจาก `GameStorage.open(LocalDate)`
4. **การเริ่มรอบวันนี้ใหม่ (`GameRound reset()`) — ข้อเสนอสัญญาใหม่**
   - ล้าง `history.clear()`, รีเซ็ต `latest = null`, `hints = 0`, `best = Integer.MAX_VALUE`, และคืน `status = Status.PLAYING` โดย **คงค่า `final LocalDate date` เดิมไว้** และคืน `this`
   - ใช้ได้ทั้งระหว่าง `PLAYING` และหลัง `WON` ทำให้คำที่เคยเดาหรือเคยได้รับจากคำใบ้ในรอบก่อนถูกลบออกจาก `history` และสามารถนำกลับมาเดาใหม่ในรอบใหม่ได้ทันที
5. **การเข้าถึงผลแสดงและแถวซ้ำ**
   - `entries()` คืนรายการตามลำดับเวลาที่รับเข้าจริง (สำหรับบันทึก)
   - `sorted()` คืนรายการใหม่เรียงตาม `rank` จากน้อยไปมาก (สำหรับแสดงรายการใน `R.id.history`)
   - `entry(String word)` **(ข้อเสนอสัญญาเสริม)** คืน `Entry` เดิมจาก `history` เพื่อให้ผู้เรียกใช้ดึงข้อมูลแถวเดิมเมื่อผลเป็น `GuessResult.REPEAT`

---

## 2. ความท้าทายทางเทคนิค วิธีแก้ และผลทดสอบก่อน–หลังแก้ (Rubric ข้อ 2.2, 2.3, 2.4 และ 5.3)

### ปัญหาที่พบและสาเหตุ (Rubric 2.2)
1. **การรับคำใบ้ไม่ได้ถูกบังคับกติกาใน `GameRound`**: ในโค้ดฐาน `6988e93` จุดเพิ่มรายการ `add(Entry)` ตรวจเพียง `status != Status.PLAYING || seen(entry.word)` ทำให้หากมีการส่ง `Entry(secret, 1, true)` หรือ `Entry(word, worseRank, true)` เข้ามา คำใบ้อันดับ `#1` จะผ่านเข้าไปตั้ง `status = Status.WON` ทันที และคำใบ้ที่อันดับแย่กว่า `best` ก็ถูกนับรวมในยอด `hints`/`attempts` ด้วย อีกทั้งยังไม่มีเมธอด `hint(canonical, ranks)` สำหรับให้ผู้เรียกใช้รับคำใบ้อย่างปลอดภัย
2. **ยังไม่มีเมธอดรีเซ็ตรอบวันนี้ใหม่**: ยังไม่มีจุดล้างสถานะภายใน (`history`, `latest`, `hints`, `best`, `status`) ที่คงค่า `date` เดิมไว้เพื่อรองรับการเล่นโจทย์เดิมซ้ำและให้ส่วนจัดการเวลาสามารถตรวจวันของรอบเดิมได้

### วิธีแก้ที่นำไปใช้จริง (Rubric 2.3)
ในไฟล์ `app/src/main/java/th/ac/tu/cs364/wordward/GameRound.java`:
- เพิ่มการตรวจ `entry == null || entry.word == null || entry.rank < 1` (โยน `IllegalArgumentException`) และเพิ่มเงื่อนไข `if (entry.isHint && (entry.rank == 1 || entry.rank >= best)) throw new IllegalStateException("Invalid hint entry")` ใน `add(Entry)`
- เพิ่มเมธอด `boolean hint(String canonical, WordRanker.Ranking ranks)` ที่คืน `false` เมื่อรอบปิด, ค่าว่าง, คำซ้ำ, คำลับ `#1`, หรืออันดับไม่ดีกว่า `best`
- เพิ่มเมธอด `GameRound reset()` ล้างสถานะทั้งหมดกลับเป็นรอบว่างที่เล่นได้ (`PLAYING`) โดยคง `date` เดิม และเพิ่ม `Entry entry(String word)` ช่วยค้นแถวเดิม
- คงการตรวจ `canonical == null || ranks == null` โดยไม่ดัก `NullPointerException` แทน `WordRanker.Ranking` เพื่อไม่ให้ซ่อนบั๊กหากส่งคำนอกคลังมาโดยไม่ได้ผ่าน `WordRanker.canonical` พร้อมระบุสัญญาใน Javadoc

### หลักฐานผลทดสอบก่อนแก้และหลังแก้ (Rubric 2.4 และ 5.3)

ใช้ชุดทดสอบเดียวกันใน `GameRoundTest.addRejectsSecretAndNonImprovingHints` (ทดสอบปฏิเสธคำใบ้ `#1` และคำใบ้ที่อันดับไม่ดีกว่า `best` ผ่าน `add(Entry)`) รันเปรียบเทียบก่อนแก้และหลังแก้:

- **ผลก่อนแก้ (รันบนฐาน `6988e93` ก่อนแก้ไข `GameRound.java`)**:
  - คำสั่ง: `.\gradlew.bat --offline --console=plain :app:testDebugUnitTest --tests th.ac.tu.cs364.wordward.GameRoundTest`
  - ผลจริง: **FAILED (1 test completed, 1 failed)**
  - รายละเอียด error:
    ```text
    GameRoundTest > addRejectsSecretAndNonImprovingHints FAILED
        java.lang.AssertionError: expected java.lang.IllegalStateException to be thrown, but nothing was thrown
            at org.junit.Assert.assertThrows(Assert.java:1028)
            at th.ac.tu.cs364.wordward.GameRoundTest.addRejectsSecretAndNonImprovingHints(GameRoundTest.java:25)
    ```
    (ยืนยันว่าฐานเดิมยอมให้คำใบ้ `#1` ผ่านเข้าไปโดยไม่ปฏิเสธ)

- **ผลหลังแก้ (รันบน branch `member/03-game` หลังแก้ไข `GameRound.java` และเพิ่มชุดทดสอบครบ 11 กรณีใน `GameRoundTest.java`)**:
  - คำสั่ง: `.\gradlew.bat --offline --console=plain --rerun-tasks :app:testDebugUnitTest :app:assembleDebug`
  - สภาพแวดล้อมที่รันจริง: Windows, Android Studio JBR 25, Gradle 9.5.0, AGP 9.3.3, Android SDK `android-37`
  - ผลจริง: **`BUILD SUCCESSFUL`** (40 actionable tasks: 40 executed)
  - ผล Unit Tests ทั้งโปรเจกต์: **39 tests, 0 failures, 0 errors, 0 skipped**:
    - `GameRoundTest`: **11 tests, 0 failures, 0 errors** (รวม `addRejectsSecretAndNonImprovingHints` เปลี่ยนจากแดงเป็นเขียว)
    - `StarterTest`: **6 tests, 0 failures, 0 errors**
    - `HintEngineTest`: **13 tests, 0 failures, 0 errors**
    - `DailyWordProviderTest`: **6 tests, 0 failures, 0 errors**
    - `ProductionWordsTest`: **3 tests, 0 failures, 0 errors**
  - Debug APK ที่สร้างได้: `app/build/outputs/apk/debug/app-debug.apk` ขนาด **2,348,653 ไบต์** (SHA-256: `A8ECE45875290D033CF2EE9DA17F3593F53E053EB55F800FE42E3DD5F8BF30C2`)

> **หมายเหตุสำคัญเรื่องขอบเขตหลักฐาน**:
> 1. ผลการทดสอบใน `GameRoundTest` เป็น **หลักฐานระดับกติกาของรอบเล่น (Domain Logic) เท่านั้น** ไม่ใช่หลักฐาน Rubric ข้อ 4.3 ทั้งข้อ (ภาพหน้าต่างยืนยันและการทดสอบกดปุ่มยกเลิก/ยืนยันบนหน้าจอแอปจริงต้องมาจากงาน 01 และงาน 05)
> 2. เทสต์ `resetDuringPlayingClearsHistoryAndCountsWhileKeepingDate` และ `resetAfterWinningReopensSamePuzzleAndKeepsDate` พิสูจน์เพียงว่า `reset()` ไม่เปลี่ยนฟิลด์ `final LocalDate date` ของ `GameRound` (คลาส `GameRound` ไม่อ่านนาฬิกา พฤติกรรมข้ามเที่ยงคืนจริงเป็นงานของ @ronnakrit303 และ @zero-h0ur)
> 3. เทสต์ `cancelLeavesRoundUntouchedAndResetEmptiesEntriesForInMemoryReplay` พิสูจน์เพียงว่าเมื่อนำ `entries()` มาสร้าง `GameRound` ใหม่ในหน่วยความจำหลัง `reset()` จะได้รอบว่าง 0 รายการ (การบันทึกและกู้เซฟจริงบน `SharedPreferences` ของ Android เป็นงานของ `GameStorage` โดย @zero-h0ur)
> 4. เทสต์ `assetsDatasetSupportsGuessesHintsWinAndResetAcrossPuzzles` อ่านจำนวนคำและจำนวนโจทย์จากไฟล์ใน `src/main/assets/` แบบไดนามิกโดยไม่ผูกติดตัวเลข 5,000 เพื่อรองรับคลังที่ตกลงไว้คือประมาณ 1,000 คำ / 31 โจทย์ และตรวจเฉพาะ invariant ของ `GameRound` โดยไม่ hardcode ช่วงอันดับภายในของ `HintEngine`

---

## 3. การเชื่อมโยงกับรายวิชา (Rubric ข้อ 1.3 และ 1.4)

### ข้อ 1.3 การเชื่อมโยงกับเนื้อหาของรายวิชา CS364
- **การแยก Domain Logic ออกจาก View และ Storage**: `GameRound` เก็บสถานะของรอบเล่น (`status`, `history`, `latest`, `hints`, `best`) แยกจาก `GameScreen` (ที่อัปเดต View ตาม `activity_game.xml`) ทำให้ตรวจสอบกติกาด้วย JUnit บน JVM ได้โดยตรง
- **การทำงานร่วมกับ Activity Lifecycle และ `SharedPreferences`**: ในแอป Android จริง `GameActivity` อัปเดตและอ่านสถานะรอบผ่าน `onResume()` (เรียก `refreshDay()` → `storage.open(day)` เพื่อกู้รอบค้างไว้จาก `SharedPreferences`) และหยุดตัวตั้งเวลาเปลี่ยนวันใน `onPause()` โดย `GameStorage` ใช้ `round.date` และ `round.entries()` เป็นรูปแบบข้อมูลบันทึกและกู้คืนสถานะเมื่อผู้ใช้สลับแอปหรือปิด–เปิดแอปใหม่

### ข้อ 1.4 การประยุกต์ใช้ความรู้เชื่อมโยงกับเนื้อหารายวิชาอื่น
- **โครงสร้างข้อมูลและอัลกอริทึม (Data Structures & Algorithms)**:
  - ใช้ `LinkedHashMap<String, Entry>` เก็บประวัติการเดาและคำใบ้ เพื่อรักษา **ลำดับเวลาที่เพิ่มจริง** (`entries()`) ควบคู่กับการตรวจสอบคำซ้ำ (`seen()`) และการดึงแถวเดิม (`entry()`) ด้วยเวลาเฉลี่ย O(1)
  - เมธอด `sorted()` สร้างสำเนา `ArrayList` แล้วเรียงลำดับด้วย `Comparator.comparingInt(e -> e.rank)` ในเวลา O(k log k) โดยไม่กระทบโครงสร้างลำดับเวลาใน `history`
- **วิศวกรรมซอฟต์แวร์ (Software Engineering)**:
  - ใช้หลักการ **Single Source of Truth** และ **Separation of Concerns** ระหว่างคลังคำ (`WordRanker`), กติการอบ (`GameRound`), คำใบ้/เวลา (`HintEngine`/`DailyWordProvider`), หน้าจอ (`GameScreen`), และการบันทึก (`GameStorage`)
  - ใช้การทดสอบระดับหน่วยแบบ **Regression Testing** (เขียนเทสต์บนฐานให้เห็นความล้มเหลวก่อนแก้ แล้วแก้โค้ดจนผ่านทั้งหมดโดยไม่ทำให้เทสต์เดิมของทีมตก)

---

## 4. ข้อเสนอสัญญาการเชื่อมงานและการประสานทีม (Issues #1, #2, #4, #5)

เมธอด `hint(...)`, `reset()`, และ `entry(...)` เป็น **ข้อเสนอสัญญาเมธอด (Proposed Contracts)** เพื่อรอการยืนยันจากสมาชิกผู้เรียกใช้ก่อนเชื่อมใน PR ถัดไป:

1. **ถึง @zero-h0ur (`GameActivity.java` และ `GameStorage.java` — Issue #5)**
   - **จุดรับคำใบ้ใน `GameActivity.hint()`**: เสนอให้เปลี่ยนจากการเรียก `round.add(new GameRound.Entry(word, ranks.rank(word), true))` มาใช้ `round.hint(word, ranks)` และ **หาก `round.hint(word, ranks)` คืน `false` ต้องไม่เรียก `storage.save(round)` และควรแสดงข้อความ `getString(R.string.no_hint)`** (เช่น `if (word == null || !round.hint(word, ranks)) { screen.show(round, getString(R.string.no_hint)); return; }`) เพื่อไม่ให้ `GameActivity.hint()` เงียบไปเมื่อ `HintEngine` คืนคำมาแต่ `GameRound` ปฏิเสธ
   - **ลำดับการยืนยันเริ่มรอบวันนี้ใหม่**: เสนอให้เชื่อม callback หลังผู้ใช้กดยืนยันใน dialog ตามลำดับ `round.reset()` → `storage.save(round)` → `screen.show(round, "")` (หากผู้ใช้กดยกเลิก ห้ามเรียก `round.reset()`)
   - **การกู้เซฟเสียใน `GameStorage.open(LocalDate)`**: `GameRound.add(Entry)` ยังคงโยน `IllegalArgumentException` และ `IllegalStateException` เมื่อข้อมูลผิดกฎ (เช่น คำซ้ำ, rank < 1, หรือคำใบ้อันดับ `#1`/ไม่ดีขึ้น) ขณะที่ `GameStorage.open` ของฐานเดิมดักเฉพาะ `JSONException` จึงเสนอให้ดัก `IllegalArgumentException | IllegalStateException` เพิ่มเติมเพื่อคืนรอบว่างไม่ให้แอปปิดตามเกณฑ์ของ @zero-h0ur
   - **เกณฑ์ Rubric ข้อ 4.2 และ 4.4**: แจ้งเตือนว่าใน `GameActivity` ปัจจุบันยังไม่มีการป้องกันการกดปุ่ม Back ออกจากเกม (ข้อ 4.2) และในรายงานส่วนของ @zero-h0ur ควรอธิบายข้อ 4.4 ว่า "ความคืบหน้าของงาน" คือสถานะรอบเล่นที่ถูกกู้กลับมาทำต่อได้ผ่าน lifecycle `onPause`/`onResume` และ `GameStorage`
2. **ถึง @PhurinKaewpuangsek (`GameScreen.java` — Issue #1)**
   - เสนอสัญญา callback สำหรับปุ่ม "เริ่มรอบวันนี้ใหม่" และหน้าต่างยืนยัน (ส่งเหตุการณ์ยืนยันไปให้ @zero-h0ur เรียก `round.reset()` แล้วใช้ `round.entry(word)` ดึงแถวซ้ำเมื่อต้องการเน้นแถวคำซ้ำในผล `REPEAT`)
   - ส่งต่อการเก็บหลักฐานและผลทดสอบบนหน้าจอจริงตาม Rubric ข้อ **3.1–3.4** (หน้าจอ 2 ขนาด, แนวตั้ง/แนวนอน, ไทย/อังกฤษ, Day/Night) และภาพหน้าต่างยืนยันยกเลิก/เริ่มใหม่สำหรับ **ข้อ 4.3**
3. **ถึง @ronnakrit303 (`HintEngine.java`, `DailyWordProvider.java` — Issue #4)**
   - แจ้งว่า `round.reset()` คงค่า `round.date` เดิมไว้เสมอ เพื่อให้ @ronnakrit303 และ @zero-h0ur ใช้เทียบกับวันที่ไทยปัจจุบัน (`DailyWordProvider.dateAt(Instant.now())`) สำหรับจัดการกรณีหน้าต่างยืนยันเปิดค้างข้ามเที่ยงคืน
   - ขอตารางรายการฟังก์ชันและผลทดสอบจริงจาก branch ของ @ronnakrit303 เพื่อรวบรวมส่งประกอบรายงาน
4. **ถึง @Micosz (`WordRanker.java`, `puzzles.tsv`, `aliases.tsv` — Issue #2)**
   - สอบถามจำนวนคำหลักและ alias จริงสำหรับชุด **1,000 คำ / 31 โจทย์** ตามข้อตกลงทีม (ตอนนี้ `main` ยังมีไฟล์ชุด 5,000 คำจาก merge ก่อนหน้า) เพื่อไม่ให้ข้อมูลในเอกสารและรายงานคลาดเคลื่อน
   - ขอตารางรายการฟังก์ชันและผลทดสอบจริงจาก branch ของ @Micosz และส่งข้อมูลการใช้ AI ของส่วนที่ 3 ด้านล่างให้ @Micosz รวมในตารางแหล่งที่มา (Rubric 5.2)

---

## 5. การเปิดเผยแหล่งที่มาและการใช้ AI (Rubric ข้อ 5.2 — ส่งให้ @Micosz รวบรวม)

| ส่วนประกอบ | แหล่งที่มา / เครื่องมือที่ใช้ | สิ่งที่เครื่องมือช่วยทำ | สิ่งที่ผู้พัฒนา (คนที่ 3) ดำเนินการและตรวจแก้เอง |
|---|---|---|---|
| `GameRound.java`, `GameRoundTest.java`, `docs/team/WORDWARD_03_GAME.md` | ใช้ AI (Antigravity) เป็นผู้ช่วยพัฒนา และอ้างอิงชุดข้อมูล/โค้ดฐานของทีมใน repo `CS364FinalApplication` | วิเคราะห์เอกสารข้อกำหนดและ rubric, ร่างกรณีทดสอบ, ร่างโค้ดเมธอด `hint`/`reset`/`entry` และการตรวจใน `add`, รันคำสั่ง Gradle เก็บผลก่อนแก้–หลังแก้, และเรียบเรียงโครงสร้างเอกสาร | ตรวจสอบความสอดคล้องกับ `TEAM_AGREEMENTS.md` และ rubric, ตัดสินใจเลือกสัญญาเมธอดผ่านการตอบคำถาม `/grill-me`, สั่งปรับแก้แผนก่อนลงมือทำ (ตัดการดัก NPE ออกจาก `guess`/`hint` แล้วระบุเงื่อนไขใน Javadoc แทน, กำหนดไม่ให้ hardcode 5,000 คำและช่วงอันดับของ `HintEngine`, แยกข้อเท็จจริงเรื่อง `reset()` คง `date` และ `replayInMemory` ให้ตรงขอบเขต, เพิ่มเงื่อนไขไม่เซฟและแสดง `no_hint` ในข้อเสนอ Issue #5, ปรับตำแหน่งไฟล์เป็น `docs/team/` และใช้ข้อความธรรมดาแทน LaTeX), ตรวจทานความสะอาดของ branch และตรวจสอบผลทดสอบจริง |

---

## 6. ตารางรายการฟังก์ชันและผลทดสอบรวมของทีม (Rubric ข้อ 1.1 และ 1.2)

> **แนวทางการรวบรวมตามข้อตกลงทีม §4**: ส่วนของงาน 03 กรอกผลทดสอบจริงที่รันผ่านบน branch `member/03-game`; ส่วนของงาน 02 และ 04 รอตารางผลทดสอบอย่างเป็นทางการจาก branch ของเจ้าของงาน; ส่วนของงาน 01 และ 05 เว้นเป็นสถานะรอผลทดสอบจริงจาก branch ของสมาชิกโดยไม่เติมผลเอง

### ส่วนที่ 3: กติการอบเกม (`GameRound.java` — branch `member/03-game`)

| ฟังก์ชัน | หน้าจอที่เห็นผล | ผลลัพธ์ | วิธีทดสอบ | ผลคาดหวัง | ผลจริง |
|---|---|---|---|---|---|
| `GameRound.guess(canonical, ranks)` | หน้าจอเกมหลัก (`activity_game.xml`: `R.id.notice`, `R.id.latest`, `R.id.totals`, `R.id.history`, `R.id.input`, `R.id.guess`, `R.id.hint`) | ตัดสินผลเดาเป็น `ADDED`, `REPEAT`, `UNKNOWN`, หรือ `CLOSED` และปรับ `status = WON` เมื่อได้อันดับ #1 | รัน `GameRoundTest` (`guessesCountOnlyAcceptedCanonicalWordsAndResolveAliases`, `entriesPreserveChronologicalOrderWhileSortedOrdersByRankAndLatestTracksNewest`, `winningViaCanonicalOrAliasClosesRoundToFurtherGuessesAndHints`) และ `StarterTest` ด้วยชุด 14 คำและข้อมูลใน `assets/` | คำใหม่นับครั้งเดียว, คำพหูพจน์/alias ของคำเดิมได้ `REPEAT` ยอดไม่เพิ่ม, คำนอกคลัง (`null`) ได้ `UNKNOWN` ยอดไม่เพิ่ม, ชนะอันดับ #1 แล้วเปลี่ยนเป็น `WON` และเดาต่อได้ `CLOSED` | ผ่าน (รัน `GameRoundTest` 11/11 และ `StarterTest` 6/6 บน `member/03-game` ได้ 0 failures, 0 errors) |
| `GameRound.hint(canonical, ranks)` *(ข้อเสนอ)* | หน้าจอเกมหลัก (`R.id.totals`, `R.id.latest`, `R.id.history`, `R.id.notice`) | คืน `true` เมื่อรับคำใบ้ (เพิ่ม `hints` และ `attempts`) หรือ `false` เมื่อผิดเงื่อนไขโดยไม่แก้รอบ | รัน `GameRoundTest` (`acceptedHintsIncrementHintsAndAttemptsAndGuessingHintedWordIsRepeat`, `hintRejectsInvalidCandidatesWithoutChangingRoundState`, `assetsDatasetSupportsGuessesHintsWinAndResetAcrossPuzzles`) | รับคำใบ้ที่ไม่ซ้ำ ไม่ใช่ #1 และอันดับดีกว่า `best` เท่านั้น; เดาคำที่เคยใบ้ซ้ำได้ `REPEAT` โดยยอดไม่เพิ่ม; ปฏิเสธค่า `null`, คำซ้ำ, `#1`, อันดับ `>= best`, และหลัง `WON` โดยไม่เปลี่ยนสถานะรอบ | ผ่าน (ทดสอบระดับกติกาใน `GameRoundTest` สำเร็จ; การเชื่อมใน `GameActivity.hint()` รอ @zero-h0ur) |
| `GameRound.add(entry)` | หน้าจอเกมหลัก (`R.id.totals`, `R.id.history`, `R.id.latest`, `R.id.notice` ผ่านการเพิ่ม/กู้รอบ) | เพิ่ม/กู้รายการตามลำดับเวลาและป้องกันข้อมูลผิดกฎด้วยการโยน `IllegalArgumentException` / `IllegalStateException` | รัน `GameRoundTest.addRejectsSecretAndNonImprovingHints` เปรียบเทียบก่อนแก้บนฐาน `6988e93` กับหลังแก้บน `member/03-game` | ก่อนแก้: ตก (ฐานเดิมรับคำใบ้ `#1`); หลังแก้: ปฏิเสธคำใบ้ `#1`, คำใบ้อันดับ `>= best`, คำซ้ำ, `null`, `rank < 1`, และรายการหลังชนะ | ผ่าน (ก่อนแก้ตกตามคาด `AssertionError` ที่บรรทัด 25; หลังแก้ผ่านบน `member/03-game`) |
| `GameRound.reset()` *(ข้อเสนอ)* | หน้าจอเกมหลัก (`R.id.day`, `R.id.totals`, `R.id.latest`, `R.id.history`, `R.id.notice`, `R.id.input`, `R.id.guess`, `R.id.hint`) | ล้างประวัติ คำใบ้ ยอดทุกชนิด `latest` `best` และคืน `status = PLAYING` โดยคง `date` เดิม และคืน `this` | รัน `GameRoundTest` (`resetDuringPlayingClearsHistoryAndCountsWhileKeepingDate`, `resetAfterWinningReopensSamePuzzleAndKeepsDate`, `cancelLeavesRoundUntouchedAndResetEmptiesEntriesForInMemoryReplay`) | ล้างรายการและยอดเป็น 0, คง `date` เดิม, ใช้ได้ทั้งระหว่าง `PLAYING` และหลัง `WON`, เดาคำเดิมซ้ำได้, คำใบ้หลังรีเซ็ตผ่าน invariant (`rank > 1`, ไม่ซ้ำ, `< best`), และไม่เรียก `reset()` เมื่อจำลองยกเลิก | ผ่านระดับกติกา (เฉพาะ Domain Logic ใน `GameRoundTest`; การแสดงหน้าต่างยืนยันและการบันทึกลงเครื่องข้อ 4.3 รอผลจริงจากงาน 01 และ 05) |
| `GameRound.entry(word)` *(ข้อเสนอ)* | หน้าจอเกมหลัก (`R.id.history`, `R.id.notice` เมื่อแสดง/เน้นแถวคำซ้ำ) | คืน `Entry` เดิมจาก `history` เมื่อพบ หรือ `null` เมื่อไม่พบหรือ `word == null` | รัน `GameRoundTest` (`newRoundStartsEmptyAndPlaying`, `guessesCountOnlyAcceptedCanonicalWordsAndResolveAliases`, `acceptedHintsIncrementHintsAndAttemptsAndGuessingHintedWordIsRepeat`) | คืน `Entry` พร้อม `word`, `rank`, `isHint` เดิมถูกต้องเมื่อค้นด้วยคำหลักหลังเกิด `REPEAT` | ผ่าน (การเชื่อมแสดงผลเน้นแถวซ้ำบน UI รอ @PhurinKaewpuangsek และ @zero-h0ur) |
| `GameRound.entries()`, `sorted()`, `seen()`, `attempts()`, `guesses()`, `hints()`, `best()`, `latest()` | หน้าจอเกมหลัก (`R.id.totals`, `R.id.latest`, `R.id.history`) | คืนประวัติตามลำดับเวลา รายการเรียงตามอันดับ สถานะเคยเห็น ยอดรวม/เดา/คำใบ้ อันดับดีที่สุด และคำล่าสุด | รัน `GameRoundTest` ทั้ง 11 กรณี และ `HintEngineTest` 13 กรณี | `entries()` เรียงตามเวลา, `sorted()` เรียงจากอันดับน้อยไปมาก, `attempts() == guesses() + hints()`, `best()` คงค่าน้อยสุดแม้คำล่าสุดแย่ลง | ผ่าน (รัน `GameRoundTest` 11/11 และ `HintEngineTest` 13/13 บน `member/03-game` ผ่านทั้งหมด) |

### ส่วนที่ 1, 2, 4, 5: รายการฟังก์ชันและสถานะผลทดสอบจากสมาชิกส่วนอื่น (รวบรวมตามข้อตกลง §4)

| ฟังก์ชัน (เจ้าของงาน) | หน้าจอที่เห็นผล | ผลลัพธ์ | วิธีทดสอบ | ผลคาดหวัง | ผลจริง |
|---|---|---|---|---|---|
| ส่วนที่ 1 (`member/01-ui`: `GameScreen.java`, `activity_game.xml`, `strings.xml`, themes) — แสดงผลล่าสุดแยกจากรายการเรียงอันดับ, เน้นคำซ้ำ, หน้าต่างยืนยันเริ่มรอบใหม่, ไทย/อังกฤษ, Day/Night, รองรับหลายขนาดจอ/การหมุน | หน้าจอเกมหลัก (`activity_game.xml`) และหน้าต่างยืนยันเริ่มรอบใหม่ | แสดงสถานะเกมและข้อความไทย/อังกฤษ ธีม Day/Night ชัดเจนบนทุกขนาดจอ พร้อมยืนยันก่อนลบ | ทดสอบบน Emulator/อุปกรณ์จริง 2 ขนาดหน้าจอ แนวตั้ง/แนวนอน สลับภาษา สลับธีม และกดปุ่มเริ่มรอบใหม่ (ยกเลิก/ยืนยัน) | แสดงผลไม่ตัด/ซ้อน, คงข้อมูลเกมเมื่อสลับภาษา/ธีม, และยืนยันก่อนเริ่มใหม่ครบ Rubric 3.1–3.4 และ 4.3 | รอผลทดสอบจริงจาก branch `member/01-ui` ของ @PhurinKaewpuangsek |
| ส่วนที่ 2 (`member/02-words`: `WordRanker.java`, `puzzles.tsv`, `aliases.tsv`) — คลังคำหลัก 31 โจทย์, การแปลง `canonical`, รูปพหูพจน์/alias, อันดับคำ | หน้าจอเกมหลัก (คำหลัก, อันดับ `#rank`, ข้อความ `unknown`/`repeat`) | แปลงคำเป็นคำหลักหรือ `null` และคืนอันดับ `1..N` ของทุกโจทย์ด้วยชุดคำหลักเดียวกัน | รันชุดทดสอบบน branch `member/02-words` (ขอตารางวิธีทดสอบและตัวเลขคลังจริงตามข้อตกลง 1,000 คำ / 31 โจทย์ จาก @Micosz) | ทุกโจทย์จัดอันดับชุดคำหลักครบไม่ซ้ำ ไม่ตกหล่น และ alias ทุกตัวชี้คำหลักที่มีอยู่ | รอขอตารางผลทดสอบอย่างเป็นทางการจาก branch `member/02-words` ของ @Micosz (บน `member/03-game` รัน `ProductionWordsTest` 3/3 ผ่านตามไฟล์ที่มีใน `main`) |
| ส่วนที่ 4 (`member/04-daily`: `HintEngine.java`, `DailyWordProvider.java`) — เลือกคำใบ้ที่ดีขึ้นจาก `best`, กำหนดวันไทย `Asia/Bangkok` และคำลับ 31 วัน | หน้าจอเกมหลัก (`R.id.day`, `R.id.history`, `R.id.notice` เมื่อขอคำใบ้และเปลี่ยนวัน) | คืนคำใบ้ที่ไม่ซ้ำ/ไม่เฉลย #1 และคืนวันที่/คำลับประจำวันตาม `Asia/Bangkok` | รันชุดทดสอบบน branch `member/04-daily` (ขอตารางวิธีทดสอบจาก @ronnakrit303) | คำใบ้ดีกว่า `best`, ไม่เฉลย #1, ไม่เปลี่ยนรอบเอง, และเปลี่ยนโจทย์ตรง 00:00 น. เวลาไทย | รอขอตารางผลทดสอบอย่างเป็นทางการจาก branch `member/04-daily` ของ @ronnakrit303 (บน `member/03-game` รัน `HintEngineTest` 13/13 และ `DailyWordProviderTest` 6/6 ผ่าน) |
| ส่วนที่ 5 (`member/05-storage`: `GameStorage.java`, `GameActivity.java`) — บันทึก/กู้รอบใน `SharedPreferences`, รับมือเซฟเสีย, lifecycle `onPause`/`onResume`, ป้องกันกด Back ออก, เชื่อมรีเซ็ตและข้ามวัน | หน้าจอเกมหลัก (`GameActivity`) และหน้าต่างยืนยันออก/เริ่มใหม่ | กู้คืนรอบเดิมหลังปิด/สลับแอป, รีเซ็ตข้ามวัน, ไม่ปิดแอปเมื่อเซฟเสีย, และยืนยันก่อน Back ออกหรือเริ่มใหม่ | ทดสอบบน Emulator/อุปกรณ์จริง: ปิด–เปิดแอป, สลับแอป (4.4), กด Back ยกเลิก/ยืนยัน (4.2), กดเริ่มใหม่ยกเลิก/ยืนยัน (4.3), จำลองเซฟเสียและข้ามเที่ยงคืน | คงรอบ/ภาษา/ธีมหลังเปิดใหม่, ลบรอบหลังยืนยันเริ่มใหม่โดยไม่คืนข้อมูลเก่า, และทำงานได้ตาม Rubric 4.1–4.4 | รอผลทดสอบจริงจาก branch `member/05-storage` ของ @zero-h0ur |
