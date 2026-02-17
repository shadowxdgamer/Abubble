/**
 * ═══════════════════════════════════════════════════════════════════════
 *  OpenRouter AI Model Picker — Self-Contained Jetpack Compose Template
 * ═══════════════════════════════════════════════════════════════════════
 *
 *  A complete, reusable component for fetching and selecting AI models
 *  from the OpenRouter API (https://openrouter.ai).
 *
 *  Everything is in ONE file:
 *    1. Data Models (API response DTOs)
 *    2. Retrofit API Interface + Client
 *    3. Repository (with in-memory cache + Mutex)
 *    4. Compose UI (Card with search, list, selection, refresh)
 *
 *  DEPENDENCIES (add to build.gradle.kts):
 *    implementation("com.squareup.retrofit2:retrofit:2.9.0")
 *    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
 *    implementation("com.squareup.okhttp3:okhttp:4.12.0")
 *    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
 *    implementation("androidx.compose.material3:material3")
 *    implementation("androidx.compose.material:material-icons-extended")
 *
 *  USAGE:
 *    val models = remember { mutableStateListOf<OpenRouterModel>() }
 *    var selectedModelId by remember { mutableStateOf("") }
 *    var searchQuery by remember { mutableStateOf("") }
 *    var isLoading by remember { mutableStateOf(false) }
 *    var error by remember { mutableStateOf<String?>(null) }
 *    val scope = rememberCoroutineScope()
 *    val repository = remember { ModelRepository.getInstance() }
 *
 *    // Fetch on launch
 *    LaunchedEffect(Unit) {
 *        isLoading = true
 *        repository.getModels()
 *            .onSuccess { models.clear(); models.addAll(it) }
 *            .onFailure { error = it.message }
 *        isLoading = false
 *    }
 *
 *    ModelSelectionCard(
 *        models = models,
 *        selectedModelId = selectedModelId,
 *        searchQuery = searchQuery,
 *        onSearchQueryChange = { searchQuery = it },
 *        isLoading = isLoading,
 *        error = error,
 *        onModelSelect = { model -> selectedModelId = model.id },
 *        onRefresh = { scope.launch { ... } }
 *    )
 */

// ─────────────────────────────────────────────────────────────────────
//  SECTION 1 — DATA MODELS
// ─────────────────────────────────────────────────────────────────────

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/** Response wrapper from GET /api/v1/models */
data class ModelsResponse(
    val data: List<OpenRouterModel>
)

/** A single AI model from OpenRouter. */
data class OpenRouterModel(
    val id: String,
    val name: String,
    @SerializedName("supported_parameters")
    val supportedParameters: List<String>? = null
) {
    /** Whether this model supports the `reasoning` parameter. */
    fun supportsReasoning(): Boolean =
        supportedParameters?.contains("reasoning") == true

    /** Friendly display name — falls back to the slug after '/'. */
    fun displayName(): String =
        name.ifBlank { id.substringAfterLast("/") }
}

/** Chat completion request body (included for completeness). */
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val reasoning: ReasoningOptions? = null
)

data class ReasoningOptions(val enabled: Boolean)

data class Message(
    val role: String,
    val content: String,
    @SerializedName("reasoning_details")
    val reasoningDetails: Any? = null
)

/** Chat completion response. */
data class ChatResponse(
    val choices: List<Choice>?
)

data class Choice(
    val message: Message?
)


// ─────────────────────────────────────────────────────────────────────
//  SECTION 2 — RETROFIT API INTERFACE + CLIENT
// ─────────────────────────────────────────────────────────────────────

interface OpenRouterApi {

    @GET("models")
    suspend fun getModels(): ModelsResponse

    @POST("chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Header("HTTP-Referer") referer: String = "https://your-app.example",
        @Header("X-Title") title: String = "YourApp",
        @Body request: ChatRequest
    ): ChatResponse
}

object RetrofitClient {

    private const val BASE_URL = "https://openrouter.ai/api/v1/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: OpenRouterApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenRouterApi::class.java)
    }
}


// ─────────────────────────────────────────────────────────────────────
//  SECTION 3 — REPOSITORY (cached, thread-safe)
// ─────────────────────────────────────────────────────────────────────

class ModelRepository {

    private var cachedModels: List<OpenRouterModel>? = null
    private val fetchMutex = Mutex()

    /**
     * Fetches models from OpenRouter, caching the result in memory.
     * Uses a Mutex so concurrent callers share a single network request.
     */
    suspend fun getModels(forceRefresh: Boolean = false): Result<List<OpenRouterModel>> {
        if (!forceRefresh) {
            cachedModels?.let { return Result.success(it) }
        }

        return fetchMutex.withLock {
            if (!forceRefresh) {
                cachedModels?.let { return Result.success(it) }
            }

            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getModels()
                }
                val models = response.data.sortedBy { it.name.lowercase() }
                cachedModels = models
                Result.success(models)
            } catch (e: Exception) {
                cachedModels?.let { Result.success(it) }
                    ?: Result.failure(e)
            }
        }
    }

    /** Returns whatever is in cache (empty list if nothing fetched yet). */
    fun getCachedModels(): List<OpenRouterModel> = cachedModels ?: emptyList()

    /** Clears the cache so the next [getModels] call hits the network. */
    fun invalidateCache() { cachedModels = null }

    /** Find a specific model in cache by its full ID string. */
    fun findModelById(id: String): OpenRouterModel? =
        cachedModels?.find { it.id == id }

    companion object {
        @Volatile private var INSTANCE: ModelRepository? = null

        fun getInstance(): ModelRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelRepository().also { INSTANCE = it }
            }
    }
}


// ─────────────────────────────────────────────────────────────────────
//  SECTION 4 — COMPOSE UI
// ─────────────────────────────────────────────────────────────────────

/**
 * A Material 3 card containing:
 *  - Header with title, loading indicator, refresh button
 *  - Currently selected model chip
 *  - Search / filter text field
 *  - Scrollable list of models (max 300 dp)
 *
 * Stateless — all state is hoisted to the caller.
 */
@Composable
fun ModelSelectionCard(
    models: List<OpenRouterModel>,
    selectedModelId: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    onModelSelect: (OpenRouterModel) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = if (searchQuery.isBlank()) {
        models
    } else {
        val query = searchQuery.lowercase()
        models.filter {
            it.displayName().lowercase().contains(query) ||
                it.id.lowercase().contains(query)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header Row ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Model",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh models",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Currently Selected Chip ──
            if (selectedModelId.isNotBlank()) {
                val selectedModel = models.find { it.id == selectedModelId }
                val displayName = selectedModel?.displayName()
                    ?: selectedModelId.substringAfterLast("/")

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            MaterialTheme.colorScheme.primaryContainer
                                .copy(alpha = 0.4f)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── Error Message ──
            if (error != null) {
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ── Search Field ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search models…") },
                singleLine = true
            )

            // ── Model List ──
            if (models.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(
                        items = filtered.take(100),
                        key = { it.id }
                    ) { model ->
                        ModelListItem(
                            model = model,
                            isSelected = model.id == selectedModelId,
                            onClick = { onModelSelect(model) }
                        )
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No models found",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (!isLoading && error == null) {
                Text(
                    text = "Enter your API key and save to load models",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * A single row in the model list.
 * Shows model name, full ID, optional reasoning icon, and highlight if selected.
 */
@Composable
private fun ModelListItem(
    model: OpenRouterModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    else
        MaterialTheme.colorScheme.surfaceContainerLow

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName(),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = model.id,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (model.supportsReasoning()) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Psychology,
                contentDescription = "Supports reasoning",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
