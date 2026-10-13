# NovaTV — ทีวีจำลองจากคลัง Plex / Jellyfin / Emby (Android TV / Nvidia Shield)

แอปสร้าง "ช่องทีวี" จากหนัง/ซีรีส์ในเซิร์ฟเวอร์ของคุณ เล่นต่อเนื่องตามตารางเวลาแบบทีวีสด
(ช่อง Action / Comedy / Drama / Sci-Fi ฯลฯ และช่องตามเครือข่ายของซีรีส์) ธีมนีออนล้ำสมัย
ไอคอนช่องวาดเอง 27 แบบ

## Build เป็น APK (ทางที่แนะนำ: GitHub Actions)

1. สร้าง repo ใหม่บน GitHub (ถ้าอยากให้ Shield โหลดผ่านลิงก์สั้นๆ ให้ตั้งเป็น Public)
2. แตกไฟล์ zip นี้ แล้ว push ทั้งโฟลเดอร์ขึ้น branch `main`
   ```
   git init && git add . && git commit -m "NovaTV"
   git branch -M main
   git remote add origin https://github.com/<you>/novatv.git
   git push -u origin main
   ```
3. เปิดแท็บ **Actions** → workflow "Build APK" (ใช้เวลาราว 5–10 นาที)
4. โหลด `NovaTV.apk` จาก Artifacts ของรันนั้น (หรือจาก Releases → "latest")

ทางเลือก: เปิดโฟลเดอร์นี้ด้วย Android Studio แล้วกด Build → Build APK(s)

## ลงบน Shield

- เปิด Settings → Device Preferences → About → กด Build 7 ครั้ง → เปิด Developer options → Network debugging
- บนคอม: `adb connect <IP ของ Shield>` แล้ว `adb install NovaTV.apk`
- หรือลงแอป **Downloader** บน Shield แล้วพิมพ์ลิงก์ของ APK (ต้องอนุญาต "Install unknown apps")

## การใช้งาน

| ปุ่มรีโมต | ทำอะไร |
|---|---|
| ▲ / ▼ (หรือ CH+/CH-) | เปลี่ยนช่อง |
| OK / ◀ / Menu | เปิดผังรายการ (Guide) |
| ▶ (หรือปุ่ม CC) | เมนูเลือก **ซับไตเติล / เสียง** ของรายการที่กำลังฉาย |
| Info | แสดงแถบข้อมูลรายการที่กำลังฉาย |
| Back | ปิดผังรายการ / กลับ |

ในผังรายการเลื่อนด้วยปุ่มลูกศร กด OK เพื่อเข้าช่อง และปุ่ม SETTINGS มุมขวาบนเพื่อจัดการเซิร์ฟเวอร์

เริ่มครั้งแรก: เข้า Settings → Add server
- **Plex**: กด "Link with plex.tv" แล้วใส่โค้ดที่ plex.tv/link (หาเซิร์ฟเวอร์ให้อัตโนมัติ) หรือกรอก URL + token เอง
- **Jellyfin / Emby**: ใส่ URL (เช่น `http://192.168.1.10:8096`) กับชื่อผู้ใช้/รหัสผ่าน
- เพิ่มได้หลายเซิร์ฟเวอร์พร้อมกัน เรื่องซ้ำข้ามเซิร์ฟเวอร์จะถูกรวมเหลือเรื่องเดียว

## ซับไตเติล และ เสียง

- ซับที่ฝังในไฟล์ (SRT/ASS/PGS ฯลฯ) และไฟล์ซับแยกจากเซิร์ฟเวอร์ แสดงในเมนู ▶ ได้เลย
- Settings → PLAYBACK ตั้งภาษาซับ/เสียงที่ชอบ (Thai / English / Japanese / Korean / Chinese) แอปจะเลือกให้อัตโนมัติทุกรายการ
- เลือกซับจากเมนู ▶ ครั้งเดียว แอปจะจำภาษานั้นไว้ใช้กับรายการถัดไป

## Transcode ผ่านเซิร์ฟเวอร์ (Settings → PLAYBACK)

| โหมด | ทำอะไร |
|---|---|
| DIRECT PLAY (ค่าเริ่มต้น) | เล่นไฟล์ต้นฉบับตรงๆ |
| SMART | เซิร์ฟเวอร์แปลงเฉพาะส่วนที่ Shield เล่นไม่ได้ (เช่นเสียง DTS, bitrate เกินกำหนด) นอกนั้นส่งต่อเฉยๆ |
| ALWAYS TRANSCODE | แปลงเป็น H.264 / AAC ทุกครั้งตาม bitrate ที่ตั้ง (เหมาะกับเน็ตช้า/ดูนอกบ้าน) |

- ใช้ HLS ของ Jellyfin / Emby / Plex ในโหมดนี้ซับจะถูก "ฝังติดภาพ" โดยเซิร์ฟเวอร์ (รองรับซับแบบภาพ PGS ด้วย)
- ถ้าเซิร์ฟเวอร์ transcode ไม่ผ่าน แอปจะสลับเป็น Direct Play ของรายการนั้นให้อัตโนมัติ
- ต้องเปิด Hardware/Software transcoding ในเซิร์ฟเวอร์ (Plex ต้องมี Plex Pass สำหรับ HW transcoding)

## ข้อจำกัดของเวอร์ชันนี้

- ยังไม่มีหน้าสร้างช่องกำหนดเอง
- พารามิเตอร์ transcode เขียนจากเอกสาร API ของแต่ละเซิร์ฟเวอร์ ยังไม่เคยทดสอบกับเซิร์ฟเวอร์จริง ถ้าโหมด SMART/ALWAYS ใช้ไม่ได้ให้บอกว่าเซิร์ฟเวอร์ตัวไหน จะได้ปรับ
- ตารางเวลาวนตามเวลาจริงของเครื่อง (ทุกเครื่องที่ใช้คลังเดียวกันจะเห็นตารางเหมือนกัน)
- โค้ดชุดนี้เขียนโดยไม่ได้คอมไพล์จริง ถ้า build ครั้งแรกขึ้น error ให้ส่งล็อกจาก GitHub Actions มาแก้ต่อได้

## โครงสร้าง

```
app/src/main/java/app/novatv/
  net/        Http, EmbyLike (Jellyfin+Emby), Plex (+ plex.tv/link), Streams (transcode URL + ภาษา)
  ChannelBuilder.kt   สร้างช่อง + Scheduler (ตารางรายการจำลอง)
  AppViewModel.kt     สถานะแอป / ซิงก์คลัง
  ui/         PlayerScreen, GuideOverlay, SettingsScreen, ไอคอน (IconData.kt)
```
