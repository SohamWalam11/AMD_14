package com.prism.ai

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * 4-stage on-device reasoning pipeline for Prism.
 *
 * Stages run sequentially within the same Gemma session to exploit
 * KV-cache locality — each stage sees prior context without re-encoding.
 *
 * LATENCY BUDGET (Tensor G4 target):
 *   Perception: ~1.0s | Reasoning: ~1.5s | Synthesis: ~0.8s | Forking: ~0.7s
 *   Total: ~4.0s nominal, 8s hard timeout per stage.
 */
object InferenceReasoningPipeline {

    private const val TAG = "ReasoningPipeline"
    private const val STAGE_TIMEOUT_MS = 8_000L
    private const val MANIFEST_CACHE_TTL_MS = 30_000L

    // --- Pipeline Stage Definitions ---

    sealed class PipelineStage(val ordinal: Int, val label: String) {
        object Perception : PipelineStage(0, "Identifying Ingredients")
        object Reasoning : PipelineStage(1, "Analyzing Constraints")
        object Synthesis : PipelineStage(2, "Generating Recipe")
        object Forking : PipelineStage(3, "Planning Meal Forks")
    }

    sealed class PipelineEvent {
        data class StageStarted(val stage: PipelineStage) : PipelineEvent()
        data class StageCompleted(val stage: PipelineStage) : PipelineEvent()
        data class StageFailed(val stage: PipelineStage, val error: String) : PipelineEvent()
        data class PartialResult(val text: String) : PipelineEvent()
        data class FinalResult(val recipe: ForkedRecipe) : PipelineEvent()
        data class IngredientConfirmation(val manifest: IngredientManifest) : PipelineEvent()
    }

    // --- Data Models ---

    data class IdentifiedItem(
        val name: String,
        val qty: String,
        val category: String,
        val confident: Boolean
    )

    data class IngredientManifest(
        val items: List<IdentifiedItem>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean =
            System.currentTimeMillis() - timestamp > MANIFEST_CACHE_TTL_MS

        fun toPromptString(): String = items.joinToString(", ") {
            "${it.name}(${it.qty})"
        }
    }

    data class IngredientMapping(
        val ingredient: String,
        val allowedMembers: List<String>,
        val methods: List<String>
    )

    data class FeasibilityMatrix(val mappings: List<IngredientMapping>) {
        fun toPromptString(): String {
            val obj = JSONArray()
            mappings.forEach { m ->
                obj.put(JSONObject().apply {
                    put("i", m.ingredient)
                    put("m", JSONArray(m.allowedMembers))
                    put("c", JSONArray(m.methods))
                })
            }
            return obj.toString()
        }
    }

    data class BaseRecipe(
        val title: String,
        val totalMin: Int,
        val steps: List<String>
    )

    data class StepMod(val stepIndex: Int, val modification: String)

    data class RecipeBranch(
        val memberName: String,
        val modifiedSteps: List<StepMod>,
        val additions: List<String>,
        val removals: List<String>
    )

    data class ForkedRecipe(
        val title: String,
        val totalMin: Int,
        val baseSteps: List<String>,
        val forkAfterStep: Int,
        val branches: List<RecipeBranch>
    )

    // --- Cached State ---
    @Volatile
    private var cachedManifest: IngredientManifest? = null

