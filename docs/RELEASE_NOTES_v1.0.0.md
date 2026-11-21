# AuraFlix TV v1.0.0

Ứng dụng xem phim cho Android TV / TV Box, điều khiển hoàn toàn bằng remote.

## Thông tin bản phát hành

| | |
|---|---|
| Phiên bản | 1.0.0 (versionCode 1) |
| Tệp | `AuraFlixTV-1.0.0.apk` |
| Dung lượng | 3.02 MB |
| SHA-256 | `CCECF9D51AA87D0A9D43065958E364D83E5C769D598C4828C0BCBF9A5DFC02AB` |
| Yêu cầu | Android 5.0 (API 21) trở lên · không cần Google Play Services · không cần root |
| Đã kiểm thử | N5Max (Amlogic S905X2, Android 9, 1920×1080, armeabi-v7a) |

## Tính năng

- **Trang chủ**: banner phim mới tự luân chuyển, hàng *Xem tiếp*, Phim mới, Phim lẻ, Phim bộ, Hoạt hình, TV Shows, Chiếu rạp.
- **Danh mục**: lọc kết hợp Loại + Thể loại + Quốc gia + Năm, cuộn vô hạn.
- **Tìm kiếm**: bàn phím ảo tối ưu remote, tìm tự động sau 400 ms.
- **Chi tiết**: poster, backdrop, metadata, mô tả, chọn nguồn phát, chọn tập, phim liên quan, yêu thích.
- **Trình phát**: ExoPlayer (Media3) + HLS, giải mã cứng, resume vị trí, tự chuyển tập (đếm ngược 10 s), đổi nguồn khi lỗi, tua ±10 s, tốc độ 0.75×–2×, đổi tỉ lệ khung hình.
- **Lịch sử & yêu thích** lưu cục bộ (DataStore).

## Cài đặt

```bash
adb connect <IP-box>:5555
adb install -r AuraFlixTV-1.0.0.apk
```

Hoặc copy APK vào USB rồi cài trực tiếp trên box (bật *Nguồn không xác định*).

## Ghi chú

- Bản release ký bằng debug keystore để tiện cài trực tiếp (không dùng để phát hành lên Google Play).
- Nội dung lấy từ API công khai `phimapi.com` (nguồn mà `auraflix.vip` đang dùng). Nếu domain thay đổi, vào **Cài đặt → Máy chủ dữ liệu phim** để đổi.
- Ứng dụng không lưu trữ hay phát tán phim; bản quyền nội dung thuộc về đơn vị cung cấp gốc.
