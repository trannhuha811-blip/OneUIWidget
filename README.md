# One UI Widget - Lịch Âm Can Chi & Thời Tiết Samsung Galaxy

Ứng dụng Android Native (Kotlin) cung cấp Home Screen Widget 4x4 / Toàn màn hình chuẩn phong cách Samsung One UI (tương thích hoàn hảo với Samsung Galaxy S26, One UI 7/8, Android 8.0 - 15, Target SDK 35).

---

## 📸 5 Khu Vực Tính Năng Trên Widget (Khớp 100% Ảnh Mẫu)

```
+-------------------------------------------------------------------------------+
| [KHU VỰC 1: THỜI TIẾT HIỆN TẠI]      | [KHU VỰC 2: LỊCH DƯƠNG & ÂM]           |
| Chào buổi sáng                      | 🔄              THÁNG 11               |
| 28°                                 | 23                                     |
| Có mây rải rác • 📍 P. Thạnh Mỹ Tây | Thứ Hai                                |
| [🌅 28°C]   [🌇 27°C]               | [ 15/10 Bính Ngọ ] (Pill trắng chữ đen)|
+-------------------------------------+-----------------------------------------+
| [KHU VỰC 3: SỰ KIỆN & NGÀY LỄ VN]   | [KHU VỰC 4: LƯỚI LỊCH THÁNG]           |
| ● Lễ Giáng sinh (Noel)              | T2   T3   T4   T5   T6   T7   CN        |
|   25-12 All Day • Ngày lễ Việt Nam  | 26   27   28   29   30   31   1         |
| ● Tết Dương lịch 2027               | 2    3    4    5    6    7    8         |
|   01-01 All Day • Ngày lễ Việt Nam  | 9    10   11   12   13   14   15        |
|                                     | 16   17   18   19   20   21   22        |
|                                     | [23] 24   25   26   27   28   29        |
|                                     | 30   1    2    3    4    5    6         |
+-------------------------------------+-----------------------------------------+
| [KHU VỰC 5: HÀNG DƯỚI CÙNG - DỰ BÁO 6 NGÀY TỚI + HÔM NAY]                     |
| 🌤️ 32°C    | THỨ 3   THỨ 4   THỨ 5   THỨ 6   THỨ 7   CN                     |
| 27° 28°    |  ⛅       ☀️      🌧️      ⛅      ⛈️      ☀️                      |
|            | ↓25°    ↓26°    ↓25°    ↓24°    ↓25°    ↓26°                    |
|            | ↑33°    ↑34°    ↑32°    ↑33°    ↑31°    ↑35°                    |
+-------------------------------------------------------------------------------+
```

### 1. Khu Vực 1: Góc Trên Bên Trái – Thời Tiết Hiện Tại
- **Lời chào tự động theo giờ:**
  - `05:00 - 10:59`: "Chào buổi sáng"
  - `11:00 - 12:59`: "Chào buổi trưa"
  - `13:00 - 17:59`: "Chào buổi chiều"
  - `18:00 - 04:59`: "Chào buổi tối"
- **Nhiệt độ chính:** Cỡ chữ 44sp cực lớn, thanh mảnh (`sans-serif-light`), đổ bóng nhẹ `shadowRadius`.
- **Mô tả & Vị trí:** Trạng thái tiếng Việt ("Có mây rải rác") + Geocoder lấy tên Phường/Xã ("P. Thạnh Mỹ Tây") kèm icon ghim vị trí.
- **2 Pill Badge:** Viền trắng mảnh nằm ngang hiển thị icon mặt trời mọc/lặn (`ic_sunrise`, `ic_sunset`) kèm nhiệt độ/giờ tương ứng.

### 2. Khu Vực 2: Góc Trên Bên Phải – Lịch Dương & Lịch Âm Việt Nam
- **Tháng & Ngày Dương:** Chữ in hoa `THÁNG 11` bên trên kèm nút làm mới dữ liệu nhanh, số ngày cực lớn (`23`) in đậm với hiệu ứng đổ bóng mờ.
- **Thứ & Lịch Âm Can Chi:** Chữ `Thứ Hai`, bên dưới là khung bo tròn nền trắng đặc, chữ đen đậm hiển thị Ngày/Tháng Âm lịch + Can Chi của ngày (`15/10 Bính Ngọ`).
- **Thuật toán Lịch Âm:** `LunarCalendarConverter.kt` tích hợp chuẩn thuật toán thiên văn Hồ Ngọc Đức (Hà Nội, múi giờ UTC+7), chạy offline 100%, tính chính xác ngày, tháng, năm âm lịch, tháng nhuận và Can Chi Ngày/Tháng/Năm.