    /**
     * Main pipeline entry point. Emits [PipelineEvent]s for UI progress tracking.
     *
     * @param frame CameraX captured bitmap (nullable if using cached manifest)
     * @param audioText Transcribed user voice query
     * @param stateJson CompressedState JSON from StateCompressionEngine
     * @param confirmedItems User-confirmed ingredient overrides (from confirmation sheet)
     */
    fun execute(
        frame: Bitmap?,
        audioText: String,
        stateJson: String,
        confirmedItems: List<IdentifiedItem>? = null
    ): Flow<PipelineEvent> = flow {

        // ========== STAGE 1: PERCEPTION ==========
        val manifest = runStage(PipelineStage.Perception) {
            executePerception(frame, confirmedItems)
        }
        if (manifest == null) {
            emit(PipelineEvent.StageFailed(PipelineStage.Perception, "Could not identify ingredients"))
            return@flow
        }
        emit(PipelineEvent.StageCompleted(PipelineStage.Perception))

        // Surface uncertain items for user confirmation
        val uncertainItems = manifest.items.filter { !it.confident }
        if (uncertainItems.isNotEmpty() && confirmedItems == null) {
            emit(PipelineEvent.IngredientConfirmation(manifest))
            // Pipeline pauses here; caller re-invokes with confirmedItems after user confirms
            return@flow
        }

        // ========== STAGE 2: CONSTRAINT REASONING (CoT) ==========
        val feasibility = runStage(PipelineStage.Reasoning) {
            executeConstraintReasoning(manifest, stateJson)
        }
        if (feasibility == null) {
            emit(PipelineEvent.StageFailed(PipelineStage.Reasoning, "Constraint analysis failed"))
            return@flow
        }
        emit(PipelineEvent.StageCompleted(PipelineStage.Reasoning))

        // ========== STAGE 3: RECIPE SYNTHESIS ==========
        val baseRecipe = runStage(PipelineStage.Synthesis) {
            executeRecipeSynthesis(feasibility, audioText, stateJson)
        }
        if (baseRecipe == null) {
            emit(PipelineEvent.StageFailed(PipelineStage.Synthesis, "Recipe generation failed"))
            return@flow
        }
        emit(PipelineEvent.StageCompleted(PipelineStage.Synthesis))

        // ========== STAGE 4: FORK PLANNING ==========
        val forkedRecipe = runStage(PipelineStage.Forking) {
            executeForkPlanning(baseRecipe, stateJson)
        }
        if (forkedRecipe == null) {
            emit(PipelineEvent.StageFailed(PipelineStage.Forking, "Fork planning failed"))
            return@flow
        }
        emit(PipelineEvent.StageCompleted(PipelineStage.Forking))
        emit(PipelineEvent.FinalResult(forkedRecipe))
    }

    // --- Stage Implementations ---

    private suspend fun executePerception(
        frame: Bitmap?,
        confirmedItems: List<IdentifiedItem>?
    ): IngredientManifest? {
        // If user already confirmed, use their curated list
        if (confirmedItems != null) {
            val manifest = IngredientManifest(confirmedItems)
            cachedManifest = manifest
            return manifest
        }

        // Use cache if still fresh and no new frame
        cachedManifest?.let { cached ->
            if (!cached.isExpired() && frame == null) return cached
        }

        if (frame == null) return cachedManifest // no frame, no cache = stuck

        val imageB64 = GemmaOrchestrator.encodeFrame(frame)

        val prompt = buildString {
            append("<system>You are a food ingredient identification system. ")
            append("Never estimate calories or give medical/nutritional advice.</system>\n")
            append("<image>$imageB64</image>\n")
            append("<task>Identify every visible food item. For each, output JSON:\n")
            append("""[{"name":"...","qty":"approx amount","category":"protein|carb|vegetable|dairy|spice|other","confidence":0.0-1.0}]""")
            append("\nOutput ONLY the JSON array, no explanation.</task>")
        }

        val result = GemmaOrchestrator.safeInfer(prompt)
        return result.getOrNull()?.let { raw ->
            parseIngredientManifest(raw)?.also { cachedManifest = it }
        }
    }

    private suspend fun executeConstraintReasoning(
        manifest: IngredientManifest,
        stateJson: String
    ): FeasibilityMatrix? {
        val isThrottled = GemmaOrchestrator.thermalState.value is GemmaOrchestrator.ThermalState.Throttled

        val prompt = buildString {
            append("<ingredients>${manifest.toPromptString()}</ingredients>\n")
            append("<state>$stateJson</state>\n")

            if (isThrottled) {
                // Collapsed single-shot prompt — no CoT, faster but less accurate
                append("<task>Output a JSON array mapping each ingredient to allowed household ")
                append("members and feasible cooking methods given time and dietary constraints. ")
                append("IMPORTANT: Respect the User Diet Type specified in the <state> section. ")
                append("""Format: [{"i":"ingredient","m":["member"],"c":["method"]}]</task>""")
            } else {
                // Full chain-of-thought reasoning
                append("<task>Think step-by-step before answering:\n")
                append("1. Which ingredients match the user's diet type (Non-Veg/Veg/Vegan etc)? Filter out restricted items.\n")
                append("2. Given available_minutes in state, which cooking methods are feasible? ")
                append("(e.g., if <15min: only stir-fry, microwave, assembly. No baking/slow-cook.)\n")
                append("3. Given sleep and step data, should we favor high-energy or recovery meals? ")
                append("(<6h sleep OR >12000 steps → recovery/comfort food; else → high-energy.)\n")
                append("4. For each household member, which ingredients violate their restrictions?\n\n")
                append("After reasoning, output ONLY a JSON array:\n")
                append("""[{"i":"ingredient","m":["allowed_member_names"],"c":["feasible_methods"]}]""")
                append("\n\nProvide reasoning in <reasoning> tags, then JSON in <output> tags.</task>")
            }
        }

        val result = GemmaOrchestrator.safeInfer(prompt)
        return result.getOrNull()?.let { parseFeasibilityMatrix(it) }
    }

