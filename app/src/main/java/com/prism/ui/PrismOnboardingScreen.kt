package com.prism.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.state.proto.DietType
import com.prism.state.proto.FitnessGoal

@Composable
fun PrismOnboardingScreen(
    onComplete: (DietType, FitnessGoal) -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var selectedDiet by remember { mutableStateOf(DietType.OMNIVORE) }
    var selectedGoal by remember { mutableStateOf(FitnessGoal.MAINTAIN) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFBFaf5))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    fadeIn() + slideInHorizontally { it } togetherWith fadeOut() + slideOutHorizontally { -it }
                },
                label = "onboarding_step"
            ) { currentStep ->
                when (currentStep) {
                    0 -> DietSelectionStep(selectedDiet) { selectedDiet = it }
                    1 -> GoalSelectionStep(selectedGoal) { selectedGoal = it }
                }
            }

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = {
                    if (step < 1) step++
                    else onComplete(selectedDiet, selectedGoal)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8D7C68))
            ) {
                Text(
                    if (step < 1) "Continue" else "Get Started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun DietSelectionStep(selected: DietType, onSelect: (DietType) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "What's your dietary preference?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1E1E),
            textAlign = TextAlign.Center
        )
        Text(
            "Prism will tailor all suggestions to this.",
            fontSize = 14.sp,
            color = Color(0xFF757575),
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        val options = listOf(
            DietType.VEGETARIAN to "Vegetarian",
            DietType.OMNIVORE to "Non-Vegetarian",
            DietType.VEGAN to "Vegan",
            DietType.JAIN to "Jain"
        )

        options.forEach { (type, label) ->
            SelectionCard(
                label = label,
                isSelected = selected == type,
                onClick = { onSelect(type) }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GoalSelectionStep(selected: FitnessGoal, onSelect: (FitnessGoal) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "What's your primary goal?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1E1E),
            textAlign = TextAlign.Center
        )
        Text(
            "We'll adjust portion sizes and nutrients.",
            fontSize = 14.sp,
            color = Color(0xFF757575),
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        val options = listOf(
            FitnessGoal.MUSCLE_GAIN to "Muscle Gain",
            FitnessGoal.FAT_LOSS to "Fat Loss",
            FitnessGoal.MAINTAIN to "Maintenance",
            FitnessGoal.ENDURANCE to "Athletic Performance"
        )

        options.forEach { (goal, label) ->
            SelectionCard(
                label = label,
                isSelected = selected == goal,
                onClick = { onSelect(goal) }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SelectionCard(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFFF2E6DA) else Color.White)
            .border(
                width = 2.dp,
                color = if (isSelected) Color(0xFF8D7C68) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color(0xFF8D7C68) else Color(0xFF1E1E1E),
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF8D7C68))
        }
    }
}
