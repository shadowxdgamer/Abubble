package com.shadow.abubble.data

import com.google.gson.annotations.SerializedName

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

/** Chat completion request body. */
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
