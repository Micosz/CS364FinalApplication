# คนที่ 5 · บันทึกเกมและประกอบแอป

## เปิดโปรเจกต์

แตก ZIP แล้วเปิดโฟลเดอร์ CS364FinalApplication ที่มี settings.gradle ใน Android Studio ใช้ Gradle JDK ของ Studio และ SDK Platform 37 รอ Sync เลือก app และ Emulator API 24 ขึ้นไป แล้วกด Run รายละเอียดอยู่ใน README.md ที่รากโปรเจกต์

## ไฟล์ที่รับผิดชอบ

- `app/src/main/java/th/ac/tu/cs364/wordward/GameStorage.java`
- `app/src/main/java/th/ac/tu/cs364/wordward/GameActivity.java`

## งานของคุณ

ตรวจและปรับการกู้รอบ รวมถึงกรณีข้อมูลเก่าที่อ่านไม่ได้และการกลับเข้าแอปหลังเที่ยงคืน ดูแลจุดเชื่อมทั้งสี่ส่วนและทดสอบเกมหลังรวม PR

บันทึกวันที่และรายการตามลำดับเป็นชุดเดียวกัน ให้ GameRound คำนวณยอดจากประวัติ เก็บภาษาแยกจากรอบ และให้ GameScreen รับผลหลังตัดสินและบันทึกแล้ว

## ลองบนฐานและตรวจงานหลังแก้

1. เดาและขอใบ้แล้ว force-stop เปิดใหม่ ตรวจคำ อันดับ ประเภท และยอดเดิม เปลี่ยนภาษาแล้วเปิดใหม่อีกครั้ง
2. จบรอบแล้วเปิดใหม่ในวันเดิมต้องยังชนะ เมื่อเปลี่ยนวันไทยต้องได้รอบใหม่ยอดศูนย์ ตรวจอีกครั้งหลังรวม PR ของเพื่อน

รัน `./gradlew :app:testDebugUnitTest :app:assembleDebug` หรือใช้ `.\gradlew.bat` แทน `./gradlew` ใน Windows PowerShell ไฟล์ test ใหม่วางใน `app/src/test/java/th/ac/tu/cs364/wordward/`

## ส่งงาน

สร้าง branch จาก Terminal ที่รากโปรเจกต์ก่อนเริ่มแก้

```sh
git switch -c member/05-storage
```

หลังตรวจ diff และ commit ไฟล์ของตนแล้ว ส่ง branch ด้วย

```sh
git push -u origin member/05-storage
```

เปิด PR เข้า main ใส่ชื่อไฟล์ที่แก้ สิ่งที่เปลี่ยน และผลที่ลองจริง คน 5 ช่วยตรวจการทำงานร่วมกันก่อนรวม
