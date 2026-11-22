# AuraFlix TV v1.1.0

Ứng dụng xem phim cho Android TV / TV Box, điều khiển hoàn toàn bằng remote.

## Thông tin bản phát hành

| | |
|---|---|
| Phiên bản | 1.1.0 (versionCode 2) |
| Tệp | `AuraFlixTV-1.1.0.apk` |
| Dung lượng | 3.04 MB |
| SHA-256 | `564B32D028F1A9F05C8F717D492E843A6170A77A8BB8716A9AE066C87A353EEF` |
| Yêu cầu | Android 5.0 (API 21) trở lên · không cần Google Play Services · không cần root |
| Đã kiểm thử | N5Max (Amlogic S905X2, Android 9, 1920×1080, armeabi-v7a) |

## Có gì mới

### Tìm kiếm: nhập liệu đầy đủ hơn

- Thêm phím **Dấu cách** trên bàn phím ảo (trước đây không gõ được khoảng trắng).
- Hỗ trợ **bàn phím USB / Bluetooth**: gõ chữ, số, dấu cách, Backspace, Enter ngay trên màn hình tìm kiếm.
- Hỗ trợ **chuột / chuột bay**: rê chuột để focus, bấm trái để chọn; hoạt động trên toàn bộ ứng dụng.
- Từ khoá được cắt khoảng trắng thừa trước khi gọi API.

### Cập nhật OTA ngay trong Cài đặt

- Mục mới **Cài đặt → Cập nhật ứng dụng**, hiển thị phiên bản đang dùng.
- Tự kiểm tra bản mới mỗi lần mở Cài đặt, hoặc bấm **Kiểm tra cập nhật**.
- Hiển thị số phiên bản, dung lượng và ghi chú phát hành.
- Tải APK ngay trong ứng dụng, có thanh tiến độ; tự đối chiếu **SHA-256** với GitHub.
- Mở trình cài đặt hệ thống để nâng cấp tại chỗ; bản mới ký cùng khoá nên **giữ nguyên lịch sử xem, yêu thích và cài đặt**.
- Nếu box chưa cho phép cài từ nguồn không rõ, app mở thẳng màn hình cấp quyền.

## Nâng cấp

Từ v1.0.0, cách nhanh nhất là mở **Cài đặt → Cập nhật ứng dụng → Cập nhật ngay**.
Hoặc cài đè bằng ADB (giữ nguyên dữ liệu):

```bash
adb connect <IP-box>:5555
adb install -r AuraFlixTV-1.1.0.apk
```

## Ghi chú

- Bản release ký bằng debug keystore để tiện cài trực tiếp (không dùng để phát hành lên Google Play).
- Nội dung lấy từ API công khai `phimapi.com` (nguồn mà `auraflix.vip` đang dùng). Nếu domain thay đổi, vào **Cài đặt → Máy chủ dữ liệu phim** để đổi.
- Ứng dụng không lưu trữ hay phát tán phim; bản quyền nội dung thuộc về đơn vị cung cấp gốc.
