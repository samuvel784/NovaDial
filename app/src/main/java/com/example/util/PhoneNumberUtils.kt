package com.example.util

object PhoneNumberUtils {
    private val EMERGENCY_NUMBERS = setOf("911", "112", "999", "000", "110", "119", "120", "122")

    fun normalize(rawNumber: String): String {
        if (rawNumber.isBlank()) return ""
        val trimmed = rawNumber.trim()
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }

    fun isEmergencyNumber(number: String): Boolean {
        val normalized = normalize(number)
        return EMERGENCY_NUMBERS.contains(normalized)
    }

    /**
     * Checks if two phone numbers match (e.g. comparing last 8-10 digits for local vs international)
     */
    fun numbersMatch(num1: String, num2: String): Boolean {
        val n1 = normalize(num1)
        val n2 = normalize(num2)
        if (n1 == n2) return true
        if (n1.isEmpty() || n2.isEmpty()) return false

        val d1 = n1.filter { it.isDigit() }
        val d2 = n2.filter { it.isDigit() }
        if (d1 == d2) return true

        val minLen = 8
        if (d1.length >= minLen && d2.length >= minLen) {
            val tail1 = d1.takeLast(minLen)
            val tail2 = d2.takeLast(minLen)
            return tail1 == tail2
        }
        return false
    }

    fun formatForDisplay(rawNumber: String): String {
        return formatByFiveDigits(rawNumber)
    }

    /**
     * Formats phone numbers by grouping digits into chunks of 5 separated by spaces:
     * e.g. "12345 67890", "12345 67890 09876"
     */
    fun formatByFiveDigits(rawNumber: String): String {
        if (rawNumber.isBlank()) return ""
        val hasPlus = rawNumber.startsWith("+")
        val cleanDigits = rawNumber.filter { it.isDigit() }
        if (cleanDigits.isEmpty()) return rawNumber

        val chunks = cleanDigits.chunked(5)
        val formatted = chunks.joinToString(" ")
        return if (hasPlus) "+$formatted" else formatted
    }

    private val T9_MAP = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2',
        'd' to '3', 'e' to '3', 'f' to '3',
        'g' to '4', 'h' to '4', 'i' to '4',
        'j' to '5', 'k' to '5', 'l' to '5',
        'm' to '6', 'n' to '6', 'o' to '6',
        'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
        't' to '8', 'u' to '8', 'v' to '8',
        'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9'
    )

    fun nameToT9Digits(name: String): String {
        val sb = StringBuilder()
        for (char in name.lowercase()) {
            val digit = T9_MAP[char]
            if (digit != null) {
                sb.append(digit)
            } else if (char.isDigit()) {
                sb.append(char)
            } else if (char.isWhitespace()) {
                sb.append(' ')
            }
        }
        return sb.toString()
    }

    /**
     * Checks if typed digits match the contact via T9 name or direct phone number substring
     */
    fun matchesT9(queryDigits: String, name: String, phoneNumbers: List<String>): Boolean {
        if (queryDigits.isBlank()) return true
        val cleanQuery = queryDigits.filter { it.isDigit() }
        if (cleanQuery.isBlank()) return true

        // 1. Direct phone number match
        for (phone in phoneNumbers) {
            val norm = normalize(phone)
            if (norm.contains(cleanQuery)) return true
        }

        // 2. T9 Name matching (matches beginning of any name token)
        val nameT9 = nameToT9Digits(name)
        val words = nameT9.split(" ").filter { it.isNotBlank() }
        for (word in words) {
            if (word.startsWith(cleanQuery)) return true
        }
        if (nameT9.replace(" ", "").contains(cleanQuery)) return true

        return false
    }
}
