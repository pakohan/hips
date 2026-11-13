package com.pakohan.hips.pdf

import kotlin.random.Random

/** Placeholder text made only of characters the HIPS font has. */
object LoremIpsum {
    private const val OPENING = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor " +
        "incididunt ut labore et dolore magna aliqua."

    private val words = (
        "ad adipiscing aliqua aliquip amet anim aute cillum commodo consectetur consequat culpa cupidatat " +
            "deserunt do dolor dolore duis ea eiusmod elit enim esse est et eu ex excepteur exercitation fugiat " +
            "id in incididunt ipsum irure labore laboris laborum lorem magna minim mollit nisi non nostrud nulla " +
            "occaecat officia pariatur proident qui quis reprehenderit sed sint sit sunt tempor ullamco ut " +
            "velit veniam voluptate"
        ).split(' ')

    /** An endless run of sentences: the familiar opening, then sentences drawn from the same vocabulary. */
    fun sentences(random: Random): Sequence<String> =
        sequenceOf(OPENING) + generateSequence {
            val length = random.nextInt(6, 15)
            val commaAfter = if (length > 8 && random.nextBoolean()) random.nextInt(2, length - 3) else -1
            List(length) { index -> words[random.nextInt(words.size)] + if (index == commaAfter) "," else "" }
                .joinToString(" ")
                .replaceFirstChar(Char::uppercaseChar) + "."
        }
}
