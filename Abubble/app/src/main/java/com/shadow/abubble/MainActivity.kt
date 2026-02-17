package com.shadow.abubble

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadow.abubble.data.ModelRepository
import com.shadow.abubble.data.OpenRouterModel
import com.shadow.abubble.service.BubbleService
import com.shadow.abubble.ui.theme.AbubbleTheme
import com.shadow.abubble.util.SecurePrefs
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AbubbleTheme {
                SettingsScreen(
                    onOpenAccessibility = { openAccessibilitySettings() },
                    onOpenOverlayPermission = { openOverlayPermission() },
                    onSaveApiKey = { key ->
                        SecurePrefs.saveApiKey(this, key)
                        Toast.makeText(this, "API Key saved!", Toast.LENGTH_SHORT).show()
                    },
                    onSaveModel = { modelId ->
                        SecurePrefs.saveSelectedModel(this, modelId)
                    },
                    getSavedApiKey = { SecurePrefs.getApiKey(this) },
                    getSavedModel = { SecurePrefs.getSelectedModel(this) },
                    isAccessibilityEnabled = { isAccessibilityServiceEnabled() },
                    onLaunchBubble = { launchBubble() },
                    onCloseBubble = { closeBubble() },
                    isBubbleShowing = { BubbleService.instance != null }
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun openOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("$packageName/com.shadow.abubble.service.BubbleService")
    }

    private fun launchBubble() {
        val service = BubbleService.instance
        if (service != null) {
            service.showBubble()
            Toast.makeText(this, "Bubble launched!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Enable the Accessibility Service first", Toast.LENGTH_LONG).show()
        }
    }

    private fun closeBubble() {
        val service = BubbleService.instance
        if (service != null) {
            service.removeBubble()
            Toast.makeText(this, "Bubble closed", Toast.LENGTH_SHORT).show()
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  SETTINGS SCREEN
// ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onSaveModel: (String) -> Unit,
    getSavedApiKey: () -> String,
    getSavedModel: () -> String,
    isAccessibilityEnabled: () -> Boolean,
    onLaunchBubble: () -> Unit,
    onCloseBubble: () -> Unit,
    isBubbleShowing: () -> Boolean
) {
    var apiKey by remember { mutableStateOf(getSavedApiKey()) }
    var showApiKey by remember { mutableStateOf(false) }
    var selectedModelId by remember { mutableStateOf(getSavedModel()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val models = remember { mutableStateListOf<OpenRouterModel>() }
    val scope = rememberCoroutineScope()
    val repository = remember { ModelRepository.getInstance() }

    // Load models on launch if API key exists
    LaunchedEffect(Unit) {
        if (apiKey.isNotBlank()) {
            isLoading = true
            repository.getModels()
                .onSuccess { list ->
                    models.clear()
                    models.addAll(list)
                }
                .onFailure { e ->
                    error = e.message
                }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Abubble Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Step 1: Permissions ──
            PermissionsCard(
                onOpenAccessibility = onOpenAccessibility,
                onOpenOverlayPermission = onOpenOverlayPermission,
                isAccessibilityEnabled = isAccessibilityEnabled
            )

            // ── Bubble Control ──
            BubbleControlCard(
                onLaunchBubble = onLaunchBubble,
                onCloseBubble = onCloseBubble,
                isBubbleShowing = isBubbleShowing,
                isAccessibilityEnabled = isAccessibilityEnabled
            )

            // ── Step 2: API Key ──
            ApiKeyCard(
                apiKey = apiKey,
                showApiKey = showApiKey,
                onApiKeyChange = { apiKey = it },
                onToggleVisibility = { showApiKey = !showApiKey },
                onSave = {
                    onSaveApiKey(apiKey)
                    // Reload models after saving key
                    scope.launch {
                        isLoading = true
                        error = null
                        repository.getModels(forceRefresh = true)
                            .onSuccess { list ->
                                models.clear()
                                models.addAll(list)
                            }
                            .onFailure { e ->
                                error = e.message
                            }
                        isLoading = false
                    }
                }
            )

            // ── Step 3: Model Selection ──
            ModelSelectionCard(
                models = models,
                selectedModelId = selectedModelId,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                isLoading = isLoading,
                error = error,
                onModelSelect = { model ->
                    selectedModelId = model.id
                    onSaveModel(model.id)
                },
                onRefresh = {
                    scope.launch {
                        isLoading = true
                        error = null
                        repository.getModels(forceRefresh = true)
                            .onSuccess { list ->
                                models.clear()
                                models.addAll(list)
                            }
                            .onFailure { e ->
                                error = e.message
                            }
                        isLoading = false
                    }
                }
            )

            // ── Instructions ──
            InstructionsCard()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  PERMISSIONS CARD
// ─────────────────────────────────────────────────────────────────

@Composable
fun PermissionsCard(
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    isAccessibilityEnabled: () -> Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Abubble needs two permissions to work:",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onOpenAccessibility,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("1. Enable Accessibility Service")
            }

            OutlinedButton(
                onClick = onOpenOverlayPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("2. Allow Overlay Permission")
            }

            val enabled = isAccessibilityEnabled()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (enabled)
                        "Accessibility Service is enabled"
                    else
                        "Accessibility Service is not enabled",
                    fontSize = 13.sp,
                    color = if (enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  BUBBLE CONTROL CARD
// ─────────────────────────────────────────────────────────────────

@Composable
fun BubbleControlCard(
    onLaunchBubble: () -> Unit,
    onCloseBubble: () -> Unit,
    isBubbleShowing: () -> Boolean,
    isAccessibilityEnabled: () -> Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BubbleChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Bubble Control",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Launch or close the floating bubble. You can also long-press the bubble to close it.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onLaunchBubble,
                    modifier = Modifier.weight(1f),
                    enabled = isAccessibilityEnabled(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Launch Bubble")
                }

                OutlinedButton(
                    onClick = onCloseBubble,
                    modifier = Modifier.weight(1f),
                    enabled = isAccessibilityEnabled()
                ) {
                    Text("Close Bubble")
                }
            }

            if (!isAccessibilityEnabled()) {
                Text(
                    text = "Enable Accessibility Service above first",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  API KEY CARD
// ─────────────────────────────────────────────────────────────────

@Composable
fun ApiKeyCard(
    apiKey: String,
    showApiKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "OpenRouter API Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Get your key from openrouter.ai/keys",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-or-...") },
                singleLine = true,
                visualTransformation = if (showApiKey)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            if (showApiKey) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility"
                        )
                    }
                }
            )

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank()
            ) {
                Text("Save API Key & Load Models")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  MODEL SELECTION CARD (from template)
// ─────────────────────────────────────────────────────────────────

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
            // Header Row
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
                    fontWeight = FontWeight.Bold,
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

            // Currently Selected Chip
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
                    if (selectedModel?.supportsReasoning() == true) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = "Supports reasoning",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Error Message
            if (error != null) {
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search models\u2026") },
                singleLine = true
            )

            // Model List
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

// ─────────────────────────────────────────────────────────────────
//  INSTRUCTIONS CARD
// ─────────────────────────────────────────────────────────────────

@Composable
fun InstructionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "How to Use",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val steps = listOf(
                "1. Enable Accessibility Service and Overlay permissions above.",
                "2. Enter your OpenRouter API key and save it.",
                "3. Select an AI model from the list.",
                "4. A floating brain bubble will appear on your screen.",
                "5. Start typing in any app (WhatsApp, Email, etc.).",
                "6. Tap the bubble \u2014 it reads your text automatically.",
                "7. Type a command like \"Fix grammar\" or \"Make professional\".",
                "8. Tap Go \u2014 the AI result is pasted back into your text field!"
            )

            steps.forEach { step ->
                Text(
                    text = step,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}