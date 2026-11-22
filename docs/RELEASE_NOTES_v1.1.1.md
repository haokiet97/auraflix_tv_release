# AuraFlix TV v1.1.1

Ứng dụng xem phim cho Android TV / TV Box, điều khiển hoàn toàn bằng remote.

## Thông tin bản phát hành

| | |
|---|---|
| Phiên bản | 1.1.1 (versionCode 3) |
| Tệp | `AuraFlixTV-1.1.1.apk` |
| Dung lượng | 3.04 MB |
| SHA-256 | `426BE35AC4BC8C9025F9EE4A2A4B0DCA93A08BDAB13D240C5BA8A59D77A5E52C` |
| Yêu cầu | Android 5.0 (API 21) trở lên · không cần Google Play Services · không cần root |
| Đã kiểm thử | N5Max (Amlogic S905X2, Android 9, 1920×1080, armeabi-v7a) |

## Có gì mới

### Phím Space dễ thấy hơn

- Chuyển phím **Space** vào **hàng chữ cuối** (cạnh phím xoá), thay vì nằm riêng ở dưới cùng.
- Nhãn đổi thành **“Space”** để không bị nhầm với nút chức năng.
- Bấm bằng remote, chuột hoặc bàn phím USB/Bluetooth đều chèn đúng khoảng trắng.

### Bàn phím an toàn trên TV màn hình thấp

- Cột bàn phím **cuộn được** khi TV/box có chiều cao hiển thị nhỏ, tránh mất phím ở vùng overscan.
- Bỏ hàng phím thừa nên bàn phím gọn hơn, nhiều khoảng trống hơn.

### Giữ nguyên các cải tiến 1.1.0

- Bàn phím USB/Bluetooth, chuột/chuột bay.
- Cập nhật OTA trong **Cài đặt → Cập nhật ứng dụng**.

## Nâng cấp

- Đang dùng **1.1.0**: mở **Cài đặt → Cập nhật ứng dụng → Cập nhật ngay**.
- Đang dùng **1.0.0**: bản này chưa có OTA, hãy cài đè `AuraFlixTV-1.1.1.apk` một lần
  bằng ADB, USB hoặc Downloader. Dữ liệu được giữ nguyên.

```bash
adb connect <IP-box>:5555
adb install -r AuraFlixTV-1.1.1.apk
```

## Ghi chú

- Bản release ký bằng debug keystore để tiện cài trực tiếp (không dùng để phát hành lên Google Play).
- Nội dung lấy từ API công khai `phimapi.com` (nguồn mà `auraflix.vip` đang dùng).
- Ứng dụng không lưu trữ hay phát tán phim; bản quyền nội dung thuộc về đơn vị cung cấp gốc.
