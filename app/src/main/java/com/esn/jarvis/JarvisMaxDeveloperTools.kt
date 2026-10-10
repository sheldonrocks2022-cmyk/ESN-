package com.esn.jarvis

/** 25 additional offline text/developer commands, bounded to 4000 input characters. */
object JarvisMaxDeveloperTools {
    fun execute(command: String, original: String): String? = when {
        command.startsWith("max binary decode ") -> run(original.drop("max binary decode ".length)) { value -> String(value.split(Regex("\\s+")).map { Integer.parseInt(it, 2).toByte() }.toByteArray(), Charsets.UTF_8) }
        command.startsWith("max hex decode ") -> run(original.drop("max hex decode ".length)) { value -> String(value.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), Charsets.UTF_8) }
        command.startsWith("max base64 decode ") -> run(original.drop("max base64 decode ".length)) { value -> String(android.util.Base64.decode(value, android.util.Base64.DEFAULT), Charsets.UTF_8) }
        command.startsWith("max url decode ") -> run(original.drop("max url decode ".length)) { value -> java.net.URLDecoder.decode(value, "UTF-8") }
        command.startsWith("max md5 ") -> run(original.drop("max md5 ".length)) { value -> java.security.MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) } }
        command.startsWith("max sha1 ") -> run(original.drop("max sha1 ".length)) { value -> java.security.MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) } }
        command.startsWith("max sha512 ") -> run(original.drop("max sha512 ".length)) { value -> java.security.MessageDigest.getInstance("SHA-512").digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) } }
        command.startsWith("max slug ") -> run(original.drop("max slug ".length)) { value -> value.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-') }
        command.startsWith("max snake case ") -> run(original.drop("max snake case ".length)) { value -> value.trim().replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').lowercase(java.util.Locale.ROOT) }
        command.startsWith("max kebab case ") -> run(original.drop("max kebab case ".length)) { value -> value.trim().replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').lowercase(java.util.Locale.ROOT) }
        command.startsWith("max camel case ") -> run(original.drop("max camel case ".length)) { value -> value.trim().split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }.mapIndexed { i, w -> if (i == 0) w.lowercase() else w.lowercase().replaceFirstChar { it.uppercase() } }.joinToString("") }
        command.startsWith("max pascal case ") -> run(original.drop("max pascal case ".length)) { value -> value.trim().split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }.joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercase() } } }
        command.startsWith("max html escape ") -> run(original.drop("max html escape ".length)) { value -> value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace(34.toChar().toString(), "&quot;") }
        command.startsWith("max json quote ") -> run(original.drop("max json quote ".length)) { value -> org.json.JSONObject.quote(value) }
        command.startsWith("max remove newlines ") -> run(original.drop("max remove newlines ".length)) { value -> value.replace(Regex("[\\r\\n]+"), " ") }
        command.startsWith("max tabs to spaces ") -> run(original.drop("max tabs to spaces ".length)) { value -> value.replace("\\t", "    ") }
        command.startsWith("max remove emojis ") -> run(original.drop("max remove emojis ".length)) { value -> value.filter { it.category != CharCategory.OTHER_SYMBOL && !it.isSurrogate() } }
        command.startsWith("max mask email ") -> run(original.drop("max mask email ".length)) { value -> value.replace(Regex("(?i)([a-z0-9._%+-])[a-z0-9._%+-]*(@[a-z0-9.-]+\\.[a-z]{2,})"), "$1***$2") }
        command.startsWith("max mask digits ") -> run(original.drop("max mask digits ".length)) { value -> value.replace(Regex("\\d"), "*") }
        command.startsWith("max mask vowels ") -> run(original.drop("max mask vowels ".length)) { value -> value.replace(Regex("[AEIOUaeiou]"), "*") }
        command.startsWith("max word frequency ") -> run(original.drop("max word frequency ".length)) { value -> value.lowercase().split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(12).joinToString(", ") { "${it.key}: ${it.value}" } }
        command.startsWith("max character frequency ") -> run(original.drop("max character frequency ".length)) { value -> value.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(12).joinToString(", ") { "${it.key}: ${it.value}" } }
        command.startsWith("max reading minutes ") -> run(original.drop("max reading minutes ".length)) { value -> "Approximately ${kotlin.math.ceil(value.split(Regex("\\s+")).size / 200.0).toInt()} minute(s) at 200 words/minute" }
        command.startsWith("max byte count ") -> run(original.drop("max byte count ".length)) { value -> value.toByteArray(Charsets.UTF_8).size.toString() }
        command.startsWith("max unicode codes ") -> run(original.drop("max unicode codes ".length)) { value -> value.take(50).map { "U+%04X".format(it.code) }.joinToString(" ") }
        else -> null
    }
    private inline fun run(input: String, transform: (String) -> String): String {
        if (input.isBlank()) return "Provide text after the command."
        if (input.length > 4000) return "Maximum input length is 4000 characters."
        return try { transform(input).take(6000) } catch (_: Exception) { "Invalid input for this operation." }
    }
}
