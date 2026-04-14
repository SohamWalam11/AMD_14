package com.prism.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BackgroundColor = Color(0xFFFBFaf5)
private val SurfaceColor = Color.White
private val TextPrimary = Color(0xFF1E1E1E)
private val TextSecondary = Color(0xFF757575)
private val AccentColor = Color(0xFF8D7C68)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrismMenuScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(BackgroundColor).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(48.dp))
        
        Text("Saved Pathways", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Explore your previous culinary reasoning branches", fontSize = 14.sp, color = TextSecondary)
        
        Spacer(Modifier.height(24.dp))

        // Search Bar mock
        TextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("Search ingredients or recipes...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceColor,
                unfocusedContainerColor = SurfaceColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))

        val dummyHistory = listOf(
            HistoryItem("High Protein Omelette", "Egg, Spinach, Chicken", "15m", 510, true),
            HistoryItem("Keto Avocado Salad", "Avocado, Olive Oil", "5m", 320, false),
            HistoryItem("Recovery Spiced Broth", "Chicken Bone Broth, Ginger", "40m", 120, true),
            HistoryItem("Endurance Pasta", "Whole Wheat Pasta, Tomato", "25m", 600, false)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(dummyHistory) { item ->
                HistoryCard(item)
            }
        }
    }
}

data class HistoryItem(val title: String, val ingredients: String, val time: String, val kcal: Int, val favorite: Boolean)

@Composable
private fun HistoryCard(item: HistoryItem) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().height(160.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2E6DA)).padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(item.time, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentColor)
                }
                if (item.favorite) {
                    Icon(Icons.Default.Star, contentDescription = "Fav", tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(item.ingredients, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.kcal} kcal", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AccentColor)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}
