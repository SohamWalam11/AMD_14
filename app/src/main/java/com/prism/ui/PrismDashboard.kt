package com.prism.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.prism.ai.InferenceReasoningPipeline.PipelineStage
import com.prism.ai.InferenceReasoningPipeline.ForkedRecipe
import com.prism.ai.InferenceReasoningPipeline.IdentifiedItem
import com.prism.ai.InferenceReasoningPipeline.RecipeBranch

// === Color Palette inspired by mockup ===
private val BackgroundColor = Color(0xFFFBFaf5)
private val SurfaceColor = Color.White
private val TextPrimary = Color(0xFF1E1E1E)
private val TextSecondary = Color(0xFF757575)
private val ChipSelectedBg = Color(0xFFF2E6DA)
private val ChipSelectedText = Color(0xFF8B6B56)
private val ChipUnselectedBg = Color(0xFFEEEBE6)
private val ChipUnselectedText = Color(0xFF8C8A87)
private val FabBackgroundColor = Color(0xFF8D7C68)
private val FabIconColor = Color.White
private val BottomNavBg = Color.White
private val BottomNavIconActiveBg = Color(0xFFF2E6DA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrismDashboard(
    availableMinutes: Int,
    onTimeSelected: (Int) -> Unit,
    isRecording: Boolean,
    onVoiceToggle: () -> Unit,
    pipelineStage: PipelineStage?,
    stageCompleted: Set<PipelineStage>,
    forkedRecipe: ForkedRecipe?,
    uncertainIngredients: List<IdentifiedItem>,
    onIngredientConfirm: (List<IdentifiedItem>) -> Unit,
    onCameraReady: (PreviewView) -> Unit,
    thermalWarning: String?,
    modifier: Modifier = Modifier
) {
    var showIngredientSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uncertainIngredients) {
        if (uncertainIngredients.isNotEmpty()) showIngredientSheet = true
    }

    Box(modifier = modifier.fillMaxSize().background(BackgroundColor)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp) // space for bottom nav
        ) {
            Spacer(Modifier.height(48.dp))
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.WifiTethering, contentDescription = "Connection", tint = TextPrimary, modifier = Modifier.size(24.dp))
                Text("Prism", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            // Thermal warning banner
            thermalWarning?.let { warning ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                ) {
                    Text(warning, color = Color(0xFFD32F2F), fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(16.dp))
            }

            // === TIME CHIPS ===
            TimeChipRow(selected = availableMinutes, onSelect = onTimeSelected)
            Spacer(Modifier.height(24.dp))

            // === CAMERA PREVIEW WITH OVERLAYS ===
            CameraPreviewWithOverlay(onReady = onCameraReady)
            Spacer(Modifier.height(24.dp))

            // === PIPELINE PROGRESS ===
            if (pipelineStage != null || stageCompleted.isNotEmpty()) {
                PipelineProgressBar(current = pipelineStage, completed = stageCompleted)
                Spacer(Modifier.height(24.dp))
            }

            // === RECIPE FORK OUTPUT ===
            if (forkedRecipe != null) {
                RecipePathwaysSections(recipe = forkedRecipe)
            } else {
                PlaceholderRecipePathways()
            }
            
            Spacer(Modifier.height(80.dp)) 
        }

        // === FAB (Microphone) ===
        VoiceFab(
            isRecording = isRecording,
            onClick = onVoiceToggle,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 120.dp, end = 24.dp) // Above bottom nav
        )

        // === BOTTOM NAVIGATION is handled by parent Scaffold now ===
    }

    if (showIngredientSheet && uncertainIngredients.isNotEmpty()) {
        IngredientConfirmationSheet(
            items = uncertainIngredients,
            onConfirm = { confirmed ->
                showIngredientSheet = false
                onIngredientConfirm(confirmed)
            },
            onDismiss = { showIngredientSheet = false }
        )
    }
}

