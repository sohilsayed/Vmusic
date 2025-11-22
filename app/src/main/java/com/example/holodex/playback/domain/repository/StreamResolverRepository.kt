// File: java/com/example/holodex/playback/domain/repository/StreamResolverRepository.kt
package com.example.holodex.playback.domain.repository

import com.example.holodex.playback.domain.model.StreamDetails

interface StreamResolverRepository {
    suspend fun resolveStreamUrl(videoId: String): Result<StreamDetails>
}