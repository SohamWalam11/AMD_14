package com.prism.state

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Compresses static profile + dynamic health + time constraint → <500-token JSON.
 * Field names abbreviated: g=goal, d=diet, m=members, sl=sleep, st=steps, am=avail_min, ts=epoch
 */
object StateCompressionEngine {

    private const val TAG = "StateCompression"
    private const val MAX_CHARS = 2000   // ~500 tokens
    private const val MAX_MEMBERS = 2
    private const val DEF_SLEEP = 7.0f
    private const val DEF_STEPS = 5000

    enum class FitnessGoal(val code: Int) {
        MAINTAIN(0), MUSCLE_GAIN(1), FAT_LOSS(2), ENDURANCE(3), RECOVERY(4)
    }

    enum class DietType(val code: String) {
        OMNIVORE("omni"), VEGETARIAN("veg"), VEGAN("vgn"), KETO("keto"),
        PALEO("paleo"), HALAL("hal"), KOSHER("kos"), JAIN("jain"), CUSTOM("cust")
    }

    data class HouseholdMember(val name: String, val restrictions: List<String>)
    data class UserProfile(val goal: FitnessGoal, val diet: DietType, val members: List<HouseholdMember>)
    data class HealthMetrics(val sleepHours: Float, val stepCount: Int)

    data class CompressedState(
        val goal: Int, val diet: String, val members: List<HouseholdMember>,
        val sleepH: Float, val steps: Int, val availMin: Int,
        val epochSeconds: Long, val truncated: Boolean = false
    ) {
        fun toJson(): String = JSONObject().apply {
            put("g", goal); put("d", diet); put("sl", sleepH)
            put("st", steps); put("am", availMin); put("ts", epochSeconds)
            put("m", JSONArray().also { arr ->
                members.forEach { m ->
                    arr.put(JSONObject().apply {
                        put("n", m.name); put("r", m.restrictions.joinToString(","))
                    })
                }
            })
            if (truncated) put("tr", true)
        }.toString()
    }

    suspend fun compress(ctx: Context, profile: UserProfile, availMin: Int): CompressedState {
        val health = readHealth(ctx)
        return enforce(CompressedState(
            profile.goal.code, profile.diet.code, profile.members,
            health.sleepHours, health.stepCount, availMin, Instant.now().epochSecond
        ))
    }

    private suspend fun readHealth(ctx: Context): HealthMetrics = try {
        val client = HealthConnectClient.getOrCreate(ctx)
        val now = Instant.now()
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val prevDay = dayStart.minus(1, ChronoUnit.DAYS)

        val sleepH = client.readRecords(ReadRecordsRequest(
            SleepSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(prevDay, dayStart)
        )).records.sumOf {
            (it.endTime.toEpochMilli() - it.startTime.toEpochMilli()).toDouble()
        }.let { (it / 3_600_000.0).toFloat().coerceIn(0f, 24f) }

        val steps = client.readRecords(ReadRecordsRequest(
            StepsRecord::class, timeRangeFilter = TimeRangeFilter.between(dayStart, now)
        )).records.sumOf { it.count }.toInt()

        HealthMetrics(if (sleepH > 0) sleepH else DEF_SLEEP, if (steps > 0) steps else DEF_STEPS)
    } catch (e: Exception) {
        Log.w(TAG, "Health Connect fallback: ${e.message}")
        HealthMetrics(DEF_SLEEP, DEF_STEPS)
    }

    /** Priority truncation: members → sleep → steps */
    private fun enforce(state: CompressedState): CompressedState {
        var s = state
        if (s.toJson().length > MAX_CHARS && s.members.size > MAX_MEMBERS)
            s = s.copy(members = s.members.take(MAX_MEMBERS), truncated = true)
        if (s.toJson().length > MAX_CHARS) s = s.copy(sleepH = DEF_SLEEP)
        if (s.toJson().length > MAX_CHARS) s = s.copy(steps = DEF_STEPS)
        return s
    }

    fun buildManual(
        goal: FitnessGoal = FitnessGoal.MUSCLE_GAIN, diet: DietType = DietType.OMNIVORE,
        members: List<HouseholdMember> = emptyList(), sleepH: Float = DEF_SLEEP,
        steps: Int = DEF_STEPS, availMin: Int = 30
    ) = enforce(CompressedState(goal.code, diet.code, members, sleepH, steps, availMin, Instant.now().epochSecond))
}
