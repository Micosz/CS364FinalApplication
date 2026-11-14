# Wordward · ฐานทีม CS364

แอป Android Java สำหรับเกมเดาคำอังกฤษจากอันดับความหมาย ทุกคนได้โจทย์เดียวกันตามวันที่ไทย ชุดข้อมูลของงาน 02 มี **31 โจทย์ 5,000 คำหลัก และ 2,772 รูปคำเพิ่มเติม** ใช้ตารางอันดับ TSV เดียวกันทั้งการเดาและคำใบ้ ดู [ที่มาและวิธีสร้างข้อมูลซ้ำ](doc/WORDWARD_02_DATA.md) แอปบันทึกรอบและภาษาไว้ในเครื่อง

## เปิดและรัน

1. แตก ZIP ทั้งชุด ถ้าใช้ Android Studio ให้เลือก **File → Open** แล้วเลือกโฟลเดอร์ **CS364FinalApplication** ที่มี `settings.gradle` และ `gradlew`
2. ใช้ Android Studio ที่รองรับ **Android Gradle Plugin 9.3.3** ตั้ง Gradle JDK เป็น JBR ที่มากับ Studio และติดตั้ง **Android SDK Platform 37** จาก SDK Manager โปรเจกต์ใช้ Gradle Wrapper 9.5.0 และดาวน์โหลดไลบรารีจาก Google Maven / Maven Central ในการ Sync ครั้งแรก
3. กด **Sync Project with Gradle Files** เลือกโมดูล **app** และ Emulator หรือมือถือ Android API 24 ขึ้นไป แล้วกด **Run ▶** หลังติดตั้งเล่นออฟไลน์ได้
4. ลองเดา `blue` ขอคำใบ้ ปิดแล้วเปิดใหม่ และสลับภาษา ในโจทย์ตัวอย่าง `ocean` คำว่า `blue` อยู่ #441 ส่วนวันเริ่มจริงและพฤติกรรมก่อนเริ่ม/หลังวันที่ 31 รอทีมยืนยันตาม [ข้อตกลงทีม](docs/team/TEAM_AGREEMENTS.md)

จาก Terminal ที่รากโปรเจกต์ ใช้ `./gradlew :app:testDebugUnitTest :app:assembleDebug` บน Windows PowerShell ใช้ `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` ถ้าเครื่องแตก ZIP แล้วสิทธิ์รันบน macOS/Linux หาย ให้ใช้ `chmod +x gradlew` ก่อน

## ใบงานของทีม

| คน | ไฟล์หลัก | ใบงาน |
|---|---|---|
| 1 | GameScreen และ XML หน้าจอ/ภาษา | [หน้าจอเกมและภาษา](docs/team/01-ui.md) |
| 2 | WordRanker และ assets | [คลังคำและอันดับ](docs/team/02-words.md) |
| 3 | GameRound | [กติกาและผลรอบ](docs/team/03-game.md) |
| 4 | HintEngine และ DailyWordProvider | [คำใบ้และโจทย์ประจำวัน](docs/team/04-daily.md) |
| 5 | GameStorage และ GameActivity | [บันทึกและประกอบแอป](docs/team/05-storage.md) |

GameActivity รับเหตุการณ์จาก GameScreen → ขอคำหลักและตารางอันดับจาก WordRanker → ส่งให้ GameRound ตัดสินและนับครั้ง → บันทึกผ่าน GameStorage → ส่งรอบกลับไปแสดง ถ้าเป็นคำใบ้ HintEngine เลือกคำให้ ส่วน DailyWordProvider เลือกโจทย์จากวันที่ Asia/Bangkok

## แยก branch และรวมงาน

ZIP แต่ละชุดมีสำเนา Git ที่ชี้ origin ไปยัง [คลังของทีม](https://github.com/Micosz/CS364FinalApplication) แตกแล้วเปิด Terminal ใน **CS364FinalApplication** ตรวจ `git status` แล้วใช้คำสั่งสร้าง branch ในใบงาน ควรสร้างก่อนแก้ ถ้าเผลอแก้ไปแล้วแต่ยังไม่ได้ commit ให้ใช้ `git switch -c ชื่อ-branch` จาก main ได้เลย งานที่แก้จะติดไปบน branch ใหม่

1. ทำงานเฉพาะไฟล์ในใบงาน ถ้าต้องเปลี่ยนสัญญาระหว่างส่วน ให้ตกลงกับเจ้าของอีกส่วนก่อน
2. ทดสอบตามใบงาน ใช้ `git diff` ตรวจสิ่งที่เปลี่ยน แล้ว `git add` เฉพาะไฟล์ของตน ตามด้วย `git commit -m "สรุปสิ่งที่ทำ"`
3. Push branch ตามคำสั่งในใบงาน เปิด Pull Request โดยเลือก base เป็น `main` ให้เพื่อนตรวจแล้วค่อย merge ผู้ใช้ต้องมีสิทธิ์ push ใน repo หรือเปิด PR จาก fork ของตน
4. ก่อนรวม PR ถัดไป นำ main ล่าสุดเข้า branch งาน (`git fetch origin` และ `git merge origin/main`) แล้วทดสอบอีกครั้ง คน 5 ตรวจ flow ข้ามส่วนหลังรวม
5. หากโปรแกรมแตก ZIP ไม่เก็บโฟลเดอร์ซ่อนและ `git status` แจ้งว่าไม่พบ repository ให้ clone จากลิงก์ทีม สร้าง branch แล้วคัดลอกเฉพาะไฟล์ของตนที่แก้ไปยัง clone ก่อน commit

เก็บ StarterTest เป็นชุดตรวจร่วม หากสมาชิกจะเพิ่ม tests ให้สร้างไฟล์ใหม่ตามชื่อที่แนะนำในใบงาน วิธีนี้ช่วยให้แต่ละ PR มีขอบเขตชัดเจน
