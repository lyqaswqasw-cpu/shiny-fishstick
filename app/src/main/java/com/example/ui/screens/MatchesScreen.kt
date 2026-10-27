package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.LeagueMatchesGroup
import com.example.data.model.MatchDetailData
import com.example.data.model.MatchItem
import com.example.ui.components.CompactTopBarIconButton
import com.example.ui.components.FoulCompactTopBar
import com.example.ui.components.OrderedColorfulDivider
import com.example.ui.theme.FoulBlackBg
import com.example.ui.theme.FoulBottomBarBlack
import com.example.ui.theme.FoulCardDark
import com.example.ui.theme.FoulCardElevated
import com.example.ui.theme.FoulDivider
import com.example.ui.theme.FoulLiveGreen
import com.example.ui.theme.FoulLiveRed
import com.example.ui.theme.FoulPurpleBright
import com.example.ui.theme.FoulPurpleDeep
import com.example.ui.theme.FoulTextMuted
import com.example.ui.theme.FoulTextSecondary
import com.example.ui.viewmodel.DateOption
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Matches Schedule Screen:
 * - Sleek Top Bar
 * - Natural 3-part Date Bar: ONLY Yesterday (أمس), Today / Calendar Table Opener (اليوم - جدول تقويم للفتح والعرض), and Tomorrow (غداً)
 *   without any garish purple backgrounds!
 * - Ordered League Cards (UEFA CL -> La Liga -> Serie A -> Premier League -> Others)
 */
