package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.FoulCompactTopBar
import com.example.ui.theme.FoulBlackBg
import com.example.ui.theme.FoulCardDark
import com.example.ui.theme.FoulCardElevated
import com.example.ui.theme.FoulDivider
import com.example.ui.theme.FoulPurpleDeep
import com.example.ui.theme.FoulTextSecondary

@Composable
fun SettingsScreen(
    telegramUrl: String = "https://t.me/jdj_q"
) {
    val context = LocalContext.current
    val targetUrl = telegramUrl.ifBlank { "https://t.me/jdj_q" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoulBlackBg)
    ) {
        // Sleek Compact Top Bar
        FoulCompactTopBar(
            title = "الإعدادات",
            subtitle = "القناة الرسمية والتواصل",
            leadingIcon = Icons.Default.Settings
        )

        Spacer(Modifier.height(20.dp))

        // ONLY Option: Official Telegram Channel Card with Custom Vector Telegram Emblem
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = FoulCardDark),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/jdj_q"))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            context.startActivity(fallbackIntent)
                        } catch (_: Exception) {
                        }
                    }
                }
                .border(1.dp, FoulDivider, RoundedCornerShape(18.dp))
                .testTag("settings_telegram_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                FoulPurpleDeep,
                                FoulCardDark
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Custom Professional Telegram Paper-Plane Vector Emblem
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2AABEE), Color(0xFF1E88E5))
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(26.dp)) {
                        val w = size.width
                        val h = size.height
                        val path = Path().apply {
                            moveTo(w * 0.92f, h * 0.14f)
                            lineTo(w * 0.08f, h * 0.47f)
                            lineTo(w * 0.34f, h * 0.58f)
                            lineTo(w * 0.76f, h * 0.28f)
                            lineTo(w * 0.45f, h * 0.64f)
                            lineTo(w * 0.45f, h * 0.86f)
                            lineTo(w * 0.58f, h * 0.72f)
                            lineTo(w * 0.78f, h * 0.86f)
                            close()
                        }
                        drawPath(path = path, color = Color.White)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "قناة التيلجرام الرسمية",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "اضغط للانتقال مباشرة إلى قناة التيلجرام",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoulTextSecondary
                    )
                    Surface(
                        color = FoulCardElevated,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "t.me/jdj_q",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(FoulCardElevated)
                        .border(1.dp, FoulDivider, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "فتح قناة التيلجرام",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
