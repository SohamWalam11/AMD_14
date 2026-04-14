package com.prism.state

import android.content.Context
import com.prism.state.proto.DietType
import com.prism.state.proto.FitnessGoal
import com.prism.state.proto.UserProfileProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileManager(private val context: Context) {

    val profileFlow: Flow<UserProfileProto> = context.userProfileDataStore.data

    val onboardingCompleted: Flow<Boolean> = profileFlow.map { it.onboardingCompleted }

    suspend fun updateOnboarding(completed: Boolean) {
        context.userProfileDataStore.updateData { current ->
            current.toBuilder().setOnboardingCompleted(completed).build()
        }
    }

    suspend fun updateDiet(diet: DietType) {
        context.userProfileDataStore.updateData { current ->
            current.toBuilder().setDiet(diet).build()
        }
    }

    suspend fun updateGoal(goal: FitnessGoal) {
        context.userProfileDataStore.updateData { current ->
            current.toBuilder().setGoal(goal).build()
        }
    }
}
