# Wordward Issue 04 daily puzzles and hints

Status: Complete - approved implementation scope; team dependencies remain
Created: 2026-11-15
Approval: Approved by user on 2026-11-15

## Summary

พัฒนา Issue #4 บน branch `member/04-daily` โดยรักษาสัญญาของผู้เรียกใช้เดิม:
คำใบ้ดีขึ้นจากอันดับดีที่สุด รวมคำใบ้เดิม ไม่ซ้ำ ไม่เผย #1 และไม่เปลี่ยนรอบเอง;
โจทย์เลือกตามวัน Asia/Bangkok และลำดับข้อมูล 31 คำของงาน 02.
แบ่ง commit ตามงานย่อยและแนบผลทดสอบจริง ผู้ใช้ขอให้ถามก่อนทำเมื่อข้อกำหนดไม่ชัดเจน.

## Clarifying Questions

- [x] ผู้ใช้อนุมัติรวม `origin/member/02-words` เข้า branch งานนี้ เพื่อทดสอบกับ 5,000 คำ / 31 โจทย์จริง โดยเก็บประวัติ commit ของเพื่อนและไม่เปลี่ยน main; ยอมรับ diff dependency จนกว่างาน 02 จะรวม main.
- [x] ผู้ใช้อนุมัติคง EPOCH 2026-11-07 และพฤติกรรมฐานเดิมชั่วคราว พร้อมทำ tests เวลาไทยและ 31 วันจริง; นโยบายนอกชุดเป็น blocker สำหรับการเปลี่ยนพฤติกรรมในงานรอบถัดไป.
- [x] ผู้ใช้อนุมัติตัวกรองคำใบ้แบบรับเข้ามาเฉพาะใน HintEngine โดยคง constructor `HintEngine(Random)` และ `next` เดิม. Constructor เดิมใช้คลังที่เกมรับได้ทุกคำ; constructor เพิ่มรับ `Predicate<String>` สำหรับอนุญาตคำตามข้อมูล common-word ในอนาคต. ข้อมูลจริงจากงาน 02 ยังเป็น blocker ของการเปิดใช้ตัวกรองในแอป.
- [x] ขอบเขตที่เสนอสำหรับ dialog ข้ามวันคือเอกสารเสนอให้งาน 01/05 ยืนยันและรายการทดสอบจุดเชื่อม; ไม่แก้ GameActivity/GameScreen หรือกำหนดพฤติกรรม UI เองในงานรอบนี้.
- [x] ขอบเขตที่เสนอสำหรับรายงานคือเนื้อหาและผลทดสอบจริงของงาน 04 ใน Markdown พร้อมรายการหลักฐานที่ต้องรับ. รายงาน Word/PDF รวมและ Checklist เลขหน้ารอ rubric/หลักฐานสมาชิกคนอื่น.

## File And Code References

- `app/src/main/java/th/ac/tu/cs364/wordward/HintEngine.java`: `next` เดิมเลือก 80%..best-1; ต้องจำกัดช่วงเป้าหมายเป็น 80–90% และจัดการกรณีช่วงเล็ก/ว่าง.
- `app/src/main/java/th/ac/tu/cs364/wordward/DailyWordProvider.java`: `dateAt`, `number`, `secret`, `BANGKOK`, `EPOCH`; ปัจจุบันเริ่ม 2026-11-07 และใช้ floorMod วนโจทย์.
- `app/src/main/java/th/ac/tu/cs364/wordward/GameRound.java`: `best`, `seen`, `entries`, `attempts`, `status`; `add` เป็นผู้เปลี่ยนรอบ ไม่ให้ HintEngine เปลี่ยนข้อมูลเอง.
- `app/src/main/java/th/ac/tu/cs364/wordward/WordRanker.java`: `daily`, `rankFor`, `Ranking.rank`, `size`, `wordAt`; ข้อมูลและสัญญาเป็นของงาน 02.
- `app/src/main/java/th/ac/tu/cs364/wordward/GameActivity.java`: `refreshDay`, `hint` เป็นจุดเชื่อมเวลาและการรับคำใบ้ของงาน 05.
- `app/src/test/java/th/ac/tu/cs364/wordward/StarterTest.java`: 6 กรณีทดสอบฐาน; branch เพื่อนแยก fixture เล็กเป็น test resources แล้ว.
- `origin/member/02-words` ที่ตรวจ `fa215b1`: 5,000 คำ, 31 คำลับ, 2,772 alias; ลำดับตรง `tools/wordward/secrets.txt`; `doc/WORDWARD_02_DATA.md` ระบุข้อมูล common-word และนโยบายวันที่ยังรอทีม.
- `docs/team/TEAM_AGREEMENTS.md`, `docs/team/04-daily.md`, Issue #4 ที่ผู้ใช้แนบ: ขอบเขตงานและรายการรอยืนยัน.

