package com.prism.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BackgroundColor = Color(0xFFFBFaf5)
private val SurfaceColor = Color.White
private val TextPrimary = Color(0xFF1E1E1E)
private val TextSecondary = Color(0xFF757575)
private val AccentColor = Color(0xFF8D7C68)

@Composable
fun PrismProfileScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(BackgroundColor)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp) // space for nav
        ) {
            item { Spacer(Modifier.height(48.dp)) }

            // Header Segment
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vitals & Profile", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(AccentColor), contentAlignment = Alignment.Center) {
                        Text("S", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            // Macros & Goals
            item {
                Text("CURRENT GOAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Aggressive Muscle Gain", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text("3200 kcal · 180g Protein", fontSize = 14.sp, color = AccentColor, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            // Diet Type
            item {
                Text("DIETARY REGIMEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DietBadge("Omnivore", true)
                    DietBadge("High Protein", true)
                    DietBadge("Keto", false)
                }
                Spacer(Modifier.height(32.dp))
            }

            // Household Members
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("HOUSEHOLD MEMBERS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                    Icon(Icons.Default.Add, contentDescription = "Add Member", tint = AccentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                HouseholdMemberCard("Soham", "No restrictions", Icons.Default.Person)
                Spacer(Modifier.height(12.dp))
                HouseholdMemberCard("Alex", "Lactose Intolerant", Icons.Default.Face)
                Spacer(Modifier.height(40.dp))
            }
            
            // Advanced Settings
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        SettingsRow("Smart Geofencing", Icons.Default.LocationOn)
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        SettingsRow("Thermal ML Limits", Icons.Default.Thermostat)
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        SettingsRow("Sync Credentials", Icons.Default.Sync)
                    }
                }
            }
        }
    }
}

@Composable
private fun DietBadge(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if(selected) AccentColor.copy(alpha=0.15f) else Color(0xFFE0E0E0))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label, 
            fontSize = 12.sp, 
            fontWeight = FontWeight.Bold, 
            color = if(selected) AccentColor else TextSecondary
        )
    }
}

@Composable
private fun HouseholdMemberCard(name: String, notes: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF2E6DA)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = AccentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(notes, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}
