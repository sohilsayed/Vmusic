package com.example.holodex.data.store

import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import kotlinx.coroutines.flow.map
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.MemoryPolicy
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Singleton
class UnifiedStoreFactory @Inject constructor(
    private val unifiedDao: UnifiedDao
) {

    // 1. ITEM STORE (Video Details) - Kept as Projection for full detail access
    fun <Key : Any, NetworkModel : Any> createItemStore(
        fetcher: suspend (Key) -> NetworkModel,
        networkToEntity: (NetworkModel) -> UnifiedMetadataEntity,
        keyToId: (Key) -> String
    ): Store<Key, UnifiedDisplayItem> {

        return StoreBuilder.from(
            fetcher = Fetcher.of { key: Key -> fetcher(key) },
            sourceOfTruth = SourceOfTruth.of(
                reader = { key: Key ->
                    unifiedDao.getItemByIdFlow(keyToId(key)).map { it?.toUnifiedDisplayItem() }
                },
                writer = { _: Key, response: NetworkModel ->
                    unifiedDao.upsertMetadata(networkToEntity(response))
                }
            )
        )
            .cachePolicy(
                MemoryPolicy.builder<Any, Any>()
                    .setExpireAfterWrite(4.hours)
                    .build()
            )
            .build()
    }

    // 2. LIST STORE (Browse, Search) - OPTIMIZED
    // Uses the new Optimized Item reader to prevent Mapper lag
    fun <Key : Any, NetworkModel : Any> createListStore(
        fetcher: suspend (Key) -> List<NetworkModel>,
        networkToEntity: (NetworkModel) -> UnifiedMetadataEntity,
        modelToId: (NetworkModel) -> String
    ): Store<Key, List<UnifiedDisplayItem>> {

        val convertingFetcher = Fetcher.of { key: Key ->
            val networkList = fetcher(key)

            // Persist items to DB
            if (networkList.isNotEmpty()) {
                val entities = networkList.map { networkToEntity(it) }
                unifiedDao.insertMetadataBatch(entities)
            }

            // OPTIMIZED HYDRATION:
            // Fetch directly from DB using the fast SQL query
            val ids = networkList.map { modelToId(it) }
            val optimizedItems = unifiedDao.getOptimizedItemsByIds(ids).associateBy { it.metadata.id }

            networkList.map { item ->
                val id = modelToId(item)
                val optimizedLocal = optimizedItems[id]

                if (optimizedLocal != null) {
                    optimizedLocal.toUnifiedDisplayItem() // Uses the fast mapper
                } else {
                    // Fallback (rare race condition)
                    val entity = networkToEntity(item)
                    // Create a dummy optimized object locally
                    com.example.holodex.data.db.UnifiedItemWithStatus(
                        metadata = entity,
                        isLiked = false, isDownloaded = false, downloadStatus = null, localFilePath = null, historyId = null
                    ).toUnifiedDisplayItem()
                }
            }
        }

        return StoreBuilder.from(
            fetcher = convertingFetcher
        )
            .cachePolicy(
                MemoryPolicy.builder<Any, Any>()
                    .setExpireAfterWrite(30.minutes)
                    .build()
            )
            .build()
    }
}