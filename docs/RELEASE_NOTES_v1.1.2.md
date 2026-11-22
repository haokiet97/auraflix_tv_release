# AuraFlix TV v1.1.2

Ứng dụng xem phim cho Android TV / TV Box, điều khiển hoàn toàn bằng remote.

## Thông tin bản phát hành

| | |
|---|---|
| Phiên bản | 1.1.2 (versionCode 4) |
| Tệp | `AuraFlixTV-1.1.2.apk` |
| Dung lượng | 3.04 MB |
| SHA-256 | `62DAE787935D7E62FFA7CBABECAD40C3BF474AF319DD0915BA0B32BE6F8D3409` |
| Yêu cầu | Android 5.0 (API 21) trở lên · không cần Google Play Services · không cần root |
| Đã kiểm thử | N5Max (Amlogic S905X2, Android 9, 1920×1080, armeabi-v7a) |

## Có gì mới

### Thanh công cụ hiện khi nhấn OK

- Trước đây khi thanh công cụ đã ẩn, chỉ **▲/▼** mới hiện lại; nhấn **OK** không có tác dụng.
- Nay **OK/Enter, ▲/▼, ◀/▶** đều hiện thanh công cụ.
- **◀/▶** vẫn tua ±10 giây và hiện thanh công cụ để thấy vị trí mới.
- Không tua phim khi đang mở **danh sách tập**, **màn hình lỗi** hoặc **overlay hết tập** — các nút trong các màn hình đó nhận phím đúng.

### Giữ nguyên các cải tiến trước

- Phím **Space** ở hàng cuối bàn phím tìm kiếm, bàn phím USB/Bluetooth, chuột/chuột bay.
- Cập nhật OTA trong **Cài đặt → Cập nhật ứng dụng**.

## Nâng cấp

- Đang dùng **1.1.0 / 1.1.1**: mở **Cài đặt → Cập nhật ứng dụng → Cập nhật ngay**.
- Đang dùng **1.0.0**: bản này chưa có OTA, hãy cài đè `AuraFlixTV-1.1.2.apk` một lần
  bằng ADB, USB hoặc Downloader. Dữ liệu được giữ nguyên.

```bash
adb connect <IP-box>:5555
adb install -r AuraFlixTV-1.1.2.apk
```

## Ghi chú

- Bản release ký bằng debug keystore để tiện cài trực tiếp (không dùng để phát hành lên Google Play).
- Nội dung lấy từ API công khai `phimapi.com` (nguồn mà `auraflix.vip` đang dùng).
- Ứng dụng không lưu trữ hay phát tán phim; bản quyền nội dung thuộc về đơn vị cung cấp gốc.
