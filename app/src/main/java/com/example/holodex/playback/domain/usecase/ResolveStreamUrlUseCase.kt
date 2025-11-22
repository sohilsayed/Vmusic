package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.StreamDetails
import com.example.holodex.playback.domain.repository.StreamResolverRepository


class ResolveStreamUrlUseCase(
    private val streamResolverRepository: StreamResolverRepository
) {
    suspend operator fun invoke(videoId: String): Result<StreamDetails> {
        return streamResolverRepository.resolveStreamUrl(videoId)
    }
}