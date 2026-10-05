package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.equalizer.EqualizerState
import com.example.BuildConfig
import com.example.player.PlayerUiState
import com.example.ui.MainTab
import com.example.ui.MusicAppUiState
import com.example.ui.MusicViewModel
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    uiState: MusicAppUiState,
    playerState: PlayerUiState,
    equalizerState: EqualizerState,
    onRequestScan: () -> Unit = { viewModel.scanDeviceAudio() },
    modifier: Modifier = Modifier
) {
    var showCustomAccentDialog by remember { mutableStateOf(false) }
    var customRed by remember { mutableFloatStateOf(0.2f) }
    var customGreen by remember { mutableFloatStateOf(0.6f) }
    var customBlue by remember { mutableFloatStateOf(1f) }

    // Back gesture returns to Library
    BackHandler {
        viewModel.setTab(MainTab.LIBRARY)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Hero Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NeonViolet.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.Headphones, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Nhịp Điệu Hi-Res Player", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("Trình phát nhạc Hi-Res Lossless & Bộ chỉnh âm 10 dải", color = TextSecondary, fontSize = 12.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ElectricAzure.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricAzure)
                ) {
                    Text(
                        text = "Lossless 24-bit/96kHz Certified",
                        color = ElectricAzure,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Section: Theme mode and customizable accent palette
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Giao diện", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chế độ tối", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (uiState.isDarkTheme) "Đang dùng giao diện tối" else "Đang dùng giao diện sáng",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = uiState.isDarkTheme,
                        onCheckedChange = viewModel::setDarkTheme,
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentContent, checkedTrackColor = NeonCyan)
                    )
                }

                Text("Màu nhấn", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                val accentOptions = listOf(
                    0xFF3399FF.toInt(), 0xFF8B5CF6.toInt(), 0xFFEC4899.toInt(),
                    0xFF22C55E.toInt(), 0xFFF59E0B.toInt(), 0xFF14B8A6.toInt()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    accentOptions.forEach { colorInt ->
                        val selected = uiState.accentColor == colorInt
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .selectable(selected = selected, role = Role.RadioButton) { viewModel.setAccentColor(colorInt) },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(colorInt),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) TextPrimary else DarkBorder
                                ),
                                modifier = Modifier.size(if (selected) 32.dp else 28.dp)
                            ) {}
                        }
                    }
                }
                TextButton(
                    onClick = {
                        val currentColor = Color(uiState.accentColor)
                        customRed = currentColor.red
                        customGreen = currentColor.green
                        customBlue = currentColor.blue
                        showCustomAccentDialog = true
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Tùy chỉnh màu…", color = NeonCyan, fontSize = 12.sp)
                }
                Text("Màu nhấn áp dụng cho nút, biểu tượng và điểm nổi bật.", color = TextMuted, fontSize = 10.sp)
            }
        }

        // Section: Định dạng âm thanh chất lượng cao hỗ trợ
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Định dạng âm thanh chất lượng cao hỗ trợ", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(10.dp))

                val formats = listOf(
                    "FLAC (Lossless 24/96)",
                    "WAV (32-bit Float PCM)",
                    "ALAC (Apple Lossless)",
                    "AAC (High Bitrate 320k)",
                    "MP3 (320kbps Studio)",
                    "OGG / Opus (Hi-Fi Stream)"
                )

                formats.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { formatName ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        formatName,
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Bộ nhớ & Quét thư viện
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Thư viện nhạc ngoại tuyến", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                SettingItemRow(
                    icon = Icons.Default.FolderOpen,
                    iconTint = NeonCyan,
                    title = "Quét lại toàn bộ bộ nhớ",
                    subtitle = "Tìm kiếm file FLAC, WAV, MP3 mới tải về máy",
                    onClick = onRequestScan
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DarkBorder)

                SettingItemRow(
                    icon = Icons.Default.Bedtime,
                    iconTint = NeonPurple,
                    title = "Hẹn giờ tắt nhạc (Sleep Timer)",
                    subtitle = if (playerState.isSleepTimerActive) "Đang hẹn giờ: ${playerState.sleepTimerRemainingSeconds / 60} phút còn lại" else "Tự động dừng phát khi đi ngủ",
                    onClick = { viewModel.setShowSleepTimer(true) }
                )
            }
        }

        // Section: Chuyển nhạc mượt mà (Crossfade)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chuyển nhạc mượt mà (Crossfade)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(
                            text = if (playerState.isCrossfadeEnabled) "Hòa trộn âm thanh ${String.format("%.1f", playerState.crossfadeDurationSeconds)}s giữa các bài hát" else "Đã tắt (Chuyển bài lập tức)",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = playerState.isCrossfadeEnabled,
                        onCheckedChange = { viewModel.setCrossfadeEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentContent, checkedTrackColor = NeonCyan)
                    )
                }

                if (playerState.isCrossfadeEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Thời gian hòa âm (Fade In/Out): ${String.format("%.1fs", playerState.crossfadeDurationSeconds)}",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = playerState.crossfadeDurationSeconds,
                        onValueChange = { viewModel.setCrossfadeDuration(it) },
                        valueRange = 0.5f..5.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.5f, 1.0f, 2.0f, 3.0f, 5.0f).forEach { sec ->
                            val isSel = kotlin.math.abs(playerState.crossfadeDurationSeconds - sec) < 0.2f
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) NeonCyan else DarkSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) NeonCyan else DarkBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.setCrossfadeDuration(sec) }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${sec.toInt().takeIf { it.toFloat() == sec } ?: sec}s",
                                        color = if (isSel) AccentContent else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Cấu hình EQ & Bộ xử lý âm thanh
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Bộ chỉnh âm Equalizer", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bật bộ lọc Equalizer", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(if (equalizerState.isEnabled) "Đang áp dụng: ${equalizerState.activePresetName}" else "Đã tắt bộ lọc", color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Switch(
                        checked = equalizerState.isEnabled,
                        onCheckedChange = { viewModel.toggleEqualizerEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentContent, checkedTrackColor = NeonCyan)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DarkBorder)

                SettingItemRow(
                    icon = Icons.Default.Tune,
                    iconTint = NeonCyan,
                    title = "Tùy chỉnh dải tần & Hiệu ứng",
                    subtitle = "Mở bảng điều khiển Equalizer, Bass Boost & Âm vòm 3D",
                    onClick = { viewModel.setTab(MainTab.EQUALIZER) }
                )
            }
        }

        // Section: Cấu hình Gemini AI & Tìm kiếm Lời bài hát
        var apiKeyInput by remember(uiState.geminiApiKey) {
            mutableStateOf(viewModel.repository.getCustomGeminiApiKey().orEmpty())
        }
        var revealApiKey by remember { mutableStateOf(false) }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonViolet.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Gemini AI & Tìm lời", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Tạo lời Karaoke LRC tự động bằng AI", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key Gemini riêng") },
                    placeholder = { Text("Nhập API Key của bạn") },
                    singleLine = true,
                    visualTransformation = if (revealApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { revealApiKey = !revealApiKey }) {
                            Icon(
                                imageVector = if (revealApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (revealApiKey) "Ẩn API Key" else "Hiện API Key",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonViolet,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Key tích hợp của ứng dụng được giữ ẩn. Để trống để tiếp tục dùng key tích hợp.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (apiKeyInput.isNotBlank()) "Đang dùng API Key riêng" else "Đang dùng API Key tích hợp (được ẩn)",
                        color = if (apiKeyInput.isNotBlank()) NeonCyan else TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (apiKeyInput.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    apiKeyInput = ""
                                    revealApiKey = false
                                    viewModel.setGeminiApiKey("")
                                }
                            ) {
                                Text("Dùng key tích hợp", fontSize = 11.sp, color = TextSecondary)
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.setGeminiApiKey(apiKeyInput)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lưu Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Text("Mô hình AI ưu tiên", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Chọn phiên bản Gemini dùng để tìm kiếm và căn chỉnh mốc thời gian karaoke", color = TextSecondary, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(10.dp))

                val models = listOf(
                    Triple("gemini-3.7-flash", "Gemini 3.7 Flash", "Khuyên dùng • Chuẩn nhạc & Lời chính xác"),
                    Triple("gemini-3.8-flash", "Gemini 3.8 Flash", "Mới nhất • Khả năng suy luận cao cấp"),
                    Triple("gemini-3.6-flash", "Gemini 3.6 Flash", "Tốc độ cao • Phản hồi nhanh & Ổn định")
                )

                models.forEach { (modelId, modelName, modelDesc) ->
                    val isSelected = uiState.geminiModel == modelId
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) NeonViolet.copy(alpha = 0.15f) else DarkSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) NeonViolet else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.setGeminiModel(modelId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setGeminiModel(modelId) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = NeonViolet,
                                    unselectedColor = TextMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = modelName,
                                    color = if (isSelected) NeonViolet else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = modelDesc,
                                    color = TextMuted,
                                    fontSize = 10.5.sp,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Thông tin ứng dụng
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Thông tin & Giấy phép", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                InfoRow("Phiên bản", BuildConfig.VERSION_NAME)
                InfoRow("Tác giả", "CD")
                InfoRow("Ngôn ngữ", "Tiếng Việt (100% Native)")
                InfoRow("Bộ xử lý âm thanh", "Android AudioFX + Native DSP")
                InfoRow("Trình phân tích lời", "Karaoke LRC Synchronizer v2.0 + Gemini AI")
            }
        }
    }

    if (showCustomAccentDialog) {
        val previewColor = Color(customRed, customGreen, customBlue)
        AlertDialog(
            onDismissRequest = { showCustomAccentDialog = false },
            title = { Text("Tùy chỉnh màu nhấn") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = previewColor,
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {}
                    Text("Đỏ · ${(customRed * 255).toInt()}", fontSize = 11.sp)
                    Slider(value = customRed, onValueChange = { customRed = it }, valueRange = 0f..1f)
                    Text("Lục · ${(customGreen * 255).toInt()}", fontSize = 11.sp)
                    Slider(value = customGreen, onValueChange = { customGreen = it }, valueRange = 0f..1f)
                    Text("Lam · ${(customBlue * 255).toInt()}", fontSize = 11.sp)
                    Slider(value = customBlue, onValueChange = { customBlue = it }, valueRange = 0f..1f)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAccentColor(previewColor.toArgb())
                    showCustomAccentDialog = false
                }) { Text("Áp dụng") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomAccentDialog = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun SettingItemRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconTint.copy(alpha = 0.15f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }

        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(0.8f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.2f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
