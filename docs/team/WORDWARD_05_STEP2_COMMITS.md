# แบ่ง commit งาน Wordward ส่วนที่ 5 ขั้นที่ 2

ใช้ branch member/05-integration ซึ่งเริ่มจาก main หลัง merge PR #9 และ #10 แล้ว ไฟล์ทั้งหมดถูกเตรียมไว้แต่ยังไม่ stage/commit/push

เปิด Terminal ของ VS Code:

```sh
cd /Users/paponpatmeevon/Desktop/CS364FinalApplication
git branch --show-current
git status
```

ควรเห็น member/05-integration ทำตามลำดับด้านล่าง และตรวจ git diff --cached --stat ก่อนยืนยันแต่ละ commit

## Commit 1 ประสานกติกา เวลา และการบันทึก พร้อม unit tests

```sh
git add app/src/main/java/th/ac/tu/cs364/wordward/GameSession.java \
  app/src/test/java/th/ac/tu/cs364/wordward/GameSessionTest.java \
  app/src/test/java/th/ac/tu/cs364/wordward/MemoryPreferences.java \
  app/src/test/java/th/ac/tu/cs364/wordward/GameStorageTest.java
git diff --cached --stat
git commit -m "feat(game): coordinate daily rounds and confirmed restarts"
```

ย้าย preferences fake เดิมออกเป็น test utility ร่วม โดยไม่เปลี่ยน tests เดิม 20 กรณี

## Commit 2 เตรียมตัวจัดการยืนยันก่อนออก

```sh
git add app/src/main/java/th/ac/tu/cs364/wordward/ExitConfirmation.java
git diff --cached --stat
git commit -m "feat(navigation): add native exit confirmation handler"
```

ตัวจัดการนี้จะถูกเรียกจริงเมื่อเชื่อม Activity ใน commit 4

## Commit 3 เตรียมการตั้งค่าภาษาและธีมก่อนสร้างหน้าจอ

```sh
git add app/src/main/java/th/ac/tu/cs364/wordward/GameAppearance.java \
  app/src/test/java/th/ac/tu/cs364/wordward/GameAppearanceTest.java \
  app/src/main/AndroidManifest.xml
git diff --cached --stat
git commit -m "feat(settings): prepare persisted locale and theme configuration"
```

ใช้ Theme.Wordward ของคน 1 และเปิด Back callback ของแพลตฟอร์ม การนำ saved configuration มาใช้ใน Activity อยู่ใน commit 4

## Commit 4 เชื่อม callbacks กับ Activity และ lifecycle

```sh
git add app/src/main/java/th/ac/tu/cs364/wordward/GameActivity.java
git diff --cached
git commit -m "feat(game): connect UI callbacks and preserve lifecycle state"
```

รวมจุดเชื่อมรีเซ็ต ธีม Back คำซ้ำ recovery และ draft ในไฟล์เจ้าของส่วนที่ 5 เดียวกัน

## Commit 5 ทดสอบ Android และ force-stop

```sh
git add app/build.gradle \
  app/src/androidTest/java/th/ac/tu/cs364/wordward/GameActivityIntegrationTest.java \
  tools/wordward/verify_force_stop.py
git diff --cached --stat
git commit -m "test(android): cover confirmations recreation and process restart"
```

## Commit 6 เอกสารและภาพหลักฐาน

```sh
git add DESIGN.md \
  docs/team/WORDWARD_05_STORAGE_STEP2.md \
  docs/team/WORDWARD_05_STEP2_COMMITS.md \
  docs/team/screenshots/05-storage
git diff --cached --stat
git commit -m "docs(storage): record integration contracts and device evidence"
```

## ตรวจแล้ว push และเปิด PR

```sh
git diff --check
git log --oneline origin/main..HEAD
git status
git push -u origin member/05-integration
```

เปิด PR ใหม่ base main / compare member/05-integration ใช้ชื่อและ description จาก template ที่เตรียมให้ ไม่ต้องเปิด PR #9 เดิมอีก และไม่ต้อง commit งานเพื่อนซ้ำ เพราะอยู่ใน main แล้ว

