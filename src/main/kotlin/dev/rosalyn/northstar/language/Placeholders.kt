package dev.rosalyn.northstar.language

fun processMessage(input: String, placeholders: Map<String, String>): String {
    val parsedPlaceholder = StringBuilder()
    var isParsingPlaceholder = false
    val builder = StringBuilder()
    var index = 0

    while (index < input.length) {
        val char = input[index++]

        if (isParsingPlaceholder) {
            if (char == '}') {
                val placeholder = parsedPlaceholder.toString()
                isParsingPlaceholder = false
                parsedPlaceholder.clear()

                if (placeholder in placeholders)
                    builder.append(placeholders[placeholder])
                else builder.append('{', placeholder, '}')
                continue
            }

            parsedPlaceholder.append(char)
            continue
        }

        if (char == '\\') {
            builder.append(input[index++])
            continue
        }

        if (char == '{') {
            isParsingPlaceholder = true
            continue
        }

        builder.append(char)
    }

    if (parsedPlaceholder.isNotEmpty())
        builder.append(parsedPlaceholder)

    return builder.toString()
}