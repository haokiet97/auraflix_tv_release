# Hướng dẫn phát hành AuraFlix Mobile

> Quy trình chuẩn để đóng gói, phát hành và kiểm tra một bản AuraFlix Mobile mới.
> Cập nhật lần cuối: **26/11/2026** · Bản hiện tại: **v1.0.3**
>
> Bản công khai (cho repo phát hành): <https://github.com/haokiet97/auraflix_mobile_release/blob/main/RELEASE_GUIDE.md>

---

## 1. Tổng quan kênh phát hành

AuraFlix Mobile dùng **repo nguồn** và **repo phát hành** tách riêng với bản TV.

| Thành phần | Giá trị |
|---|---|
| Repo nguồn | <https://github.com/haokiet97/auraflix_tv> (nhánh `master`) |
| Repo phát hành | <https://github.com/haokiet97/auraflix_mobile_release> (nhánh `main`) |
| Trang giới thiệu | <https://haokiet97.github.io/auraflix_mobile_release/> |
| API OTA | `https://api.github.com/repos/haokiet97/auraflix_mobile_release/releases/latest` |
| Package | `com.auraflix.mobile` |
| Module build | `:mobile` (dùng chung `:shared`) |
| Keystore hiện tại | debug keystore (bản phát hành nội bộ) |

Luồng phát hành:

```
Sửa code → tăng version → build release → tạo checksum
        → tạo GitHub Release (tag vX.Y.Z + APK + .sha256)
        → app đọc /releases/latest để OTA
        → trang GitHub Pages đọc API để hiện bản mới
```

> **Nguyên tắc quan trọng:** mỗi phiên bản đã phát hành là bất biến. Không sửa/ghi đè
> asset của release cũ — hãy phát hành bản vá mới (`vX.Y.Z+1`). App và landing page
> đều dựa vào tag, tên asset và `digest` của GitHub.

---

## 2. Chuẩn bị môi trường

- JDK 17
- Android SDK: platform 35, build-tools 35.0.0
- `local.properties` trỏ tới SDK
- Quyền push vào cả hai repo
- GitHub CLI (`gh`) **hoặc** Personal Access Token (scope `repo`) để tạo release
- (Khuyến nghị) WSL có SSH key GitHub nếu muốn dùng git qua WSL

Kiểm tra nhanh trên Windows PowerShell:

```powershell
$env:JAVA_HOME="C:\Users\<you>\AppData\Local\Programs\jdk17\jdk-17.0.21.1+2"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat --version
gh auth status          # nếu dùng gh
```

Repo release hiện đã bật **GitHub Pages**: source `main`, thư mục `/`, có `.nojekyll`.

---

## 3. Quy ước phiên bản & tên tệp

| Quy ước | Ví dụ |
|---|---|
| Tag GitHub | `v1.0.0`, `v1.1.0`, `v1.1.1` |
| `versionName` | `1.0.0`, `1.1.0`, `1.1.1` |
| `versionCode` | Số nguyên, **luôn tăng** (1, 2, 3…) |
| Tên APK | `AuraFlixMobile-<versionName>.apk` → `AuraFlixMobile-1.1.0.apk` |
| Checksum | `AuraFlixMobile-1.1.0.apk.sha256` |
| Release notes | `docs/RELEASE_NOTES_mobile_v<versionName>.md` |

- `versionCode` dùng để Android so sánh khi nâng cấp — **không được giảm hoặc dùng lại**.
- `UpdateManager` bỏ tiền tố `v` khi đọc tag, nên `v1.1.0` tương đương `1.1.0`.
- Mỗi release chỉ nên có **một file `.apk`**. App lấy asset đầu tiên có đuôi `.apk`.
- Có thể đính kèm thêm `.sha256`; app không tải file này, nhưng người dùng có thể đối chiếu.

---

## 4. Các bước phát hành

### Bước 1 — Tăng phiên bản

Sửa `mobile/build.gradle.kts`:

```kotlin
defaultConfig {
    applicationId = "com.auraflix.mobile"
    minSdk = 21
    targetSdk = 34
    versionCode = 2          // +1 so với bản trước
    versionName = "1.1.0"    // theo semver
}
```

Kiểm tra lại app đang đọc đúng kênh OTA trong `:shared`:

