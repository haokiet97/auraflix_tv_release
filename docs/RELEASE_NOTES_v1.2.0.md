# AuraFlix TV v1.2.0 — Điều khiển TV bằng điện thoại

Ứng dụng xem phim cho Android TV / TV Box, điều khiển bằng remote — và từ **v1.2.0 còn điều khiển
được bằng app AuraFlix Mobile** trong cùng mạng Wi-Fi.

## Thông tin bản phát hành

| | |
|---|---|
| Phiên bản | 1.2.0 (versionCode 6) |
| Tệp | `AuraFlixTV-1.2.0.apk` |
| Dung lượng | 3.07 MB |
| SHA-256 | `29C69408C117D4A90CC5557238CE74B3D32F1441515ED5E600F93C8EB9F0022A` |
| Yêu cầu | Android 5.0 (API 21) trở lên · không cần Google Play Services · không cần root |
| App đi kèm | AuraFlix Mobile **v1.1.0** (`AuraFlixMobile-1.1.0.apk`) |

## Có gì mới

### 📺 Điều khiển TV bằng điện thoại (cùng Wi-Fi)

- TV mở server nhỏ trong LAN (**Cài đặt → Điều khiển bằng điện thoại**) và hiện **mã PIN 6 số** + IP.
- Điện thoại tự tìm TV (UDP broadcast) hoặc nhập IP tay, nhập PIN để ghép cặp — chỉ một lần.
- Chọn phim/tập trên điện thoại rồi bấm **Phát trên TV**; điều khiển phát/tạm dừng, tua ±10 giây,
  thanh tua, chuyển tập, dừng trên TV.
- Bảo mật: **Đổi PIN** huỷ mọi thiết bị đã ghép, **Ngắt thiết bị**, **Tắt** server.
- Điện thoại gửi lệnh sẽ đánh thức màn hình TV; TV hiện thông báo khi có thiết bị ghép cặp.

Chi tiết: [`docs/CAST.md`](CAST.md).

### Sửa lỗi / độ ổn định

- Server LAN tự phục hồi khi cổng bận hoặc socket bị đóng (bật `SO_REUSEADDR`, tự thử lại,
  báo lỗi rõ nếu cổng đang do bản AuraFlix khác giữ).
- Bộ điều khiển trên điện thoại không còn hiện nhầm "TV chưa phát phim nào" trong vài giây đầu.

## Nâng cấp

Từ v1.1.x: **Cài đặt → Cập nhật ứng dụng → Cập nhật ngay** (giữ nguyên lịch sử, yêu thích, cài đặt).
Bản Mobile cài riêng (`com.auraflix.mobile`) và cập nhật qua *Cài đặt → Cập nhật* của app Mobile.

## Yêu cầu để dùng điều khiển

- TV và điện thoại **cùng một mạng Wi-Fi**, router không bật "AP isolation".
- TV: *Cài đặt → Điều khiển bằng điện thoại* đang **Bật**.
- Nếu mạng chặn broadcast: trên điện thoại bấm **Nhập IP TV thủ công**.

## Kiểm thử

- 19/19 unit test pass (gồm 6 test giao thức cast).
- Emulator: server LAN lên cổng, `/info`, `/ping`, `/state` đúng; điện thoại hiện thanh mini và điều
  khiển tạm dừng TV. Phần ghép cặp qua mạng thật cần thử lại trên box + điện thoại thật (xem
  `docs/CAST.md`).