## Plan Todos

- [x] P1: ยืนยัน dependency และพฤติกรรมที่ยังไม่ตกลง บันทึกคำตอบและปรับแผนก่อนเริ่มพัฒนา.
- [x] P2: เตรียมข้อมูลทดสอบตามวิธีที่ผู้ใช้เลือก ตรวจ baseline ของ branch และ commit แผนที่ยืนยันแล้ว. รวมข้อมูลแล้วที่ `093591d`; baseline 9 tests และ assembleDebug ผ่านหลังดาวน์โหลด dependency 2.1.5 ที่ขาด.
- [x] P3: ปรับ `HintEngine` พร้อม `HintEngineTest` ให้ช่วงแรกสัมพันธ์กับ N, ช่วงถัดไปอิง best 80–90%, ใกล้ #2 ยังมีทางไปต่อ, รอบจบ/ไม่มีผู้สมัครคืน null, ผลไม่เปลี่ยนรอบ.
- [x] P4: เพิ่ม `DailyWordProviderTest` สำหรับ 31 วันจริงและขอบเที่ยงคืนไทยหลายเขตเวลาของเครื่อง พร้อมอธิบายสัญญา/นโยบายชั่วคราวใน Javadoc ของ DailyWordProvider โดยไม่เปลี่ยนผลลัพธ์ของฐาน.
- [x] P5: ทดสอบคำใบ้ต่อเนื่องบนข้อมูลจริง 5,000 คำครบทั้ง 31 โจทย์และ fixture เล็ก รวมรอบใหม่ที่ประวัติว่างและใช้โจทย์เดิม.
- [x] P6: จัดทำ `doc/WORDWARD_04_DAILY.md` เป็นเนื้อหารายงานงาน 04, ผลทดสอบ, ข้อเสนอ dialog ข้ามวัน, จุดเชื่อมสำหรับงาน 01/02/03/05 และรายการหลักฐาน/การตัดสินใจที่ยังรอทีม.
- [x] P7: รัน unit tests และ assembleDebug ตามสภาพแวดล้อมที่มี ตรวจ diff/status และ commit แต่ละชุดที่ตรวจแล้ว.

## Grill-Me Outcome

- Transcript: `tmp/grill-me/session-wordward-issue-04-20261115-000731.md` (finalized).
- Outcome: `tmp/grill-me/outcome-wordward-issue-04-20261115-000731.md`.
- Summary: ยืนยัน dependency, นโยบายวันที่ชั่วคราว, ตัวกรอง optional และขอบเขตส่งมอบเรียบร้อย ผู้ใช้อนุมัติเริ่มพัฒนาแล้ว.

## Build From Plan

- Ready to build: Yes - user approved on 2026-11-15.
- Selected todos: P1–P7 ได้รับอนุมัติในขอบเขตรอบนี้; งานนอกชุดวัน, ตัวกรอง common-word ที่เปิดใช้จริง, UI dialog และรายงานรวมมี blocker ระบุชัดและไม่ถือว่าสำเร็จในรอบนี้.
- Execution notes: หลังผู้ใช้อนุมัติ อ่านแผนล่าสุดอีกครั้ง ก่อนแก้โค้ด/commit. Commit แยกเป็นแผน, คำใบ้และ tests, โจทย์รายวันและ tests, เอกสารผลตรวจ โดย merge dependency `093591d` แยกไว้แล้ว.