```kotlin
// shared/.../core/update/UpdateManager.kt
const val MOBILE_RELEASE_URL =
    "https://api.github.com/repos/haokiet97/auraflix_mobile_release/releases/latest"
```

Và `MobileApp.kt`:

```kotlin
updates = UpdateManager(
    context = application,
    http = http,
    latestReleaseUrl = UpdateManager.MOBILE_RELEASE_URL,
    apkNamePrefix = "AuraFlixMobile",
)
```

### Bước 2 — Build và test

```powershell
# Unit test tầng dữ liệu dùng chung
.\gradlew.bat :app:testDebugUnitTest

# Build thử bản debug
.\gradlew.bat :mobile:assembleDebug

# Build APK phát hành (R8 + shrink)
.\gradlew.bat :mobile:assembleRelease
```

APK nằm tại:

```
mobile/build/outputs/apk/release/mobile-release.apk
```

Kiểm tra nhanh metadata:

```powershell
$aapt = "C:\Users\<you>\AppData\Local\Android\Sdk\build-tools\35.0.0\aapt2.exe"
& $aapt dump badging .\mobile\build\outputs\apk\release\mobile-release.apk |
  Select-String "package:|launchable-activity|sdkVersion|targetSdkVersion"
```

Kỳ vọng:

```
package: name='com.auraflix.mobile' versionCode='2' versionName='1.1.0'
launchable-activity: name='com.auraflix.mobile.MainActivity'
minSdkVersion:'21'
targetSdkVersion:'34'
```

### Bước 3 — Kiểm thử trên máy thật

Tối thiểu các luồng sau (chi tiết trong [`MOBILE.md`](MOBILE.md) mục 6):

- [ ] Trang chủ, banner, Xem tiếp, các hàng phim
- [ ] Danh mục + lọc + cuộn tải thêm
- [ ] Tìm kiếm bằng bàn phím hệ thống
- [ ] Chi tiết + chọn tập + yêu thích
- [ ] Trình phát HLS, xoay ngang, double-tap tua, seek bar
- [ ] Thoát giữa chừng → Xem tiếp đúng vị trí
- [ ] Cài đặt + kiểm tra cập nhật
- [ ] Mất mạng → màn hình lỗi + Thử lại
- [ ] Không có crash/ANR trong logcat

```powershell
adb install -r mobile/build/outputs/apk/release/mobile-release.apk
adb logcat -c
# ... thao tác các luồng cần test ...
adb logcat -d | Select-String "FATAL|AndroidRuntime"
```