### 3. Khu Vực 3: Giữa Bên Trái – Sự Kiện & Ngày Lễ Việt Nam
- Truy vấn `CalendarContract.Events` trên máy người dùng để lấy 2 sự kiện sắp tới.
- Tự động tích hợp sẵn danh sách ngày lễ truyền thống & quốc gia của Việt Nam (Tết Dương lịch, Giáng sinh, Tết Nguyên Đán, Giỗ Tổ Hùng Vương, Quốc khánh, v.v.) làm dữ liệu dự phòng chuẩn xác.
- Hiển thị theo dạng timeline: Vạch kẻ dọc mảnh bên trái, chấm tròn trắng đầu sự kiện, tiêu đề in đậm, dòng ngày tháng in nghiêng.

### 4. Khu Vực 4: Giữa Bên Phải – Lưới Lịch Tháng (Calendar Grid)
- Render trực tiếp bằng `CalendarGridRenderer.kt` ra Bitmap độ phân giải cao, sắc nét, không bị lag.
- 7 cột từ `T2` đến `CN` (tuần bắt đầu từ Thứ Hai).
- Cột `CN` tô chữ màu đỏ mềm (`#FF4D4D`).
- Các ngày thuộc tháng trước/tháng sau được làm mờ nhẹ (`alpha = 0.35`).
- Ngày hiện tại được khoanh bằng một vòng tròn nền đỏ nổi bật (`#E53935`), số ngày in đậm màu trắng.

### 5. Khu Vực 5: Hàng Dưới Cùng – Dự Báo 6 Ngày Tới + Hôm Nay
- **Cột trái ngoài cùng:** Icon thời tiết thực tế cỡ lớn kèm nhiệt độ hiện tại (`32°C`) và min/max trong ngày (`27°  28°`).
- **6 cột bên phải:** 6 ngày tiếp theo (`THỨ 3, THỨ 4, THỨ 5, THỨ 6, THỨ 7, CN`), mỗi cột hiển thị tên thứ màu vàng nhạt (`#FFE082`), icon thời tiết trực quan, nhiệt độ thấp nhất (`↓ 25°`) và cao nhất (`↑ 33°`).

---

## 🛠️ Công Nghệ & Kiến Trúc

- **Ngôn ngữ:** Kotlin 2.0.21.
- **Android SDK:** Min SDK 26 (Android 8.0 Oreo), Target SDK 35 (Android 15 / Samsung One UI 7 & 8).
- **Widget Engine:** `AppWidgetProvider` + `RemoteViews` + Dynamic Canvas Rendering cho calendar grid bitmap.
- **Cập nhật nền:**
  - `WorkManager`: Chu kỳ 30 phút/lần (`WidgetUpdateWorker`) tải dữ liệu thời tiết mới nhất.
  - `AlarmManager`: Chuông báo chính xác lúc `00:00:01` hàng ngày (`MidnightAlarmReceiver`) để nhảy lịch, đổi thứ, đổi ngày âm ngay tức thì.
  - `Nút chạm thủ công`: Icon `ic_refresh` trên góc widget để ép làm mới dữ liệu ngay lập tức.
- **Thời tiết API (Miễn phí, không cần Key):** Open-Meteo API (`https://api.open-meteo.com/v1/forecast`) kèm cache SharedPreferences lưu trữ ngoại tuyến.

---

## 🚀 Hướng Dẫn Biên Dịch & Cài Đặt File .APK

### Cách 1: Biên dịch bằng dòng lệnh Gradle
```bash
# Di chuyển vào thư mục dự án
cd "C:\Users\Tra Dao Cam Sa\.gemini\antigravity\scratch\OneUIWidget"

# Build debug APK trên Windows:
.\gradlew.bat assembleDebug

# Hoặc trên Linux/macOS:
chmod +x gradlew
./gradlew assembleDebug
```
File APK đầu ra sẽ nằm tại:
`app/build/outputs/apk/debug/app-debug.apk`

### Cách 2: Biên dịch qua PowerShell Script tự động
```powershell
.\build.ps1
```

### Cách 3: Mở trong Android Studio
1. Mở **Android Studio** -> Chọn **Open**.
2. Chọn thư mục `OneUIWidget`.
3. Đợi Gradle Sync hoàn tất, sau đó chọn **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)** hoặc nhấn nút **Run** (Shift+F10) để cài trực tiếp vào điện thoại Samsung.

### Cách 4: Tự động Build bằng GitHub Actions
Dự án đã có sẵn file cấu hình `.github/workflows/build-apk.yml`. Khi đẩy code lên GitHub, GitHub Actions sẽ tự động biên dịch và tạo link tải trực tiếp file `app-debug.apk`.
