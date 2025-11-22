package com.example.holodex.viewmodel.state

/**
 * A generic class that represents a resource's state: Loading, Success, or Error.
 * This is used for individual shelves in the Discovery Hub and other async UI components.
 */
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}