@Composable
fun MatchesScreen(
    dateOptions: List<DateOption>,
    selectedDate: String,
    isLoading: Boolean,
    error: String?,
    leagueGroups: List<LeagueMatchesGroup>,
    selectedMatch: MatchItem?,
    isLoadingMatchDetail: Boolean,
    matchDetailError: String?,
    matchDetail: MatchDetailData?,
    onSelectDate: (String) -> Unit,
    onMatchClick: (MatchItem) -> Unit,
    onCloseMatchDetail: () -> Unit,
    onRefresh: () -> Unit
) {
    if (selectedMatch != null) {
        MatchDetailScreen(
            match = selectedMatch,
            isLoading = isLoadingMatchDetail,
            error = matchDetailError,
            detail = matchDetail,
            onBack = onCloseMatchDetail,
            onRetry = { onMatchClick(selectedMatch) }
        )
        return
    }

    val baghdadTz = remember { TimeZone.getTimeZone("Asia/Baghdad") }
    val apiFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = baghdadTz } }

    val yesterdayDate = remember {
        dateOptions.firstOrNull { it.labelAr == "أمس" }?.apiDateString ?: run {
            val c = Calendar.getInstance(baghdadTz)
            c.add(Calendar.DAY_OF_YEAR, -1)
            apiFmt.format(c.time)
        }
    }
    val todayDate = remember {
        dateOptions.firstOrNull { it.labelAr == "اليوم" }?.apiDateString ?: run {
            val c = Calendar.getInstance(baghdadTz)
            apiFmt.format(c.time)
        }
    }
    val tomorrowDate = remember {
        dateOptions.firstOrNull { it.labelAr == "غداً" }?.apiDateString ?: run {
            val c = Calendar.getInstance(baghdadTz)
            c.add(Calendar.DAY_OF_YEAR, 1)
            apiFmt.format(c.time)
        }
    }

    var isCalendarDialogOpen by remember { mutableStateOf(false) }

    val centerCalendarLabel = when (selectedDate) {
        todayDate -> "اليوم • $selectedDate"
        yesterdayDate -> "أمس • $selectedDate"
        tomorrowDate -> "غداً • $selectedDate"
        else -> selectedDate
    }

    if (isCalendarDialogOpen) {
        CalendarTableDialog(
            selectedDateStr = selectedDate,
            todayDateStr = todayDate,
            onSelectDate = { pickedDate ->
                isCalendarDialogOpen = false
                onSelectDate(pickedDate)
            },
            onDismiss = { isCalendarDialogOpen = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoulBlackBg)
    ) {
        // Sleek Compact Top Bar
        FoulCompactTopBar(
            title = "جدول المباريات",
            subtitle = "النتائج والمواعيد المباشرة",
            leadingIcon = Icons.Default.SportsSoccer,
            actions = {
                CompactTopBarIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "تحديث المباريات",
                    onClick = onRefresh,
                    modifier = Modifier.testTag("matches_refresh_button")
                )
            }
        )

        // Clean 3-Part Date Bar: أمس | اليوم (جدول تقويم للفتح والعرض) | غداً
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FoulBottomBarBlack)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. أمس (Yesterday)
            val isYesterdaySelected = selectedDate == yesterdayDate
            Surface(
                color = if (isYesterdaySelected) FoulCardElevated else FoulCardDark,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectDate(yesterdayDate) }
                    .border(
                        width = 1.dp,
                        color = if (isYesterdaySelected) Color.White.copy(alpha = 0.45f) else FoulDivider,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .testTag("date_yesterday_button")
            ) {
                Text(
                    text = "أمس",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isYesterdaySelected) Color.White else FoulTextSecondary,
                    fontWeight = if (isYesterdaySelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }

            // 2. اليوم / Calendar Picker Button (بشكل جدول تقويم للفتح والعرض)
            val isCenterHighlighted = selectedDate != yesterdayDate && selectedDate != tomorrowDate
            Surface(
                color = if (isCenterHighlighted) FoulCardElevated else FoulCardDark,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { isCalendarDialogOpen = true }
                    .border(
                        width = 1.dp,
                        color = if (isCenterHighlighted) Color.White.copy(alpha = 0.45f) else FoulDivider,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .testTag("date_calendar_picker_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "فتح التقويم",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = centerCalendarLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = FoulTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 3. غداً (Tomorrow)
            val isTomorrowSelected = selectedDate == tomorrowDate
            Surface(
                color = if (isTomorrowSelected) FoulCardElevated else FoulCardDark,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectDate(tomorrowDate) }
                    .border(
                        width = 1.dp,
                        color = if (isTomorrowSelected) Color.White.copy(alpha = 0.45f) else FoulDivider,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .testTag("date_tomorrow_button")
            ) {
                Text(
                    text = "غداً",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isTomorrowSelected) Color.White else FoulTextSecondary,
                    fontWeight = if (isTomorrowSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }
        OrderedColorfulDivider(thickness = 1.dp)

        // Matches Content
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                        Text(
                            text = "جاري جلب جدول المباريات...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FoulTextSecondary
                        )
                    }
                }
            }

            error != null && leagueGroups.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = FoulCardElevated)
                        ) {
                            Text("إعادة المحاولة", color = Color.White)
                        }
                    }
                }
            }

            leagueGroups.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد مباريات مسجلة في هذا التاريخ",
                        style = MaterialTheme.typography.titleMedium,
                        color = FoulTextSecondary
                    )
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("matches_lazy_column")
                ) {
                    items(leagueGroups, key = { it.competitionId }) { group ->
                        LeagueGroupCard(
                            group = group,
                            onMatchClick = onMatchClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Calendar Table Modal ("بشكل جدول تقويم للفتح والعرض")
 */
@Composable
private fun CalendarTableDialog(
    selectedDateStr: String,
    todayDateStr: String,
    onSelectDate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val baghdadTz = remember { TimeZone.getTimeZone("Asia/Baghdad") }
    val apiFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = baghdadTz } }

    val initialCal = remember(selectedDateStr) {
        val c = Calendar.getInstance(baghdadTz)
        try {
            val parsed = apiFmt.parse(selectedDateStr)
            if (parsed != null) c.time = parsed
        } catch (_: Exception) {
        }
        c
    }

    var currentYear by remember { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(initialCal.get(Calendar.MONTH)) }

    val arabicMonths = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )
    val weekDaysAr = listOf("أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")

    val daysInMonth = remember(currentYear, currentMonth) {
        val c = Calendar.getInstance(baghdadTz)
        c.set(currentYear, currentMonth, 1)
        val maxDay = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = c.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
        val cells = mutableListOf<Int?>()
        repeat(firstDayOfWeek) { cells.add(null) }
        for (d in 1..maxDay) cells.add(d)
        cells
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = FoulCardDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FoulDivider, RoundedCornerShape(18.dp))
                .testTag("calendar_table_dialog")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "اختر تاريخ المباريات",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = FoulTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Month & Year Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FoulCardElevated)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (currentMonth == 0) {
                                currentMonth = 11
                                currentYear -= 1
                            } else {
                                currentMonth -= 1
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "الشهر السابق",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "${arabicMonths[currentMonth]} $currentYear",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            if (currentMonth == 11) {
                                currentMonth = 0
                                currentYear += 1
                            } else {
                                currentMonth += 1
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "الشهر التالي",
                            tint = Color.White
                        )
                    }
                }

                // Weekday Headers
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekDaysAr.forEach { dayName ->
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = FoulTextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 7-Column Calendar Table Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) {
                    items(daysInMonth.size) { index ->
                        val dayNum = daysInMonth[index]
                        if (dayNum == null) {
                            Box(modifier = Modifier.aspectRatio(1f))
                        } else {
                            val dateStr = String.format(
                                Locale.US,
                                "%02d/%02d/%04d",
                                dayNum,
                                currentMonth + 1,
                                currentYear
                            )
                            val isSelected = dateStr == selectedDateStr
                            val isToday = dateStr == todayDateStr

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isSelected -> FoulPurpleBright
                                            isToday -> FoulCardElevated
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = when {
                                            isSelected -> Color.White
                                            isToday -> FoulDivider
                                            else -> Color.Transparent
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectDate(dateStr) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$dayNum",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Quick Jump Buttons (اليوم + 21/10/2026)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSelectDate(todayDateStr) },
                        colors = ButtonDefaults.buttonColors(containerColor = FoulCardElevated),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("مباريات اليوم", color = Color.White)
                    }
                    Button(
                        onClick = { onSelectDate("21/10/2026") },
                        colors = ButtonDefaults.buttonColors(containerColor = FoulPurpleDeep),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("21/10/2026", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeagueGroupCard(
    group: LeagueMatchesGroup,
    onMatchClick: (MatchItem) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = FoulCardDark),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FoulDivider, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Clean Dark League Header Row (No garish purple)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FoulCardElevated)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AsyncImage(
                        model = group.logoUrl,
                        contentDescription = group.competitionName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                    )
                    Text(
                        text = group.competitionName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = FoulBlackBg.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${group.matches.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = FoulTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Matches List inside League
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                group.matches.forEach { match ->
                    MatchRowItem(
                        match = match,
                        onClick = { onMatchClick(match) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchRowItem(
    match: MatchItem,
    onClick: () -> Unit
) {
    Surface(
        color = FoulBlackBg.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .border(1.dp, FoulDivider.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .testTag("match_card_${match.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Home Team (Right in RTL)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AsyncImage(
                    model = match.homeTeam.logoUrl,
                    contentDescription = match.homeTeam.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = match.homeTeam.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (match.homeTeam.redCards > 0) {
                        Text(
                            text = "طرد (${match.homeTeam.redCards})",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoulLiveRed
                        )
                    }
                }
            }

            // Center Score / Time Pill
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                if (match.homeTeam.hasScore && match.awayTeam.hasScore) {
                    Surface(
                        color = FoulCardElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(
                            1.dp,
                            if (match.isLive) FoulLiveGreen else FoulDivider,
                            RoundedCornerShape(8.dp)
                        )
                    ) {
                        Text(
                            text = "${match.homeTeam.score}  -  ${match.awayTeam.score}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (match.isLive && match.gameTimeDisplay.isNotEmpty()) {
                            "مباشر ${match.gameTimeDisplay}"
                        } else {
                            match.statusText
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (match.isLive) FoulLiveGreen else FoulTextMuted,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Surface(
                        color = FoulCardElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, FoulDivider, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = match.displayTime.ifBlank { match.statusText },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Away Team (Left in RTL)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = match.awayTeam.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    if (match.awayTeam.redCards > 0) {
                        Text(
                            text = "طرد (${match.awayTeam.redCards})",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoulLiveRed
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                AsyncImage(
                    model = match.awayTeam.logoUrl,
                    contentDescription = match.awayTeam.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
