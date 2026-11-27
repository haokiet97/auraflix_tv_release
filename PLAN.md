# AuraFlix TV — Kế hoạch phát triển ứng dụng Android TV

> Ứng dụng xem phim cho TV Box (Amlogic S905X2 · 4GB RAM · 32GB ROM · Android 9/10)
> Nguồn nội dung: **AuraFlix** (`https://auraflix.vip`) thông qua API công khai mà chính website đang dùng.

---

## 1. Mục tiêu

| Hạng mục | Yêu cầu |
|---|---|
| Nền tảng | Android TV / Google TV / TV Box (không cần màn hình cảm ứng) |
| Điều khiển | 100% bằng remote (D-pad, OK, Back, Play/Pause) |
| Nội dung | Phim lẻ, phim bộ, hoạt hình, TV Shows, chiếu rạp — Vietsub/Thuyết minh |
| Chất lượng | FHD/HD qua HLS (`.m3u8`), tận dụng giải mã phần cứng của S905X2 |
| Hiệu năng | Khởi động < 4s, cuộn mượt trên 2GB heap, RAM dùng < 400MB |
| Ngôn ngữ | Tiếng Việt |

## 2. Phân tích nguồn dữ liệu (đã khảo sát thực tế)

`auraflix.vip` là **React SPA**, không render HTML phía server → không thể scrape HTML.
Đọc bundle JS (`/assets/index-*.js`) cho thấy toàn bộ catalog được lấy từ API công khai:

**Base API: `https://phimapi.com`** (OPhim / KKPhim v1 API, miễn phí, không cần key).

| Chức năng | Endpoint | Ghi chú |
|---|---|---|
| Phim mới cập nhật | `GET /v1/api/danh-sach/phim-moi-cap-nhat?page={n}` | 24 item/trang, ~30.000 phim |
| Danh sách theo loại | `GET /v1/api/danh-sach/{phim-le\|phim-bo\|hoat-hinh\|tv-shows\|phim-chieu-rap}?page={n}` | |
| Theo thể loại | `GET /v1/api/the-loai/{slug}?page={n}` | 26 thể loại |
| Theo quốc gia | `GET /v1/api/quoc-gia/{slug}?page={n}` | 36 quốc gia |
| Tìm kiếm | `GET /v1/api/tim-kiem?keyword={q}&page={n}` | |
| Chi tiết + tập phim | `GET /phim/{slug}` | trả `movie` + `episodes[]` (server_data chứa `link_m3u8`) |

**Ảnh poster/thumb:** tiền tố `https://phimimg.com/` + đường dẫn tương đối trong `poster_url`/`thumb_url`
(trường `APP_DOMAIN_CDN_IMAGE` trong response dùng để cấu hình, có thể thay đổi).

**Stream:** mỗi tập có `link_m3u8` (HLS master playlist → variant 3500kb/1080p).
Phụ đề Vietsub **đã được burn-in** vào video (MPEG-TS không kèm track phụ đề) → không cần pipeline subtitle phức tạp, nhưng vẫn hỗ trợ chọn track nếu stream có.

**Rủi ro & đối phó**

| Rủi ro | Đối phó |
|---|---|
| Domain API đổi | Base URL cấu hình được trong Settings (lưu DataStore) |
| CDN ảnh đổi | Đọc `APP_DOMAIN_CDN_IMAGE` từ response, fallback `phimimg.com` |
| Stream chết / server lỗi | Thử lần lượt các `server_data` khác, nếu fail hiện nút "Thử lại / Đổi server" |
| Mạng chậm | OkHttp disk cache 100MB + Coil cache ảnh, prefetch trang kế |

> Ghi nhận: website còn có mục Live TV (VTV, bóng đá) lấy từ nguồn m3u8 bên thứ ba rất không ổn định → **không đưa vào phạm vi v1** (đã thống nhất với người dùng).

## 3. Kiến trúc kỹ thuật

```
┌─────────────────────────── App (single module) ───────────────────────────┐
│  UI: Jetpack Compose for TV (androidx.tv:tv-material3)                    │
│      HomeScreen · BrowseScreen · SearchScreen · DetailScreen ·            │
│      PlayerScreen · SettingsScreen                                        │
│  ViewModel (StateFlow) + UiState                                         │
│  Repository (cache RAM 5') ── OkHttp + kotlinx.serialization              │
│  DataStore: lịch sử xem, yêu thích, cấu hình                             │
│  Media3 ExoPlayer + HLS (giải mã HW) + OkHttp DataSource                 │
└──────────────────────────────────────────────────────────────────────────┘
```

**Stack**

