package com.example.holodex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.holodex.R
import com.example.holodex.domain.action.GlobalMediaActionHandler
import com.example.holodex.ui.composables.EmptyState
import com.example.holodex.ui.composables.UnifiedListItem
import com.example.holodex.viewmodel.LibraryState
import com.example.holodex.viewmodel.LibraryType
import com.example.holodex.viewmodel.LibraryViewModel

@Composable
fun StandardMediaListScreen(
    libraryType: LibraryType,
    actions: GlobalMediaActionHandler,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    // 1. Initialize ViewModel for this specific screen type
    LaunchedEffect(libraryType) {
        viewModel.setLibraryType(libraryType)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {

        // 2. Search Bar (Visible for Downloads and Favorites, maybe optional for History)
        // You can customize logic here, e.g., if (libraryType != LibraryType.HISTORY)
        SearchBar(
            query = state.searchQuery,
            onQueryChange = viewModel::onSearchQueryChanged,
            onClear = { viewModel.onSearchQueryChanged("") },
            onSearch = { focusManager.clearFocus() },
            placeholder = when(libraryType) {
                LibraryType.DOWNLOADS -> stringResource(R.string.search_your_downloads_hint)
                else -> stringResource(R.string.search_your_music_hint)
            }
        )

        // 3. Content
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.isEmpty -> {
                    val emptyMessage = when(libraryType) {
                        LibraryType.HISTORY -> stringResource(R.string.message_no_history)
                        LibraryType.DOWNLOADS -> stringResource(R.string.message_no_downloads)
                        LibraryType.FAVORITES -> stringResource(R.string.message_no_favorites_or_segments)
                    }
                    EmptyState(
                        message = if (state.searchQuery.isNotEmpty()) "No results found" else emptyMessage,
                        onRefresh = {} // Local DB doesn't really need pull-to-refresh usually
                    )
                }
                else -> {
                    MediaList(
                        state = state,
                        actions = actions,
                        contentPadding = contentPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, "Clear")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@Composable
private fun MediaList(
    state: LibraryState,
    actions: GlobalMediaActionHandler,
    contentPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(
            items = state.items,
            key = { _, item -> item.stableId }
        ) { index, item ->
            UnifiedListItem(
                item = item,
                actions = actions,
                onItemClick = {
                    actions.onPlay(state.items, index)
                }
            )
        }
    }
}