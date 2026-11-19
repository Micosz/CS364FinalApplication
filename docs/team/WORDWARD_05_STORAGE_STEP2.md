# Wordward งานส่วนที่ 5 ขั้นที่ 2 การเชื่อมเกมกับ UI

วันที่ตรวจ 19 พฤศจิกายน 2569

งานนี้ต่อจาก PR #9 และใช้ UI ที่ผู้ใช้ merge ผ่าน PR #10 แล้ว ฐาน main คือ 3ad1ad0 โค้ดจัดอันดับยังเป็นเวอร์ชันใน main โดยไม่ต้องรอ PR #11

## พฤติกรรมที่เพิ่ม

| เรื่อง | การทำงาน |
|---|---|
| Back | ใช้ OnBackInvokedCallback บน API 33+ และ onBackPressed สำหรับรุ่นเก่า แสดง native dialog เพียงชุดเดียว ยกเลิกแล้วอยู่ต่อ ยืนยันแล้ว finish โดยไม่ล้างรอบ |
| คำซ้ำ | GameSession คืนคำหลักจาก WordRanker ให้ GameActivity ส่งตรงเข้า show(round, notice, repeatedWord) จึงรองรับ children → child โดยไม่ให้ UI เดารูปคำ |
| รีเซ็ต | รับเฉพาะ callback หลัง dialog ยืนยัน ตรวจวันไทยใหม่ reset() → save(round, ranks) → แสดงรอบว่าง ใช้ได้ก่อนและหลังชนะ |
| ธีม | อ่านค่าที่ UI บันทึกแล้วมาสร้าง configuration ก่อน inflate ไม่มีการ toggle ซ้ำใน callback |
| ภาษาและ draft | คงภาษา ธีม ประวัติ และข้อความที่ยังไม่ส่งเมื่อ recreate/หมุนจอ เก็บ draft เฉพาะวันเดิมและรอบที่ยังเล่นอยู่ |
| Recovery | แสดง recovery_notice เมื่อเซฟเสีย/อันดับเปลี่ยน/schema ไม่รองรับ ไม่เตือนสำหรับการย้ายเซฟเดิมที่สำเร็จ คงคำเตือนจนมีการกระทำที่รับสำเร็จ |
| วันใหม่ | GameSession ตรวจวันอีกครั้งก่อนเดา ขอใบ้ และรีเซ็ต แม้ timer ของ Activity ยังไม่ทำงาน |
| Lifecycle | หยุด timer เมื่อ pause ยกเลิก timer และ unregister Back เมื่อ destroy ปิด exit dialog เมื่อ stop และกู้รอบจาก storage เมื่อสร้างใหม่ |

## โครงสร้างและเจ้าของข้อมูล

- GameRound เป็นผู้ตัดสินกติกาและคำนวณยอด
- GameStorage เป็นผู้ serialize/ตรวจเซฟและเก็บการตั้งค่า
- GameSession ประสานเวลาไทย กติกา และการบันทึก โดยรับ Clock เพื่อทดสอบข้ามวันได้
- GameActivity เป็นจุดเชื่อม callbacks ของคน 1 กับ session
- GameAppearance เตรียม locale/uiMode ก่อนสร้าง UI และ normalize ค่าธีมเก่าที่มีชนิดผิด
- ExitConfirmation จัดการ dialog กับการลงทะเบียน Back ของแพลตฟอร์ม
- GameScreen และ resources จาก PR #10 ถูกนำมาใช้ตามสัญญา โดยงานส่วนที่ 5 ไม่แก้ไฟล์เหล่านั้น

ลำดับรีเซ็ต: UI ยืนยันและตรวจวัน → callback → GameSession.refresh() → เทียบวันที่ยืนยันกับวันที่ปัจจุบัน → reset → save → render หากวันเปลี่ยนจะปฏิเสธการลบรอบใหม่

Callback ธีมเรียก recreate() อย่างเดียว เพราะ GameScreen เขียนค่าธีมใหม่ก่อน callback แล้ว

## ผลตรวจที่ทำจริง

สภาพแวดล้อม: Android Studio JBR, Gradle 9.5.0, AGP 9.3.3, compile SDK 37; emulator Pixel 6a, Android 15 / API 35, session แบบ read-only

| การตรวจ | ผล |
|---|---|
| Local JVM tests | 83 tests ผ่านทั้งหมด รวม GameSessionTest ใหม่ 15 และ GameAppearanceTest ใหม่ 2 |
| Instrumentation เต็มชุด | 9 tests ผ่านทั้งหมดบน Pixel 6a / API 35 |
| สร้าง Debug APK และ test APK | ผ่าน |
| Force-stop แล้วเปิดใหม่ | PASS เทียบ round JSON ตรงกัน และคงภาษา/ธีม |
| Static UI audit | strict audit: 0 errors / 0 warnings |
| DESIGN.md lint | 0 errors / 0 warnings |
| Android lint | ผ่าน: 0 errors / 62 warnings |
| diff whitespace | ตรวจด้วย git diff --check |

