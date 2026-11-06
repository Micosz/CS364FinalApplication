# คนที่ 2 · คลังคำและอันดับความหมาย

## เปิดโปรเจกต์

แตก ZIP แล้วเปิดโฟลเดอร์ CS364FinalApplication ที่มี settings.gradle ใน Android Studio ใช้ Gradle JDK ของ Studio และ SDK Platform 37 รอ Sync เลือก app และ Emulator API 24 ขึ้นไป แล้วกด Run รายละเอียดอยู่ใน README.md ที่รากโปรเจกต์

## ไฟล์ที่รับผิดชอบ

- `app/src/main/java/th/ac/tu/cs364/wordward/WordRanker.java`
- `app/src/main/assets/puzzles.tsv`
- `app/src/main/assets/aliases.tsv`

## งานของคุณ

เพิ่มคำเดาทางตรงและทางอ้อม เช่น river หรือ green พร้อมตรวจความสมเหตุสมผลของอันดับแต่ละโจทย์ ขยายรูปพหูพจน์ที่รับได้ ถ้าใช้ข้อมูลภายนอกให้ระบุที่มาและสิทธิ์แจกจ่าย

ใน puzzles.tsv แต่ละบรรทัดเป็น คำลับ → TAB → ทุกคำเรียงตามอันดับ โดยคำแรกคือคำลับและทุกโจทย์ต้องมีชุดคำหลักเดียวกัน ลำดับบรรทัดคือลำดับโจทย์ ใน aliases.tsv แต่ละบรรทัดเป็น รูปคำ → TAB → คำหลัก คง canonical, rankFor, daily และเมธอด Ranking ที่คน 3–4 เรียกได้ หากเปลี่ยนสองโจทย์เดิมให้ปรับชุดตรวจร่วมโดยประสานทีม

## ลองบนฐานและตรวจงานหลังแก้

1. ปิดอินเทอร์เน็ตของ Emulator ลอง blue, pencil และคำใหม่ในทั้งสองโจทย์ ดูอันดับเทียบไฟล์
2. ลอง children เทียบ child และคำที่ไม่มีในคลัง ยอดครั้งสำหรับคำซ้ำและคำนอกคลังต้องเท่าเดิม เพิ่ม WordRankerTest.java เพื่อตรวจข้อมูลที่เพิ่ม

รัน `./gradlew :app:testDebugUnitTest :app:assembleDebug` หรือใช้ `.\gradlew.bat` แทน `./gradlew` ใน Windows PowerShell ไฟล์ test ใหม่วางใน `app/src/test/java/th/ac/tu/cs364/wordward/`

## ส่งงาน

สร้าง branch จาก Terminal ที่รากโปรเจกต์ก่อนเริ่มแก้

```sh
git switch -c member/02-words
```

หลังตรวจ diff และ commit ไฟล์ของตนแล้ว ส่ง branch ด้วย

```sh
git push -u origin member/02-words
```

เปิด PR เข้า main ใส่ชื่อไฟล์ที่แก้ สิ่งที่เปลี่ยน และผลที่ลองจริง คน 5 ช่วยตรวจการทำงานร่วมกันก่อนรวม
