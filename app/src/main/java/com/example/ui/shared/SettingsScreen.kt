package com.example.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.VideoViewModel

@Composable
fun SettingsScreen(
    musicViewModel: MusicViewModel,
    videoViewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Subtitle variables
    val subLanguage by videoViewModel.subLanguage.collectAsState()
    val subFontSize by videoViewModel.subFontSize.collectAsState()
    val rememberPosition by videoViewModel.rememberPosition.collectAsState()
    val defaultSpeed by videoViewModel.defaultSpeed.collectAsState()

    // EQ bands flows
    val eq60Hz by musicViewModel.eq60Hz.collectAsState()
    val eq230Hz by musicViewModel.eq230Hz.collectAsState()
    val eq910Hz by musicViewModel.eq910Hz.collectAsState()
    val eq3k6Hz by musicViewModel.eq3k6Hz.collectAsState()
    val eq14kHz by musicViewModel.eq14kHz.collectAsState()

    var showRescanNotif by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState)
            .testTag("settings_screen_root")
    ) {
        Text(
            text = "الإعدادات العامة (Settings)",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // SECTION 1: EQUALIZER MODIFIERS (المحاكي الصوتي)
        SettingsCategoryHeader(title = "أكولايزر الصوت (Equalizer Simulators)", icon = Icons.Default.GraphicEq)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 60Hz
                BandSliderRow(title = "60 Hz (Bass Boost)", value = eq60Hz, onValueChange = { musicViewModel.eq60Hz.value = it }, tag = "eq_band_60")
                // 230Hz
                BandSliderRow(title = "230 Hz", value = eq230Hz, onValueChange = { musicViewModel.eq230Hz.value = it }, tag = "eq_band_230")
                // 910Hz
                BandSliderRow(title = "910 Hz", value = eq910Hz, onValueChange = { musicViewModel.eq910Hz.value = it }, tag = "eq_band_910")
                // 3.6kHz
                BandSliderRow(title = "3.6 kHz", value = eq3k6Hz, onValueChange = { musicViewModel.eq3k6Hz.value = it }, tag = "eq_band_3k")
                // 14kHz
                BandSliderRow(title = "14 kHz (Treble)", value = eq14kHz, onValueChange = { musicViewModel.eq14kHz.value = it }, tag = "eq_band_14k")
            }
        }

        // SECTION 2: SUBTITLE PREFERENCES (ترجمة الأفلام)
        SettingsCategoryHeader(title = "الترجمة والتحكم (Subtitles & Players)", icon = Icons.Default.Subtitles)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Font Size Settings
                Column {
                    Text(text = "حجم خط الترجمة الافتراضي: ${subFontSize.toInt()}sp", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = subFontSize,
                        onValueChange = { videoViewModel.updateSubtitleFontSize(it) },
                        valueRange = 10f..32f,
                        colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_subtitle_size")
                    )
                }

                // Subtitle language dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "اللغة الافتراضية للترجمة:", color = Color.White, fontSize = 13.sp)
                    TextButton(onClick = {
                        val nextLang = if (subLanguage == "Arabic (AR)") "English (EN)" else "Arabic (AR)"
                        videoViewModel.updateSubtitleLanguage(nextLang)
                    }) {
                        Text(text = subLanguage, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.05f))

                // Play speed defaults settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "سرعة التشغيل التلقائية:", color = Color.White, fontSize = 13.sp)
                    TextButton(onClick = {
                        val nextSpeed = when (defaultSpeed) {
                            "1.0x" -> "1.25x"
                            "1.25x" -> "1.5x"
                            else -> "1.0x"
                        }
                        videoViewModel.updateDefaultSpeed(nextSpeed)
                    }) {
                        Text(text = defaultSpeed, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // SECTION 3: SYSTEM METADATA ACTIONS (خيارات وإجراءات النظام)
        SettingsCategoryHeader(title = "إعدادات التشغيل الذاكرة والبحث", icon = Icons.Default.Settings)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Save Play Position Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "تذكر مكان التوقف تلقائياً", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "سيقوم مشغل الأفلام باستئناف تشغيل الفيديو من آخر موضع توقفت فيه.", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = rememberPosition,
                        onCheckedChange = { videoViewModel.updateRememberPosition(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("settings_remember_position_switch")
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.05f))

                // Rescan Button
                Button(
                    onClick = {
                        showRescanNotif = true
                        musicViewModel.loadMedia()
                        videoViewModel.loadFoldersAndVideos()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_rescan_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Text(text = "إعادة مسح تخزين الجهاز (Rescan Storage)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                if (showRescanNotif) {
                    Text(
                        text = "جاري إعادة فحص ملفات الميديا والترجمات... (Scanning completed)",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text = title, color = Color.LightGray.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun BandSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    tag: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            Text(text = String.format("%.1f dB", value), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -10f..10f,
            colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
        )
    }
}