| Thành phần | Lựa chọn | Lý do |
|---|---|---|
| Ngôn ngữ | Kotlin 2.0 | chuẩn hiện tại |
| UI | Compose for TV (`tv-material3`) | focus/D-pad native, ít code hơn Leanback |
| Video | AndroidX Media3 ExoPlayer 1.4 + `media3-exoplayer-hls` | chuẩn, hỗ trợ HW decode tốt trên Amlogic |
| HTTP | OkHttp 4.12 + Retrofit 2.11 | ổn định, interceptor cache/log |
| JSON | kotlinx.serialization | không cần reflection, build nhanh |
| Ảnh | Coil 3 (`coil-compose`, `coil-network-okhttp`) | async, memory/disk cache |
| DI | thủ công (ServiceLocator) | tránh Hilt/KSP, APK nhỏ, build nhanh |
| Lưu trữ | DataStore Preferences + JSON | lịch sử/yêu thích nhỏ, không cần Room |
| Build | AGP 8.7 · Gradle 8.9 · JDK 17 · minSdk 21 · target 34 | tương thích box Android 9 |

**Cấu trúc thư mục**

```
app/src/main/java/com/auraflix/tv/
├── AuraFlixApp.kt              # Application, khởi tạo ServiceLocator
├── MainActivity.kt             # 1 activity, Compose host + NavHost
├── core/
│   ├── net/                    # ApiClient, ApiService, interceptors, DTO
│   ├── model/                  # Movie, Episode, Server, Category, Country
│   ├── repo/                   # MovieRepository, SettingsRepository, HistoryRepository
│   └── util/                   # ImageUrl, Formatter, Focus helpers
├── ui/
│   ├── theme/                  # Color, Type, TvTheme
│   ├── components/             # MovieCard, Carousel, Loading, ErrorBox, TvKeyboard
│   ├── home/  browse/  search/  detail/  player/  settings/
│   └── nav/                    # Destinations + NavHost
└── player/                     # PlayerController (resume, next-ep, speed, aspect)
```

## 4. Thiết kế UI/UX cho TV

- **Layout 1920×1080**, safe-area 5% (overscan), chữ tối thiểu 14sp, card focus scale 1.08 + viền accent.
- **Home**: hero banner tự luân chuyển (backdrop mờ + poster + nút "Xem ngay"), các hàng ngang: *Xem tiếp*, *Phim mới cập nhật*, *Phim lẻ*, *Phim bộ*, *Hoạt hình*, *TV Shows*, *Chiếu rạp*.
- **Danh mục**: grid 6 cột, bộ lọc (Loại / Thể loại / Quốc gia / Năm) + lazy load vô hạn.
- **Tìm kiếm**: **bàn phím ảo tự vẽ** (D-pad di chuyển, OK chọn) — tránh IME lỗi trên box; kèm lịch sử tìm kiếm.
- **Chi tiết**: backdrop full-width, poster, meta (năm, chất lượng, ngôn ngữ, thời lượng), mô tả mở rộng, nút **Phát** / **Yêu thích**, danh sách tập theo server (tab), phim liên quan.
- **Player**: overlay tự ẩn sau 3s
  - ◀/▶ seek ±10s (thanh công cụ ẩn); **giữ ◀/▶ tua tăng dần 10→60s/bước, có OSD**
  - OK/▲/▼ hiện controls; ◀/▶ khi controls hiện để di chuyển focus
  - OSD: timeline, tên phim/tập, nút Tập tiếp, Tốc độ, Tỉ lệ khung hình, Chọn server
  - Lưu vị trí xem mỗi 5s → hỏi "Xem tiếp từ 12:34?"
  - Tự phát tập kế với countdown 10s ở cuối tập
- **Settings**: server ưu tiên, chế độ giải mã (Auto/HW/SW), base API URL, xoá lịch sử/yêu thích.

## 5. Các giai đoạn thực hiện

### Phase 0 — Toolchain ✅
- [x] Khảo sát API, xác nhận stream HLS chạy được
- [x] Tải & cài **JDK 17 (Temurin)** portable vào `%LOCALAPPDATA%\Programs\jdk17`
- [x] Tải **Android cmdline-tools**, cài `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`
- [x] Chấp nhận license SDK, tạo `local.properties`

### Phase 1 — Khung dự án & build xanh ✅
- [x] `settings.gradle.kts`, `build.gradle.kts` (root + app), `gradle.properties`
- [x] Gradle wrapper 8.9, version catalog `libs.versions.toml`
- [x] `AndroidManifest.xml`: `LEANBACK_LAUNCHER`, `uses-feature leanback` (required=false), `touchscreen required=false`, INTERNET, banner 320×180
- [x] Icon + banner TV, theme tối, `MainActivity` + Compose host
- [x] Build ra APK debug và release

### Phase 2 — Tầng dữ liệu ✅
- [x] DTO khớp JSON thật của `phimapi.com`
- [x] `HttpClient` (OkHttp cache 128MB, timeout, retry) + JSON parser
- [x] `MovieRepository`: danh sách, bộ lọc kết hợp, chi tiết, tìm kiếm, cache RAM theo key+TTL
- [x] `UserDataRepository`: lịch sử, yêu thích, cài đặt (DataStore)

