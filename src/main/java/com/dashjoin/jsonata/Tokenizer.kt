/**
 * jsonata-java is the JSONata Java reference port
 *
 * Copyright Dashjoin GmbH. https://dashjoin.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Derived from Javascript code under this license:
/**
 * © Copyright IBM Corp. 2016, 2018 All Rights Reserved
 * Project name: JSONata
 * This project is licensed under the MIT License, see LICENSE
 */
package com.dashjoin.jsonata

import com.dashjoin.jsonata.Utils.convertNumber
import java.util.regex.Pattern

class Tokenizer internal constructor(// Tokenizer (lexer) - invoked by the parser to return one token at a time
    var path: String
) {
    var position: Int = 0
    var length: Int = path.length // = path.length;

    class Token {
        @JvmField
        var type: String? = null
        @JvmField
        var value: Any? = null
        @JvmField
        var position: Int = 0

        //
        var id: String? = null
    }

    fun create(type: String?, value: Any?): Token {
        val t = Token()
        t.type = type
        t.value = value
        t.position = position
        return t
    }

    fun isClosingSlash(position: Int): Boolean {
        if (path[position] == '/' && depth == 0) {
            var backslashCount = 0
            while (path[position - (backslashCount + 1)] == '\\') {
                backslashCount++
            }
            if (backslashCount % 2 == 0) {
                return true
            }
        }
        return false
    }

    var depth: Int = 0

    fun scanRegex(): Pattern {
        // the prefix '/' will have been previously scanned. Find the end of the regex.
        // search for closing '/' ignoring any that are escaped, or within brackets
        var start = position
        //int depth = 0;
        val pattern: String
        val flags: String

        while (position < length) {
            var currentChar = path[position]
            if (isClosingSlash(position)) {
                // end of regex found
                pattern = path.substring(start, position)
                if (pattern == "") {
                    throw JException("S0301", position)
                }
                position++
                currentChar = path[position]
                // flags
                start = position
                while (currentChar == 'i' || currentChar == 'm') {
                    position++
                    currentChar = path[position]
                }
                flags = path.substring(start, position) + 'g'


                // Convert flags to Java Pattern flags
                var _flags = 0
                if (flags.contains("i")) _flags = _flags or Pattern.CASE_INSENSITIVE
                if (flags.contains("m")) _flags = _flags or Pattern.MULTILINE
                return Pattern.compile(
                    pattern,
                    _flags
                ) // Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
            }
            if ((currentChar == '(' || currentChar == '[' || currentChar == '{') && path[position - 1] != '\\') {
                depth++
            }
            if ((currentChar == ')' || currentChar == ']' || currentChar == '}') && path[position - 1] != '\\') {
                depth--
            }
            position++
        }
        throw JException("S0302", position)
    }

    fun next(prefix: Boolean): Token? {
        if (position >= length) return null
        var currentChar = path[position]
        // skip whitespace
        while (position < length && " \t\n\r".indexOf(currentChar) > -1) { // Uli: removed \v as Java doesn't support it
            position++
            if (position >= length) return null // Uli: JS relies on charAt returns null

            currentChar = path[position]
        }
        // skip comments
        if (currentChar == '/' && path[position + 1] == '*') {
            val commentStart = position
            position += 2
            currentChar = path[position]
            while (!(currentChar == '*' && path[position + 1] == '/')) {
                currentChar = path[++position]
                if (position >= length) {
                    // no closing tag
                    throw JException("S0106", commentStart)
                }
            }
            position += 2
            currentChar = path[position]
            return next(prefix) // need this to swallow any following whitespace
        }
        // test for regex
        if (prefix != true && currentChar == '/') {
            position++
            return create("regex", scanRegex())
        }
        // handle double-char operators
        val haveMore = position < path.length - 1 // Java: position+1 is valid
        if (currentChar == '.' && haveMore && path[position + 1] == '.') {
            // double-dot .. range operator
            position += 2
            return create("operator", "..")
        }
        if (currentChar == ':' && haveMore && path[position + 1] == '=') {
            // := assignment
            position += 2
            return create("operator", ":=")
        }
        if (currentChar == '!' && haveMore && path[position + 1] == '=') {
            // !=
            position += 2
            return create("operator", "!=")
        }
        if (currentChar == '>' && haveMore && path[position + 1] == '=') {
            // >=
            position += 2
            return create("operator", ">=")
        }
        if (currentChar == '<' && haveMore && path[position + 1] == '=') {
            // <=
            position += 2
            return create("operator", "<=")
        }
        if (currentChar == '*' && haveMore && path[position + 1] == '*') {
            // **  descendant wildcard
            position += 2
            return create("operator", "**")
        }
        if (currentChar == '~' && haveMore && path[position + 1] == '>') {
            // ~>  chain function
            position += 2
            return create("operator", "~>")
        }
        // test for single char operators
        if (operators["" + currentChar] != null) {
            position++
            return create("operator", currentChar)
        }
        // test for string literals
        if (currentChar == '"' || currentChar == '\'') {
            val quoteType = currentChar
            // double quoted string literal - find end of string
            position++
            var qstr: String? = ""
            while (position < length) {
                currentChar = path[position]
                if (currentChar == '\\') { // escape sequence
                    position++
                    currentChar = path[position]
                    if (escapes["" + currentChar] != null) {
                        qstr += escapes["" + currentChar]
                    } else if (currentChar == 'u') {
                        //  u should be followed by 4 hex digits
                        val octets = path.substring(position + 1, (position + 1) + 4)
                        if (octets.matches("^[0-9a-fA-F]+$".toRegex())) { //  /^[0-9a-fA-F]+$/.test(octets)) {
                            val codepoint = octets.toInt(16)
                            qstr += Character.toString(codepoint.toChar())
                            position += 4
                        } else {
                            throw JException("S0104", position)
                        }
                    } else {
                        // illegal escape sequence
                        throw JException("S0301", position, currentChar)
                    }
                } else if (currentChar == quoteType) {
                    position++
                    return create("string", qstr)
                } else {
                    qstr += currentChar
                }
                position++
            }
            throw JException("S0101", position)
        }
        // test for numbers
        val numregex = Pattern.compile("^-?(0|([1-9][0-9]*))(\\.[0-9]+)?([Ee][-+]?[0-9]+)?")
        val match = numregex.matcher(path.substring(position))
        if (match.find()) {
            val num = match.group(0).toDouble()
            if (!java.lang.Double.isNaN(num) && java.lang.Double.isFinite(num)) {
                position += match.group(0).length
                // If the number is integral, use long as type
                return create("number", convertNumber(num))
            } else {
                throw JException("S0102", position) //, match.group[0]);
            }
        }

        // test for quoted names (backticks)
        val name: String
        if (currentChar == '`') {
            // scan for closing quote
            position++
            val end = path.indexOf('`', position)
            if (end != -1) {
                name = path.substring(position, end)
                position = end + 1
                return create("name", name)
            }
            position = length
            throw JException("S0105", position)
        }
        // test for names
        var i = position
        var ch: Char
        while (true) {
            //if (i>=length) return null; // Uli: JS relies on charAt returns null

            ch = if (i < length) path[i] else 0.toChar()
            if (i == length || " \t\n\r".indexOf(ch) > -1 || operators.containsKey("" + ch)) { // Uli: removed \v
                if (path[position] == '$') {
                    // variable reference
                    val _name = path.substring(position + 1, i)
                    position = i
                    return create("variable", _name)
                } else {
                    val _name = path.substring(position, i)
                    position = i
                    when (_name) {
                        "or", "in", "and" -> return create("operator", _name)
                        "true" -> return create("value", true)
                        "false" -> return create("value", false)
                        "null" -> return create("value", null)
                        else -> {
                            if (position == length && _name == "") {
                                // whitespace at end of input
                                return null
                            }
                            return create("name", _name)
                        }
                    }
                }
            } else {
                i++
            }
        }
    }

    companion object {
        // = function (path) {
        @JvmField
        var operators: HashMap<String, Int> = object : HashMap<String, Int>() {
            init {
                put(".", 75)
                put("[", 80)
                put("]", 0)
                put("{", 70)
                put("}", 0)
                put("(", 80)
                put(")", 0)
                put(",", 0)
                put("@", 80)
                put("#", 80)
                put(";", 80)
                put(":", 80)
                put("?", 20)
                put("+", 50)
                put("-", 50)
                put("*", 60)
                put("/", 60)
                put("%", 60)
                put("|", 20)
                put("=", 40)
                put("<", 40)
                put(">", 40)
                put("^", 40)
                put("**", 60)
                put("..", 20)
                put(":=", 10)
                put("!=", 40)
                put("<=", 40)
                put(">=", 40)
                put("~>", 40)
                put("and", 30)
                put("or", 25)
                put("in", 40)
                put("&", 50)
                put("!", 0)
                put("~", 0)
            }
        }

        var escapes: HashMap<String, String> = object : HashMap<String, String>() {
            init {
                // JSON string escape sequences - see json.org
                put("\"", "\"")
                put("\\", "\\")
                put("/", "/")
                put("b", "\b")
                put("f", "\u000c")
                put("n", "\n")
                put("r", "\r")
                put("t", "\t")
            }
        }
    }
}
