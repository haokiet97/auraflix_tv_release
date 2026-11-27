# AuraFlix Mobile v1.0.0 — Ghi chú phát hành

Bản Android dành cho **điện thoại cảm ứng**, cài song song với bản TV.

## Điểm chính

- Giao diện Material3 cảm ứng với **bottom navigation**: Trang chủ · Danh mục · Yêu thích · Cài đặt.
- Trang chủ: banner phim mới tự chuyển, **Xem tiếp**, các hàng phim cuộn ngang.
- Danh mục: grid 3 cột, lọc **Loại + Thể loại + Quốc gia + Năm** bằng bottom sheet, cuộn vô hạn.
- Tìm kiếm: bàn phím hệ thống, debounce 400 ms, lịch sử tìm kiếm trong phiên.
- Chi tiết: backdrop lớn, chọn nguồn, **sheet chọn tập**, yêu thích, phim liên quan.
- Trình phát: ExoPlayer + HLS, tự xoay ngang, ẩn thanh hệ thống, chạm hiện/ẩn điều khiển,
  **double-tap tua ±10 giây**, kéo seek bar, đổi tốc độ/tỉ lệ/nguồn, tự chuyển tập sau 10 giây.
- Lưu vị trí xem và **Xem tiếp** đúng vị trí sau khi thoát.
- **OTA** trong Cài đặt (kênh repo mobile riêng), xác thực SHA-256 trước khi cài.

## Kỹ thuật

- Package `com.auraflix.mobile`, versionCode 1, minSdk 21, targetSdk 34.
- Tách module `:shared` dùng chung dữ liệu/ViewModel với bản TV; bản TV vẫn build bình thường.
- APK release ~3.07 MB, ký bằng debug keystore (tiện cài trực tiếp).

## Cài đặt

```bash
adb install -r AuraFlixMobile-1.0.0.apk
```

Hoặc copy APK vào điện thoại và mở (bật *Nguồn không xác định*).

## Tải về

- Trang giới thiệu & tải nhanh: <https://haokiet97.github.io/auraflix_mobile_release/>
- Trang phát hành: <https://github.com/haokiet97/auraflix_mobile_release/releases/latest>
- Tệp: `AuraFlixMobile-1.0.0.apk` (3.07 MB)
- Tệp kiểm tra: `AuraFlixMobile-1.0.0.apk.sha256`

SHA-256:

```
05933F834AD47BDC2BEA10AE94B9BD930C3A8E9D319CECA88194B73FE3565BDD
```

## Đã kiểm thử

Smoke test trên emulator Android 34 (x86, 540×960): trang chủ, danh mục + lọc, tìm kiếm,
chi tiết, yêu thích, chọn tập, phát HLS, tua, lưu tiến trình, cài đặt, OTA (404 khi repo chưa tạo).
Không có crash/ANR trong logcat. **Cần chạy lại checklist trên điện thoại thật trước khi phát hành rộng rãi.**

## Cấu hình OTA

Tạo repo GitHub `haokiet97/auraflix_mobile_release`, mỗi bản phát hành gắn tag `vX.Y.Z` và đính kèm APK.
Chi tiết: [`MOBILE.md`](MOBILE.md) mục 5.