### Phase 3 — UI chính ✅
- [x] Theme TV + component `TvFocusable`, `PosterCard`, `WideCard`, `SectionHeader`, keyboard ảo
- [x] Navigation Compose
- [x] HomeScreen (hero tự luân chuyển + hàng Xem tiếp + 6 hàng phim)
- [x] BrowseScreen (grid 6 cột + lọc Loại/Thể loại/Quốc gia/Năm + cuộn vô hạn)
- [x] SearchScreen (bàn phím ảo + debounce 400ms)
- [x] DetailScreen (thông tin + chọn nguồn/tập + yêu thích + phim liên quan)
- [x] FavoritesScreen, SettingsScreen

### Phase 4 — Trình phát ✅
- [x] `PlayerScreen` + ExoPlayer/Media3, lifecycle an toàn
- [x] Overlay điều khiển TV, timeline, OSD auto-hide sau 5s
- [x] Resume position, next episode + đếm ngược 10s, chọn server/tập
- [x] Xử lý lỗi stream, đổi nguồn, thử lại

### Phase 5 — Hoàn thiện & build ✅
- [x] Settings screen, About, disclaimer
- [x] Build **release APK** (R8 + shrink, ký debug key) — **3.02 MB**
- [x] `README.md` hướng dẫn cài đặt, ảnh chụp thực tế từ TV box

### Phase 6 — v1.1.0: nhập liệu & cập nhật OTA ✅
- [x] Tìm kiếm: thêm phím **Dấu cách** trên bàn phím ảo
- [x] Tìm kiếm: nhận **bàn phím USB/Bluetooth** (chữ, số, dấu cách, Backspace, Enter)
- [x] Toàn app: **chuột/chuột bay** — rê để focus, bấm để chọn (`TvFocusable`)
- [x] `UpdateManager`: kiểm tra GitHub Releases, so sánh phiên bản, tải APK có tiến độ
- [x] Xác thực **SHA-256** + cài qua **FileProvider** (nâng cấp tại chỗ, giữ dữ liệu)
- [x] Settings: mục **Cập nhật ứng dụng** (tự kiểm tra khi mở + nút kiểm tra thủ công)
- [x] Build release **v1.1.0** (versionCode 2), cập nhật README/site/release notes
- [x] **v1.1.1**: đưa phím **Space** vào hàng chữ cuối, nhãn “Space”, cột bàn phím cuộn được trên TV thấp
- [x] **v1.1.2**: thanh công cụ trình phát hiện khi nhấn **OK/▲/▼/◀/▶**; không tua nhầm khi mở overlay
- [x] **v1.1.3**: **giữ ◀/▶ để tua tăng dần** (10→60 giây/bước) + OSD tổng thời lượng; tài liệu `docs/FEATURES.md`

### Phase 7 — Bản Mobile (điện thoại) ✅
- [x] Tách module `:shared` (Android library): model, net, repository, OTA, util và toàn bộ ViewModel
- [x] `:app` (TV) chỉ còn lớp UI, thêm `implementation(project(":shared"))` — build/test vẫn xanh
- [x] Module `:mobile` (`com.auraflix.mobile`), Material3 cảm ứng, bottom navigation
- [x] Trang chủ (banner tự chuyển + Xem tiếp + hàng phim), Danh mục (grid + bottom sheet lọc), Tìm kiếm (bàn phím hệ thống), Chi tiết (sheet chọn tập), Yêu thích, Cài đặt
- [x] Trình phát cảm ứng: tự xoay ngang, ẩn thanh hệ thống, double-tap tua ±10s, kéo seek bar, chọn tập/nguồn/tốc độ/tỉ lệ
- [x] OTA riêng qua `UpdateManager.MOBILE_RELEASE_URL`; tài liệu [`docs/MOBILE.md`](docs/MOBILE.md)
- [x] APK release `AuraFlixMobile-1.0.0.apk` (3.07 MB) + smoke test emulator Android 34, không crash/ANR


## 6. Tiêu chí nghiệm thu

1. APK cài được trên box S905X2 (Android 9+), xuất hiện icon trong hàng ứng dụng Android TV.
2. Mở app bằng remote, không cần chuột/touch.
3. Trang chủ hiển thị ≥ 5 hàng phim có poster; chọn phim → chi tiết → phát được video 1080p.
4. Tìm kiếm bằng bàn phím ảo trả kết quả đúng.
5. Xem dở, thoát ra vào lại → hỏi xem tiếp đúng vị trí.
6. Chuyển tập, đổi server khi lỗi hoạt động.
7. Không crash khi mất mạng (hiện màn hình lỗi + nút thử lại).
8. APK < 15MB, RAM khi xem phim < 400MB.

