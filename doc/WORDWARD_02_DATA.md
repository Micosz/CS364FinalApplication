# ชุดข้อมูล Wordward งาน 02

รุ่น **`wordward-en-5000-31-v1`** มี **5,000 คำหลักภาษาอังกฤษ, 31 คำลับ และ 2,772 รูปคำเพิ่มเติม** ตารางอันดับทั้งสองไฟล์รวม 1,225,582 ไบต์ก่อนบีบอัด APK ดูขั้นตอนและเกณฑ์ตั้งต้นที่ [แผนงาน 02](WORDWARD_02_IMPLEMENTATION_PLAN.md)

## สร้างข้อมูลซ้ำ

ใช้ Python 3.9 ขึ้นไป รันจากราก repo เตรียมไฟล์ต้นทางข้างนอก repo และเลือกโฟลเดอร์ปลายทางแยกหากต้องการเทียบผลก่อนแทนที่ assets สคริปต์ใช้สมาชิก 100 มิติจาก ZIP โดยตรง

| อินพุต | SHA-256 ของไฟล์ต้นทาง |
|---|---|
| [Stanford GloVe 6B ZIP](https://huggingface.co/stanfordnlp/glove/resolve/main/glove.6B.zip) ขนาด 862,182,753 ไบต์ เลือก `glove.6B.100d.txt` | `6471382cdd837544bf3ac72497a38715e845897d265b2b424b4761832009c837` |
| [Princeton WordNet 3.0](https://wordnetcode.princeton.edu/3.0/WordNet-3.0.tar.gz) | `640db279c949a88f61f851dd54ebbb22d003f8b90b85267042ef85a3781d3a52` |
| [wordfreq 3.1.1 wheel](https://pypi.org/project/wordfreq/3.1.1/#files) | `4b1c6ecffc6198be3396d5cf871c4423ca71c907c231348d352dd54d62b97473` |

```sh
python3 -m venv /tmp/wordward-venv
/tmp/wordward-venv/bin/python -m pip install -r tools/wordward/requirements.txt
/tmp/wordward-venv/bin/python tools/wordward/generate.py \
  --glove /path/to/glove.6B.zip \
  --wordnet /path/to/WordNet-3.0.tar.gz \
  --out /tmp/wordward-generated --top /tmp/wordward-top50.tsv
cmp app/src/main/assets/puzzles.tsv /tmp/wordward-generated/puzzles.tsv
cmp app/src/main/assets/aliases.tsv /tmp/wordward-generated/aliases.tsv
./gradlew --offline :app:testDebugUnitTest :app:assembleDebug
```

Gradle ต้องมี SDK และ dependencies ในเครื่องแล้วหากใช้ `--offline` สคริปต์ตรวจ hash ของ GloVe และ WordNet ก่อนอ่าน สำหรับ wordfreq ตารางด้านบนให้ hash wheel เพื่อใช้ยืนยันไฟล์ที่ดาวน์โหลดได้จาก PyPI

- ลำดับผู้สมัครเริ่มจากคำลับที่ตรึงไว้ คำเสริมหลายหมวด และคำเป้าหมายของ alias ที่ตรวจแล้ว จากนั้นใช้ความถี่ภาษาอังกฤษ wordfreq แบบ large ลำดับต้น 40,000 คำ ตรวจผ่าน lemma ตัวพิมพ์เล็กของ WordNet และรายการตัดคำ คัดต่อจาก 19,852 ผู้สมัคร มี 7 คำที่ไม่พบเวกเตอร์และไม่พบเวกเตอร์เสียในผู้สมัครที่อ่าน จึงเลือก 5,000 คำที่มีเวกเตอร์ครบ 100 มิติ
- รูปคำใช้ข้อยกเว้นนามของ WordNet และกฎพหูพจน์เฉพาะคำนามที่ระบุในสคริปต์ กรองรูปที่ไม่อยู่ในชุดความถี่ 100,000 คำแรก รูปชนกับ lemma หรือมีหลายเป้าหมาย เพิ่มคู่ที่ตรวจแล้วจาก `reviewed-aliases.tsv` เช่น `shoes → shoe` และ `rooms → room` ชุดนี้รองรับรูปคำที่อยู่ในไฟล์เท่านั้น
- คำนวณ cosine ด้วยเวกเตอร์ที่ normalize แล้ว รวมผลคูณด้วย `math.fsum` ใช้คะแนนปัด 12 ตำแหน่งเพื่อเรียงจากมากไปน้อย กรณีคะแนนเท่ากันเรียง ASCII วางคำลับเป็นอันดับ 1 ไม่มีการสุ่ม รายการคำลับอยู่ใน `tools/wordward/secrets.txt` สั่ง `--top` เพื่อส่งออก 50 อันดับแรกสำหรับตรวจเพิ่มเติม

| ผลลัพธ์ | SHA-256 |
|---|---|
| `puzzles.tsv` | `9cd29f808a36d15d11fcc489af23c79b08e17d136198f03e36a80e59c2261711` |
| `aliases.tsv` | `3d2dafd56dc2470b2a694744b032f7b9f6880de9ee6e3a88c9bf32c72e350ccf` |

## ตรวจคุณภาพอันดับ

ตรวจรายงาน 50 อันดับแรกทั้ง 31 โจทย์ในรอบคัดชุดคำ โดยดูคำใกล้ที่ผู้เล่นนึกต่อได้ คำหลายความหมาย คำจากบริบทเฉพาะ และหมวดที่ขาด มีการเพิ่มคำเพื่อกระจายหมวด กรองชื่อบางรายการ และให้รูปพหูพจน์ทั่วไปใช้คำหลักร่วมกัน ผลนี้เป็นการตรวจจากรายการอันดับ ยังเหลือการทดลองกับผู้เล่นจริงเพื่อปรับความยากและทบทวนอคติจาก corpus

| ตัวอย่าง | ผลและข้อสังเกต |
|---|---|
| ocean | `sea` #2, `blue` #441, `pencil` #4855 ช่วยทดสอบทั้งคำตรง คำทางอ้อม และคำไกล |
| apple | `pear` #7 และ `fruit` #19 แต่ `software` #2 สะท้อนความหมายด้านเทคโนโลยีจาก corpus |
| clock | `time` #8 และ `watch` #9 โดยมีคำด้านสัญญาณและเครื่องจักรนำหน้า |
| garden, kitchen, window | มีเส้นทางจากสถานที่และสิ่งของ เช่น park, dining, door |
| pillow, rainbow | คำกลุ่มเครื่องนอนและสียังใช้เดาต่อได้ แต่บริบทอย่าง shower และ banner อาจทำให้โจทย์ยากขึ้น |

## สิทธิ์ใช้งานและการส่งต่อ

เก็บไฟล์เครดิตและใบอนุญาตคู่กับชุดข้อมูลใน [assets/notices](../app/src/main/assets/notices/) และบรรจุใน APK ส่วนตารางข้อมูลที่ดัดแปลงแจกภายใต้ CC BY-SA 4.0 พร้อมเครดิตชุดความถี่ของ Robyn Speer ผู้จัดทำ SUBTLEX และแหล่งใน NOTICE แยกจาก Apache 2.0 ของโค้ด wordfreq ส่วนเวกเตอร์ pretrained ของ GloVe ใช้ PDDL และข้อมูลคำ/รูปคำจาก Princeton ใช้ WordNet License ของตนเอง [wordfreq NOTICE](https://github.com/rspeer/wordfreq/blob/master/NOTICE.md) · [GloVe pretrained vectors](https://github.com/stanfordnlp/GloVe/blob/master/README.md) · [WordNet License](https://wordnet.princeton.edu/license-and-commercial-use)

- **งาน 04** ใช้ลำดับ 31 โจทย์ตามไฟล์ `secrets.txt` วันเริ่มจริงและนโยบายก่อนเริ่ม/หลังวันที่ 31 รอทีมยืนยัน พฤติกรรมวนใน `DailyWordProvider` ยังเป็นของฐาน ข้อมูล common-word แยกสำหรับคำใบ้เป็นข้อเสนอที่ต้องตกลงก่อนเพิ่ม
- **งาน 05** ใช้ `wordward-en-5000-31-v1` เป็นรหัสชุดข้อมูลอ้างอิง รหัสนี้บันทึกใน notice และเอกสาร การอ่านรหัสเพื่อผูกเซฟและการย้ายเซฟเป็นงานเชื่อมระบบของคน 5
- **งาน 01/03/05** เชื่อมการเริ่มรอบวันนี้ใหม่ตามเจ้าของไฟล์ การอ่านอันดับด้วยคำลับและรุ่นข้อมูลเดิมจะได้อันดับชุดเดิม `canonical`, `daily`, `rankFor`, `rank`, `size` และ `wordAt` ใช้สัญญาเดิม
- [ข้อตกลงทีม](../docs/team/TEAM_AGREEMENTS.md) ยังระบุ 1,000 คำ ให้ทีมใช้ค่าล่าสุด 5,000 คำในการตรวจงาน 02 และประสานเกณฑ์ร่วมก่อนรวม branch

## ผลตรวจจริงของชุด v1 · 14 พฤศจิกายน 2569

| การตรวจ | ผล |
|---|---|
| สร้างซ้ำบน Mac ด้วย Python 3.9.6 | เทียบด้วย `cmp` แล้วผล TSV ทั้งสองไฟล์ตรงกันทุกไบต์ |
| Gradle Wrapper แบบออฟไลน์ | `testDebugUnitTest` และ `assembleDebug` ผ่าน ชุดรวม 9 tests (6 จาก fixture เดิม + 3 ทดสอบข้อมูลจริงและข้อมูลเสีย) |
| ขนาด assets | puzzles 1,182,274 ไบต์ และ aliases 43,308 ไบต์ |
| Android Emulator API 37.0 arm64 แบบชั่วคราว | เปิดโหมดเครื่องบินและปิด Wi‑Fi แล้ว เดา blue ได้ #111 ของโจทย์วันทดสอบ ขอใบ้ได้อีก 1 ครั้ง และคำที่ไม่มีในคลังไม่เพิ่มยอด |
| เวลาอ่าน TSV สองไฟล์ | แยกวัดใน ART ด้วย `app_process` โดยเริ่มจับเวลาก่อนสร้าง `WordRanker` และหยุดเมื่อ constructor คืนค่า รุ่นเดิม 2,040 / 1,328 / 1,249 ms รุ่นที่ใช้ regex ซ้ำ 126 / 143 / 144 ms |
| เปิดแอปและหน่วยความจำ | วัดด้วย `am start -W` หลัง force-stop รอบแรกหลังล้างข้อมูลทดสอบรายงาน WaitTime 10,130 ms โดยไม่ระบุ LaunchState ว่า COLD อีกสองรอบ COLD ได้ TotalTime 1,860 และ 1,743 ms หลังเปิดสำเร็จ `dumpsys meminfo` รายงาน PSS 67,192 KiB และ RSS 180,556 KiB เป็นค่าหนึ่งจุดหลังโหลด |

ผลเวลาอ่าน TSV ใช้ไฟล์ทดสอบในเครื่อง Android โดยตรง ส่วนเวลาเปิดแอปอ่าน assets จาก APK จึงเป็นคนละการวัด แอปตอบสนองต่อช่องพิมพ์ ปุ่มเดา และ Hint หลังเปิดเสร็จ รอบนี้ยังไม่มีผลวัด frame time ระหว่างโหลด การวัด peak memory หรือผลจากมือถือจริง การเริ่มรอบวันนี้ใหม่รอส่วน UI/กติกา/การบันทึกจากงานอื่น

Debug APK หลังรวมไฟล์เครดิตมีขนาด 2926998 ไบต์