> Bản release hiện ký bằng **debug keystore**. Nếu phát hành công khai, hãy tạo keystore
> riêng, cấu hình `signingConfigs` và **sao lưu keystore**. Đổi khoá sẽ khiến Android
> từ chối nâng cấp tại chỗ (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

### Bước 4 — Đổi tên APK và tạo checksum

Đặt tên theo quy ước, ví dụ `AuraFlixMobile-1.1.0.apk`, rồi tạo checksum:

PowerShell:

```powershell
$apk = "AuraFlixMobile-1.1.0.apk"
$hash = (Get-FileHash $apk -Algorithm SHA256).Hash
"$hash  $apk`n" | Set-Content -NoNewline -Encoding ascii "$apk.sha256"
Get-Content "$apk.sha256"
```

Linux/WSL:

```bash
sha256sum AuraFlixMobile-1.1.0.apk | tee AuraFlixMobile-1.1.0.apk.sha256
```

### Bước 5 — Viết release notes

Tạo `docs/RELEASE_NOTES_mobile_v1.1.0.md`:

````markdown
# AuraFlix Mobile v1.1.0 — Ghi chú phát hành

## Có gì mới

- Mô tả thay đổi 1
- Mô tả thay đổi 2

## Cài đặt

`adb install -r AuraFlixMobile-1.1.0.apk`

SHA-256:

```text
<hash>
```
````

> Giữ đúng tiêu đề **`## Có gì mới`**. App dùng tiêu đề này để rút gọn nội dung
> hiển thị trong mục *Cài đặt → Cập nhật ứng dụng*.

### Bước 6 — Tạo GitHub Release

#### Cách A — GitHub UI

1. Mở <https://github.com/haokiet97/auraflix_mobile_release/releases/new>.
2. Chọn tag mới `v1.1.0` (tạo tag khi publish).
3. Tiêu đề: `AuraFlix Mobile v1.1.0`.
4. Nội dung: dán release notes.
5. Đính kèm **APK** và tệp **`.sha256`**.
6. Không chọn *draft*; chỉ chọn *prerelease* nếu là bản thử nghiệm.
7. Bấm **Publish release**.

#### Cách B — `gh` CLI

```bash
gh release create v1.1.0 \
  AuraFlixMobile-1.1.0.apk \
  AuraFlixMobile-1.1.0.apk.sha256 \
  --repo haokiet97/auraflix_mobile_release \
  --title "AuraFlix Mobile v1.1.0" \
  --notes-file docs/RELEASE_NOTES_mobile_v1.1.0.md \
  --latest
```

#### Cách C — REST API (PowerShell + curl)

Yêu cầu biến môi trường `GH_TOKEN` (PAT có scope `repo`). Không ghi token vào file/script.

```powershell
$env:GH_TOKEN = Read-Host "Dán GitHub PAT"   # chỉ dùng trong phiên shell hiện tại
```

```powershell
$repo = "haokiet97/auraflix_mobile_release"
$tag = "v1.1.0"
$apk = "AuraFlixMobile-1.1.0.apk"

# 1) Tạo JSON release
$payload = [ordered]@{
  tag_name         = $tag
  target_commitish = "main"
  name             = "AuraFlix Mobile $tag"
  body             = [IO.File]::ReadAllText("docs/RELEASE_NOTES_mobile_v1.1.0.md", [Text.Encoding]::UTF8)
  draft            = $false
  prerelease       = $false
} | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText("$PWD\release.json", $payload, (New-Object System.Text.UTF8Encoding($false)))

# 2) Tạo release
curl.exe -sS -X POST `
  -H "Authorization: token $env:GH_TOKEN" `
  -H "Accept: application/vnd.github+json" `
  -H "Content-Type: application/json; charset=utf-8" `
  --data-binary "@release.json" `
  -o release-response.json `
  "https://api.github.com/repos/$repo/releases"

# 3) Lấy release id rồi upload APK
$id = (Get-Content -Raw release-response.json | ConvertFrom-Json).id
curl.exe -sS -X POST `
  -H "Authorization: token $env:GH_TOKEN" `
  -H "Accept: application/vnd.github+json" `
  -H "Content-Type: application/vnd.android.package-archive" `
  --data-binary "@$apk" `
  "https://uploads.github.com/repos/$repo/releases/$id/assets?name=$apk"

# 4) Upload kèm tệp checksum (tùy chọn, khuyến nghị)
curl.exe -sS -X POST `
  -H "Authorization: token $env:GH_TOKEN" `
  -H "Accept: application/vnd.github+json" `
  -H "Content-Type: text/plain" `
  --data-binary "@$apk.sha256" `
  "https://uploads.github.com/repos/$repo/releases/$id/assets?name=$apk.sha256"
```

> Nếu release đã được tạo nhưng upload lỗi, chỉ cần upload lại asset — không tạo tag mới.

### Bước 7 — Kiểm tra API OTA

```powershell
curl.exe -sS "https://api.github.com/repos/haokiet97/auraflix_mobile_release/releases/latest"
```

Kỳ vọng:

- `tag_name` = `v1.1.0`
- `draft` = `false`, `prerelease` = `false`
- Có asset kết thúc bằng `.apk`
- Asset APK có `digest` dạng `sha256:<hash>` (app dùng để xác thực)
- `browser_download_url` tải được (HTTP 200 sau redirect)

Có thể kiểm tra nhanh bằng PowerShell:

```powershell
$r = Invoke-RestMethod "https://api.github.com/repos/haokiet97/auraflix_mobile_release/releases/latest"
$r.tag_name
$r.assets | Select-Object name, size, digest, browser_download_url
```

### Bước 8 — Cập nhật trang giới thiệu và tài liệu

Trang <https://haokiet97.github.io/auraflix_mobile_release/> **tự cập nhật** phiên bản,
dung lượng, ngày và checksum từ API. Không bắt buộc sửa `index.html` mỗi lần release.

Tuy vậy nên cập nhật các giá trị dự phòng/trình bày:

- `index.html` — object `FALLBACK` gần cuối file:
  ```js
  var FALLBACK = {
    version: "1.1.0",
    size: "3.08 MB",
    sha: "<hash>",
    updated: "24/11/2026"
  };
  ```
- `README.md` của repo phát hành — bảng phiên bản.
- `docs/MOBILE.md` — phiên bản, đường dẫn APK, checksum, ảnh chụp nếu UI đổi.
- `README.md` của repo nguồn — bảng **Tải APK Mobile**.
- Ảnh chụp mới (nếu giao diện thay đổi) → `assets/` của repo phát hành.

Commit và push repo phát hành (ví dụ dùng WSL):

```bash
cd /mnt/c/Users/<you>/path/to/auraflix_mobile_release
git add index.html README.md assets
git commit -m "docs: update for v1.1.0"
git push origin main
```

Kiểm tra GitHub Pages đã build lại:

```powershell
# Có thể xem trong Settings → Pages, hoặc:
curl.exe -sS -H "Authorization: token $env:GH_TOKEN" `
  "https://api.github.com/repos/haokiet97/auraflix_mobile_release/pages"
```

Chờ `"status": "built"` rồi mở lại trang (Ctrl/Cmd + Shift + R để bỏ cache).

### Bước 9 — Kiểm tra OTA end-to-end

Đây là bước quan trọng nhất, không chỉ kiểm tra file tồn tại:

1. Cài bản **cũ** (ví dụ v1.0.0) lên điện thoại.
2. Mở **Cài đặt → Cập nhật ứng dụng**.
3. Kỳ vọng: báo có bản mới `v1.1.0` kèm ghi chú.
4. Bấm **Cập nhật ngay**, theo dõi tiến độ.
5. Kiểm tra SHA-256 được xác thực (app báo lỗi nếu sai).
6. Bấm **Cài đặt ngay**, xác nhận cài đè.
7. Mở app: dữ liệu lịch sử/yêu thích/cài đặt còn nguyên.
8. Kiểm tra lại: app báo **“Bạn đang dùng bản mới nhất (v1.1.0)”**.

Kiểm tra thêm trên trang web: nút **Tải APK** phải trỏ trực tiếp tới file của bản mới
(sau khi API tải xong), và mã SHA-256 hiển thị khớp.

### Bước 10 — Commit và push repo nguồn

```bash
cd /mnt/d/Project/auraflix_tv
git status
git add mobile docs README.md PLAN.md gradle settings.gradle.kts build.gradle.kts
git commit -m "release: mobile v1.1.0"
git push origin master
```

Với WSL, nếu remote HTTPS bị treo ở bước push, chuyển sang SSH:

```bash
git remote set-url origin git@github.com:haokiet97/auraflix_tv.git
GIT_TERMINAL_PROMPT=0 git push origin master
```

APK trong thư mục gốc đang bị `.gitignore` bỏ qua (`*.apk`). Nếu muốn lưu APK trong repo
theo cách các bản TV cũ:

```bash
git add -f AuraFlixMobile-1.1.0.apk
```

---

## 5. Checklist phát hành nhanh

Sao chép checklist này vào issue/ghi chú release:

```markdown
- [ ] Đã tăng versionCode và versionName
- [ ] `:app:testDebugUnitTest` pass
- [ ] `:mobile:assembleRelease` thành công
- [ ] Đã cài và test trên điện thoại thật
- [ ] Không có crash/ANR trong logcat
- [ ] Đã tạo `AuraFlixMobile-X.Y.Z.apk` và `.sha256`
- [ ] Release notes có tiêu đề `## Có gì mới`
- [ ] Đã tạo GitHub Release tag `vX.Y.Z`, không draft
- [ ] Release có đúng 1 asset `.apk` + 1 `.sha256`
- [ ] `/releases/latest` trả đúng tag và `digest`
- [ ] Trang GitHub Pages hiển thị bản mới, nút tải trỏ đúng APK
- [ ] Đã test OTA từ bản cũ → bản mới, dữ liệu còn nguyên
- [ ] Đã cập nhật README, `docs/MOBILE.md`, release notes
- [ ] Đã push repo nguồn và repo phát hành
- [ ] GitHub Pages báo `built`
```

---

## 6. Xử lý sự cố

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `/releases/latest` trả 404 | Chưa có release, release đang draft hoặc chỉ có prerelease | Publish một release stable, không draft/prerelease |
| App báo “Chưa có bản phát hành nào” | Repo/tag sai, API bị chặn, mất mạng | Kiểm tra `MOBILE_RELEASE_URL`, mở API bằng trình duyệt |
| App không thấy bản mới | Chưa tăng `versionName`; tag không đúng dạng `vX.Y.Z`; API cache | Sửa tag/version, đợi cache, thử lại sau ít phút |
| App tải nhầm file | Release có nhiều file `.apk` hoặc asset không đúng | Chỉ để một `.apk` mỗi release |
| Lỗi SHA-256 | APK upload lỗi/hết phiên, file `.sha256` sai | Upload lại APK, đối chiếu `digest` của GitHub |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK mới ký bằng keystore khác | Dùng đúng keystore cũ; nếu đổi khoá phải gỡ app (mất dữ liệu) |
| Không mở được trình cài đặt | Chưa bật “Cài đặt ứng dụng không rõ nguồn gốc” | Bật quyền cho AuraFlix/trình quản lý tệp rồi thử lại |
| Trang Pages 404 | Pages chưa bật/build xong, thiếu `index.html`/`.nojekyll` | Kiểm tra Settings → Pages; chờ build; kiểm tra nhánh `main`, thư mục `/` |
| Trang hiện bản cũ | API rate limit, cache trình duyệt, `FALLBACK` cũ | Hard refresh; kiểm tra API; cập nhật `FALLBACK` |
| Console báo CORS khi lấy `.sha256` | Fetch file release từ JS bị chặn CORS | Dùng `digest` từ GitHub API (trang hiện đã làm vậy), không fetch trực tiếp |
| Push WSL bị treo | Remote HTTPS đang chờ nhập credential | Đổi remote sang SSH và `GIT_TERMINAL_PROMPT=0` |
| Pages build lỗi | File HTML lỗi cú pháp hoặc Jekyll xử lý | Mở tab Actions/Pages xem log; giữ `.nojekyll` |

---

## 7. Rollback & bản vá

- **Không sửa hoặc xoá asset** của release đã công bố nếu đã có người tải: `digest` và
  liên kết sẽ thay đổi, OTA có thể lỗi.
- Nếu bản mới có lỗi: phát hành bản vá `vX.Y.Z+1` với `versionCode` lớn hơn.
- Nếu release bị tạo nhầm trước khi công bố: có thể xoá release/tag và tạo lại, nhưng
  phải đảm bảo chưa ai tải.
- Luôn giữ **keystore** và mật khẩu ở nơi an toàn; mất keystore = không thể nâng cấp tại chỗ.
- Không dùng lại `versionCode`/tag đã phát hành.

---

## 8. Bản phát hành hiện tại (v1.0.3)

| Hạng mục | Giá trị |
|---|---|
| Tag | `v1.0.3` |
| Release | <https://github.com/haokiet97/auraflix_mobile_release/releases/tag/v1.0.3> |
| Trang giới thiệu | <https://haokiet97.github.io/auraflix_mobile_release/> |
| APK | `AuraFlixMobile-1.0.3.apk` — 3.07 MB |
| SHA-256 | `F43903BAE6F95192721F4429461D70882CBDB0ECCC65B714B8AB5ECDAF88010C` |
| Kích thước | 3,220,211 bytes |
| versionCode | 4 |

### Bản trước

| Tag | APK | SHA-256 |
|---|---|---|
| `v1.0.2` | `AuraFlixMobile-1.0.2.apk` (3,221,037 bytes) | `733EAADF72E2B497701440C275330149CBD9DFC03E5A0D0131723252505257D9` |
| `v1.0.1` | `AuraFlixMobile-1.0.1.apk` (3,218,287 bytes) | `F52A319E6AA91E7151A04B1D32F1E184561BD98D21188B6A4CFE9515BCB8BD06` |
| `v1.0.0` | `AuraFlixMobile-1.0.0.apk` (3,218,340 bytes) | `05933F834AD47BDC2BEA10AE94B9BD930C3A8E9D319CECA88194B73FE3565BDD` |

---

## 9. Liên kết

- Repo nguồn: <https://github.com/haokiet97/auraflix_tv>
- Repo phát hành: <https://github.com/haokiet97/auraflix_mobile_release>
- Trang tải: <https://haokiet97.github.io/auraflix_mobile_release/>
- Release notes v1.0.0: [`RELEASE_NOTES_mobile_v1.0.0.md`](RELEASE_NOTES_mobile_v1.0.0.md)
- Tài liệu tính năng Mobile: [`MOBILE.md`](MOBILE.md)