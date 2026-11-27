# AuraFlix Mobile v1.1.0 — Điều khiển AuraFlix TV

> 27/11/2026 · APK: `AuraFlixMobile-1.1.0.apk` · versionCode 5
> Hướng dẫn: [`CAST.md`](CAST.md)

## Điểm mới

- **Ghép cặp với AuraFlix TV cùng Wi-Fi**: app tự tìm TV đang mở AuraFlix bằng UDP broadcast
  (hoặc nhập IP thủ công), nhập **mã PIN 6 số** hiện trong *Cài đặt → Điều khiển bằng điện thoại*
  trên TV.
- **Phát trên TV**: nút mới ở màn Chi tiết gửi phim/tập/nguồn đang chọn sang TV, TV mở thẳng trình phát.
- **Thanh mini "Đang phát trên TV"** trên mọi tab: poster, tập, tiến trình, nút phát/tạm dừng;
  bấm vào để mở bộ điều khiển.
- **Bộ điều khiển TV**: phát/tạm dừng, tua ±10 giây, thanh tua, tập trước/tập tiếp, chọn tập,
  dừng trên TV, đổi sang TV khác.
- **Cài đặt → Điều khiển TV**: trạng thái kết nối, mở điều khiển, đổi TV, ngắt kết nối.
- Trạng thái TV cập nhật ~1 giây/lần; điện thoại nhớ thiết bị nên chỉ nhập PIN một lần.

## Sửa lỗi

- Bộ điều khiển không còn hiện nhầm "TV chưa phát phim nào" trong vài giây đầu khi TV đang chiếu.
- Biểu tượng TV khi chưa ghép cặp mở **Kết nối TV** thay vì bộ điều khiển rỗng.

## Yêu cầu

- TV chạy **AuraFlix TV v1.2.0** trở lên, cùng mạng Wi-Fi với điện thoại.
- Router không bật "AP isolation"; nếu không tự tìm thấy TV, nhập tay IP hiển thị trên TV.

## Nâng cấp

Cài đè APK v1.1.0 (hoặc *Cài đặt → Cập nhật* trong app Mobile); dữ liệu xem tiếp, yêu thích,
cài đặt được giữ nguyên.
