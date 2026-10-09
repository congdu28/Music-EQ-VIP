# Music EQ — Tóm tắt bàn giao

Ngày lập: 2026-10-09 (Asia/Bangkok)

## Dự án và quy ước

- Ứng dụng Android Music EQ, Kotlin + Jetpack Compose + Room.
- Repository: [congdu28/Music-EQ-VIP](https://github.com/congdu28/Music-EQ-VIP), nhánh chính `main`.
- Workspace theo thông tin người dùng: `D:\App\Project Fun\Music 4K VIP`.
- Trao đổi bằng tiếng Việt, câu trả lời dễ hiểu, cụ thể.
- Quy ước người dùng đã dặn và vẫn còn hiệu lực: mỗi lần sửa tính năng app, rà soát diff, push lên `main`, chờ GitHub Actions build và phát hành APK mới; không hỏi lại việc đã được ủy quyền này.
- GitHub Actions release dùng secret `DEBUG_KEYSTORE_BASE64` để ký debug APK ổn định; workflow cần quyền `contents: write` để tạo/cập nhật GitHub Release.
- Người dùng đang cân nhắc chuyển repo sang private nhưng chưa xác nhận đã chuyển. Nếu chuyển, GitHub connection/Codex cần quyền đọc và ghi repo private; release APK của repo private chỉ người được cấp quyền đọc repo mới tải công khai được.

## Trạng thái mới nhất (ưu tiên phần này)

- Bản mới nhất: **v1.0.75**, `versionCode 76`, `versionName 1.0.75`.
- Đã push lên `main`. Commit cuối tại thời điểm bàn giao: `b750d5c877ae19001880b8fb0e57e8acd3e1e66a`.
- GitHub Actions build và release thành công: [run 37765292788](https://github.com/congdu28/Music-EQ-VIP/actions/runs/37765292788).
- APK: [MusicEQ-v1.0.75.apk](https://github.com/congdu28/Music-EQ-VIP/releases/download/v1.0.75/MusicEQ-v1.0.75.apk) (~23.9 MB).
- Release: [v1.0.75](https://github.com/congdu28/Music-EQ-VIP/releases/tag/v1.0.75).
- Bản trước: v1.0.74, code 75; commit nền trước tối ưu tìm kiếm: `094f4c78885b61c25a8f70b79291b3d45ee304ee`.
- Tóm tắt lịch sử ban đầu có v1.0.65 (code 66); nội dung đó đã cũ, hãy lấy v1.0.75 làm trạng thái hiện hành.

## Tối ưu YouTube đã phát hành trong v1.0.75

Người dùng báo app chạy nhanh trên điện thoại cấu hình cao nhưng chậm trên máy tầm trung; tìm nhạc YouTube mất nhiều thời gian. Đã hỏi so sánh cùng Wi-Fi giữa hai máy; người dùng xác nhận máy tầm trung vẫn chậm. Điều này gợi ý xử lý trên thiết bị góp phần, nhưng không chứng minh mạng/API không liên quan; chưa có profile trực tiếp trên điện thoại.

Đã triển khai:

- Autocomplete YouTube dùng debounce 280 ms và chỉ gửi truy vấn từ 2 ký tự trở lên.
- Chuyển autocomplete và tìm kiếm sang OkHttp bất đồng bộ, hủy request khi coroutine bị hủy để không tiếp tục tiêu thụ mạng/tài nguyên khi người dùng gõ tiếp hoặc đổi từ khóa.
- Cache tối đa 24 trang kết quả truy vấn trong 2 phút; tìm lại cùng từ khóa trong cửa sổ cache sẽ nhanh hơn.
- Bỏ request `getVisitorData()` riêng khi mới vào tab YouTube; response tìm kiếm có thể cung cấp visitorData.
- Ghi thời gian request mạng và parse JSON vào Logcat với tag `YouTubeMusicService` để tách độ trễ mạng/API khỏi chi phí parse.
- Tính/ghi nhớ ID YouTube cho danh sách và dùng key ổn định trong `LazyColumn` để giảm tính toán lặp khi state player thay đổi.

File đã sửa: `app/src/main/java/com/example/data/YouTubeMusicService.kt`, `app/src/main/java/com/example/ui/MusicViewModel.kt`, `app/src/main/java/com/example/ui/screens/YouTubeOnlineScreen.kt`, `app/build.gradle.kts`, `.github/workflows/release-apk.yml`.

Việc cần tiếp tục: hỏi người dùng đã cài v1.0.75 và có thấy nhanh hơn không. Nếu vẫn chậm, xin model máy/Android và log `network=...ms` cùng `parse=...ms` từ tag `YouTubeMusicService`. Tìm lại truy vấn trong 2 phút để so sánh cache; lần tìm đầu vẫn phụ thuộc YouTube. Nếu network thấp nhưng UI vẫn khựng, tiếp tục profile CPU/Compose.

## Các yêu cầu/tính năng đã trao đổi

### Thư viện, Favorites, Playlist, Offline

- Thư viện gồm Tất cả, Yêu thích, Playlist, Gần đây (30 bài), Offline YouTube; có quét thiết bị/chọn thư mục.
- Sort A–Z/mới nhất/cũ nhất; hiển thị tên bài hai dòng, UI responsive.
- Nhạc tải từ YouTube được lưu offline trong app, có tiến trình/thông báo/chọn chất lượng; ưu tiên phát file local nếu có.
- Xóa bài có xác nhận ở thư viện, thư mục, playlist, kết quả YouTube đã tải. File do app tải được xóa vật lý; file gốc quét từ máy chỉ gỡ/ẩn khỏi thư viện và giữ file; playlist có thao tác gỡ riêng; dọn queue/mini-player, liên kết playlist và lịch sử khi xóa bài.
- Người dùng từng báo luồng Yêu thích và Playlist có thể xung đột: thêm bài vào Playlist rồi phát, bấm Yêu thích trong player thì app tự chuyển tới Playlist đó. Người dùng muốn rà luồng này. Cần kiểm tra trạng thái hiện hành nếu họ nhắc lại; không tự coi vấn đề đã được giải quyết nếu chưa có xác nhận.
- v1.0.64 trước đây sửa bài YouTube đã tải trong Yêu thích bị nhận diện nhầm là Online, cho phép thêm YouTube vào playlist với Room ID hợp lệ, và giữ mục Settings đang mở khi quay lại.

### YouTube, playback, Player

- YouTube có tìm kiếm, gợi ý, danh mục, phân trang, session playback riêng cho từng video, trạng thái loading; Back từ Player phải quay lại đúng tab nguồn.
- Downloader nhúng trong app, có chất lượng tải 128/320 kbps nếu nguồn cho phép; không cần YTDLnis/server do người dùng cài.
- Khi chơi, local file đã tải được ưu tiên nếu có.
- Player có mini-player, ảnh bìa/lời bài hát, lyric sync/cuộn theo playback, LRC/offset, tua ±10s, tốc độ, EQ, hẹn giờ, favorite, waveform nhiều kiểu.
- Bài YouTube 128 kbps không thể được nội suy để trở thành âm thanh có chi tiết gốc như 320 kbps/lossless. Người dùng yêu cầu tối ưu chất lượng phát YouTube; các luồng phát đã được rà/tinh chỉnh ở các lần trước.

### Tag nguồn và UI Player

- Tên tag nguồn đã thống nhất: nhạc quét từ máy là `Thư viện`; nhạc tải YouTube là `YouTube Downloaded`; nhạc stream là `YouTube Online`.
- Tag từng quá sát/che nghệ sĩ; người dùng yêu cầu sắp xếp đầy đủ thông tin và bỏ tag nhỏ nằm bên trong ảnh album.
- Thêm nút thêm vào Playlist bên trái cạnh nút quay lại trong Player.
- Hiển thị tag nguồn trở lại; nếu bài được phát từ playlist thì hiện tên playlist thành tag nổi bên dưới nghệ sĩ.
- Thu nhỏ album art/nội dung và đẩy controls bên dưới lên để tránh tràn màn hình.
- Waveform dạng sóng phát sáng cần nối vòng mượt; trước đây người dùng đã phản hồi vẫn bị khựng lúc loop.

### Ambient light, theme, settings

- Người dùng muốn ambient light có hiệu ứng chuyển tiếp mượt; pause thì dừng tại vị trí hiện tại, resume thì tiếp tục; thêm 3–4 kiểu hiệu ứng mới (dự án hiện có nhiều style).
- Có phản hồi ambient light không hoạt động dù đã tùy chỉnh; lỗi sizing/canvas đã được sửa trước commit v1.0.74 (`Fix ambient canvas sizing`).
- Mặc định giao diện sáng/tối lấy theo hệ thống; người dùng có thể chỉnh lại.
- Ambient supports tắt, màu ảnh bìa, accent, RGB/Aurora, tốc độ và style; đã yêu cầu animation hòa màu nổi bật nhưng tiết kiệm pin.
- Settings chia nhóm thu gọn/mở rộng và cần giữ trạng thái khi quay lại.

### Lyrics và EQ

- Lyrics ưu tiên nguồn chính xác (ví dụ LRCLIB); Gemini là tùy chọn khi không có lời chuẩn, cần thông báo lời AI có thể sai và không tự thay lời chuẩn.
- Hỗ trợ LRC, chỉnh offset, đồng bộ và cuộn theo playback.
- EQ 10 dải, preset; hiệu ứng bass/surround/reverb/loudness; controls responsive.

## SoundCloud — đã dừng theo yêu cầu

- Người dùng từng yêu cầu tìm kiếm/phát SoundCloud giống YouTube.
- Đã xác định tích hợp API chính thức cần app credentials; `client_secret` không được nhúng vào APK và cần backend an toàn. Hướng dẫn đăng ký cũng phức tạp/đòi điều kiện tài khoản theo tài liệu hiện thời.
- Người dùng nói bỏ qua tính năng này. Chưa thay đổi code cho SoundCloud; không tiếp tục trừ khi được yêu cầu lại.

## File chính thường cần tra cứu

- `app/src/main/java/com/example/MainActivity.kt` — điều hướng, dialog.
- `app/src/main/java/com/example/ui/MusicViewModel.kt` — app state, search, playlist, playback, download, lyrics.
- `app/src/main/java/com/example/data/MusicRepository.kt` — Room, scan, favorites, offline.
- `app/src/main/java/com/example/data/YouTubeMusicService.kt` — search, suggestions, pagination, stream resolve.
- `app/src/main/java/com/example/player/MusicPlayerController.kt` — queue và playback.
- `app/src/main/java/com/example/ui/screens/LibraryScreen.kt`, `YouTubeOnlineScreen.kt`, `NowPlayingScreen.kt`, `SettingsScreen.kt`.
- `.github/workflows/release-apk.yml` — build/release tự động.

## Tiếp tục ở chat mới

1. Đọc file này, sau đó kiểm tra trạng thái thật của `main`/release vì có thể đã có cập nhật sau 2026-10-09.
2. Hỏi người dùng kết quả thử v1.0.75 trên máy tầm trung; nếu còn chậm thì lấy model, Android và Logcat network/parse.
3. Nếu người dùng đổi repo sang private, thử quyền GitHub connector; nếu bị từ chối thì nhờ cập nhật quyền cài đặt cho repo `Music-EQ-VIP`, không yêu cầu họ gửi token cá nhân.
4. Khi được yêu cầu sửa app, hoàn tất thay đổi, rà diff, push `main`, đợi Actions thành công và xác nhận APK release như quy ước.

---

End of handoff.