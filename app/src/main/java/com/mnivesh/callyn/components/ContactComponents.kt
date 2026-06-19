package com.mnivesh.callyn.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import androidx.compose.runtime.*
import com.mnivesh.callyn.ui.theme.AppTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnivesh.callyn.db.AppContact
import kotlin.math.abs

// --- Data Classes ---
data class DeviceNumber(
    val number: String,
    val isDefault: Boolean
)

@Immutable
data class DeviceContact(
    val id: String,
    val name: String,
    val numbers: List<DeviceNumber>,
    val isStarred: Boolean = false
)

// --- Helper Functions ---
fun getColorForName(name: String): Color {
    val palette = listOf(
        Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF8B5CF6),
        Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444),
        Color(0xFF3B82F6), Color(0xFF14B8A6), Color(0xFFF97316)
    )
    return palette[abs(name.hashCode()) % palette.size]
}

fun getInitials(name: String): String {
    val trimmed = name.trim()

    // If it's a number (ignore +)
    if (trimmed.replace("+", "").all { it.isDigit() }) {
        return trimmed.replace("+", "").take(2)
    }

    return trimmed.split(" ")
        .mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { trimmed.firstOrNull { it.isLetter() }?.uppercase() ?: "" }
}

fun sanitizePhoneNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.length > 10) digits.takeLast(10) else digits
}

@Composable
fun getHighlightedText(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val startIndex = text.indexOf(query, ignoreCase = true)
    if (startIndex == -1) return AnnotatedString(text)

    val spanStyles = listOf(
        AnnotatedString.Range(
            SpanStyle(color = Color(0xFFCB9C00), fontWeight = FontWeight.ExtraBold),
            start = startIndex,
            end = startIndex + query.length
        )
    )
    return AnnotatedString(text, spanStyles = spanStyles)
}

// --- Shared UI Components ---

@Composable
fun CustomTabContent(text: String, icon: Any, count: Int?, isSelected: Boolean) {
    val isDark = AppTheme.colors.isDark
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon is androidx.compose.ui.graphics.vector.ImageVector) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF3B82F6) else AppTheme.colors.textSecondary,
                modifier = Modifier.size(18.sdp())
            )
        } else if (icon is Painter) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(28.sdp())
            )
        }

        Spacer(modifier = Modifier.width(8.sdp()))

        // force single line, let text ellipsize if tab width gets constrained
        Text(
            text = text,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 15.ssp(),
            color = if (isSelected) Color(0xFF3B82F6) else AppTheme.colors.textSecondary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        if(count != null) {
            Spacer(modifier = Modifier.width(8.sdp()))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) AppTheme.colors.textPrimary.copy(alpha = 0.15f) else AppTheme.colors.textPrimary.copy(alpha = 0.08f))
                    .padding(horizontal = 6.sdp(), vertical = 2.sdp())
            ) {
                // prevent wrapping for large numbers like 24682
                Text(
                    text = count.toString(),
                    fontSize = 12.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun FavoriteContactItem(contact: DeviceContact, onClick: () -> Unit) {
    val avatarColor = getColorForName(contact.name)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.sdp()).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.size(64.sdp()).clip(CircleShape).background(Brush.linearGradient(listOf(avatarColor, avatarColor.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center
        ) {
            Text(getInitials(contact.name), color = Color.White, fontSize = 22.ssp(), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.sdp()))
        Text(contact.name.split(" ").first(), color = AppTheme.colors.textPrimary.copy(alpha = 0.9f), fontSize = 12.ssp(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun ModernWorkContactCard(contact: AppContact, onClick: () -> Unit, highlightQuery: String = "") {
    val avatarColor = getColorForName(contact.name)
    val isDark = AppTheme.colors.isDark
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.sdp()).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.sdp()),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
        border = if (isDark) null else BorderStroke(1.sdp(), AppTheme.colors.border)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.sdp()), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.sdp()).clip(CircleShape).background(Brush.linearGradient(listOf(avatarColor, avatarColor.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(getInitials(contact.name), color = Color.White, fontSize = 20.ssp(), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.sdp()))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = getHighlightedText(contact.name, highlightQuery), fontSize = 17.ssp(), fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.sdp())) {
                    Icon(Icons.Default.FamilyRestroom, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(16.sdp()))
                    Spacer(modifier = Modifier.width(4.sdp()))
                    Text(text = getHighlightedText(contact.familyHead, highlightQuery), fontSize = 13.ssp(), color = Color(0xFF3B82F6), fontWeight = FontWeight.Medium)
                }
            }
            Box(
                modifier = Modifier.size(48.sdp()).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))).clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, "Call", tint = Color.White, modifier = Modifier.size(22.sdp()))
            }
        }
    }
}

