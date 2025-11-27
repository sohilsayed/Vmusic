package com.example.holodex.ui.composables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout

/**
 * A custom layout that stacks the [content] behind the [bottomBar].
 * It measures the [bottomBar] height and passes it as [PaddingValues] to the [content].
 */
@Composable
fun MainScreenLayout(
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    SubcomposeLayout(modifier = modifier) { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        // 1. Measure Bottom Bar first to know its height
        val bottomBarPlaceables = subcompose("bottomBar", bottomBar).map {
            it.measure(constraints.copy(minHeight = 0))
        }

        val bottomBarHeight = bottomBarPlaceables.maxOfOrNull { it.height } ?: 0
        val bottomBarHeightDp = bottomBarHeight.toDp()

        // 2. Prepare Content Padding
        // The content padding bottom is exactly the height of the bottom bar (Nav + Player)
        // plus any system navigation bar insets if handled internally.
        val contentPadding = PaddingValues(
            bottom = bottomBarHeightDp
        )

        // 3. Measure Content with the full screen constraints (it draws behind)
        val contentPlaceables = subcompose("content") {
            content(contentPadding)
        }.map {
            it.measure(constraints)
        }

        // 4. Place them
        layout(layoutWidth, layoutHeight) {
            // Place Content at (0,0) - full screen
            contentPlaceables.forEach { it.place(0, 0) }

            // Place Bottom Bar at the bottom
            bottomBarPlaceables.forEach {
                it.place(0, layoutHeight - bottomBarHeight)
            }
        }
    }
}