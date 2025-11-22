package com.example.holodex.ui.composables

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.holodex.R
import com.example.holodex.viewmodel.ApiKeySaveResult
import com.example.holodex.viewmodel.SettingsViewModel
import org.orbitmvi.orbit.compose.collectAsState // <--- Import

@Composable
fun ApiKeyInputScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onApiKeySavedSuccessfully: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // FIX: Collect state first
    val state by settingsViewModel.collectAsState()

    // FIX: Access properties from state
    val currentApiKey = state.currentApiKey
    val apiKeySaveResult = state.apiKeySaveResult

    var apiKeyInputText by remember(currentApiKey) { mutableStateOf(currentApiKey) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(apiKeySaveResult) {
        when (val result = apiKeySaveResult) {
            is ApiKeySaveResult.Success -> {
                Toast.makeText(context, R.string.toast_api_key_saved, Toast.LENGTH_SHORT).show()
                onApiKeySavedSuccessfully()
                settingsViewModel.resetApiKeySaveResult()
            }
            is ApiKeySaveResult.Empty -> {
                Toast.makeText(context, R.string.toast_api_key_empty, Toast.LENGTH_SHORT).show()
                settingsViewModel.resetApiKeySaveResult()
            }
            is ApiKeySaveResult.Error -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                settingsViewModel.resetApiKeySaveResult()
            }
            is ApiKeySaveResult.Idle -> { /* Do nothing */ }
        }
    }

    Column(
        modifier = modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = apiKeyInputText,
            onValueChange = { apiKeyInputText = it },
            label = { Text(stringResource(id = R.string.hint_api_key)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                settingsViewModel.saveApiKey(apiKeyInputText)
            })
        )
        Button(
            onClick = {
                focusManager.clearFocus()
                settingsViewModel.saveApiKey(apiKeyInputText)
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(id = R.string.button_save_key))
        }
    }
}