@Composable
fun ModernDeviceContactCard(contact: DeviceContact, onClick: () -> Unit, highlightQuery: String = "") {
    val avatarColor = getColorForName(contact.name)
    val displayNumber = contact.numbers.firstOrNull()?.number ?: "No Number"
    val count = contact.numbers.size
    val isDark = AppTheme.colors.isDark

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.sdp()).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.sdp()),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
        border = if (isDark) null else BorderStroke(1.sdp(), AppTheme.colors.border)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.sdp()), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.sdp()).clip(CircleShape).background(Brush.linearGradient(listOf(avatarColor, avatarColor.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(getInitials(contact.name), color = Color.White, fontSize = 20.ssp(), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.sdp()))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = getHighlightedText(contact.name, highlightQuery), fontSize = 17.ssp(), fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.sdp())) {
                    Text(displayNumber, fontSize = 13.ssp(), color = AppTheme.colors.textSecondary)
                    if (count > 1) {
                        Spacer(modifier = Modifier.width(6.sdp()))
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.sdp())).background(AppTheme.colors.textSecondary.copy(alpha = 0.15f)).padding(horizontal = 4.sdp(), vertical = 2.sdp())) {
                            Text("+$count", fontSize = 10.ssp(), color = AppTheme.colors.textPrimary.copy(alpha = 0.8f))
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.size(48.sdp()).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))).clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, "Call", tint = Color.White, modifier = Modifier.size(22.sdp()))
            }
        }
    }
}

@Composable
fun ModernEmployeeCard(contact: AppContact, onClick: () -> Unit, highlightQuery: String = "") {
    val avatarColor = getColorForName(contact.name)
    val isDark = AppTheme.colors.isDark
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.sdp()),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
        border = if (isDark) null else BorderStroke(1.sdp(), AppTheme.colors.border)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.sdp()), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.sdp()).clip(CircleShape).background(Brush.linearGradient(listOf(avatarColor, avatarColor.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(getInitials(contact.name), color = Color.White, fontSize = 20.ssp(), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.sdp()))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = getHighlightedText(contact.name, highlightQuery), fontSize = 17.ssp(), fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.sdp())) {
                    Icon(Icons.Default.Business, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.sdp()))
                    Spacer(modifier = Modifier.width(4.sdp()))
                    Text(if (contact.familyHead.isNotBlank()) contact.familyHead else "Employee", fontSize = 13.ssp(), color = Color(0xFF3B82F6), fontWeight = FontWeight.Medium)
                }
            }
            Box(
                modifier = Modifier.size(48.sdp()).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))).clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, "Call", tint = Color.White, modifier = Modifier.size(22.sdp()))
            }
        }
    }
}

@Composable
fun PermissionRequiredCard(onGrantPermission: () -> Unit) {
    val isDark = AppTheme.colors.isDark
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.sdp()),
        shape = RoundedCornerShape(24.sdp()),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
        border = if (isDark) null else BorderStroke(1.sdp(), AppTheme.colors.border)
    ) {
        Column(modifier = Modifier.padding(32.sdp()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(80.sdp()).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ContactPage, null, tint = Color.White, modifier = Modifier.size(40.sdp()))
            }
            Spacer(modifier = Modifier.height(24.sdp()))
            Text("Contact Permission Required", fontSize = 20.ssp(), fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary, textAlign = TextAlign.Center)
            Text("Allow access to view your personal contacts", fontSize = 14.ssp(), color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.sdp()))
            Spacer(modifier = Modifier.height(24.sdp()))
            Button(
                onClick = onGrantPermission,
                modifier = Modifier.fillMaxWidth().height(52.sdp()),
                shape = RoundedCornerShape(16.sdp()),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Text("Grant Permission", fontSize = 16.ssp(), fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
fun EmptyStateCard(message: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.sdp())) {
        Box(modifier = Modifier.size(100.sdp()).clip(CircleShape).background(AppTheme.colors.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppTheme.colors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(48.sdp()))
        }
        Spacer(modifier = Modifier.height(24.sdp()))
        Text(message, fontSize = 16.ssp(), color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun LoadingCard(shimmerOffset: Float) {
    val isDark = AppTheme.colors.isDark
    Column(verticalArrangement = Arrangement.spacedBy(12.sdp()), modifier = Modifier.fillMaxWidth()) {
        repeat(1) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.sdp()),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                border = if (isDark) null else BorderStroke(1.sdp(), AppTheme.colors.border)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.sdp()), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.sdp()).clip(CircleShape).background(AppTheme.colors.surfaceVariant))
                    Spacer(modifier = Modifier.width(16.sdp()))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.sdp()).clip(RoundedCornerShape(8.sdp())).background(AppTheme.colors.surfaceVariant))
                        Spacer(modifier = Modifier.height(8.sdp()))
                        Box(modifier = Modifier.fillMaxWidth(0.4f).height(12.sdp()).clip(RoundedCornerShape(6.sdp())).background(AppTheme.colors.surfaceVariant.copy(alpha = 0.8f)))
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.sdp()), shape = RoundedCornerShape(20.sdp()), colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f))) {
        Row(modifier = Modifier.padding(20.sdp()), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.sdp()))
            Spacer(modifier = Modifier.width(12.sdp()))
            Text(message, color = Color(0xFFEF4444), fontSize = 15.ssp(), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ModernDetailRow(icon: ImageVector, label: String?, value: String?, iconColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(36.sdp()).clip(RoundedCornerShape(10.sdp())).background(iconColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.sdp()))
        }
        Spacer(modifier = Modifier.width(16.sdp()))
        Column {
            Text(label ?: "N/A", fontSize = 11.ssp(), color = AppTheme.colors.textSecondary, fontWeight = FontWeight.Medium)
            Text(value ?: "N/A", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.ssp(), color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Medium)
        }
    }
}