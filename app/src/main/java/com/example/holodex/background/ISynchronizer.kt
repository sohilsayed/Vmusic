// File: java/com/example/holodex/background/ISynchronizer.kt (NEW FILE)
package com.example.holodex.background

/**
 * Defines the contract for a class that can synchronize a specific type of data
 * between the local database and a remote server.
 */
interface ISynchronizer {
    /**
     * The unique name of this synchronizer, used for logging.
     */
    val name: String

    /**
     * Executes the full synchronization logic for this data type.
     * @return `true` if the synchronization was successful, `false` otherwise.
     */
    suspend fun synchronize(): Boolean
}