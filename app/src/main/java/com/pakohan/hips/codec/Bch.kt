package com.pakohan.hips.codec

/**
 * Extended BCH(63,36): 36 data bits in a 64-bit word, any five wrong bits are corrected.
 *
 * GF(64) with the primitive polynomial x^6 + x + 1. Bit i of a word is the coefficient of x^i; the
 * 27 parity bits come first, then the data, then one overall parity bit. Must stay bit-compatible
 * with `poc/hips/smallcode.py`; `BchTest` checks that against exported vectors.
 */
object Bch {
    const val DATA_BITS = 36
    const val WORD_BITS = 64
    const val CORRECTABLE = 5

    private const val CODE_BITS = 63
    private const val PARITY_BITS = CODE_BITS - DATA_BITS
    private const val FIELD_ORDER = 63

    private val exp = IntArray(2 * FIELD_ORDER).also { table ->
        table[0] = 1
        for (i in 1 until table.size) {
            val doubled = table[i - 1] shl 1
            table[i] = if (doubled and 64 != 0) doubled xor 0b1000011 else doubled
        }
    }
    private val log = IntArray(64).also { table ->
        for (power in 0 until FIELD_ORDER) table[exp[power]] = power
    }
    private val generator: IntArray = buildGenerator()

    data class Decoded(val data: List<Int>, val correctedBits: Int)

    fun encode(data: List<Int>): List<Int> {
        require(data.size == DATA_BITS) { "expected $DATA_BITS data bits, got ${data.size}" }
        val remainder = IntArray(CODE_BITS)
        data.forEachIndexed { index, bit -> remainder[PARITY_BITS + index] = bit }
        for (power in CODE_BITS - 1 downTo PARITY_BITS) {
            if (remainder[power] == 1) {
                generator.forEachIndexed { offset, coefficient ->
                    remainder[power - PARITY_BITS + offset] = remainder[power - PARITY_BITS + offset] xor coefficient
                }
            }
        }
        val word = remainder.take(PARITY_BITS) + data
        return word + word.sum() % 2
    }

    /** Null when more than [CORRECTABLE] bits are wrong (or the word is not near any codeword). */
    fun decode(received: List<Int>): Decoded? {
        require(received.size == WORD_BITS) { "expected $WORD_BITS bits, got ${received.size}" }
        val word = received.take(CODE_BITS).toIntArray()
        val syndromes = IntArray(2 * CORRECTABLE)
        word.forEachIndexed { position, bit ->
            if (bit == 1) {
                for (j in syndromes.indices) syndromes[j] = syndromes[j] xor exp[position * (j + 1) % FIELD_ORDER]
            }
        }
        var errors = 0
        if (syndromes.any { it != 0 }) {
            val locator = errorLocator(syndromes)
            val degree = locator.size - 1
            val positions = (0 until CODE_BITS).filter { position ->
                var value = 0
                locator.forEachIndexed { power, coefficient ->
                    value = value xor multiply(coefficient, exp[(FIELD_ORDER - position) * power % FIELD_ORDER])
                }
                value == 0
            }
            if (degree > CORRECTABLE || positions.size != degree) return null
            positions.forEach { word[it] = word[it] xor 1 }
            errors = degree
        }
        if (word.sum() % 2 != received[CODE_BITS]) {
            // Either the parity bit itself is wrong or an odd number of wrong bits went unnoticed.
            errors += 1
            if (errors > CORRECTABLE) return null
        }
        return Decoded(word.drop(PARITY_BITS), errors)
    }

    /** Berlekamp-Massey: the shortest recurrence that generates the syndromes, lowest power first. */
    private fun errorLocator(syndromes: IntArray): IntArray {
        var locator = intArrayOf(1)
        var previous = intArrayOf(1)
        var length = 0
        var shift = 1
        var last = 1
        for (step in syndromes.indices) {
            var discrepancy = syndromes[step]
            for (i in 1..length) {
                if (i < locator.size) discrepancy = discrepancy xor multiply(locator[i], syndromes[step - i])
            }
            if (discrepancy == 0) {
                shift += 1
                continue
            }
            val scale = multiply(discrepancy, exp[(FIELD_ORDER - log[last]) % FIELD_ORDER])
            val candidate = IntArray(maxOf(locator.size, shift + previous.size))
            locator.copyInto(candidate)
            previous.forEachIndexed { index, coefficient ->
                candidate[shift + index] = candidate[shift + index] xor multiply(scale, coefficient)
            }
            if (2 * length <= step) {
                previous = locator
                last = discrepancy
                length = step + 1 - length
                shift = 1
            } else {
                shift += 1
            }
            locator = candidate
        }
        return locator.dropLastWhile { it == 0 }.toIntArray()
    }

    private fun multiply(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]

    /** Product of the minimal polynomials of alpha^1 .. alpha^(2 * CORRECTABLE). */
    private fun buildGenerator(): IntArray {
        val roots = sortedSetOf<Int>()
        for (power in 1..2 * CORRECTABLE) {
            var conjugate = power
            while (roots.add(conjugate)) conjugate = conjugate * 2 % FIELD_ORDER
        }
        var polynomial = intArrayOf(1)
        for (root in roots) {
            val next = IntArray(polynomial.size + 1)
            polynomial.forEachIndexed { index, coefficient ->
                next[index + 1] = next[index + 1] xor coefficient
                next[index] = next[index] xor multiply(coefficient, exp[root])
            }
            polynomial = next
        }
        check(polynomial.size == PARITY_BITS + 1 && polynomial.all { it == 0 || it == 1 })
        return polynomial
    }
}
