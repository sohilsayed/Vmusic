package com.example.holodex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.holodex.R
import com.example.holodex.data.db.PlaylistEntity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce

@OptIn(FlowPreview::class)
@Composable
fun EditablePlaylistHeader(
    playlist: PlaylistEntity,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(playlist.name) { mutableStateOf(playlist.name ?: "") }
    var description by remember(playlist.description) { mutableStateOf(playlist.description ?: "") }

    // Use StateFlows with debounce to avoid excessive recompositions on every keystroke
    val nameFlow = remember { MutableStateFlow(name) }
    val descriptionFlow = remember { MutableStateFlow(description) }

    LaunchedEffect(Unit) {
        nameFlow.debounce(300).collect { onNameChange(it) }
    }
    LaunchedEffect(Unit) {
        descriptionFlow.debounce(300).collect { onDescriptionChange(it) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                nameFlow.value = it
            },
            label = { Text(stringResource(R.string.hint_playlist_name)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            singleLine = true
        )
        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it
                descriptionFlow.value = it
            },
            label = { Text(stringResource(R.string.hint_playlist_description_optional)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            maxLines = 3
        )
    }
}