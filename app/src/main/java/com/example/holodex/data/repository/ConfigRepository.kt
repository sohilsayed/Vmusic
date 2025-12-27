package com.example.holodex.data.repository

import com.example.holodex.data.api.HolodexApiService
import com.example.holodex.di.ApplicationScope
import com.example.holodex.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles application configuration, static data, and organization lists.
 */
@Singleton
class ConfigRepository @Inject constructor(
    private val holodexApiService: HolodexApiService,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    private val _availableOrganizations = MutableStateFlow<List<Pair<String, String?>>>(
        listOf("All Vtubers" to null, "Favorites" to "Favorites")
    )
    val availableOrganizations: StateFlow<List<Pair<String, String?>>> = _availableOrganizations.asStateFlow()

    init {
        applicationScope.launch {
            fetchOrganizationList()
        }
    }

    private suspend fun fetchOrganizationList() {
        try {
            val response = withContext(defaultDispatcher) { holodexApiService.getOrganizations() }
            if (response.isSuccessful && response.body() != null) {
                val orgs = response.body()!!
                val orgList = orgs.map { org -> (org.name to org.name) }
                _availableOrganizations.value =
                    listOf("All Vtubers" to null, "Favorites" to "Favorites") + orgList
            } else {
                useFallbackOrgs()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load dynamic organization list. Using fallback.")
            useFallbackOrgs()
        }
    }

    private fun useFallbackOrgs() {
        _availableOrganizations.value = listOf(
            "All Vtubers" to null,
            "Favorites" to "Favorites",
            "Hololive" to "Hololive",
            "Nijisanji" to "Nijisanji",
            "Independents" to "Independents"
        )
    }
}