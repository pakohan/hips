package com.pakohan.hips.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pakohan.hips.R
import com.pakohan.hips.ui.theme.BrandAmber
import com.pakohan.hips.ui.theme.BrandNavy
import kotlinx.coroutines.delay

private const val INITIALS_ONLY_MILLIS = 500L
private const val STAGGER_MILLIS = 180L
private const val REVEAL_MILLIS = 450
private const val HOLD_MILLIS = 1100L
private val LetterSize = 56.sp

/**
 * What the app shows first: the letters H, I, P, S, which then turn out to be the initials of
 * "Hidden In Plain Sight". The initials are thick and amber, the rest thin and white, like the
 * letters that carry the message in the icon. A tap skips it.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val words = stringArrayResource(R.array.intro_words)
    val description = stringResource(R.string.intro_description)
    var revealed by remember { mutableIntStateOf(0) }
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        delay(INITIALS_ONLY_MILLIS)
        for (count in 1..words.size) {
            revealed = count
            delay(STAGGER_MILLIS)
        }
        delay(REVEAL_MILLIS + HOLD_MILLIS)
        finish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BrandNavy)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column {
            words.forEachIndexed { index, word ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(word.take(1), color = BrandAmber, fontWeight = FontWeight.Black, fontSize = LetterSize)
                    AnimatedVisibility(
                        visible = index < revealed,
                        enter = fadeIn(tween(REVEAL_MILLIS)) + expandHorizontally(tween(REVEAL_MILLIS), expandFrom = Alignment.Start),
                    ) {
                        Text(word.drop(1), color = Color.White, fontWeight = FontWeight.Light, fontSize = LetterSize)
                    }
                }
            }
        }
    }
}
