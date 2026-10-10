package com.esn.jarvis

import java.util.Locale

/** Forty offline two-operand calculators. Syntax: max <operation> NUMBER NUMBER. */
object JarvisMaxPairMath {
    private inline fun calculate(input: String, fn: (Double, Double) -> Double): String {
        val parts = input.trim().split(Regex("\\s+"))
        if (parts.size != 2) return "Enter two numbers separated by a space."
        val a = parts[0].toDoubleOrNull() ?: return "Invalid first number."
        val b = parts[1].toDoubleOrNull() ?: return "Invalid second number."
        if (!a.isFinite() || !b.isFinite()) return "Use finite numbers."
        val answer = fn(a,b)
        if (!answer.isFinite()) return "Undefined or out-of-range result."
        return String.format(Locale.US, "%.8f", answer).trimEnd('0').trimEnd('.')
    }
    fun execute(command: String, original: String): String? = when {
        command.startsWith("max add ") -> calculate(original.drop("max add ".length)) { a, b -> a + b }
        command.startsWith("max subtract ") -> calculate(original.drop("max subtract ".length)) { a, b -> a - b }
        command.startsWith("max multiply ") -> calculate(original.drop("max multiply ".length)) { a, b -> a * b }
        command.startsWith("max divide ") -> calculate(original.drop("max divide ".length)) { a, b -> a / b }
        command.startsWith("max power ") -> calculate(original.drop("max power ".length)) { a, b -> Math.pow(a,b) }
        command.startsWith("max modulo ") -> calculate(original.drop("max modulo ".length)) { a, b -> a % b }
        command.startsWith("max hypotenuse ") -> calculate(original.drop("max hypotenuse ".length)) { a, b -> Math.hypot(a,b) }
        command.startsWith("max atan2 ") -> calculate(original.drop("max atan2 ".length)) { a, b -> Math.toDegrees(Math.atan2(a,b)) }
        command.startsWith("max minimum ") -> calculate(original.drop("max minimum ".length)) { a, b -> minOf(a,b) }
        command.startsWith("max maximum ") -> calculate(original.drop("max maximum ".length)) { a, b -> maxOf(a,b) }
        command.startsWith("max average ") -> calculate(original.drop("max average ".length)) { a, b -> (a+b)/2.0 }
        command.startsWith("max difference ") -> calculate(original.drop("max difference ".length)) { a, b -> kotlin.math.abs(a-b) }
        command.startsWith("max percent of ") -> calculate(original.drop("max percent of ".length)) { a, b -> a*b/100.0 }
        command.startsWith("max percent change ") -> calculate(original.drop("max percent change ".length)) { a, b -> (b-a)/a*100.0 }
        command.startsWith("max ratio ") -> calculate(original.drop("max ratio ".length)) { a, b -> a/b }
        command.startsWith("max rectangle area ") -> calculate(original.drop("max rectangle area ".length)) { a, b -> a*b }
        command.startsWith("max rectangle perimeter ") -> calculate(original.drop("max rectangle perimeter ".length)) { a, b -> 2*(a+b) }
        command.startsWith("max triangle area ") -> calculate(original.drop("max triangle area ".length)) { a, b -> a*b/2.0 }
        command.startsWith("max parallelogram area ") -> calculate(original.drop("max parallelogram area ".length)) { a, b -> a*b }
        command.startsWith("max ellipse area ") -> calculate(original.drop("max ellipse area ".length)) { a, b -> Math.PI*a*b }
        command.startsWith("max cylinder volume ") -> calculate(original.drop("max cylinder volume ".length)) { a, b -> Math.PI*a*a*b }
        command.startsWith("max cylinder surface ") -> calculate(original.drop("max cylinder surface ".length)) { a, b -> 2*Math.PI*a*(a+b) }
        command.startsWith("max cone volume ") -> calculate(original.drop("max cone volume ".length)) { a, b -> Math.PI*a*a*b/3.0 }
        command.startsWith("max prism volume ") -> calculate(original.drop("max prism volume ".length)) { a, b -> a*a*b }
        command.startsWith("max kinetic energy ") -> calculate(original.drop("max kinetic energy ".length)) { a, b -> 0.5*a*b*b }
        command.startsWith("max momentum ") -> calculate(original.drop("max momentum ".length)) { a, b -> a*b }
        command.startsWith("max distance ") -> calculate(original.drop("max distance ".length)) { a, b -> a*b }
        command.startsWith("max speed ") -> calculate(original.drop("max speed ".length)) { a, b -> a/b }
        command.startsWith("max acceleration ") -> calculate(original.drop("max acceleration ".length)) { a, b -> a/b }
        command.startsWith("max density ") -> calculate(original.drop("max density ".length)) { a, b -> a/b }
        command.startsWith("max force ") -> calculate(original.drop("max force ".length)) { a, b -> a*b }
        command.startsWith("max work ") -> calculate(original.drop("max work ".length)) { a, b -> a*b }
        command.startsWith("max power watts ") -> calculate(original.drop("max power watts ".length)) { a, b -> a/b }
        command.startsWith("max ohms law voltage ") -> calculate(original.drop("max ohms law voltage ".length)) { a, b -> a*b }
        command.startsWith("max ohms law current ") -> calculate(original.drop("max ohms law current ".length)) { a, b -> a/b }
        command.startsWith("max ohms law resistance ") -> calculate(original.drop("max ohms law resistance ".length)) { a, b -> a/b }
        command.startsWith("max simple interest ") -> calculate(original.drop("max simple interest ".length)) { a, b -> a*b/100.0 }
        command.startsWith("max discount ") -> calculate(original.drop("max discount ".length)) { a, b -> a*(1-b/100.0) }
        command.startsWith("max tax total ") -> calculate(original.drop("max tax total ".length)) { a, b -> a*(1+b/100.0) }
        command.startsWith("max tip amount ") -> calculate(original.drop("max tip amount ".length)) { a, b -> a*b/100.0 }
        else -> null
    }
}
