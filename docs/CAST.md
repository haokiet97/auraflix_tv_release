# Điều khiển TV bằng điện thoại (cùng Wi-Fi)

Điện thoại cài **AuraFlix Mobile** có thể tự tìm TV đang mở **AuraFlix TV** trong cùng mạng,
ghép cặp bằng mã PIN rồi dùng điện thoại để **chọn phim, chọn tập, phát/tạm dừng, tua và chuyển tập**
— phim được phát trên TV.

Không cần tài khoản, không cần Internet cho phần điều khiển: dữ liệu đi trực tiếp trong LAN.

---

## 1. Cách dùng

1. **Trên TV:** mở AuraFlix → **Cài đặt → Điều khiển bằng điện thoại**. Server tự bật và hiện
   **mã PIN 6 số** + **IP của TV**. Nếu cần tắt thì bấm **Bật/Tắt**.
2. **Trên điện thoại:** mở AuraFlix → biểu tượng **TV** ở góc phải thanh trên (hoặc
   *Cài đặt → Điều khiển TV → Kết nối TV*).
3. Chọn TV trong danh sách **TV tìm thấy trên mạng**, nhập **mã PIN** đang hiện trên TV, bấm **Ghép cặp**.
   - Không thấy TV? Bấm **Nhập IP TV thủ công** và gõ IP hiển thị trên TV (ví dụ `192.168.1.12`).
4. Ghép xong, mỗi phim ở màn **Chi tiết** có thêm nút **Phát trên TV**.
   Trong app sẽ có **thanh mini** báo phim đang chiếu trên TV; bấm vào để mở **bộ điều khiển**.

### Bộ điều khiển trên điện thoại

| Nút | Chức năng |
|---|---|
| Thẻ "đang chiếu" | Poster, tên phim, tập, server, trạng thái phát / tạm dừng |
| Thanh tua | Kéo tới bất kỳ vị trí (chỉ gửi lệnh khi nhả tay) |
| ◀◀ / ▶▶ | Tua lùi / tới **10 giây** |
| ⏯ | Phát hoặc tạm dừng trên TV |
| ⏮ / ⏭ | Tập trước / tập tiếp theo |
| Chọn tập | Chọn nhanh tập trong lưới |
| Dừng trên TV | Đóng trình phát trên TV |
| Biểu tượng TV (góc phải trên) | Đổi sang TV khác |

Trạng thái bên TV được cập nhật khoảng **1 giây/lần** (vị trí, thời lượng, đang phát / tạm dừng,
đang tải, hết tập, lỗi).

## 2. Ghép cặp là "nhớ" vĩnh viễn

- Sau khi ghép, điện thoại lưu token nên lần sau chỉ cần mở app là điều khiển được.
- TV đổi IP (do DHCP): điện thoại tự dùng lại token khi tìm thấy TV cùng mã thiết bị.
- TV đổi PIN hoặc **Ngắt thiết bị**: mọi điện thoại đã ghép phải nhập PIN lại.

### Bảo mật trên TV

| Nút trong *Cài đặt → Điều khiển bằng điện thoại* | Tác dụng |
|---|---|
| **Đổi PIN** | Tạo PIN mới **và huỷ tất cả token** — dùng khi nghi có người lạ truy cập |
| **Ngắt N thiết bị** | Giữ PIN nhưng huỷ toàn bộ token đã cấp |
| **Sao chép IP** | Copy IP TV vào clipboard để nhập tay trên điện thoại |
| **Tắt** | Dừng hoàn toàn server (đóng cổng TCP + UDP), điện thoại không kết nối được |

> Mọi lệnh đều yêu cầu token; sai PIN bị chặn ~0,9 giây mỗi lần thử để chống dò mã.
> Khi điện thoại gửi lệnh, nếu TV đang tắt màn hình, TV được đánh thức trong 5 giây.

## 3. Giao thức (dành cho lập trình viên)

Mã nguồn: [`shared/.../core/cast`](../shared/src/main/java/com/auraflix/tv/core/cast).

```
TV    = HTTP server :8765  +  UDP discovery :8766
Phones = UDP broadcast "AURAFLIX_DISCOVER_V1"  →  TV trả "AURAFLIX_TV_V1 {json thông tin TV}"
```

| Endpoint | Phương thức | Ghi chú |
|---|---|---|
| `/info` | GET | Tên, id, tag, phiên bản — không cần token |
| `/pair` | POST | `{"pin":"123456","deviceName":"…"}` → `{"ok":true,"token":"…"}` |
| `/ping` | GET | Kiểm tra token còn hiệu lực |
| `/state` | GET | Snapshot trạng thái phát (JSON) — điện thoại poll ~1s |
| `/command` | POST | Lệnh: `play`, `pause`, `toggle`, `seek`, `seek_abs`, `seek_by`, `next_episode`, `prev_episode`, `select_episode`, `open`, `stop` |

- Header: `X-Aura-Token` cho mọi lệnh cần xác thực, `X-Aura-Device` khi ghép cặp.
- Server viết tay bằng `ServerSocket`/`DatagramSocket` (không thư viện ngoài) để chạy được từ Android 5.0
  và APK không phình to; PIN, token lưu trong DataStore.
- Một bộ `PlayerControllerHolder` nối HTTP server với màn hình Trình phát đang mở; lệnh được
  xếp hàng và chạy trên main thread.

## 4. Kiểm thử

- Unit test: `:app:testDebugUnitTest` — `CastProtocolTest` kiểm tra serialize/deserialize
  `CastPlaybackState`, `CastCommand`, `CastDeviceInfo`, header token (6 test).
- Smoke test trên emulator (TV 1920×1080 + điện thoại 1920×1080, chung NAT, ghép qua cổng chuyển tiếp):

| Luồng | Kết quả |
|---|---|
| Phát phim trên TV → điện thoại thấy thanh mini "Đang phát trên TV · Tập 01" + tiến trình | ✅ |
| Mở bộ điều khiển: poster, tập, thời lượng 24:49, vị trí chạy | ✅ |
| Bấm ⏯ → TV tạm dừng, điện thoại hiện "Tạm dừng" | ✅ |
| Điện thoại vừa mở app đã nhận trạng thái TV (không còn "TV chưa phát phim nào") | ✅ |
| Tìm TV bằng UDP broadcast (không cần nhập IP) | ✅ |
| Chưa ghép cặp → biểu tượng TV mở màn **Kết nối TV** (không phải bộ điều khiển rỗng) | ✅ |

## 5. Xử lý sự cố

| Hiện tượng | Cách xử lý |
|---|---|
| Không tìm thấy TV khi quét | Hai thiết bị phải chung Wi-Fi (không dùng "AP isolation"); TV đang mở app, mục *Điều khiển bằng điện thoại* đang **Bật**; router chặn broadcast → nhập **IP thủ công** |
| Ghép cặp báo "Mã PIN không đúng" | Xem lại PIN trong Cài đặt TV; PIN đổi khi bấm **Đổi PIN** |
| Bộ điều khiển báo "Mất kết nối" | TV tắt app / ngủ sâu / đổi mạng → bấm **Kết nối lại**; kiểm tra IP TV (DHCP có thể đổi) |
| Nút phát không tác dụng | TV đang ở màn khác (ví dụ giá trị cũ) → mở app TV; lệnh chỉ áp dụng khi màn Trình phát đang mở |
| Bấm **Phát trên TV** không thấy phim | Chờ 2–5 giây (TV phải tải chi tiết phim); kiểm tra TV không báo lỗi nguồn phát |
| Muốn khoá điều khiển | TV → **Đổi PIN** (huỷ mọi token) hoặc **Tắt** server |
