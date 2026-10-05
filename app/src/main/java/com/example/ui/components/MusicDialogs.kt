package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.lyrics.LrcParser
import com.example.model.Playlist
import com.example.model.Song
import com.example.ui.theme.*

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, desc: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Tạo danh sách phát mới", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên danh sách") },
                    placeholder = { Text("Ví dụ: Nhạc Buổi Sáng, EDM...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("playlist_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Mô tả (tùy chọn)") },
                    placeholder = { Text("Ghi chú về thể loại hoặc tâm trạng...") },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("playlist_desc_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), desc.trim())
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AccentContent),
                modifier = Modifier.testTag("create_playlist_confirm_button")
            ) {
                Text("Tạo mới", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}

@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Thêm vào danh sách phát", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Bài hát: ${song.title}",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Text(
                        "Chưa có danh sách phát nào.",
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectPlaylist(playlist) }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = NeonPurple,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(playlist.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    if (playlist.description.isNotEmpty()) {
                                        Text(playlist.description, color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onCreateNewPlaylist,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tạo danh sách mới")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}

@Composable
fun EditLyricsDialog(
    song: Song,
    isAligning: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (lyricsText: String, offsetMs: Long) -> Unit,
    onAlignLyrics: ((plainLyrics: String) -> Unit)? = null
) {
    var lyricsText by remember(song.id) { mutableStateOf(song.lyrics ?: "") }
    var offsetText by remember(song.id) { mutableStateOf(song.lrcOffsetMs.toString()) }

    LaunchedEffect(song.lyrics) {
        if (!song.lyrics.isNullOrBlank() && song.lyrics != lyricsText) {
            lyricsText = song.lyrics
        }
    }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.EditNote, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chỉnh sửa lời bài hát (LRC)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Định dạng LRC đồng bộ: [01:23.45] Câu hát ở đây",
                    color = TextHighlight,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Tools Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    val synced = if (clip.contains("[0") || clip.contains("[1")) {
                                        clip
                                    } else {
                                        LrcParser.convertPlainTextToSyncedLrc(clip, song.durationMs)
                                    }
                                    lyricsText = synced
                                    Toast.makeText(context, "Đã dán và tự động đồng bộ mốc thời gian!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Bộ nhớ tạm rỗng", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Lỗi đọc bộ nhớ tạm: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dán & Mốc cơ bản", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val query = "lời bài hát ${song.title} ${song.artist} lrc"
                                val url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPink),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tìm trên Google", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Tools Row 2: AI Căn chỉnh chuẩn nhạc
                Button(
                    onClick = {
                        if (lyricsText.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập hoặc dán lời bài hát vào khung trước", Toast.LENGTH_SHORT).show()
                        } else {
                            onAlignLyrics?.invoke(lyricsText)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet.copy(alpha = 0.25f), contentColor = NeonViolet),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonViolet),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isAligning,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isAligning) {
                        CircularProgressIndicator(color = NeonViolet, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemini AI đang căn chỉnh chuẩn nhạc...", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonViolet)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Căn chỉnh chuẩn nhạc (Khớp lời tự động)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = lyricsText,
                    onValueChange = { lyricsText = it },
                    label = { Text("Nội dung lời bài hát (LRC / Text)") },
                    minLines = 8,
                    maxLines = 14,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("lyrics_editor_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = offsetText,
                        onValueChange = { offsetText = it },
                        label = { Text("Độ trễ Offset (ms)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f).testTag("lyrics_offset_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val current = offsetText.toLongOrNull() ?: 0L
                            offsetText = (current + 500).toString()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Text("+500ms", fontSize = 11.sp, color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = {
                            val current = offsetText.toLongOrNull() ?: 0L
                            offsetText = (current - 500).toString()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Text("-500ms", fontSize = 11.sp, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val offset = offsetText.toLongOrNull() ?: 0L
                    onSave(lyricsText, offset)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AccentContent),
                modifier = Modifier.testTag("save_lyrics_confirm_button")
            ) {
                Text("Lưu lời bài hát", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}

@Composable
fun SleepTimerDialog(
    currentRemainingSec: Int,
    isTimerActive: Boolean,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    val presetMinutes = listOf(15, 30, 45, 60, 90)
    var customMinutes by remember { mutableStateOf("") }
    val customMinutesValue = customMinutes.toIntOrNull()?.takeIf { it in 1..1440 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Bedtime, contentDescription = null, tint = NeonPurple)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hẹn giờ tắt nhạc", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isTimerActive) {
                    val minutes = currentRemainingSec / 60
                    val seconds = currentRemainingSec % 60
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NeonPurple.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Đang đếm ngược:", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                String.format("%02d:%02d", minutes, seconds),
                                color = NeonPurple,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text("Chọn thời gian hẹn giờ:", color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetMinutes.forEach { mins ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceVariant,
                            modifier = Modifier
                                .clickable {
                                    onSelectMinutes(mins)
                                    onDismiss()
                                }
                        ) {
                            Text(
                                "$mins phút",
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Hoặc nhập số phút tùy chỉnh (1–1440):", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { value ->
                            if (value.length <= 4 && value.all(Char::isDigit)) customMinutes = value
                        },
                        label = { Text("Phút") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = customMinutesValue != null,
                        onClick = {
                            customMinutesValue?.let(onSelectMinutes)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        Text("Bắt đầu")
                    }
                }
            }
        },
        confirmButton = {
            if (isTimerActive) {
                Button(
                    onClick = {
                        onCancelTimer()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink)
                ) {
                    Text("Tắt hẹn giờ", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}

@Composable
fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (presetName: String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Lưu cấu hình Equalizer", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Đặt tên cho thiết lập EQ tùy chỉnh của bạn:", color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Ví dụ: Tai Nghe Sony Bass, Phòng Ngủ...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("preset_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AccentContent),
                modifier = Modifier.testTag("save_preset_confirm_button")
            ) {
                Text("Lưu Preset", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}

@Composable
fun AudioSpecsDialog(
    song: Song?,
    onDismiss: () -> Unit
) {
    if (song == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = HiResGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Thông số kỹ thuật Hi-Res Audio", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SpecItem("Tên bài hát", song.title)
                SpecItem("Nghệ sĩ", song.artist)
                SpecItem("Định dạng âm thanh", song.format)
                SpecItem("Độ phân giải", "${song.bitDepth}-bit Lossless Audio")
                SpecItem("Tần số lấy mẫu (Sample Rate)", "${song.sampleRateHz / 1000} kHz")
                SpecItem("Tốc độ truyền (Bitrate)", "${song.bitrateKbps} kbps")
                SpecItem("Chuẩn xử lý DAC", if (song.isHiRes) "Hi-Res Audio 24-bit HD Certified" else "CD Quality 16-bit")
                SpecItem("Bộ giải mã", "Hardware DSP Native Decoder Engine")
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AccentContent)
            ) {
                Text("Đóng", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}

@Composable
private fun SpecItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EditSongMetadataDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, format: String) -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var format by remember { mutableStateOf(song.format) }

    val formatOptions = listOf("FLAC", "WAV", "ALAC", "MP3", "AAC")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chỉnh sửa thông tin bài hát", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên bài hát") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_song_title_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Nghệ sĩ / Ca sĩ") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_song_artist_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Tên Album") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_song_album_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Định dạng âm thanh:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    formatOptions.forEach { opt ->
                        val isSelected = format.equals(opt, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) NeonCyan else DarkSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NeonCyan else DarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { format = opt }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt,
                                    color = if (isSelected) AccentContent else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Đường dẫn file: ${song.filePath}",
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), artist.trim(), album.trim(), format.trim())
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AccentContent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("edit_song_save_button")
            ) {
                Text("Lưu thay đổi", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}
