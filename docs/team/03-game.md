# คนที่ 3 · กติกาการเดาและจบรอบ

## เปิดโปรเจกต์

แตก ZIP แล้วเปิดโฟลเดอร์ CS364FinalApplication ที่มี settings.gradle ใน Android Studio ใช้ Gradle JDK ของ Studio และ SDK Platform 37 รอ Sync เลือก app และ Emulator API 24 ขึ้นไป แล้วกด Run รายละเอียดอยู่ใน README.md ที่รากโปรเจกต์

## ไฟล์ที่รับผิดชอบ

- `app/src/main/java/th/ac/tu/cs364/wordward/GameRound.java`

## งานของคุณ

ปรับกติกาให้ GameRound เป็นจุดเดียวที่ตรวจการเพิ่มประวัติและสรุปผล เพิ่มทางเรียกสำหรับรับคำใบ้ที่ป้องกันการนับซ้ำ และทดสอบว่าการกระทำหลังชนะเพิ่มยอดไม่ได้

รับคำหลักหรือ null และ Ranking จากคน 2 คืน GuessResult ให้คน 5 ใช้แจ้งผล ให้ entries ตามลำดับเวลาและ sorted ตามอันดับ ถ้าเพิ่มเมธอดรับคำใบ้ ให้คน 5 ปรับจุดเรียกใน GameActivity

## ลองบนฐานและตรวจงานหลังแก้

1. เดา book ตามด้วย books และคำที่ไม่มีในคลัง ยอดรวมต้องเท่ากับ 1
2. กดใบ้ 1 ครั้ง เดาคำที่เพิ่งใบ้ซ้ำ แล้วเดาคำลับรูปพหูพจน์ ยอดรวมต้องเป็นผลรวมของเดาเองและคำใบ้ ปิดรอบแล้วเพิ่มยอดไม่ได้ เพิ่ม GameRoundTest.java

รัน `./gradlew :app:testDebugUnitTest :app:assembleDebug` หรือใช้ `.\gradlew.bat` แทน `./gradlew` ใน Windows PowerShell ไฟล์ test ใหม่วางใน `app/src/test/java/th/ac/tu/cs364/wordward/`

## ส่งงาน

สร้าง branch จาก Terminal ที่รากโปรเจกต์ก่อนเริ่มแก้

```sh
git switch -c member/03-game
```

หลังตรวจ diff และ commit ไฟล์ของตนแล้ว ส่ง branch ด้วย

```sh
git push -u origin member/03-game
```

เปิด PR เข้า main ใส่ชื่อไฟล์ที่แก้ สิ่งที่เปลี่ยน และผลที่ลองจริง คน 5 ช่วยตรวจการทำงานร่วมกันก่อนรวม
