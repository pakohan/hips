package com.pakohan.hips.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntroScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val name = "HIPS: hidden in plain sight"

    @Test
    fun finishesOnItsOwnAfterTheNameWasShown() {
        var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { IntroScreen(onFinished = { finished += 1 }) }

        compose.onNodeWithContentDescription(name).assertExists()
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals("still showing the name after two seconds", 0, finished)
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(1, finished)
    }

    @Test
    fun aTapSkipsIt() {
        var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { IntroScreen(onFinished = { finished += 1 }) }

        compose.onNodeWithContentDescription(name).performClick()
        assertEquals(1, finished)
    }
}