@Composable
private fun TimeChipRow(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(10, 20, 45)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            "AVAILABLE PREPARATION TIME",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { min ->
                val isSelected = selected == min
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) ChipSelectedBg else ChipUnselectedBg)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        "${min}m",
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) ChipSelectedText else ChipUnselectedText
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithOverlay(onReady: (PreviewView) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(380.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { onReady(it) }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Mock Overlay Elements (as per inspiration)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("IDENTIFIED", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                Text("Scanning...", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF8D7C68).copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("AI Vision", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun RecipePathwaysSections(recipe: ForkedRecipe) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Recipe Pathways", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("TAILORED TO YOUR VITALS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
        
        Spacer(Modifier.height(16.dp))
        
        // COMMON BASE
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                Text("COMMON BASE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text(recipe.title.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            }
        }
        
        // Connector Line
        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFE0E0E0)))
        
        // Branches
        if (recipe.branches.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(recipe.branches) { branch ->
                    BranchCardMockup(branch)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderRecipePathways() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Recipe Pathways", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("AWAITING INGREDIENTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
    }
}

@Composable
private fun BranchCardMockup(branch: RecipeBranch) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.width(160.dp).height(200.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("PATH", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                Icon(Icons.Default.Spa, contentDescription = null, tint = FabBackgroundColor, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(branch.memberName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            
            val description = branch.additions.joinToString(" & ") + 
                              if (branch.removals.isNotEmpty()) " (No ${branch.removals.joinToString()})" else ""
            Text(
                description.takeIf { it.isNotEmpty() } ?: "Standard preparation", 
                fontSize = 11.sp, 
                color = TextSecondary,
                lineHeight = 16.sp
            )
            
            Spacer(Modifier.weight(1f))
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BackgroundColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Custom", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
        }
    }
}

@Composable
fun PrismBottomNavigationBar(currentTab: Int, onTabSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(BottomNavBg)
            .padding(vertical = 12.dp, horizontal = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onTabSelect(0) }) {
                Icon(Icons.Default.GridView, contentDescription = "Menu", tint = if (currentTab == 0) TextPrimary else Color.LightGray, modifier = Modifier.size(28.dp))
            }
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (currentTab == 1) BottomNavIconActiveBg else Color.Transparent)
                    .clickable { onTabSelect(1) }
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Scan", tint = if (currentTab == 1) TextPrimary else Color.LightGray, modifier = Modifier.size(28.dp))
            }
            
            IconButton(onClick = { onTabSelect(2) }) {
                Icon(Icons.Default.PersonOutline, contentDescription = "Profile", tint = if (currentTab == 2) TextPrimary else Color.LightGray, modifier = Modifier.size(28.dp))
            }
        }
    }
}


@Composable
private fun VoiceFab(isRecording: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .size(64.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(if (isRecording) FabBackgroundColor.copy(alpha = pulseAlpha) else FabBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.MicNone,
                contentDescription = "Voice",
                tint = FabIconColor, 
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun PipelineProgressBar(current: PipelineStage?, completed: Set<PipelineStage>) {
    val stages = listOf(
        PipelineStage.Perception, PipelineStage.Reasoning,
        PipelineStage.Synthesis, PipelineStage.Forking
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { idx, stage ->
            val isDone = stage in completed
            val isActive = stage == current

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDone || isActive) FabBackgroundColor else Color(0xFFE0E0E0))
                )
                Spacer(Modifier.height(8.dp))
                if (isActive) {
                    Text(
                        stage.label.split(" ").first(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = FabBackgroundColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            if (idx < stages.size - 1) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientConfirmationSheet(
    items: List<IdentifiedItem>,
    onConfirm: (List<IdentifiedItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val kept = remember { mutableStateListOf(*items.toTypedArray()) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BottomNavBg
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("Confirm Ingredients", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("These items were detected with low confidence.", fontSize = 14.sp, color = TextSecondary)
            Spacer(Modifier.height(16.dp))

            kept.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text("${item.qty} · ${item.category}", fontSize = 12.sp, color = TextSecondary)
                    }
                    TextButton(onClick = { kept.remove(item) }) {
                        Text("Remove", color = Color(0xFFD32F2F))
                    }
                }
                HorizontalDivider(color = Color(0xFFEEEEEE))
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onConfirm(kept.toList()) },
                colors = ButtonDefaults.buttonColors(containerColor = FabBackgroundColor),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
