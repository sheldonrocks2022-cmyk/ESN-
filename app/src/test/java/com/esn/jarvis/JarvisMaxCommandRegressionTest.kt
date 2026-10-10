package com.esn.jarvis

import org.junit.Assert.*
import org.junit.Test

/** Regression tests for pure JVM-compatible JARVIS MAX operations. */
class JarvisMaxCommandRegressionTest {
    @Test fun math() {
        assertEquals("144", JarvisMaxMath.execute("max square 12", "max square 12"))
        assertEquals("12", JarvisMaxMath.execute("max sqrt 144", "max sqrt 144"))
        assertEquals("Undefined or out-of-range result.", JarvisMaxMath.execute("max sqrt -1", "max sqrt -1"))
        assertEquals("120", JarvisMaxMath.execute("max factorial 5", "max factorial 5"))
    }
    @Test fun twoOperandMath() {
        assertEquals("75", JarvisMaxPairMath.execute("max add 25 50", "max add 25 50"))
        assertEquals("80", JarvisMaxPairMath.execute("max discount 100 20", "max discount 100 20"))
        assertEquals("Undefined or out-of-range result.", JarvisMaxPairMath.execute("max divide 1 0", "max divide 1 0"))
    }
    @Test fun conversions() {
        assertEquals("32.000000 °F", JarvisMaxConversions.execute("max c to f 0", "max c to f 0"))
        assertEquals("1024.000000 MiB", JarvisMaxConversions.execute("max gb to mb 1", "max gb to mb 1"))
    }
    @Test fun text() {
        assertEquals("HELLO", JarvisMaxTextTools.execute("max swap case hello", "max swap case hello"))
        assertEquals("3", JarvisMaxTextTools.execute("max characters a😀", "max characters a😀"))
        assertEquals("a\nb", JarvisMaxTextTools.execute("max sort lines b\na", "max sort lines b\na"))
        assertEquals("No", JarvisMaxTextTools.execute("max palindrome abc", "max palindrome abc"))
    }
}
