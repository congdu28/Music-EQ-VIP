# 🎵 Music EQ - Hi-Res Audio Player & 10-Band Equalizer

Ứng dụng nghe nhạc ngoại tuyến chất lượng cao với bộ chỉnh âm **Equalizer 10 dải tần**, khuếch đại âm lượng, lời bài hát đồng bộ thời gian thực (**Karaoke LRC**), và điều chỉnh tốc độ phát vi mô.

---

## ✨ Tính Năng Nổi Bật

- **🎛️ Equalizer 10 Dải Tần & Hỗ Trợ Toàn Hệ Thống**:
  - Tinh chỉnh 10 dải tần độc lập (31Hz đến 16kHz) với độ chính xác cao.
  - Tăng cường âm trầm (**Bass Boost**), âm thanh vòm 3D (**Virtualizer**), và hiệu ứng không gian (**Reverb Acoustic**).
  - Tích hợp bộ khuếch đại phần cứng (**LoudnessEnhancer**) và bộ giới hạn chống rè loa (**Anti-Clipping Limiter**).
  - Tương thích tốt với mọi dòng máy (Samsung, Xiaomi, Oppo, Pixel), không xung đột với Dolby Atmos hay Dirac.

- **🎤 Lời Bài Hát Đồng Bộ Thời Gian Thực (Karaoke LRC)**:
  - Hiển thị lời bài hát mượt mà, cuộn tự động theo nhịp nhạc.
  - Chạm vào câu hát bất kỳ để nhảy ngay đến đoạn đó.
  - Trình chỉnh sửa, dán và tinh chỉnh độ lệch thời gian (Offset ms) tích hợp sẵn.

- **⚡ Điều Chỉnh Tốc Độ Phát Tùy Chỉnh (Continuous Variable Speed)**:
  - Kéo trượt tự do từ `0.50x` đến `2.50x` với độ chính xác `0.01x`.
  - Nút tinh chỉnh nhanh `[-0.05x]` / `[+0.05x]` và đặt lại `1.00x`.

- **📱 Giao Diện Thư Viện Tối Ưu Cho Điện Thoại**:
  - Giao diện siêu gọn, hiển thị 8+ bài hát trên một màn hình.
  - Quét nhanh bộ nhớ thiết bị, tìm kiếm tức thì theo bài hát, nghệ sĩ, định dạng.
  - Quản lý danh sách phát (Playlist) cá nhân hóa.

- **🇻🇳 100% Tiếng Việt Chuẩn & Font Chữ Cao Cấp**:
  - Tích hợp bộ font **Be Vietnam Pro** hiển thị chuẩn xác tất cả thanh điệu và ký tự đặc biệt tiếng Việt.

---

## 🚀 Tải Về Cài Đặt (Download APK)

Bạn có thể tải file `.apk` cài đặt trực tiếp tại:
👉 **[Mục Releases của Repository](https://github.com/congdu28/Music-EQ/releases)**

---

## 🛠️ Công Nghệ Sử Dụng

- **Ngôn ngữ**: Kotlin 100%
- **Giao diện**: Jetpack Compose & Material Design 3
- **Audio Engine**: Android MediaPlayer & AudioFX Framework (`Equalizer`, `BassBoost`, `Virtualizer`, `LoudnessEnhancer`, `PresetReverb`)
- **Lưu trữ cục bộ**: Room Database & DataStore
- **Kiến trúc**: MVVM + Clean Architecture + StateFlow
