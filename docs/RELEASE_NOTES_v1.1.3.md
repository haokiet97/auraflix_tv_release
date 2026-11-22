# AuraFlix TV v1.1.3

Ứng dụng xem phim cho Android TV / TV Box, điều khiển hoàn toàn bằng remote.

## Thông tin bản phát hành

| | |
|---|---|
| Phiên bản | 1.1.3 (versionCode 5) |
| Tệp | `AuraFlixTV-1.1.3.apk` |
| Dung lượng | 3.04 MB |
| SHA-256 | `EDCCE6C960BA70475F45B2236A65B384559EE6E0EC31A5C8CD3A71275054C83F` |
| Yêu cầu | Android 5.0 (API 21) trở lên · không cần Google Play Services · không cần root |
| Đã kiểm thử | N5Max (Amlogic S905X2, Android 9, 1920×1080, armeabi-v7a) |

## Có gì mới

### Giữ ◀/▶ để tua nhanh dần

- **Nhấn một lần**: tua ±10 giây như cũ.
- **Giữ nút**: bước tua tự tăng dần **10 → 20 → 30 → 40 → 50 → 60 giây**.
- OSD hiển thị **tổng thời lượng đã tua**, vị trí hiện tại và tổng thời lượng phim.
- Nhả nút dừng ngay; OSD giữ thêm ~1.2 giây.
- Thanh công cụ không bật trong lúc giữ để việc tua liên tục; nhấn **OK/▲/▼** khi muốn mở.
- Không tua khi đang mở danh sách tập, màn hình lỗi hoặc overlay hết tập.

### Tài liệu tính năng chi tiết

- Thêm tài liệu [Hướng dẫn tính năng chi tiết](https://github.com/haokiet97/auraflix_tv_release/blob/main/docs/FEATURES.md):
  hướng dẫn đầy đủ về trang chủ, danh mục, tìm kiếm, chi tiết phim, trình phát, tua nhanh, OTA,
  cài đặt, dữ liệu cục bộ, xử lý sự cố và lịch sử phiên bản.

## Nâng cấp

- Đang dùng **1.1.0 / 1.1.1 / 1.1.2**: mở **Cài đặt → Cập nhật ứng dụng → Cập nhật ngay**.
- Đang dùng **1.0.0**: bản này chưa có OTA, hãy cài đè `AuraFlixTV-1.1.3.apk` một lần
  bằng ADB, USB hoặc Downloader. Dữ liệu được giữ nguyên.

```bash
adb connect <IP-box>:5555
adb install -r AuraFlixTV-1.1.3.apk
```

## Ghi chú

- Bản release ký bằng debug keystore để tiện cài trực tiếp (không dùng để phát hành lên Google Play).
- Nội dung lấy từ API công khai `phimapi.com` (nguồn mà `auraflix.vip` đang dùng).
- Ứng dụng không lưu trữ hay phát tán phim; bản quyền nội dung thuộc về đơn vị cung cấp gốc.
