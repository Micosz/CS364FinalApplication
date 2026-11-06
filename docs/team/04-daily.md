# คนที่ 4 · คำใบ้และโจทย์ประจำวัน

## เปิดโปรเจกต์

แตก ZIP แล้วเปิดโฟลเดอร์ CS364FinalApplication ที่มี settings.gradle ใน Android Studio ใช้ Gradle JDK ของ Studio และ SDK Platform 37 รอ Sync เลือก app และ Emulator API 24 ขึ้นไป แล้วกด Run รายละเอียดอยู่ใน README.md ที่รากโปรเจกต์

## ไฟล์ที่รับผิดชอบ

- `app/src/main/java/th/ac/tu/cs364/wordward/HintEngine.java`
- `app/src/main/java/th/ac/tu/cs364/wordward/DailyWordProvider.java`

## งานของคุณ

ปรับการสุ่มให้ขยับจากคำดีที่สุดเล็กน้อยตามขนาดคลัง ตกลงช่วงแรกและกรณีใกล้เฉลย ทดสอบการเลือกโจทย์ที่ขอบเที่ยงคืนตามเวลาไทยกับหลายเขตเวลา

next คืนคำที่มีอันดับดีกว่าที่เคยเห็น หรือ null เมื่อให้คำใบ้ไม่ได้ คน 3 นับครั้ง คน 5 บันทึกรอบ ส่วน secret ใช้วันที่กรุงเทพฯ และลำดับโจทย์ของคน 2

## ลองบนฐานและตรวจงานหลังแก้

1. เดา blue แล้ว pencil ในรอบ ocean กดใบ้หลายครั้ง คำใหม่ต้องใกล้กว่า blue และดีขึ้นทุกครั้งโดยไม่เผย #1
2. ทดสอบเวลา 2026-11-07T16:59:59Z กับ 17:00:00Z ต้องได้คนละวันไทย เพิ่ม HintEngineTest.java และ DailyWordProviderTest.java แทนการเปลี่ยนนาฬิกามือถือส่วนตัว

รัน `./gradlew :app:testDebugUnitTest :app:assembleDebug` หรือใช้ `.\gradlew.bat` แทน `./gradlew` ใน Windows PowerShell ไฟล์ test ใหม่วางใน `app/src/test/java/th/ac/tu/cs364/wordward/`

## ส่งงาน

สร้าง branch จาก Terminal ที่รากโปรเจกต์ก่อนเริ่มแก้

```sh
git switch -c member/04-daily
```

หลังตรวจ diff และ commit ไฟล์ของตนแล้ว ส่ง branch ด้วย

```sh
git push -u origin member/04-daily
```

เปิด PR เข้า main ใส่ชื่อไฟล์ที่แก้ สิ่งที่เปลี่ยน และผลที่ลองจริง คน 5 ช่วยตรวจการทำงานร่วมกันก่อนรวม