Android lint ยังมี 62 warnings ได้แก่ unused resources, plural/i18n, API attributes, annotation และเวอร์ชัน test dependencies งานนี้ไม่ได้ปิดคำเตือนทั้งหมดของโปรเจกต์ และไม่ตั้ง lint baseline เพื่อซ่อน errors

Instrumentation 9 กรณีครอบคลุม Back ยกเลิก/ยืนยัน/เปิดใหม่, รีเซ็ตยกเลิก/ยืนยันและกู้รอบว่าง, alias children, ธีมเปลี่ยนครั้งเดียว, ภาษา, stop/resume, recovery+ธีมชนิดผิด, หมุน landscape/portrait และเริ่มใหม่หลังชนะ

รอบแรกของ instrumentation พบ test ของ finish() ตรวจสถานะ lifecycle เร็วเกินไป จึงแก้ให้รอสถานะ DESTROYED แบบมี timeout แล้วรันเต็มชุด 9 กรณีผ่าน ไม่มีการเปลี่ยนกติกาผลิตภัณฑ์เพื่อทำให้ test ผ่าน

รัน 3 กรณีซ้ำเพื่อเก็บภาพด้วย leaveApksInstalledAfterRun เพราะค่าเริ่มต้นของ Gradle ถอนแอปหลังจบ tests และลบ external files ของแอป ภาพที่แนบจึงเป็นกรณี Back, restart, theme และ force-stop ส่วนผลเต็มชุด 9 tests มาจากรอบเต็มที่รันผ่านก่อนหน้า

### คำสั่งตรวจซ้ำ

ต้องมี ANDROID_HOME และ JAVA_HOME ที่ตั้งค่าพร้อม:

```sh
./gradlew --offline --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
ANDROID_SERIAL=emulator-5560 ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest
python3 tools/wordward/verify_force_stop.py --serial emulator-5560 --adb "$ANDROID_HOME/platform-tools/adb"
```

หากเป็นครั้งแรกให้ตัด --offline เพื่อให้ Gradle ดาวน์โหลด dependency การรัน device tests จะใช้และเปลี่ยนข้อมูลรอบของแอปบน development emulator จึงไม่ควรใช้กับเครื่องที่มีข้อมูลสำคัญ

verify_force_stop.py ไม่เปลี่ยนนาฬิกาหรือล้าง app storage หากรอบชนะแล้วให้เริ่มใหม่เอง หรือส่ง --restart-won-round เพื่อยืนยันรีเซ็ตผ่าน UI ของรอบทดสอบก่อนเริ่ม smoke test

## หลักฐาน

- [Back confirmation](screenshots/05-storage/01-back-confirmation.png)
- [Restart confirmation](screenshots/05-storage/02-restart-confirmation.png)
- [รอบหลังเริ่มใหม่](screenshots/05-storage/03-restarted-round.png)
- [ธีมมืดและ draft หลัง recreate](screenshots/05-storage/05-dark-theme-restored.png)
- [เปิดใหม่หลัง force-stop](screenshots/05-storage/force-stop-restored.png)

## ขอบเขตของผลทดสอบ

- ผ่านบน emulator API 35 ยังไม่ได้ยืนยันบนมือถือจริงหรือ API 24–32
- การป้องกันข้ามวันใช้ Clock-based tests และ guard ของ UI ไม่เปลี่ยนนาฬิกาอุปกรณ์จริง
- คลังจริงปัจจุบัน 5,000 คำ / 31 โจทย์ ไม่เปลี่ยนข้อมูลหรือการตัดสินขอบเขต 1,000 คำเดิมของทีม
- วันเริ่มจริงและพฤติกรรมก่อนเริ่ม/หลังวันที่ 31 ยังคงนโยบายเดิมและรายการที่ทีมต้องยืนยัน
- Give up และสถิติสะสมไม่อยู่ในงานชุดนี้
- การทดสอบนี้ยังไม่แทนการตรวจรับรวมของทีมทุกอุปกรณ์และทุกข้อ rubric

## แหล่งที่มาและการใช้ AI

ใช้ Codex ช่วยวิเคราะห์และ implement โค้ด ทดสอบอัตโนมัติบน JVM/emulator และจัดทำเอกสาร ผู้ใช้เป็นผู้ merge PR #9/#10 และจะเป็นผู้ commit/push/เปิด PR ของงานชุดนี้เอง

อ้างอิง [Android Back APIs](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture), [Activity recreation](https://developer.android.com/guide/topics/resources/runtime-changes), [AndroidJUnitRunner](https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner) และสัญญาของ GameScreen ใน PR #10