### Hint selection to implement

- คง `HintEngine(Random)` และ `next(GameRound, Ranking)`; เพิ่ม overload `HintEngine(Random, Predicate<String>)` ที่รับเกณฑ์อนุญาตคำ. เกณฑ์พื้นฐานยังตรวจไม่เคยเห็น, rank >= 2 และ rank < best เสมอ.
- ไม่มีประวัติ: เลือกจากช่วง 50–75% ของ N เช่นเดิม โดยบีบขอบให้ปลอดภัยใน 2..N; คลังไม่มีคำที่ไม่ใช่เฉลยคืน null.
- มีประวัติ: ใช้ช่วง floor(0.8*best)..floor(0.9*best) ภายใน 2..min(N,best-1); เมื่อ best ใกล้ 2 ให้ใช้ช่วงที่ยังเหลือ.
- เลือกแบบสุ่มจากผู้สมัครที่ผ่านเงื่อนไขด้วย Random ที่ฉีดเข้ามา. หากช่วงเป้าหมายไม่มีคำผ่านตัวกรอง ให้ขยายขอบออกทีละประมาณ 10% ของ N (รอบแรก) หรือ best (รอบถัดไป) จนเต็มช่วงที่อนุญาต. หากยังไม่มีคืน null.
- การเลือกคำไม่มีการเพิ่ม entry/ยอด/สถานะใน GameRound และไม่เก็บสถานะเฉพาะรอบไว้ใน HintEngine; รอบใหม่ประวัติว่างจึงใช้กติกาครั้งแรกเอง.
- ตรวจด้วย fixture จริง, ขนาดเล็กพิเศษ, คลังจำลอง 1,000 และข้อมูลเพื่อน 5,000; ตัวกรองทดสอบใช้ข้อมูลจำลองเพื่อพิสูจน์ fallback โดยไม่อ้างว่าเป็นนโยบาย common-word จริง.

## Validation

