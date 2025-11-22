package com.example.holodex.playback.player

import androidx.media3.exoplayer.ExoPlayer
import com.example.holodex.playback.data.queue.PlaybackQueueState
import com.example.holodex.playback.domain.model.DomainShuffleMode
import timber.log.Timber
import javax.inject.Inject

/**
 * Responsible for keeping the ExoPlayer timeline in sync with the PlaybackQueueState.
 * It calculates the minimal set of moves required to make the player match the domain state.
 */
class TimelineSynchronizer @Inject constructor() {

    private data class Move(val from: Int, val to: Int)

    suspend fun syncPlayerWithQueueState(player: ExoPlayer, queueState: PlaybackQueueState) {
        // 1. Sync Shuffle Mode
        val shouldShuffle = queueState.shuffleMode == DomainShuffleMode.ON
        if (player.shuffleModeEnabled != shouldShuffle) {
            player.shuffleModeEnabled = shouldShuffle
        }

        // 2. Sync Timeline Order
        // We map IDs to find discrepancies between the Player's reality and the Queue's reality.
        val currentTimelineIds = getMediaIdsFromPlayer(player)
        val desiredOrderIds = queueState.activeList.map { it.id }

        if (currentTimelineIds == desiredOrderIds) {
            return // Perfect match, no work needed
        }

        // Calculate and apply moves
        val moves = calculateOptimalMoves(currentTimelineIds, desiredOrderIds)

        // Apply moves sequentially
        moves.forEach { move ->
            if (move.from in 0 until player.mediaItemCount && move.to in 0 until player.mediaItemCount) {
                player.moveMediaItem(move.from, move.to)
            } else {
                Timber.e("TimelineSynchronizer: Invalid move calculated: $move. Player size: ${player.mediaItemCount}")
            }
        }
    }

    private fun getMediaIdsFromPlayer(player: ExoPlayer): List<String> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        return (0 until timeline.windowCount).map {
            // We assume mediaId is set correctly on all items
            player.getMediaItemAt(it).mediaId
        }
    }

    /**
     * Calculates the moves needed to transform [current] list into [desired] list.
     */
    private fun calculateOptimalMoves(current: List<String>, desired: List<String>): List<Move> {
        if (current.size != desired.size) {
            // If sizes mismatch, we can't just reorder. The Repository should handle adding/removing first.
            // This checks prevents crashes.
            return emptyList()
        }

        val moves = mutableListOf<Move>()
        val workingList = current.toMutableList()

        for (targetIndex in desired.indices) {
            val targetId = desired[targetIndex]
            val currentIndex = workingList.indexOf(targetId)

            if (currentIndex != -1 && currentIndex != targetIndex) {
                moves.add(Move(from = currentIndex, to = targetIndex))
                val item = workingList.removeAt(currentIndex)
                workingList.add(targetIndex, item)
            }
        }
        return moves
    }
}