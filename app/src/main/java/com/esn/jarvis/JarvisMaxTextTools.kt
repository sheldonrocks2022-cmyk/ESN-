package com.esn.jarvis

import java.util.Locale

/**
 * Forty offline text tools. Input and output are bounded to prevent UI stalls.
 * No network, filesystem writes or account access.
 */
object JarvisMaxTextTools {
    private val locale = Locale.getDefault()

    private inline fun transform(input: String, operation: (String) -> String): String =
        if (input.isBlank()) "Provide text after the command."
        else if (input.length > 4000) "Input limit: 4000 characters."
        else try { operation(input).take(6000) } catch (_: Exception) { "Unable to process this input." }

    fun execute(command: String, original: String): String? = when {
            command.startsWith("max trim ") -> transform(original.drop("max trim ".length)) { value -> value.trim() }
            command.startsWith("max length ") -> transform(original.drop("max length ".length)) { value -> value.length.toString() }
            command.startsWith("max characters ") -> transform(original.drop("max characters ".length)) { value -> value.codePointCount(0, value.length).toString() }
            command.startsWith("max lines ") -> transform(original.drop("max lines ".length)) { value -> value.lines().size.toString() }
            command.startsWith("max sentences ") -> transform(original.drop("max sentences ".length)) { value -> value.split(Regex("[.!?]+" )).count { it.isNotBlank() }.toString() }
            command.startsWith("max paragraphs ") -> transform(original.drop("max paragraphs ".length)) { value -> value.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }.toString() }
            command.startsWith("max title case ") -> transform(original.drop("max title case ".length)) { value -> value.lowercase(locale).split(Regex("\\s+")).joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(locale) } } }
            command.startsWith("max capitalize ") -> transform(original.drop("max capitalize ".length)) { value -> value.replaceFirstChar { it.titlecase(locale) } }
            command.startsWith("max swap case ") -> transform(original.drop("max swap case ".length)) { value -> value.map { if (it.isUpperCase()) it.lowercaseChar() else it.uppercaseChar() }.joinToString("") }
            command.startsWith("max remove spaces ") -> transform(original.drop("max remove spaces ".length)) { value -> value.filterNot { it.isWhitespace() } }
            command.startsWith("max normalize spaces ") -> transform(original.drop("max normalize spaces ".length)) { value -> value.trim().replace(Regex("\\s+"), " ") }
            command.startsWith("max remove digits ") -> transform(original.drop("max remove digits ".length)) { value -> value.filterNot { it.isDigit() } }
            command.startsWith("max only digits ") -> transform(original.drop("max only digits ".length)) { value -> value.filter { it.isDigit() } }
            command.startsWith("max remove punctuation ") -> transform(original.drop("max remove punctuation ".length)) { value -> value.filter { it.isLetterOrDigit() || it.isWhitespace() } }
            command.startsWith("max only letters ") -> transform(original.drop("max only letters ".length)) { value -> value.filter { it.isLetter() } }
            command.startsWith("max only numbers ") -> transform(original.drop("max only numbers ".length)) { value -> value.filter { it.isDigit() || it == '.' || it == '-' } }
            command.startsWith("max sort words ") -> transform(original.drop("max sort words ".length)) { value -> value.split(Regex("\\s+")).sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(" ") }
            command.startsWith("max unique words ") -> transform(original.drop("max unique words ".length)) { value -> value.split(Regex("\\s+")).distinctBy { it.lowercase(locale) }.joinToString(" ") }
            command.startsWith("max sort lines ") -> transform(original.drop("max sort lines ".length)) { value -> value.lines().sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString("\\n") }
            command.startsWith("max unique lines ") -> transform(original.drop("max unique lines ".length)) { value -> value.lines().distinct().joinToString("\\n") }
            command.startsWith("max reverse words ") -> transform(original.drop("max reverse words ".length)) { value -> value.split(Regex("\\s+")).reversed().joinToString(" ") }
            command.startsWith("max reverse lines ") -> transform(original.drop("max reverse lines ".length)) { value -> value.lines().reversed().joinToString("\\n") }
            command.startsWith("max first word ") -> transform(original.drop("max first word ".length)) { value -> value.trim().split(Regex("\\s+")).firstOrNull().orEmpty() }
            command.startsWith("max last word ") -> transform(original.drop("max last word ".length)) { value -> value.trim().split(Regex("\\s+")).lastOrNull().orEmpty() }
            command.startsWith("max first line ") -> transform(original.drop("max first line ".length)) { value -> value.lines().firstOrNull().orEmpty() }
            command.startsWith("max last line ") -> transform(original.drop("max last line ".length)) { value -> value.lines().lastOrNull().orEmpty() }
            command.startsWith("max initials ") -> transform(original.drop("max initials ".length)) { value -> value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.first().uppercaseChar() }.joinToString("") }
            command.startsWith("max palindrome ") -> transform(original.drop("max palindrome ".length)) { value -> value.filter { it.isLetterOrDigit() }.lowercase(locale).let { if (it == it.reversed()) "Yes" else "No" } }
            command.startsWith("max vowels ") -> transform(original.drop("max vowels ".length)) { value -> value.count { it.lowercaseChar() in "aeiou" }.toString() }
            command.startsWith("max consonants ") -> transform(original.drop("max consonants ".length)) { value -> value.count { it.isLetter() && it.lowercaseChar() !in "aeiou" }.toString() }
            command.startsWith("max digit count ") -> transform(original.drop("max digit count ".length)) { value -> value.count { it.isDigit() }.toString() }
            command.startsWith("max space count ") -> transform(original.drop("max space count ".length)) { value -> value.count { it == ' ' }.toString() }
            command.startsWith("max uppercase count ") -> transform(original.drop("max uppercase count ".length)) { value -> value.count { it.isUpperCase() }.toString() }
            command.startsWith("max lowercase count ") -> transform(original.drop("max lowercase count ".length)) { value -> value.count { it.isLowerCase() }.toString() }
            command.startsWith("max ascii ") -> transform(original.drop("max ascii ".length)) { value -> value.all { it.code <= 127 }.let { if (it) "ASCII only" else "Contains non-ASCII characters" } }
            command.startsWith("max base64 encode ") -> transform(original.drop("max base64 encode ".length)) { value -> android.util.Base64.encodeToString(value.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP) }
            command.startsWith("max url encode ") -> transform(original.drop("max url encode ".length)) { value -> java.net.URLEncoder.encode(value, "UTF-8") }
            command.startsWith("max hex encode ") -> transform(original.drop("max hex encode ".length)) { value -> value.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 255) } }
            command.startsWith("max binary encode ") -> transform(original.drop("max binary encode ".length)) { value -> value.toByteArray(Charsets.UTF_8).joinToString(" ") { (it.toInt() and 255).toString(2).padStart(8, '0') } }
            command.startsWith("max rot13 ") -> transform(original.drop("max rot13 ".length)) { value -> value.map { c -> when(c) { in 'a'..'z' -> ('a'.code + (c.code - 'a'.code + 13) % 26).toChar(); in 'A'..'Z' -> ('A'.code + (c.code - 'A'.code + 13) % 26).toChar(); else -> c } }.joinToString("") }
        else -> null
    }
}