- Baseline และหลังแก้: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` ใช้ JBR ของ Android Studio และ SDK ที่ติดตั้ง; ตรวจเวอร์ชันและ cache ก่อนรัน.
- สภาพแวดล้อม: JBR 25.0.2, SDK platform `android-37.0`, Build Tools 36.0.0, Gradle 9.5.0 และ AGP 9.3.3. Git author name/email ตั้งค่าแล้ว.
- คำใบ้: fixture เล็ก, คลัง N=1/2/3, N ประมาณ 1,000 และข้อมูลจริง 5,000; seed คงที่, คำล่าสุดแย่กว่า best, คำใบ้ต่อจากคำใบ้, ไม่ซ้ำ/#1, ไม่เปลี่ยน round เมื่อเลือกคำหรือคืน null.
- วัน: Instant ก่อน/หลัง `17:00:00Z` สอดคล้องเที่ยงคืนไทย, เปลี่ยน JVM default timezone แล้วผลเหมือนเดิม, วันแรก/กลาง/31 และคำลับไม่ซ้ำ; boundary tests เพิ่มเฉพาะนโยบายที่ได้รับอนุมัติ.
- Baseline จริง: คำสั่ง offline ครั้งแรกขาด `desugar_jdk_libs:2.1.5`; รันคำสั่งเดิมแบบ online สำเร็จ (15 วินาที), 9 tests (StarterTest 6 + ProductionWordsTest 3), failures/errors 0; assembleDebug ผ่าน. ผล XML อยู่ใน `app/build/test-results/testDebugUnitTest/`.
- หลังปรับคำใบ้: `--offline :app:testDebugUnitTest` ผ่าน; HintEngineTest ครอบคลุมช่วงแรก, best เทียบคำล่าสุด, คำใบ้ต่อเนื่อง, ตัวกรอง/fallback, คลัง 0/1/2/3/1,000/5,000, รอบจบและรอบว่างใหม่ รวมการเดินคำใบ้ครบ 31 โจทย์จริง.
- หลังเพิ่ม daily tests: `--offline :app:testDebugUnitTest :app:assembleDebug` ผ่าน (3 วินาที), รวม 28 tests: HintEngineTest 13, DailyWordProviderTest 6, ProductionWordsTest 3, StarterTest 6; failures/errors 0. ตรวจขอบเที่ยงคืนครบ 30 จุดภายในชุดและ default timezone 6 เขต.
- APK จาก source ชุดที่ตรวจมีขนาด 2,361,039 ไบต์, SHA-256 `aca0c04c0b426cf4a5baf57655ab62e5dd914f07862b034331dca38467166f5f`; เอกสารผลตรวจและรายการรอทีมอยู่ใน `doc/WORDWARD_04_DAILY.md`.
- `git diff --check`, ตรวจรายงาน tests และสถานะ branch หลังแต่ละชุดงาน; ไม่รวมการ push/เปิด PR เพราะสิทธิ์ GitHub ปัจจุบันถูกปฏิเสธ.

## Risks

- งาน 02 เปลี่ยนเป้าหมายเป็น 5,000 คำ แต่ Issue/ข้อตกลงทีมยังระบุประมาณ 1,000; ใช้ N จากข้อมูลจริงและให้ผู้ใช้ยืนยัน dependency.
- การ merge งาน 02 เข้า branch นี้จะทำให้ diff เทียบ main มีงานเพื่อนด้วย จนกว่างาน 02 จะรวม main; เก็บ commit เดิมและไม่แก้ข้อมูลของเพื่อน.
- การเปลี่ยน epoch หรือการวนจะเปลี่ยนคำลับตามวันและกระทบเซฟ; ต้องยืนยันก่อนทำ.
- การคืน null/โยน exception สำหรับวันนอกชุดอาจต้องให้ GameActivity จัดการ; หลีกเลี่ยงแก้ API โดยไม่ตรวจ caller และขอบเขตงาน 05.
- ยังไม่มีข้อมูลคำทั่วไปแยกสำหรับคำใบ้ และ GameRound ยังไม่มีเมธอด reset ของงาน 03; ต้องแยกทดสอบการเริ่มด้วยรอบว่างจากการอ้างว่าจุดเชื่อมรีเซ็ตครบแล้ว.
- รายงานรวมและการตรวจ rubric ต้องมี rubric/หลักฐานทีมครบ; ไม่สร้างผลทดสอบหรือเลขหน้าที่ไม่มีหลักฐาน.

## Approval

- Status: Approved on 2026-11-15
- ผู้ใช้อนุญาตให้พัฒนา Issue #4 และ commit บ่อย โดยถามก่อนทำเมื่อไม่มั่นใจ. Dependency, การคงนโยบายวันที่ชั่วคราว, ขอบเขตตัวกรองและสิ่งส่งมอบได้รับการอนุมัติครบแล้ว.
- 2026-11-15: ผู้ใช้อนุมัติขั้น dependency ของ P2 โดยตอบว่า "รวมข้อมูลเพื่อนเข้า branch งานผม (แนะนำ)".
- รวม dependency งาน 02 แบบ merge commit `093591d` โดยไม่แก้ main; baseline 9 tests และ assembleDebug ผ่าน.
- 2026-11-15: ผู้ใช้ตอบ "คงฐานเดิม รอทีมยืนยันนโยบาย (แนะนำ)" จึงคง EPOCH และพฤติกรรมเดิมชั่วคราว และแยกนโยบายนอกชุดเป็น blocker.
- 2026-11-15: ผู้ใช้ตอบ "อนุมัติแผน เริ่มพัฒนาได้ (แนะนำ)" อนุมัติ P3–P7 และรายการส่งมอบ/ข้อจำกัดที่ระบุในแผน.
