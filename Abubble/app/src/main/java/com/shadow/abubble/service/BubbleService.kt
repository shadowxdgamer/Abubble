package com.shadow.abubble.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.shadow.abubble.R
import com.shadow.abubble.data.ChatRequest
import com.shadow.abubble.data.Message
import com.shadow.abubble.data.ModelRepository
import com.shadow.abubble.data.OpenRouterModel
import com.shadow.abubble.data.ReasoningOptions
import com.shadow.abubble.data.RetrofitClient
import com.shadow.abubble.util.SecurePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BubbleService : AccessibilityService() {

    companion object {
        /** Live reference so MainActivity can tell us to show/hide the bubble */
        @Volatile
        var instance: BubbleService? = null
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var promptView: View? = null
    private var popupMenuView: View? = null
    private var isPromptShowing = false
    private var isPopupShowing = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository = ModelRepository.getInstance()
    private var modelList: List<OpenRouterModel> = emptyList()
    private var capturedContext: String = ""

    private val conversationHistory = mutableListOf<Message>()

    // Long-press detection
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private val longPressDuration = 500L

    override fun onServiceConnected() {
        super.onServiceConnected()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this

        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        showBubble()
        loadModels()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeBubble()
        removePrompt()
        removePopupMenu()
        serviceScope.cancel()
    }

    // ═════════════════════════════════════════════════════
    //  BUBBLE (Floating App Icon)
    // ═════════════════════════════════════════════════════

    fun showBubble() {
        if (bubbleView != null) return

        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.layout_bubble, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        setupBubbleTouchListener(params)
        windowManager.addView(bubbleView, params)
    }

    private fun setupBubbleTouchListener(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var isLongPress = false
        val clickThreshold = 10

        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isLongPress = false

                    // Start long-press timer
                    longPressRunnable = Runnable {
                        isLongPress = true
                        showPopupMenu(params)
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, longPressDuration)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(dx) > clickThreshold || Math.abs(dy) > clickThreshold) {
                        isDragging = true
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    }

                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }

                    if (isLongPress) {
                        // Long-press already handled
                    } else if (!isDragging) {
                        removePopupMenu()
                        onBubbleTapped()
                    } else {
                        snapToEdge(params)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    true
                }

                else -> false
            }
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val midX = screenWidth / 2

        params.x = if (params.x < midX) 0 else screenWidth - (bubbleView?.width ?: 0)
        windowManager.updateViewLayout(bubbleView, params)
    }

    fun removeBubble() {
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null
    }

    private fun onBubbleTapped() {
        if (isPromptShowing) {
            removePrompt()
        } else {
            captureContextAndShowDialog()
        }
    }

    // ═════════════════════════════════════════════════════
    //  LONG-PRESS POPUP MENU
    // ═════════════════════════════════════════════════════

    private fun showPopupMenu(bubbleParams: WindowManager.LayoutParams) {
        if (isPopupShowing) return

        val inflater = LayoutInflater.from(this)
        popupMenuView = inflater.inflate(R.layout.layout_popup_menu, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val menuX = if (bubbleParams.x < screenWidth / 2) {
            bubbleParams.x + 60
        } else {
            bubbleParams.x - 190
        }

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = menuX
            y = bubbleParams.y
        }

        popupMenuView?.findViewById<TextView>(R.id.popup_close)?.setOnClickListener {
            removePopupMenu()
            removeBubble()
            removePrompt()
            Toast.makeText(this, "Bubble closed. Relaunch from the app.", Toast.LENGTH_SHORT).show()
        }

        popupMenuView?.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_OUTSIDE) {
                removePopupMenu()
                true
            } else {
                false
            }
        }

        windowManager.addView(popupMenuView, menuParams)
        isPopupShowing = true
    }

    private fun removePopupMenu() {
        popupMenuView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        popupMenuView = null
        isPopupShowing = false
    }

    // ═════════════════════════════════════════════════════
    //  CONTEXT GRABBER
    // ═════════════════════════════════════════════════════

    private fun captureContextAndShowDialog() {
        val rootNode = rootInActiveWindow ?: run {
            showPromptDialog("")
            return
        }

        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val currentText = focusedNode?.text?.toString() ?: ""
        val contextSnippet = currentText.takeLast(500)
        capturedContext = contextSnippet

        showPromptDialog(contextSnippet)
    }

    // ═════════════════════════════════════════════════════
    //  PROMPT DIALOG (Redesigned floating overlay)
    // ═════════════════════════════════════════════════════

    private fun showPromptDialog(context: String) {
        if (isPromptShowing) return

        val inflater = LayoutInflater.from(this)
        promptView = inflater.inflate(R.layout.layout_prompt_dialog, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        setupPromptViews(context)
        windowManager.addView(promptView, params)
        isPromptShowing = true
    }

    private fun setupPromptViews(context: String) {
        val view = promptView ?: return

        val tvContextPreview = view.findViewById<TextView>(R.id.tv_context_preview)
        val spinnerModel = view.findViewById<Spinner>(R.id.spinner_model)
        val reasoningContainer = view.findViewById<LinearLayout>(R.id.reasoning_container)
        val switchReasoning = view.findViewById<Switch>(R.id.switch_reasoning)
        val etPrompt = view.findViewById<EditText>(R.id.et_prompt)
        val btnGo = view.findViewById<Button>(R.id.btn_go)
        val btnClose = view.findViewById<ImageButton>(R.id.btn_close)
        val progressLoading = view.findViewById<ProgressBar>(R.id.progress_loading)

        // Set context preview
        tvContextPreview.text = context.ifBlank { getString(R.string.no_context) }

        // Setup model spinner
        setupModelSpinner(spinnerModel, reasoningContainer, switchReasoning)

        // Load saved reasoning toggle state
        switchReasoning.isChecked = SecurePrefs.isReasoningEnabled(this)

        // Close button
        btnClose.setOnClickListener { removePrompt() }

        // Dismiss on outside touch
        view.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_OUTSIDE) {
                removePrompt()
                true
            } else {
                false
            }
        }

        // Go button
        btnGo.setOnClickListener {
            val userPrompt = etPrompt.text.toString().trim()
            if (userPrompt.isEmpty()) {
                Toast.makeText(this, "Please enter a command", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val apiKey = SecurePrefs.getApiKey(this)
            if (apiKey.isBlank()) {
                Toast.makeText(this, "Set your API key in Abubble settings", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedPosition = spinnerModel.selectedItemPosition
            val selectedModel = if (selectedPosition in modelList.indices) {
                modelList[selectedPosition]
            } else {
                Toast.makeText(this, "Please select a model", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val reasoningEnabled = switchReasoning.isChecked &&
                    selectedModel.supportsReasoning()
            SecurePrefs.saveReasoningEnabled(this, reasoningEnabled)

            // Show loading state
            btnGo.isEnabled = false
            btnGo.alpha = 0.6f
            progressLoading.visibility = View.VISIBLE

            sendToAI(
                apiKey = apiKey,
                model = selectedModel,
                userPrompt = userPrompt,
                contextText = capturedContext,
                reasoningEnabled = reasoningEnabled,
                onResult = { result ->
                    progressLoading.visibility = View.GONE
                    btnGo.isEnabled = true
                    btnGo.alpha = 1.0f
                    pasteResult(result)
                    removePrompt()
                },
                onError = { error ->
                    progressLoading.visibility = View.GONE
                    btnGo.isEnabled = true
                    btnGo.alpha = 1.0f
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun setupModelSpinner(
        spinner: Spinner,
        reasoningContainer: LinearLayout,
        switchReasoning: Switch
    ) {
        val models = repository.getCachedModels()
        modelList = models

        val displayNames = models.map { it.displayName() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val savedModelId = SecurePrefs.getSelectedModel(this)
        if (savedModelId.isNotBlank()) {
            val index = models.indexOfFirst { it.id == savedModelId }
            if (index >= 0) spinner.setSelection(index)
        }

        spinner.setOnItemSelectedListener(object :
            android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val model = modelList.getOrNull(position) ?: return
                SecurePrefs.saveSelectedModel(this@BubbleService, model.id)
                reasoningContainer.visibility = if (model.supportsReasoning()) {
                    View.VISIBLE
                } else {
                    switchReasoning.isChecked = false
                    View.GONE
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun removePrompt() {
        promptView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        promptView = null
        isPromptShowing = false
        conversationHistory.clear()
    }

    // ═════════════════════════════════════════════════════
    //  AI COMMUNICATION
    // ═════════════════════════════════════════════════════

    private fun sendToAI(
        apiKey: String,
        model: OpenRouterModel,
        userPrompt: String,
        contextText: String,
        reasoningEnabled: Boolean,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val systemContent = buildString {
            append("You are a writing assistant. ")
            if (contextText.isNotBlank()) {
                append("The user is typing in an app. Current text: '")
                append(contextText)
                append("'. ")
            }
            append("Task: Follow the user's instruction below. ")
            append("Return ONLY the improved/modified text — no explanations, ")
            append("no markdown formatting, no code blocks. Plain text only.")
        }

        if (conversationHistory.isEmpty()) {
            conversationHistory.add(Message(role = "system", content = systemContent))
        }
        conversationHistory.add(Message(role = "user", content = userPrompt))

        val reasoning = if (reasoningEnabled) ReasoningOptions(enabled = true) else null

        val request = ChatRequest(
            model = model.id,
            messages = conversationHistory.toList(),
            reasoning = reasoning
        )

        serviceScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.chat(
                        auth = "Bearer $apiKey",
                        request = request
                    )
                }

                val resultText = response.choices?.firstOrNull()?.message?.content
                if (resultText != null) {
                    val cleanText = stripMarkdown(resultText)
                    conversationHistory.add(
                        Message(role = "assistant", content = cleanText)
                    )
                    onResult(cleanText)
                } else {
                    onError("Empty response from AI")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    private fun stripMarkdown(text: String): String {
        var result = text.trim()
        result = result.replace(Regex("```[\\s\\S]*?```"), "")
        result = result.replace(Regex("`([^`]+)`"), "$1")
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        result = result.replace(Regex("\\*(.+?)\\*"), "$1")
        result = result.replace(Regex("__(.+?)__"), "$1")
        result = result.replace(Regex("_(.+?)_"), "$1")
        result = result.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        result = result.replace(Regex("^[\\-*+]\\s+", RegexOption.MULTILINE), "")
        return result.trim()
    }

    // ═════════════════════════════════════════════════════
    //  PASTE / REPLACE TEXT (The Magic)
    // ═════════════════════════════════════════════════════

    private fun pasteResult(resultText: String) {
        val rootNode = rootInActiveWindow
        val focusedNode = rootNode?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null) {
            // Method 1: ACTION_SET_TEXT — directly replaces the entire text field (API 21+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val setTextArgs = Bundle()
                setTextArgs.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    resultText
                )
                val success = focusedNode.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArgs
                )
                if (success) return
            }

            // Method 2: Select all + clipboard paste (fallback)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Abubble AI", resultText)
            clipboard.setPrimaryClip(clip)

            val selectArgs = Bundle()
            selectArgs.putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0
            )
            selectArgs.putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                focusedNode.text?.length ?: 0
            )
            focusedNode.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION, selectArgs
            )
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } else {
            // Last resort: clipboard only
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Abubble AI", resultText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }

    // ═════════════════════════════════════════════════════
    //  MODEL LOADING
    // ═════════════════════════════════════════════════════

    private fun loadModels() {
        serviceScope.launch {
            repository.getModels()
                .onSuccess { models -> modelList = models }
                .onFailure { /* will load when prompt opens */ }
        }
    }
}
