# AuraFlix TV v1.3.0 — Chọn phim từ điện thoại, phát trên TV

Ứng dụng xem phim cho Android TV / TV Box. Bản v1.3.0 mở rộng phần **điều khiển bằng điện thoại**
(cùng Wi-Fi) đã có từ v1.2.0: điện thoại giờ **chọn được phim để phát trên TV** ngay từ app,
không còn chỉ dừng ở điều khiển phim đang chiếu.

## Thông tin bản phát hành

| | |
|---|---|
| Phiên bản | 1.3.0 (versionCode 7) |
| Tệp | `AuraFlixTV-1.3.0.apk` |
| Dung lượng | 3.07 MB |
| SHA-256 | `5DFF7C45E9AA247466B1DFEEE233B91D59AFC4B4C1E18C47CF5CBB210E107A17` |
| Yêu cầu | Android 5.0 (API 21) trở lên · không cần Google Play Services · không cần root |
| App đi kèm | AuraFlix Mobile **v1.2.0** (`AuraFlixMobile-1.2.0.apk`) |

## Có gì mới

### 📲 Điện thoại chọn phim để phát trên TV

- TV chia sẻ **kho phim gần đây** (mục "Xem tiếp" + lịch sử xem) qua LAN, có xác thực token như
  các lệnh điều khiển khác.
- Khi TV đang rảnh, app Mobile hiện ô **"TV đã kết nối → Chọn phim"** để duyệt poster và phát
  ngay trên TV — kể cả khi điện thoại không tải được danh sách phim từ server.
- Trong trình phát trên điện thoại có thêm nút **TV** / **Phát trên TV**: chuyển đúng phim và tập
  đang xem sang TV, hoặc chọn phim khác trong "Phim xem gần đây trên TV".

### 🔄 Chuyển phim mượt hơn

- Điện thoại mở phim khác khi TV đang chiếu: TV **đóng phiên phát cũ** trước, không còn tự phát lại
  phim cũ khi quay lại app.
- Chọn *Xem tiếp* trên điện thoại khi TV đang chiếu phim khác: điện thoại mở trình phát riêng và
  có nút chuyển tiếp sang TV (trước đây cả hai thiết bị cùng phát một phim).
- Trạng thái phát gửi kèm định danh phim (slug) để điện thoại biết TV đang chiếu phim nào.

## Tương thích

- Hoạt động tốt nhất cùng **AuraFlix Mobile v1.2.0**. Điện thoại dùng bản v1.1.0 vẫn điều khiển
  được như cũ, nhưng chưa có màn "Chọn phim trên TV" và danh sách gợi ý trong trình phát.
- Server LAN, mã PIN và thiết bị đã ghép cặp **giữ nguyên** khi nâng cấp.

## Nâng cấp

Từ v1.1.x / v1.2.0: **Cài đặt → Cập nhật ứng dụng → Cập nhật ngay** (giữ nguyên lịch sử, yêu thích,
cài đặt, PIN và danh sách thiết bị đã ghép).

## Kiểm thử

- 21/21 unit test pass (gồm 8 test giao thức cast, có thêm kiểm tra payload `/library`).
- Build release: R8 + shrink thành công, ký bằng keystore gói cài đặt hiện hành (nâng cấp tại chỗ).
- Cần thử thêm trên TV box + điện thoại thật luồng: mở phim trên điện thoại → **Phát trên TV**;
  TV rảnh → ô "Chọn phim" trên thanh dưới của điện thoại.