    private suspend fun executeRecipeSynthesis(
        matrix: FeasibilityMatrix,
        audioText: String,
        stateJson: String
    ): BaseRecipe? {
        // Extract avail_min from state for time enforcement
        val availMin = try {
            JSONObject(stateJson).optInt("am", 30)
        } catch (_: Exception) { 30 }

        val prompt = buildString {
            append("<matrix>${matrix.toPromptString()}</matrix>\n")
            append("<query>$audioText</query>\n")
            append("<task>Generate ONE recipe using only ingredients from the matrix.\n")
            append("Rules:\n")
            append("- total_time_min MUST be ≤ $availMin\n")
            append("- Use only cooking methods listed in the matrix\n")
            append("- Steps should be precise (quantities, temperatures, times)\n")
            append("- Do NOT include calorie counts or nutritional claims\n\n")
            append("Output JSON:\n")
            append("""{"title":"...","total_min":N,"steps":["step1","step2",...]}""")
            append("\nOutput ONLY JSON.</task>")
        }

        val result = GemmaOrchestrator.safeInfer(prompt)
        val recipe = result.getOrNull()?.let { parseBaseRecipe(it) }

        // Time enforcement: if recipe exceeds budget, retry with stricter constraint
        if (recipe != null && recipe.totalMin > availMin) {
            Log.w(TAG, "Recipe ${recipe.totalMin}min exceeds budget ${availMin}min, retrying strict")
            val retryPrompt = buildString {
                append("<task>The previous recipe took ${recipe.totalMin} minutes but the limit is $availMin.\n")
                append("Simplify aggressively: fewer steps, simpler methods, skip optional garnishes.\n")
                append("Output JSON: ")
                append("""{"title":"...","total_min":N,"steps":["step1",...]}""")
                append(" where total_min ≤ $availMin. ONLY JSON.</task>")
            }
            val retry = GemmaOrchestrator.safeInfer(retryPrompt)
            return retry.getOrNull()?.let { parseBaseRecipe(it) } ?: recipe // fallback to original
        }

        return recipe
    }

