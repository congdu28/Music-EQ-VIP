# Tóm tắt bàn giao Music EQ

## Dự án và quy ước

- Ứng dụng Android Music EQ, Kotlin + Jetpack Compose, Room; repo https://github.com/congdu28/Music-EQ-VIP
- Workspace: `D:\App\Project Fun\Music 4K VIP`, nhánh `main`.
- Người dùng đã yêu cầu: khi sửa tính năng app thì push Git và phát hành APK mới, không cần hỏi lại.
- Trao đổi bằng tiếng Việt, rõ ràng và dễ hiểu.

## Trạng thái mã và APK

- Commit tính năng mới nhất: `0cd1e904b09fdb48af712c70dee9f8c5938b1e59` — `Add confirmed song deletion across library and downloads`, đã push lên `main`.
- Release [v1.0.65](https://github.com/congdu28/Music-EQ-VIP/releases/tag/v1.0.65), APK `MusicEQ-v1.0.65.apk` (22.7 MB).
- GitHub Actions [run 37611236493](https://github.com/congdu28/Music-EQ-VIP/actions/runs/37611236493) build/release thành công.
- Android `versionCode 66`, `versionName 1.0.65`, `minSdk 23` (Android 6.0), `targetSdk 36`.
- Tính năng mới nhất: menu xóa có xác nhận trong Thư viện, thư mục, playlist và kết quả YouTube đã tải. Tệp do app tải được xóa vật lý; bài quét từ máy chỉ gỡ khỏi thư viện, giữ file gốc, ẩn khỏi lần quét sau. Playlist có thao tác gỡ riêng, cũng có xác nhận. Khi xóa bài đang phát, dọn khỏi queue/mini-player; gỡ liên kết playlist và lịch sử nghe.

## Bối cảnh tính năng

### Thư viện, playlist, offline

- Bài yêu thích tách khỏi Tất cả; thư viện có Yêu thích, Playlist, Gần đây (30 bài), Offline YouTube. Mặc định A–Z, có A–Z/mới nhất/cũ nhất. Tên bài hai dòng; UI cần gọn, responsive.
- Quét thư viện gợi ý lần đầu, hỗ trợ quét thiết bị/chọn thư mục.
- Nhạc tải từ YouTube lưu offline trong app, có tiến trình/thông báo/chọn chất lượng; nếu file local có sẵn thì ưu tiên phát local.
- v1.0.64 sửa bài YouTube đã tải trong Yêu thích bị nhận là Online, thêm YouTube vào playlist bằng Room ID hợp lệ, và giữ mục Settings đang mở khi điều hướng trở lại.

### YouTube và Player

- YouTube tab tìm kiếm và phân trang; cần playback session riêng cho từng video, trạng thái loading; Back từ Player quay lại đúng tab nguồn.
- Downloader nhúng trong app, không cần cài YTDLnis hay tự dựng server.
- Player có mini-player, tab Ảnh bìa/Lời bài hát, lyric cuộn theo nhạc, tua ±10 giây, tốc độ/EQ/hẹn giờ/yêu thích và nhiều kiểu sóng.
- Tab Lyrics nên ẩn metadata/nút phụ để lời có thêm không gian; tab Ảnh bìa giữ thông tin đầy đủ.

### Lyrics

- Ưu tiên lời chính xác từ nguồn lyrics (ví dụ LRCLIB). Nếu không có thì đề xuất Gemini tùy chọn; ghi rõ lời AI có thể sai, không tự thay lời chuẩn.
- Hỗ trợ LRC, chỉnh offset, cuộn theo playback.
- Gemini mặc định `gemini-3.8-flash` với fallback như cấu hình trước. Key hệ thống không hiển thị; người dùng có thể dùng key riêng.

### EQ, theme, Settings

- EQ 10 dải kéo được và lưu preset; hiệu ứng âm trầm/surround/reverb/loudness cần gọn, responsive.
- Hỗ trợ sáng/tối/theo hệ thống, mặc định sáng; màu nhấn Color Picker.
- Ambient light: tắt, album, accent, RGB/Aurora, tốc độ và kiểu. Yêu cầu hiệu ứng phủ rộng, màu hòa trộn nổi bật nhưng tiết kiệm pin.
- Settings chia nhóm thu gọn/mở rộng và phải giữ trạng thái khi quay lại. UI toàn app cần gọn, responsive, animation mượt nhẹ.

## File thường cần xem

- `app/src/main/java/com/example/MainActivity.kt` — điều hướng, dialog.
- `app/src/main/java/com/example/ui/MusicViewModel.kt` — state, playlist, playback, download, lyrics.
- `app/src/main/java/com/example/data/MusicRepository.kt` — Room, scan, favorites, tải offline.
- `app/src/main/java/com/example/player/MusicPlayerController.kt` — queue/playback.
- `app/src/main/java/com/example/ui/screens/LibraryScreen.kt`, `YouTubeOnlineScreen.kt`, `NowPlayingScreen.kt`, `SettingsScreen.kt` — các màn chính.
- `.github/workflows/release-apk.yml` — build/release tự động khi push nhánh chính.

## Việc chờ

Tính năng xóa đã phát hành trong v1.0.65, chưa có yêu cầu sửa app nào khác đang chờ. Khi nhận lỗi tiếp theo, sửa và rà diff, push `main`, đợi Actions thành công, xác nhận APK trên GitHub Release.