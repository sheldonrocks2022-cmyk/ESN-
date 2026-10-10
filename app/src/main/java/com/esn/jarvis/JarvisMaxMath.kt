package com.esn.jarvis

import java.util.Locale

/** 40 bounded, offline mathematical commands. Trigonometric input uses degrees. */
object JarvisMaxMath {
    private inline fun calculate(input: String, fn: (Double) -> Double): String {
        val number = input.trim().toDoubleOrNull() ?: return "Enter a valid number."
        if (!number.isFinite()) return "Enter a finite number."
        val answer = fn(number)
        if (!answer.isFinite()) return "Undefined or out-of-range result."
        return String.format(Locale.US, "%.8f", answer).trimEnd('0').trimEnd('.')
    }
    fun execute(command: String, original: String): String? = when {
        command.startsWith("max square ") -> calculate(original.drop("max square ".length)) { value -> value * value }
        command.startsWith("max cube ") -> calculate(original.drop("max cube ".length)) { value -> value * value * value }
        command.startsWith("max sqrt ") -> calculate(original.drop("max sqrt ".length)) { value -> kotlin.math.sqrt(value) }
        command.startsWith("max cbrt ") -> calculate(original.drop("max cbrt ".length)) { value -> Math.cbrt(value) }
        command.startsWith("max absolute ") -> calculate(original.drop("max absolute ".length)) { value -> kotlin.math.abs(value) }
        command.startsWith("max negate ") -> calculate(original.drop("max negate ".length)) { value -> -value }
        command.startsWith("max reciprocal ") -> calculate(original.drop("max reciprocal ".length)) { value -> 1.0 / value }
        command.startsWith("max percent ") -> calculate(original.drop("max percent ".length)) { value -> value / 100.0 }
        command.startsWith("max double ") -> calculate(original.drop("max double ".length)) { value -> value * 2.0 }
        command.startsWith("max half ") -> calculate(original.drop("max half ".length)) { value -> value / 2.0 }
        command.startsWith("max triple ") -> calculate(original.drop("max triple ".length)) { value -> value * 3.0 }
        command.startsWith("max factorial ") -> calculate(original.drop("max factorial ".length)) { value -> if (value < 0 || value > 170 || value % 1.0 != 0.0) Double.NaN else (1..value.toInt()).fold(1.0) { acc, n -> acc * n } }
        command.startsWith("max sin ") -> calculate(original.drop("max sin ".length)) { value -> kotlin.math.sin(Math.toRadians(value)) }
        command.startsWith("max cos ") -> calculate(original.drop("max cos ".length)) { value -> kotlin.math.cos(Math.toRadians(value)) }
        command.startsWith("max tan ") -> calculate(original.drop("max tan ".length)) { value -> kotlin.math.tan(Math.toRadians(value)) }
        command.startsWith("max asin ") -> calculate(original.drop("max asin ".length)) { value -> Math.toDegrees(kotlin.math.asin(value)) }
        command.startsWith("max acos ") -> calculate(original.drop("max acos ".length)) { value -> Math.toDegrees(kotlin.math.acos(value)) }
        command.startsWith("max atan ") -> calculate(original.drop("max atan ".length)) { value -> Math.toDegrees(kotlin.math.atan(value)) }
        command.startsWith("max ln ") -> calculate(original.drop("max ln ".length)) { value -> kotlin.math.ln(value) }
        command.startsWith("max log10 ") -> calculate(original.drop("max log10 ".length)) { value -> kotlin.math.log10(value) }
        command.startsWith("max exp ") -> calculate(original.drop("max exp ".length)) { value -> kotlin.math.exp(value) }
        command.startsWith("max floor ") -> calculate(original.drop("max floor ".length)) { value -> kotlin.math.floor(value) }
        command.startsWith("max ceil ") -> calculate(original.drop("max ceil ".length)) { value -> kotlin.math.ceil(value) }
        command.startsWith("max round ") -> calculate(original.drop("max round ".length)) { value -> kotlin.math.round(value) }
        command.startsWith("max sign ") -> calculate(original.drop("max sign ".length)) { value -> kotlin.math.sign(value) }
        command.startsWith("max degrees to radians ") -> calculate(original.drop("max degrees to radians ".length)) { value -> Math.toRadians(value) }
        command.startsWith("max radians to degrees ") -> calculate(original.drop("max radians to degrees ".length)) { value -> Math.toDegrees(value) }
        command.startsWith("max circle area ") -> calculate(original.drop("max circle area ".length)) { value -> Math.PI * value * value }
        command.startsWith("max circle circumference ") -> calculate(original.drop("max circle circumference ".length)) { value -> 2.0 * Math.PI * value }
        command.startsWith("max sphere volume ") -> calculate(original.drop("max sphere volume ".length)) { value -> 4.0 / 3.0 * Math.PI * value * value * value }
        command.startsWith("max sphere surface ") -> calculate(original.drop("max sphere surface ".length)) { value -> 4.0 * Math.PI * value * value }
        command.startsWith("max cube volume ") -> calculate(original.drop("max cube volume ".length)) { value -> value * value * value }
        command.startsWith("max cube surface ") -> calculate(original.drop("max cube surface ".length)) { value -> 6.0 * value * value }
        command.startsWith("max square perimeter ") -> calculate(original.drop("max square perimeter ".length)) { value -> 4.0 * value }
        command.startsWith("max square area ") -> calculate(original.drop("max square area ".length)) { value -> value * value }
        command.startsWith("max minutes to milliseconds ") -> calculate(original.drop("max minutes to milliseconds ".length)) { value -> value * 60000.0 }
        command.startsWith("max milliseconds to seconds ") -> calculate(original.drop("max milliseconds to seconds ".length)) { value -> value / 1000.0 }
        command.startsWith("max seconds to milliseconds ") -> calculate(original.drop("max seconds to milliseconds ".length)) { value -> value * 1000.0 }
        command.startsWith("max hours to seconds ") -> calculate(original.drop("max hours to seconds ".length)) { value -> value * 3600.0 }
        command.startsWith("max days to minutes ") -> calculate(original.drop("max days to minutes ".length)) { value -> value * 1440.0 }
        else -> null
    }
}