    private suspend fun executeForkPlanning(
        baseRecipe: BaseRecipe,
        stateJson: String
    ): ForkedRecipe? {
        // Extract member profiles from state
        val members = try {
            val state = JSONObject(stateJson)
            val arr = state.optJSONArray("m") ?: return ForkedRecipe(
                title = baseRecipe.title,
                totalMin = baseRecipe.totalMin,
                baseSteps = baseRecipe.steps,
                forkAfterStep = baseRecipe.steps.size - 1,
                branches = emptyList()
            )
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (_: Exception) {
            return ForkedRecipe(
                title = baseRecipe.title,
                totalMin = baseRecipe.totalMin,
                baseSteps = baseRecipe.steps,
                forkAfterStep = baseRecipe.steps.size - 1,
                branches = emptyList()
            )
        }

        if (members.size <= 1) {
            // Single member = no forking needed
            return ForkedRecipe(
                title = baseRecipe.title,
                totalMin = baseRecipe.totalMin,
                baseSteps = baseRecipe.steps,
                forkAfterStep = baseRecipe.steps.size - 1,
                branches = emptyList()
            )
        }

        val stepsJson = JSONArray(baseRecipe.steps).toString()
        val membersStr = members.joinToString("; ") { m ->
            "${m.optString("n", "?")}:${m.optString("r", "none")}"
        }

        val prompt = buildString {
            append("<recipe>{\"title\":\"${baseRecipe.title}\",\"steps\":$stepsJson}</recipe>\n")
            append("<members>$membersStr</members>\n")
            append("<task>Determine the optimal fork point — the LATEST step where all members\n")
            append("can still share the same preparation. Then for each member with restrictions:\n")
            append("- fork_after_step: step index (0-based) to split\n")
            append("- mods: [{\"s\":step_index,\"m\":\"modification description\"}]\n")
            append("- add: [ingredients to add for this member]\n")
            append("- rm: [ingredients to remove for this member]\n\n")
            append("Output JSON:\n")
            append("""{"fork":N,"branches":[{"n":"name","mods":[{"s":0,"m":"..."}],"add":[],"rm":[]}]}""")
            append("\nONLY JSON.</task>")
        }

        val result = GemmaOrchestrator.safeInfer(prompt)
        return result.getOrNull()?.let { raw ->
            parseForkedRecipe(raw, baseRecipe)
        }
    }

    // --- JSON Parsing Helpers ---
    // All parsers are lenient: they extract JSON from mixed text (model may wrap in markdown)

    private fun extractJson(raw: String): String {
        // Try to find JSON in <output> tags first (from CoT prompt)
        val outputMatch = Regex("<output>(.*?)</output>", RegexOption.DOT_MATCHES_ALL).find(raw)
        if (outputMatch != null) return outputMatch.groupValues[1].trim()

        // Try to find JSON array or object
        val jsonStart = raw.indexOfFirst { it == '[' || it == '{' }
        val jsonEnd = raw.indexOfLast { it == ']' || it == '}' }
        return if (jsonStart >= 0 && jsonEnd > jsonStart) {
            raw.substring(jsonStart, jsonEnd + 1)
        } else raw.trim()
    }

    private fun parseIngredientManifest(raw: String): IngredientManifest? {
        return try {
            val json = JSONArray(extractJson(raw))
            val items = (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                IdentifiedItem(
                    name = obj.getString("name"),
                    qty = obj.optString("qty", "some"),
                    category = obj.optString("category", "other"),
                    confident = obj.optDouble("confidence", 0.5) >= 0.6
                )
            }
            IngredientManifest(items)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ingredient manifest: ${e.message}")
            null
        }
    }

    private fun parseFeasibilityMatrix(raw: String): FeasibilityMatrix? {
        return try {
            val json = JSONArray(extractJson(raw))
            val mappings = (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                IngredientMapping(
                    ingredient = obj.getString("i"),
                    allowedMembers = obj.getJSONArray("m").let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    },
                    methods = obj.getJSONArray("c").let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    }
                )
            }
            FeasibilityMatrix(mappings)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse feasibility matrix: ${e.message}")
            null
        }
    }

    private fun parseBaseRecipe(raw: String): BaseRecipe? {
        return try {
            val obj = JSONObject(extractJson(raw))
            BaseRecipe(
                title = obj.getString("title"),
                totalMin = obj.getInt("total_min"),
                steps = obj.getJSONArray("steps").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse recipe: ${e.message}")
            null
        }
    }

    private fun parseForkedRecipe(raw: String, base: BaseRecipe): ForkedRecipe? {
        return try {
            val obj = JSONObject(extractJson(raw))
            val forkPoint = obj.getInt("fork")
            val branches = obj.getJSONArray("branches").let { arr ->
                (0 until arr.length()).map { i ->
                    val b = arr.getJSONObject(i)
                    RecipeBranch(
                        memberName = b.getString("n"),
                        modifiedSteps = b.optJSONArray("mods")?.let { mods ->
                            (0 until mods.length()).map { j ->
                                val m = mods.getJSONObject(j)
                                StepMod(m.getInt("s"), m.getString("m"))
                            }
                        } ?: emptyList(),
                        additions = b.optJSONArray("add")?.let { a ->
                            (0 until a.length()).map { a.getString(it) }
                        } ?: emptyList(),
                        removals = b.optJSONArray("rm")?.let { r ->
                            (0 until r.length()).map { r.getString(it) }
                        } ?: emptyList()
                    )
                }
            }
            ForkedRecipe(
                title = base.title,
                totalMin = base.totalMin,
                baseSteps = base.steps,
                forkAfterStep = forkPoint,
                branches = branches
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse forked recipe: ${e.message}")
            null
        }
    }

    // --- Stage Runner with Timeout ---

    private suspend inline fun <T> runStage(
        stage: PipelineStage,
        crossinline block: suspend () -> T?
    ): T? {
        Log.d(TAG, "Stage ${stage.label} started")
        return withTimeoutOrNull(STAGE_TIMEOUT_MS) {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Stage ${stage.label} failed: ${e.message}")
                null
            }
        }
    }
}
