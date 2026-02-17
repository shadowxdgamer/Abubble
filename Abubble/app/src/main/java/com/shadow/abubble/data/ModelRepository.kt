package com.shadow.abubble.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    fun invalidateCache() {
        cachedModels = null
    }

    /** Find a specific model in cache by its full ID string. */
    fun findModelById(id: String): OpenRouterModel? =
        cachedModels?.find { it.id == id }

    companion object {
        @Volatile
        private var INSTANCE: ModelRepository? = null

        fun getInstance(): ModelRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelRepository().also { INSTANCE = it }
            }
    }
}