## 7. Ngoài phạm vi (v1)
Live TV / bóng đá, tài khoản đăng nhập, bình luận, tải offline, cast.

---

## 8. Trạng thái triển khai (cập nhật)

**Đã hoàn thành toàn bộ Phase 0 → 6.** APK release v1.1.3: `app/build/outputs/apk/release/app-release.apk` (3.04 MB).

**v1.1.3** thêm **giữ ◀/▶ để tua tăng dần** (10→20→30→40→50→60 giây/bước, có OSD tổng thời lượng)
và tài liệu tính năng chi tiết [`docs/FEATURES.md`](docs/FEATURES.md).
**v1.1.2** sửa điều khiển trình phát: nhấn **OK/▲/▼/◀/▶** đều hiện thanh công cụ (trước đây chỉ ▲/▼),
và không tua phim khi đang mở danh sách tập/màn hình lỗi/overlay hết tập.
**v1.1.1** đưa phím **Space** lên hàng chữ cuối (nhãn “Space”, tránh bị overscan che) và cho
cột bàn phím cuộn được trên TV màn hình thấp. **v1.1.0** bổ sung phím dấu cách + bàn phím ngoài
+ chuột cho tìm kiếm, và mục **Cài đặt → Cập nhật ứng dụng** (kiểm tra GitHub Releases, tải OTA,
xác thực SHA-256, cài đè giữ nguyên dữ liệu).

Các điều chỉnh so với kế hoạch ban đầu (đều có lợi cho độ ổn định):

| Hạng mục | Kế hoạch | Thực tế |
|---|---|---|
| Bộ lọc | Loại/Thể loại/Quốc gia/Năm dùng endpoint riêng | Dùng chung `/v1/api/danh-sach/{type}?category=&country=&year=` → **kết hợp được nhiều điều kiện** (đã kiểm chứng với API) |
| DI | ServiceLocator | Giữ nguyên, thêm `Graph.appScope` để lưu tiến trình xem không bị huỷ khi pop màn hình |
| Decoder | Auto/HW/SW | Chỉ dùng chế độ mặc định của Media3 (`setEnableDecoderFallback`), vì S905X2 giải mã cứng tốt và ép SW dễ gây giật |
| Banner | 320×180 | Ảnh 640×360 (scale xuống), TV launcher tự thu nhỏ |
| Ảnh | Coil mặc định | Đăng ký tường minh `OkHttpNetworkFetcherFactory` để không bị R8 lược bỏ ở bản release |
| Kích thước APK | < 15 MB | 3.02 MB (R8 + shrinkResources) |

**Kiểm chứng đã thực hiện**
- `assembleDebug` và `assembleRelease` thành công, không lỗi biên dịch.
- `aapt2 dump badging`: có `leanback-launchable-activity`, banner, minSdk 21, targetSdk 34.
- API thực tế: danh sách/bộ lọc/tìm kiếm/chi tiết trả dữ liệu đúng; link `m3u8` master + variant tải được (HTTP 200).
- 5 unit test parse JSON đều pass.

**Kiểm thử trên thiết bị thật (N5Max · S905X2 · Android 9 · 1920×1080 · 320dpi → 960×540dp)**
- Cài bằng `adb install`, app xuất hiện với icon/banner trên launcher Android TV.
- Trang chủ: hero + 6 hàng phim + hàng "Xem tiếp" hiển thị đúng tiếng Việt.
- Chi tiết: poster, metadata, mô tả, 7 tập, nút Phát/Yêu thích.
- Phát phim 1080p: Media3 chọn `OMX.amlogic.avc.decoder.awesome` (giải mã cứng), OSD, timeline, seek ±10s hoạt động.
- Panel chọn tập + đổi server (Vietsub / Thuyết Minh).
- Thoát giữa chừng rồi mở lại → hàng **Xem tiếp** hiển thị đúng "Tập 01 · 8%".
- Danh mục + lọc thể loại (Hành Động) hoạt động; tìm kiếm bằng bàn phím ảo trả kết quả.
- Cài đặt lưu lựa chọn server/xem tiếp/domain API.
- Logcat: **không có crash, không ANR**; RAM Java heap ~6 MB khi phát phim.

**Lỗi phát hiện khi test thật và đã sửa**
1. Hàng nút điều khiển trình phát tràn mép phải ở 960dp → chuyển sang `FlowRow` + nút compact, nhãn ngắn hơn.
2. `FilterChip` mất chữ khi focus (chữ trắng trên nền trắng) → đổi màu chữ sang màu tối khi focus và chưa chọn.
3. Thanh điều hướng trang chủ bị cuộn khỏi màn hình → tách TopBar ra khỏi `LazyColumn`.
4. Banner/icon sinh ra ở tỉ lệ 1:1 → crop về 640×360 cho banner TV.


