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
package com.dashjoin.jsonata

import com.dashjoin.jsonata.JException
import com.dashjoin.jsonata.Jsonata.JFunction
import com.dashjoin.jsonata.Utils.RangeList
import com.dashjoin.jsonata.Utils.checkUrl
import com.dashjoin.jsonata.Utils.convertNumber
import com.dashjoin.jsonata.Utils.createSequence
import com.dashjoin.jsonata.Utils.isFunction
import com.dashjoin.jsonata.Utils.isSequence
import com.dashjoin.jsonata.Utils.quote
import com.dashjoin.jsonata.json.Json.parseJson
import com.dashjoin.jsonata.utils.Constants
import com.dashjoin.jsonata.utils.DateTimeUtils
import com.dashjoin.jsonata.utils.DateTimeUtils.formatDateTime
import com.dashjoin.jsonata.utils.DateTimeUtils.lettersToDecimal
import com.dashjoin.jsonata.utils.DateTimeUtils.parseDateTime
import com.dashjoin.jsonata.utils.DateTimeUtils.romanToDecimal
import com.dashjoin.jsonata.utils.DateTimeUtils.wordsToLong
import java.io.UnsupportedEncodingException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.MatchResult
import java.util.regex.Pattern
import kotlin.math.pow

object Functions {
    /**
     * Sum function
     * @param {Object} args - Arguments
     * @returns {number} Total value of arguments
     */
    @JvmStatic
    fun sum(args: List<Number>?): Number? {
        // undefined inputs always return undefined
        if (args == null) {
            return null
        }

        val total = args.stream().mapToDouble { obj: Number -> obj.toDouble() }.sum()
        return total
    }

    /**
     * Count function
     * @param {Object} args - Arguments
     * @returns {number} Number of elements in the array
     */
    @JvmStatic
    fun count(args: List<Any?>?): Number {
        // undefined inputs always return undefined
        if (args == null) {
            return 0
        }

        return args.size
    }

    /**
     * Max function
     * @param {Object} args - Arguments
     * @returns {number} Max element in the array
     */
    @JvmStatic
    fun max(args: List<Number>?): Number? {
        // undefined inputs always return undefined
        if (args == null || args.size == 0) {
            return null
        }

        val res = args.stream().mapToDouble { obj: Number -> obj.toDouble() }.max()
        return if (res.isPresent) res.asDouble
        else null
    }

    /**
     * Min function
     * @param {Object} args - Arguments
     * @returns {number} Min element in the array
     */
    @JvmStatic
    fun min(args: List<Number>?): Number? {
        // undefined inputs always return undefined
        if (args == null || args.size == 0) {
            return null
        }

        val res = args.stream().mapToDouble { obj: Number -> obj.toDouble() }.min()
        return if (res.isPresent) res.asDouble
        else null
    }

    /**
     * Average function
     * @param {Object} args - Arguments
     * @returns {number} Average element in the array
     */
    @JvmStatic
    fun average(args: List<Number>?): Number? {
        // undefined inputs always return undefined
        if (args == null || args.size == 0) {
            return null
        }

        val res = args.stream().mapToDouble { obj: Number -> obj.toDouble() }.average()
        return if (res.isPresent) res.asDouble
        else null
    }

    /**
     * Stringify arguments
     * @param {Object} arg - Arguments
     * @param {boolean} [prettify] - Pretty print the result
     * @returns {String} String from arguments
     */
    @JvmStatic
    fun string(arg: Any?, prettify: Boolean?): String? {
        var arg = arg
        if (arg is Utils.JList<*>) if (arg.outerWrapper) arg =
            arg[0]

        if (arg == null) return null

        // see https://docs.jsonata.org/string-functions#string: Strings are unchanged
        if (arg is String) return arg

        val sb = StringBuilder()
        string(sb, arg, prettify != null && prettify, "")
        return sb.toString()
    }

    /**
     * Internal recursive string function based on StringBuilder.
     * Avoids creation of intermediate String objects
     */
    private fun string(b: StringBuilder, arg: Any?, prettify: Boolean, indent: String) {
        // if (arg == null)
        //   return null;

        if (arg == null || arg === Jsonata.NULL_VALUE) {
            b.append("null")
            return
        }

        if (arg is JFunction) {
            return
        }

        if (arg is Parser.Symbol) {
            return
        }

        if (arg is Double) {
            // TODO: this really should be in the jackson serializer
            val bd = BigDecimal((arg as Double?)!!, MathContext(15))
            var res = bd.stripTrailingZeros().toString()

            if (res.indexOf("E+") > 0) res = res.replace("E+", "e+")
            if (res.indexOf("E-") > 0) res = res.replace("E-", "e-")

            b.append(res)
            return
        }

        if (arg is Number || arg is Boolean) {
            b.append(arg)
            return
        }

        if (arg is String) {
            // quotes within strings must be escaped
            quote((arg as String?)!!, b)
            return
        }

        if (arg is Map<*, *>) {
            b.append('{')
            if (prettify == true) b.append('\n')
            for ((key, v) in (arg as Map<String?, Any>)) {
                if (prettify == true) {
                    b.append(indent)
                    b.append("  ")
                }
                b.append('"')
                b.append(key)
                b.append('"')
                b.append(':')
                if (prettify == true) b.append(' ')
                if (v is String || v is Parser.Symbol
                    || v is JFunction
                ) {
                    b.append('"')
                    string(b, v, prettify, "$indent  ")
                    b.append('"')
                } else string(b, v, prettify, "$indent  ")
                b.append(',')
                if (prettify == true) b.append('\n')
            }
            if (!(arg as Map<*, *>).isEmpty()) b.deleteCharAt(b.length - (if (prettify == true) 2 else 1))
            if (prettify == true) b.append(indent)
            b.append('}')
            return
        }

        if ((arg is List<*>)) {
            if (arg.isEmpty()) {
                b.append("[]")
                return
            }
            b.append('[')
            if (prettify == true) b.append('\n')
            for (v in arg) {
                if (prettify == true) {
                    b.append(indent)
                    b.append("  ")
                }
                if (v is String || v is Parser.Symbol || v is JFunction) {
                    b.append('"')
                    string(b, v, prettify, "$indent  ")
                    b.append('"')
                } else string(b, v, prettify, "$indent  ")
                b.append(',')
                if (prettify == true) b.append('\n')
            }
            if (!arg.isEmpty()) b.deleteCharAt(b.length - (if (prettify == true) 2 else 1))
            if (prettify == true) b.append(indent)
            b.append(']')
            return
        }

        // Throw error for unknown types
        throw IllegalArgumentException("Only JSON types (values, Map, List) can be stringified. Unsupported type: " + arg.javaClass.name)
    }


    /**
     * Validate input data types.
     * This will make sure that all input data can be processed.
     *
     * @param arg
     * @return
     */
    @JvmStatic
    fun validateInput(arg: Any?) {
        // if (arg == null)
        //   return null;

        if (arg == null || arg === Jsonata.NULL_VALUE) {
            return
        }

        if (arg is JFunction) {
            return
        }

        if (arg is Parser.Symbol) {
            return
        }

        if (arg is Double) {
            return
        }

        if (arg is Number || arg is Boolean) {
            return
        }

        if (arg is String) {
            return
        }

        if (arg is Map<*, *>) {
            for ((key, value) in (arg as Map<String?, Any?>)) {
                validateInput(key)
                validateInput(value)
            }
            return
        }

        if ((arg is List<*>)) {
            for (v in arg) {
                validateInput(v)
            }
            return
        }

        // Throw error for unknown types
        throw IllegalArgumentException(
            "Only JSON types (values, Map, List) are allowed as input. Unsupported type: " +
                    arg.javaClass.canonicalName
        )
    }

    /**
     * Create substring based on character number and length
     * @param {String} str - String to evaluate
     * @param {Integer} start - Character number to start substring
     * @param {Integer} [length] - Number of characters in substring
     * @returns {string|*} Substring
     */
    @JvmStatic
    fun substring(str: String?, _start: Number?, _length: Number?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        var start = _start?.toInt()
        val length = _length?.toInt()

        // not used: var strArray = stringToArray(str);
        val strLength = str.length

        if (strLength + start!! < 0) {
            start = 0
        }

        if (length != null) {
            if (length <= 0) {
                return ""
            }
            return substr(str, start, length)
        }

        return substr(str, start, strLength)
    }


    /**
     * Source = Jsonata4Java JSONataUtils.substr
     * @param str
     * @param start  Location at which to begin extracting characters. If a negative
     * number is given, it is treated as strLength - start where
     * strLength is the length of the string. For example,
     * str.substr(-3) is treated as str.substr(str.length - 3)
     * @param length The number of characters to extract. If this argument is null,
     * all the characters from start to the end of the string are
     * extracted.
     * @return A new string containing the extracted section of the given string. If
     * length is 0 or a negative number, an empty string is returned.
     */
    @JvmStatic
    fun substr(str: String?, start: Int?, length: Int?): String {
        // below has to convert start and length for emojis and unicode

        var start = start
        var length = length
        val origLen = str!!.length

        val strData = Objects.requireNonNull(str).intern()
        val strLen = strData.codePointCount(0, strData.length)
        if (start!! >= strLen) {
            return ""
        }
        // If start is negative, substr() uses it as a character index from the
        // end of the string; the index of the last character is -1.
        start = strData.offsetByCodePoints(
            0,
            (if (start >= 0) start else (if ((strLen + start) < 0) 0 else strLen + start))
        )
        if (start < 0) {
            start = 0
        } // If start is negative and abs(start) is larger than the length of the

        // string, substr() uses 0 as the start index.
        // If length is omitted, substr() extracts characters to the end of the
        // string.
        if (length == null) {
            length = strData.length
        } else if (length < 0) {
            // If length is 0 or negative, substr() returns an empty string.
            return ""
        } else if (length > strData.length) {
            length = strData.length
        }

        length = strData.offsetByCodePoints(0, length)

        if (start >= 0) {
            // If start is positive and is greater than or equal to the length of
            // the string, substr() returns an empty string.
            if (start >= origLen) {
                return ""
            }
        }

        // collect length characters (unless it reaches the end of the string
        // first, in which case it will return fewer)
        var end = start + length
        if (end > origLen) {
            end = origLen
        }

        return strData.substring(start, end)
    }

    /**
     * Create substring up until a character
     * @param {String} str - String to evaluate
     * @param {String} chars - Character to define substring boundary
     * @returns {*} Substring
     */
    @JvmStatic
    fun substringBefore(str: String?, chars: String?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        if (chars == null) return str

        val pos = str.indexOf(chars)
        return if (pos > -1) {
            str.substring(0, pos)
        } else {
            str
        }
    }

    /**
     * Create substring after a character
     * @param {String} str - String to evaluate
     * @param {String} chars - Character to define substring boundary
     * @returns {*} Substring
     */
    @JvmStatic
    fun substringAfter(str: String?, chars: String?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        val pos = str.indexOf(chars!!)
        return if (pos > -1) {
            str.substring(pos + chars.length)
        } else {
            str
        }
    }

    /**
     * Lowercase a string
     * @param {String} str - String to evaluate
     * @returns {string} Lowercase string
     */
    @JvmStatic
    fun lowercase(str: String?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        return str.lowercase(Locale.getDefault())
    }

    /**
     * Uppercase a string
     * @param {String} str - String to evaluate
     * @returns {string} Uppercase string
     */
    @JvmStatic
    fun uppercase(str: String?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        return str.uppercase(Locale.getDefault())
    }

    /**
     * length of a string
     * @param {String} str - string
     * @returns {Number} The number of characters in the string
     */
    @JvmStatic
    fun length(str: String?): Int? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        return str.codePointCount(0, str.length)
    }

    /**
     * Normalize and trim whitespace within a string
     * @param {string} str - string to be trimmed
     * @returns {string} - trimmed string
     */
    @JvmStatic
    fun trim(str: String?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        if (str.isEmpty()) {
            return ""
        }


        // normalize whitespace
        var result = str.replace("[ \t\n\r]+".toRegex(), " ")
        if (result[0] == ' ') {
            // strip leading space
            result = result.substring(1)
        }

        if (result.isEmpty()) {
            return ""
        }

        if (result[result.length - 1] == ' ') {
            // strip trailing space
            result = result.substring(0, result.length - 1)
        }
        return result
    }

    /**
     * Pad a string to a minimum width by adding characters to the start or end
     * @param {string} str - string to be padded
     * @param {number} width - the minimum width; +ve pads to the right, -ve pads to the left
     * @param {string} [char] - the pad character(s); defaults to ' '
     * @returns {string} - padded string
     */
    @JvmStatic
    fun pad(str: String?, width: Int?, _char: String?): String? {
        // undefined inputs always return undefined
        var _char = _char
        if (str == null) {
            return null
        }

        if (_char == null || _char.isEmpty()) {
            _char = " "
        }

        val result = if (width!! < 0) {
            leftPad(str, -width, _char)
        } else {
            rightPad(str, width, _char)
        }
        return result
    }

    // Source: Jsonata4Java PadFunction
    @JvmStatic
    fun leftPad(str: String?, size: Int?, padStr: String?): String? {
        var padStr = padStr
        if (str == null) {
            return null
        }
        if (padStr == null) {
            padStr = " "
        }

        val strData = Objects.requireNonNull(str).intern()
        val strLen = strData.codePointCount(0, strData.length)

        val padData = Objects.requireNonNull(padStr).intern()
        val padLen = padData.codePointCount(0, padData.length)

        if (padLen == 0) {
            padStr = " "
        }
        val pads = size!! - strLen
        if (pads <= 0) {
            return str
        }
        var padding = ""
        for (i in 0 until pads + 1) {
            padding += padStr
        }
        return substr(padding, 0, pads) + str
    }

    // Source: Jsonata4Java PadFunction
    @JvmStatic
    fun rightPad(str: String?, size: Int?, padStr: String?): String? {
        var padStr = padStr
        if (str == null) {
            return null
        }
        if (padStr == null) {
            padStr = " "
        }

        val strData = Objects.requireNonNull(str).intern()
        val strLen = strData.codePointCount(0, strData.length)

        val padData = Objects.requireNonNull(padStr).intern()
        val padLen = padData.codePointCount(0, padData.length)

        if (padLen == 0) {
            padStr = " "
        }
        val pads = size!! - strLen
        if (pads <= 0) {
            return str
        }
        var padding = ""
        for (i in 0 until pads + 1) {
            padding += padStr
        }
        return str + substr(padding, 0, pads)
    }

    /**
     * Evaluate the matcher function against the str arg
     *
     * @param {*} matcher - matching function (native or lambda)
     * @param {string} str - the string to match against
     * @returns {object} - structure that represents the match(es)
     */
    @JvmStatic
    fun evaluateMatcher(matcher: Pattern?, str: String?): List<RegexpMatch> {
        val res: MutableList<RegexpMatch> = ArrayList()
        val m = matcher!!.matcher(str)
        while (m.find()) {
            val groups: MutableList<String> = ArrayList()

            // Collect the groups
            for (g in 1..m.groupCount()) groups.add(m.group(g))

            val rm = RegexpMatch(m.group(), m.start(), groups)
            res.add(rm)
        }
        return res
    }

    /**
     * Tests if the str contains the token
     * @param {String} str - string to test
     * @param {String} token - substring or regex to find
     * @returns {Boolean} - true if str contains token
     */
    @JvmStatic
    fun contains(str: String?, token: Any?): Boolean? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        val result: Boolean

        if (token is String) {
            result = (str.indexOf(token) != -1)
        } else if (token is Pattern) {
            val matches = evaluateMatcher(token, str)
            //if (dbg) System.out.println("match = "+matches);
            //result = (typeof matches !== 'undefined');
            //throw new Error("regexp not impl"); //result = false;
            result = !matches.isEmpty()
        } else {
            throw Error("unknown type to match: $token")
        }

        return result
    }

    /**
     * Match a string with a regex returning an array of object containing details of each match
     * @param {String} str - string
     * @param {String} regex - the regex applied to the string
     * @param {Integer} [limit] - max number of matches to return
     * @returns {Array} The array of match objects
     */
    @JvmStatic
    fun match(str: String?, regex: Pattern?, limit: Int?): MutableList<Map<*, *>>? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        // limit, if specified, must be a non-negative number
        if (limit != null && limit < 0) {
            throw JException(
                "D3040", -1, limit
            )
        }

        val result = createSequence()
        val matches = evaluateMatcher(regex, str)
        var max = Int.MAX_VALUE
        if (limit != null) max = limit

        for (i in matches.indices) {
            val m: MutableMap<String, Any?> = LinkedHashMap<String, Any?>()
            val rm = matches[i]
            // Convert to JSON map:
            m["match"] = rm.match
            m["index"] = rm.index
            m["groups"] = rm.groups
            result.add(m)
            if (i >= max) break
        }
        return result as MutableList<Map<*, *>>?
    }

    /**
     * Join an array of strings
     * @param {Array} strs - array of string
     * @param {String} [separator] - the token that splits the string
     * @returns {String} The concatenated string
     */
    @JvmStatic
    fun join(strs: List<String?>?, separator: String?): String? {
        // undefined inputs always return undefined
        var separator = separator
        if (strs == null) {
            return null
        }

        // if separator is not specified, default to empty string
        if (separator == null) {
            separator = ""
        }

        return java.lang.String.join(separator, strs)
    }

    private fun safeReplacement(`in`: String): String {
        // In JSONata and in Java the $ in the replacement test usually starts the insertion of a capturing group
        // In order to replace a simple $ in Java you have to escape the $ with "\$"
        // in JSONata you do this with a '$$'
        // "\$" followed any character besides '<' and and digit into $ + this character  
        return `in`.replace("\\$\\$".toRegex(), "\\\\\\$")
            .replace("([^\\\\]|^)\\$([^0-9^<])".toRegex(), "$1\\\\\\$$2")
            .replace("\\$$".toRegex(), "\\\\\\$") // allow $ at end
    }

    /**
     * Safe replaceAll
     *
     * In Java, non-existing groups cause an exception.
     * Ignore these non-existing groups (replace with "")
     *
     * @param s
     * @param pattern
     * @param replacement
     * @return
     */
    private fun safeReplaceAll(s: String?, pattern: Pattern, _replacement: Any?): String? {
        if (_replacement !is String) return safeReplaceAllFn(s, pattern, _replacement)

        var replacement = _replacement as String

        replacement = safeReplacement(replacement)
        val m = pattern.matcher(s)
        var r: String? = null
        for (i in 0..9) {
            try {
                r = m.replaceAll(replacement)
                break
            } catch (e: IndexOutOfBoundsException) {
                val msg = e.message

                // Message we understand needs to be:
                // No group X
                if (!msg!!.contains("No group")) throw e

                // Adjust replacement to remove the non-existing group
                val g = "" + msg[msg.length - 1]

                replacement = replacement.replace("$$g", "")
            }
        }
        return r
    }

    /**
     * Converts Java MatchResult to the Jsonata object format
     * @param mr
     * @return
     */
    private fun toJsonataMatch(mr: MatchResult): Map<*, *> {
        val obj: MutableMap<String, Any?> = LinkedHashMap<String, Any?>()
        obj["match"] = mr.group()

        val groups: MutableList<Any> = ArrayList<Any>()
        for (i in 0..mr.groupCount()) groups.add(mr.group(i))

        obj["groups"] = groups

        return obj
    }

    /**
     * Regexp Replace with replacer function
     * @param s
     * @param pattern
     * @param fn
     * @return
     */
    private fun safeReplaceAllFn(s: String?, pattern: Pattern, fn: Any?): String? {
        val m = pattern.matcher(s)
        var r: String? = null
        r = m.replaceAll { t: MatchResult ->
            try {
                val res = funcApply(fn, java.util.List.of(toJsonataMatch(t)))
                if (res is String) return@replaceAll res
                else return@replaceAll null
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            null
        }
        return r
    }

    /**
     * Safe replaceFirst
     *
     * @param s
     * @param pattern
     * @param replacement
     * @return
     */
    private fun safeReplaceFirst(s: String?, pattern: Pattern, replacement: String): String? {
        var replacement = replacement
        replacement = safeReplacement(replacement)
        val m = pattern.matcher(s)
        var r: String? = null
        for (i in 0..9) {
            try {
                r = m.replaceFirst(replacement)
                break
            } catch (e: IndexOutOfBoundsException) {
                val msg = e.message

                // Message we understand needs to be:
                // No group X
                if (!msg!!.contains("No group")) throw e

                // Adjust replacement to remove the non-existing group
                val g = "" + msg[msg.length - 1]

                replacement = replacement.replace("$$g", "")
            }
        }
        return r
    }

    @JvmStatic
    fun replace(str: String?, pattern: Any?, replacement: Any?, limit: Int?): String? {
        var str = str ?: return null
        if (pattern is String) if (pattern.isEmpty()) throw JException(
            "Second argument of replace function cannot be an empty string",
            0
        )
        if (limit == null) {
            return if (pattern is String) {
                str.replace(pattern, (replacement as String))
            } else {
                safeReplaceAll(str, pattern as Pattern, replacement)
            }
        } else {
            if (limit < 0) throw JException("Fourth argument of replace function must evaluate to a positive number", 0)

            for (i in 0 until limit) if (pattern is String) {
                str = str.replaceFirst(pattern.toRegex(), (replacement as String))
            } else {
                str = safeReplaceFirst(str, pattern as Pattern, replacement as String)!!
            }
            return str
        }
    }


    /**
     * Base64 encode a string
     * @param {String} str - string
     * @returns {String} Base 64 encoding of the binary data
     */
    @JvmStatic
    fun base64encode(str: String?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }
        try {
            return Base64.getEncoder().encodeToString(str.toByteArray(charset("utf-8")))
        } catch (e: UnsupportedEncodingException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
            return null
        }
    }

    /**
     * Base64 decode a string
     * @param {String} str - string
     * @returns {String} Base 64 encoding of the binary data
     */
    @JvmStatic
    fun base64decode(str: String?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }
        try {
            return String(Base64.getDecoder().decode(str), charset("utf-8"))
        } catch (e: UnsupportedEncodingException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
            return null
        }
    }

    /**
     * Encode a string into a component for a url
     * @param {String} str - String to encode
     * @returns {string} Encoded string
     */
    @JvmStatic
    fun encodeUrlComponent(str: String?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        checkUrl(str)

        return URLEncoder.encode(str, StandardCharsets.UTF_8)
            .replace("\\+".toRegex(), "%20")
            .replace("\\%21".toRegex(), "!")
            .replace("\\%27".toRegex(), "'")
            .replace("\\%28".toRegex(), "(")
            .replace("\\%29".toRegex(), ")")
            .replace("\\%7E".toRegex(), "~")
    }

    /**
     * Encode a string into a url
     * @param {String} str - String to encode
     * @returns {string} Encoded string
     */
    @JvmStatic
    fun encodeUrl(str: String?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        checkUrl(str)

        try {
            // only encode query part: https://docs.jsonata.org/string-functions#encodeurl
            val url = URL(str)
            val query = url.query
            if (query != null) {
                val offset = str.indexOf(query)
                val strResult = str.substring(0, offset)
                return strResult + encodeURI(query)
            }
        } catch (e: Exception) {
            // ignore and return default
        }
        return URLEncoder.encode(str, StandardCharsets.UTF_8)
    }

    private fun encodeURI(uri: String?): String? {
        var result: String? = null
        if (uri != null) {
            try {
                // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/encodeURI
                // Not encoded: A-Z a-z 0-9 ; , / ? : @ & = + $ - _ . ! ~ * ' ( ) #
                result = URLEncoder.encode(uri, "UTF-8").replace("\\+".toRegex(), "%20")
                    .replace("%20".toRegex(), " ").replace("\\%21".toRegex(), "!")
                    .replace("\\%23".toRegex(), "#").replace("\\%24".toRegex(), "$")
                    .replace("\\%26".toRegex(), "&").replace("\\%27".toRegex(), "'")
                    .replace("\\%28".toRegex(), "(").replace("\\%29".toRegex(), ")")
                    .replace("\\%2A".toRegex(), "*").replace("\\%2B".toRegex(), "+")
                    .replace("\\%2C".toRegex(), ",").replace("\\%2D".toRegex(), "-")
                    .replace("\\%2E".toRegex(), ".").replace("\\%2F".toRegex(), "/")
                    .replace("\\%3A".toRegex(), ":").replace("\\%3B".toRegex(), ";")
                    .replace("\\%3D".toRegex(), "=").replace("\\%3F".toRegex(), "?")
                    .replace("\\%40".toRegex(), "@").replace("\\%5F".toRegex(), "_")
                    .replace("\\%7E".toRegex(), "~")
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
        }
        return result
    }

    /**
     * Decode a string from a component for a url
     * @param {String} str - String to decode
     * @returns {string} Decoded string
     */
    @JvmStatic
    fun decodeUrlComponent(str: String?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        return URLDecoder.decode(str, StandardCharsets.UTF_8)
    }

    /**
     * Decode a string from a url
     * @param {String} str - String to decode
     * @returns {string} Decoded string
     */
    @JvmStatic
    fun decodeUrl(str: String?): String? {
        // undefined inputs always return undefined
        if (str == null) {
            return null
        }

        return URLDecoder.decode(str, StandardCharsets.UTF_8)
    }

    @JvmStatic
    fun split(str: String?, pattern: Any?, limit: Number?): List<String?>? {
        if (str == null) return null

        if (limit != null && limit.toInt() < 0) throw JException("D3020", -1, str)

        var result: MutableList<String?> = ArrayList()
        if (limit != null && limit.toInt() == 0) return result

        if (pattern is String) {
            val sep = pattern
            if (sep.isEmpty()) {
                // $split("str", ""): Split string into characters
                val l = limit?.toInt() ?: Int.MAX_VALUE
                var i = 0
                while (i < str.length && i < l) {
                    result.add("" + str[i])
                    i++
                }
            } else {
                // Quote separator string + preserve trailing empty strings (-1)
                result = Arrays.asList(*str.split(Pattern.quote(sep).toRegex()).toTypedArray())
            }
        } else {
            result = Arrays.asList(*(pattern as Pattern).split(str, -1))
        }
        if (limit != null && limit.toInt() < result.size) {
            result = result.subList(0, limit.toInt())
        }
        return result
    }

    /**
     * Formats a number into a decimal string representation using XPath 3.1 F&O fn:format-number spec
     * @param {number} value - number to format
     * @param {String} picture - picture string definition
     * @param {Object} [options] - override locale defaults
     * @returns {String} The formatted string
     */
    @JvmStatic
    fun formatNumber(value: Number?, picture: String?, options: Map<*, *>?): String? {
        // undefined inputs always return undefined
        if (value == null) {
            return null
        }

        if (picture != null) {
            if (picture.contains(",,")) throw RuntimeException("The sub-picture must not contain two adjacent instances of the 'grouping-separator' character")
            if (picture.indexOf('%') >= 0) if (picture.indexOf('e') >= 0) throw RuntimeException("A sub-picture that contains a 'percent' or 'per-mille' character must not contain a character treated as an 'exponent-separator")
        }

        val symbols = if (options == null) DecimalFormatSymbols(Locale.US) else processOptionsArg(options)

        // Create the formatter and format the number
        val formatter = DecimalFormat()
        formatter.decimalFormatSymbols = symbols
        var fixedPicture = picture //picture.replaceAll("9", "0");
        var c = '1'
        while (c <= '9') {
            fixedPicture = fixedPicture!!.replace(c, '0')
            c++
        }

        var littleE = false
        if (fixedPicture!!.contains("e")) {
            fixedPicture = fixedPicture.replace("e", "E")
            littleE = true
        }
        //System.out.println("picture "+fixedPicture);
        formatter.applyLocalizedPattern(fixedPicture)
        var result = formatter.format(value)

        if (littleE) result = result.replace("E", "e")


        return result
    }

    // From JSONata4Java FormatNumberFunction
    private fun processOptionsArg(argOptions: Map<*, *>): DecimalFormatSymbols {
        // Create the variable return
        val symbols =
            DecimalFormatSymbols(Locale.US) // (DecimalFormatSymbols) Constants.DEFAULT_DECIMAL_FORMAT_SYMBOLS.clone();

        // Iterate over the formatting character overrides
        val fieldNames: Iterator<*> = argOptions.keys.iterator()
        while (fieldNames.hasNext()) {
            val fieldName = fieldNames.next()
            val valueNode = argOptions[fieldName] as String?
            when (fieldName) {
                Constants.SYMBOL_DECIMAL_SEPARATOR -> {
                    val value = getFormattingCharacter(valueNode, Constants.SYMBOL_DECIMAL_SEPARATOR, true)
                    symbols.decimalSeparator = value[0]
                }

                Constants.SYMBOL_GROUPING_SEPARATOR -> {
                    val value = getFormattingCharacter(valueNode, Constants.SYMBOL_GROUPING_SEPARATOR, true)
                    symbols.groupingSeparator = value[0]
                }

                Constants.SYMBOL_INFINITY -> {
                    val value = getFormattingCharacter(valueNode, Constants.SYMBOL_INFINITY, false)
                    symbols.infinity = value
                }

                Constants.SYMBOL_MINUS_SIGN -> {
                    val value = getFormattingCharacter(valueNode, Constants.SYMBOL_MINUS_SIGN, true)
                    symbols.minusSign = value[0]
                }

                Constants.SYMBOL_NAN -> {
                    val value = getFormattingCharacter(valueNode, Constants.SYMBOL_NAN, false)
                    symbols.naN = value
                }

                Constants.SYMBOL_PERCENT -> {
                    val value = getFormattingCharacter(valueNode, Constants.SYMBOL_PERCENT, true)
                    symbols.percent = value[0]
                }

                Constants.SYMBOL_PER_MILLE -> {
                    val value = getFormattingCharacter(valueNode, Constants.SYMBOL_PER_MILLE, false)
                    symbols.perMill = value[0]
                }

                Constants.SYMBOL_ZERO_DIGIT -> {
                    val value = getFormattingCharacter(valueNode, Constants.SYMBOL_ZERO_DIGIT, true)
                    symbols.zeroDigit = value[0]
                }

                Constants.SYMBOL_DIGIT -> {
                    val value = getFormattingCharacter(valueNode, Constants.SYMBOL_DIGIT, true)
                    symbols.digit = value[0]
                }

                Constants.SYMBOL_PATTERN_SEPARATOR -> {
                    val value = getFormattingCharacter(valueNode, Constants.SYMBOL_PATTERN_SEPARATOR, true)
                    symbols.patternSeparator = value[0]
                }

                else -> {
                    //final String msg = String.format(Constants.ERR_MSG_INVALID_OPTIONS_UNKNOWN_PROPERTY,
                    //    Constants.FUNCTION_FORMAT_NUMBER, fieldName);
                    throw RuntimeException("Error parsing formatNumber format string")
                }
            }
        } // WHILE


        return symbols
    }

    // From JSONata4Java FormatNumberFunction
    private fun getFormattingCharacter(value: String?, propertyName: String, isChar: Boolean): String {
        // Create the variable to return
        var formattingChar: String? = null

        // Make sure that we have a valid node and that its content is textual
        //if (valueNode != null && valueNode.isTextual()) {
        // Read the value
        //String value = valueNode.textValue();
        if (value != null && !value.isEmpty()) {
            // If the target property requires a single char, check the length

            formattingChar = if (isChar) {
                if (value.length == 1) {
                    value
                } else {
                    //final String msg = String.format(Constants.ERR_MSG_INVALID_OPTIONS_SINGLE_CHAR,
                    //    Constants.FUNCTION_FORMAT_NUMBER, propertyName);
                    throw RuntimeException()
                }
            } else {
                value
            }
        } else {
            val msgTemplate = if (isChar) {
                Constants.ERR_MSG_INVALID_OPTIONS_SINGLE_CHAR
            } else {
                Constants.ERR_MSG_INVALID_OPTIONS_STRING
            }
            //final String msg = String.format(msgTemplate, Constants.FUNCTION_FORMAT_NUMBER, propertyName);
            throw RuntimeException(msgTemplate)
        }

        //} 
        return formattingChar
    }

    /**
     * Converts a number to a string using a specified number base
     * @param {number} value - the number to convert
     * @param {number} [radix] - the number base; must be between 2 and 36. Defaults to 10
     * @returns {string} - the converted string
     */
    @JvmStatic
    fun formatBase(value: Number?, _radix: Number?): String? {
        // undefined inputs always return undefined
        var value: Number? = value ?: return null

        value = round(value, 0)
        val radix = _radix?.toInt() ?: 10

        if (radix < 2 || radix > 36) {
            throw JException(
                "D3100",  //stack: (new Error()).stack,
                radix
            )
        }

        val result = value!!.toLong().toString(radix.coerceIn(2, 36))

        return result
    }

    /**
     * Cast argument to number
     * @param {Object} arg - Argument
     * @throws NumberFormatException
     * @returns {Number} numeric value of argument
     */
    @Throws(NumberFormatException::class, JException::class)
    @JvmStatic
    fun number(arg: Any?): Number? {
        var result: Number? = null

        // undefined inputs always return undefined
        if (arg == null) {
            return null
        }

        if (arg === Jsonata.NULL_VALUE) throw JException("T0410", -1)

        if (arg is Number) result = arg
        else if (arg is String) {
            val s = arg
            result = if (s.startsWith("0x")) s.substring(2).toLong(16)
            else if (s.startsWith("0B")) s.substring(2).toLong(2)
            else if (s.startsWith("0O")) s.substring(2).toLong(8)
            else arg.toDouble()
        } else if (arg is Boolean) {
            result = if (arg) 1 else 0
        }
        return result
    }

    /**
     * Absolute value of a number
     * @param {Number} arg - Argument
     * @returns {Number} absolute value of argument
     */
    @JvmStatic
    fun abs(arg: Number?): Number? {
        // undefined inputs always return undefined

        if (arg == null) {
            return null
        }

        return if (arg is Double) kotlin.math.abs(arg.toDouble()) else kotlin.math.abs(arg.toLong().toDouble())
    }

    /**
     * Rounds a number down to integer
     * @param {Number} arg - Argument
     * @returns {Number} rounded integer
     */
    @JvmStatic
    fun floor(arg: Number?): Number? {
        // undefined inputs always return undefined

        if (arg == null) {
            return null
        }

        return kotlin.math.floor(arg.toDouble())
    }

    /**
     * Rounds a number up to integer
     * @param {Number} arg - Argument
     * @returns {Number} rounded integer
     */
    @JvmStatic
    fun ceil(arg: Number?): Number? {
        // undefined inputs always return undefined

        if (arg == null) {
            return null
        }

        return kotlin.math.ceil(arg.toDouble())
    }

    /**
     * Round to half even
     * @param {Number} arg - Argument
     * @param {Number} [precision] - number of decimal places
     * @returns {Number} rounded integer
     */
    @JvmStatic
    fun round(arg: Number?, precision: Number?): Number? {
        // undefined inputs always return undefined

        var precision = precision
        if (arg == null) {
            return null
        }

        var b = BigDecimal(arg.toString() + "")
        if (precision == null) precision = 0
        b = b.setScale(precision.toInt(), RoundingMode.HALF_EVEN)

        return b.toDouble()
    }

    /**
     * Square root of number
     * @param {Number} arg - Argument
     * @returns {Number} square root
     */
    @JvmStatic
    fun sqrt(arg: Number?): Number? {
        // undefined inputs always return undefined

        if (arg == null) {
            return null
        }

        if (arg.toDouble() < 0) {
            throw JException(
                "D3060",
                1,
                arg
            )
        }

        return kotlin.math.sqrt(arg.toDouble())
    }

    /**
     * Raises number to the power of the second number
     * @param {Number} arg - the base
     * @param {Number} exp - the exponent
     * @returns {Number} rounded integer
     */
    @JvmStatic
    fun power(arg: Number?, exp: Number?): Number? {
        // undefined inputs always return undefined

        if (arg == null) {
            return null
        }

        val result: Double = arg.toDouble().pow(exp!!.toDouble())

        if (!java.lang.Double.isFinite(result)) {
            throw JException(
                "D3061",
                1,
                arg,
                exp
            )
        }

        return result
    }

    /**
     * Returns a random number 0 <= n < 1
     * @returns {number} random number
     */
    @JvmStatic
    fun random(): Number {
        return Math.random()
    }

    /**
     * Evaluate an input and return a boolean
     * @param {*} arg - Arguments
     * @returns {boolean} Boolean
     */
    @JvmStatic
    fun toBoolean(arg: Any?): Boolean? {
        // cast arg to its effective boolean value
        // boolean: unchanged
        // string: zero-length -> false; otherwise -> true
        // number: 0 -> false; otherwise -> true
        // null -> false
        // array: empty -> false; length > 1 -> true
        // object: empty -> false; non-empty -> true
        // function -> false

        // undefined inputs always return undefined

        if (arg == null) {
            return null // Uli: Null would need to be handled as false anyway
        }

        var result = false
        if (arg is List<*>) {
            val l = arg
            if (l.size == 1) {
                result = toBoolean(l[0])!!
            } else if (l.size > 1) {
                val truesLength = l.stream().filter { e: Any? -> Jsonata.boolize(e) }.count()
                result = truesLength > 0
            }
        } else if (arg is String) {
            if (arg.length > 0) {
                result = true
            }
        } else if (arg is Number) { //isNumeric(arg)) {
            if (arg.toDouble() != 0.0) {
                result = true
            }
        } else if (arg is Map<*, *>) {
            if (!arg.isEmpty()) result = true
        } else if (arg is Boolean) {
            result = arg
        }
        return result
    }

    /**
     * returns the Boolean NOT of the arg
     * @param {*} arg - argument
     * @returns {boolean} - NOT arg
     */
    @JvmStatic
    fun not(arg: Any?): Boolean? {
        // undefined inputs always return undefined
        if (arg == null) {
            return null
        }

        return !toBoolean(arg)!!
    }


    @JvmStatic
    fun getFunctionArity(func: Any?): Int {
        return if (func is JFunction) {
            func.signature!!.minNumberOfArgs
        } else {
            // Lambda
            (func as Parser.Symbol).arguments!!.size
        }
    }

    /**
     * Helper function to build the arguments to be supplied to the function arg of the
     * HOFs map, filter, each, sift and single
     * @param {function} func - the function to be invoked
     * @param {*} arg1 - the first (required) arg - the value
     * @param {*} arg2 - the second (optional) arg - the position (index or key)
     * @param {*} arg3 - the third (optional) arg - the whole structure (array or object)
     * @returns {*[]} the argument list
     */
    @JvmStatic
    fun hofFuncArgs(func: Any?, arg1: Any?, arg2: Any?, arg3: Any?): List<*> {
        val func_args: MutableList<Any?> = ArrayList<Any?>()
        func_args.add(arg1) // the first arg (the value) is required
        // the other two are optional - only supply it if the function can take it
        val length = getFunctionArity(func)
        if (length >= 2) {
            func_args.add(arg2)
        }
        if (length >= 3) {
            func_args.add(arg3)
        }
        return func_args
    }

    /**
     * Call helper for Java
     *
     * @param func
     * @param funcArgs
     * @return
     * @throws Throwable
     */
    @Throws(Throwable::class)
    @JvmStatic
    fun funcApply(func: Any?, funcArgs: List<*>?): Any? {
        val res = if (isLambda(func)) Jsonata.current.get()
            .apply(func, funcArgs, null, Jsonata.current.get().environment)
        else (func as JFunction).call(null, funcArgs)
        return res
    }

    /**
     * Create a map from an array of arguments
     * @param {Array} [arr] - array to map over
     * @param {Function} func - function to apply
     * @returns {Array} Map array
     */
    @Throws(Throwable::class)
    @JvmStatic
    fun map(arr: List<*>?, func: Any?): List<*>? {
        // undefined inputs always return undefined

        if (arr == null) {
            return null
        }

        val result = createSequence()
        // do the map - iterate over the arrays, and invoke func
        for (i in arr.indices) {
            val arg = arr[i]!!
            val funcArgs = hofFuncArgs(func, arg, i, arr)

            val res = funcApply(func, funcArgs)
            if (res != null) result.add(res)
        }
        return result
    }

    /**
     * Create a map from an array of arguments
     * @param {Array} [arr] - array to filter
     * @param {Function} func - predicate function
     * @returns {Array} Map array
     */
    @Throws(Throwable::class)
    @JvmStatic
    fun filter(arr: List<*>?, func: Any?): List<*>? {
        // undefined inputs always return undefined
        if (arr == null) {
            return null
        }

        val result = createSequence()

        for (i in arr.indices) {
            val entry = arr[i]!!
            val func_args = hofFuncArgs(func, entry, i, arr)
            // invoke func
            val res = funcApply(func, func_args)
            if (toBoolean(res)!!) {
                result.add(entry)
            }
        }

        return result
    }

    /**
     * Given an array, find the single element matching a specified condition
     * Throws an exception if the number of matching elements is not exactly one
     * @param {Array} [arr] - array to filter
     * @param {Function} [func] - predicate function
     * @returns {*} Matching element
     */
    @Throws(Throwable::class)
    @JvmStatic
    fun single(arr: List<*>?, func: Any?): Any? {
        // undefined inputs always return undefined
        if (arr == null) {
            return null
        }

        var hasFoundMatch = false
        var result: Any? = null

        for (i in arr.indices) {
            val entry = arr[i]!!
            var positiveResult = true
            if (func != null) {
                val func_args = hofFuncArgs(func, entry, i, arr)
                // invoke func
                val res = funcApply(func, func_args)
                positiveResult = toBoolean(res)!!
            }
            if (positiveResult) {
                if (!hasFoundMatch) {
                    result = entry
                    hasFoundMatch = true
                } else {
                    throw JException(
                        "D3138",
                        i
                    )
                }
            }
        }

        if (!hasFoundMatch) {
            throw JException(
                "D3139", -1
            )
        }

        return result
    }

    /**
     * Convolves (zips) each value from a set of arrays
     * @param {Array} [args] - arrays to zip
     * @returns {Array} Zipped array
     */
    @JvmStatic
    fun zip(args: Utils.JList<List<*>?>): List<*> {
        val result = ArrayList<Any>()
        // length of the shortest array
        var length = Int.MAX_VALUE
        var nargs = 0
        // nargs : the real size of args!=null
        while (nargs < args.size) {
            if (args[nargs] == null) {
                length = 0
                break
            }

            length = kotlin.math.min(length.toDouble(), args[nargs]!!.size.toDouble()).toInt()
            nargs++
        }

        for (i in 0 until length) {
            val tuple: MutableList<Any?> = ArrayList<Any?>()
            for (k in 0 until nargs) tuple.add(args[k]!![i])
            result.add(tuple)
        }
        return result
    }

    /**
     * Fold left function
     * @param {Array} sequence - Sequence
     * @param {Function} func - Function
     * @param {Object} init - Initial value
     * @returns {*} Result
     */
    @Throws(Throwable::class)
    @JvmStatic
    fun foldLeft(sequence: List<*>?, func: Any?, init: Any?): Any? {
        // undefined inputs always return undefined
        if (sequence == null) {
            return null
        }
        var result: Any? = null

        val arity = getFunctionArity(func)
        if (arity < 2) {
            throw JException(
                "D3050",
                1
            )
        }

        var index: Int
        if (init == null && sequence.size > 0) {
            result = sequence[0]
            index = 1
        } else {
            result = init
            index = 0
        }

        while (index < sequence.size) {
            val args: MutableList<Any?> = ArrayList<Any?>()
            args.add(result)
            args.add(sequence[index])
            if (arity >= 3) {
                args.add(index)
            }
            if (arity >= 4) {
                args.add(sequence)
            }
            result = funcApply(func, args)
            index++
        }

        return result
    }

    /**
     * Return keys for an object
     * @param {Object} arg - Object
     * @returns {Array} Array of keys
     */
    @JvmStatic
    fun keys(arg: Any?): List<Any> {
        val result = createSequence()

        if (arg is List<*>) {
            val keys: MutableSet<Any> = LinkedHashSet<Any>()
            // merge the keys of all of the items in the array
            for (el in arg) {
                keys.addAll(keys(el))
            }

            result.addAll(keys)
        } else if (arg is Map<*, *>) {
            result.addAll(arg.keys as Collection<Any>)
        }
        return result as List<Any>
    }

    // here: append, lookup
    /**
     * Determines if the argument is undefined
     * @param {*} arg - argument
     * @returns {boolean} False if argument undefined, otherwise true
     */
    @JvmStatic
    fun exists(arg: Any?): Boolean {
        return if (arg == null) {
            false
        } else {
            true
        }
    }

    /**
     * Splits an object into an array of object with one property each
     * @param {*} arg - the object to split
     * @returns {*} - the array
     */
    @JvmStatic
    fun spread(arg: Any?): Any? {
        var result: Any? = createSequence()

        if (arg is List<*>) {
            // spread all of the items in the array
            for (item in arg) result = append(result, spread(item))
        } else if (arg is Map<*, *>) {
            for ((key, value) in (arg as Map<Any?, Any?>)) {
                val obj = LinkedHashMap<Any, Any>()
                obj[key!!] = value!!
                (result as MutableList<Any>?)!!.add(obj)
            }
        } else {
            return arg // result = arg;
        }
        return result
    }

    /**
     * Merges an array of objects into a single object.  Duplicate properties are
     * overridden by entries later in the array
     * @param {*} arg - the objects to merge
     * @returns {*} - the object
     */
    @JvmStatic
    fun merge(arg: List<*>?): Any? {
        // undefined inputs always return undefined
        if (arg == null) {
            return null
        }

        val result = LinkedHashMap<Any, Any>()

        for (obj in arg) {
            for ((key, value) in (obj as Map<Any?, Any?>)) {
                result[key!!] = value!!
            }
        }
        return result
    }

    /**
     * Reverses the order of items in an array
     * @param {Array} arr - the array to reverse
     * @returns {Array} - the reversed array
     */
    @JvmStatic
    fun reverse(arr: List<*>?): List<*>? {
        // undefined inputs always return undefined
        if (arr == null) {
            return null
        }

        if (arr.size <= 1) {
            return arr
        }

        val result: ArrayList<*> = ArrayList(arr)
        Collections.reverse(result)
        return result
    }

    /**
     *
     * @param {*} obj - the input object to iterate over
     * @param {*} func - the function to apply to each key/value pair
     * @throws Throwable
     * @returns {Array} - the resultant array
     */
    @Throws(Throwable::class)
    @JvmStatic
    fun each(obj: Map<*, *>?, func: Any?): List<*>? {
        if (obj == null) {
            return null
        }

        val result = createSequence()

        for (key in obj.keys) {
            val func_args = hofFuncArgs(func, obj[key], key, obj)
            // invoke func
            val `val` = funcApply(func, func_args)
            if (`val` != null) {
                result.add(`val`)
            }
        }

        return result
    }

    /**
     *
     * @param {string} [message] - the message to attach to the error
     * @throws custom error with code 'D3137'
     */
    @Throws(Throwable::class)
    @JvmStatic
    fun error(message: String?) {
        throw JException(
            "D3137", -1,
            message ?: "\$error() function evaluated"
        )
    }

    /**
     *
     * @param {boolean} condition - the condition to evaluate
     * @param {string} [message] - the message to attach to the error
     * @throws custom error with code 'D3137'
     * @returns {undefined}
     */
    @Throws(Throwable::class)
    @JvmStatic
    fun assertFn(condition: Boolean?, message: String?) {
        if (condition == false) {
            throw JException("D3141", -1, "\$assert() statement failed")
            //                message: message || "$assert() statement failed"
        }
    }

    /**
     *
     * @param {*} [value] - the input to which the type will be checked
     * @returns {string} - the type of the input
     */
    @JvmStatic
    fun type(value: Any?): String? {
        if (value == null) {
            return null
        }

        if (value === Jsonata.NULL_VALUE) {
            return "null"
        }

        if (value is Number) {
            return "number"
        }

        if (value is String) {
            return "string"
        }

        if (value is Boolean) {
            return "boolean"
        }

        if (value is List<*>) {
            return "array"
        }

        if (isFunction(value) || isLambda(value)) {
            return "function"
        }

        return "object"
    }

    /**
     * Implements the merge sort (stable) with optional comparator function
     *
     * @param {Array} arr - the array to sort
     * @param {*} comparator - comparator function
     * @returns {Array} - sorted array
     */
    @JvmStatic
    fun sort(arr: List<*>?, comparator: Any?): List<*>? {
        // undefined inputs always return undefined
        if (arr == null) {
            return null
        }

        if (arr.size <= 1) {
            return arr
        }

        val result: MutableList<Any> = ArrayList(arr)

        if (comparator != null) {
            val comp: Comparator<Any> = object : Comparator<Any> {
                override fun compare(o1: Any, o2: Any): Int {
                    try {
                        val swap = funcApply(comparator, Arrays.asList(o1, o2)) as Boolean
                        return if (swap) 1
                        else -1
                    } catch (e: Throwable) {
                        // TODO Auto-generated catch block
                        //e.printStackTrace();
                        throw RuntimeException(e)
                    }
                }
            }
            if (comparator is Comparator<*>) result.sortWith(comparator as Comparator<Any>)
            else result.sortWith(comp)
        } else {
            result.sortWith(Comparator { o1: Any, o2: Any -> (o1 as Comparable<Any>).compareTo(o2) })
        }

        return result
    }

    /**
     * Randomly shuffles the contents of an array
     * @param {Array} arr - the input array
     * @returns {Array} the shuffled array
     */
    @JvmStatic
    fun shuffle(arr: List<*>?): List<*>? {
        // undefined inputs always return undefined
        if (arr == null) {
            return null
        }

        if (arr.size <= 1) {
            return arr
        }

        val result: MutableList<*> = ArrayList(arr)
        Collections.shuffle(result)
        return result
    }

    /**
     * Returns the values that appear in a sequence, with duplicates eliminated.
     * @param {Array} arr - An array or sequence of values
     * @returns {Array} - sequence of distinct values
     */
    @JvmStatic
    fun distinct(_arr: Any?): Any? {
        // undefined inputs always return undefined
        if (_arr == null) {
            return null
        }

        if (_arr !is List<*> || _arr.size <= 1) {
            return _arr
        }
        val arr = _arr as List<Any>

        val results = if ((arr is Utils.JList<*> /*sequence*/)) createSequence() else ArrayList()

        // Create distinct list of elements by adding all to a set,
        // and then adding the set to the result
        val set: LinkedHashSet<Any> = LinkedHashSet<Any>(arr.size)
        set.addAll(arr)
        results.addAll(set)

        return results
    }

    /**
     * Applies a predicate function to each key/value pair in an object, and returns an object containing
     * only the key/value pairs that passed the predicate
     *
     * @param {object} arg - the object to be sifted
     * @param {object} func - the predicate function (lambda or native)
     * @throws Throwable
     * @returns {object} - sifted object
     */
    @Throws(Throwable::class)
    @JvmStatic
    fun sift(arg: Map<Any?, Any?>?, func: Any?): Any? {
        if (arg == null) {
            return null
        }

        var result: LinkedHashMap<Any?, Any?>? = LinkedHashMap()

        for (item in arg.keys) {
            val entry = arg[item]
            val func_args = hofFuncArgs(func, entry, item, arg)
            // invoke func
            val res = funcApply(func, func_args)
            if (Jsonata.boolize(res)) {
                result!![item] = entry
            }
        }

        // empty objects should be changed to undefined
        if (result!!.isEmpty()) {
            result = null
        }

        return result
    }

    ///////
    ///////
    ///////
    ///////
    /**
     * Append second argument to first
     * @param {Array|Object} arg1 - First argument
     * @param {Array|Object} arg2 - Second argument
     * @returns {*} Appended arguments
     */
    @JvmStatic
    fun append(arg1: Any?, arg2: Any?): Any? {
        // disregard undefined args
        var arg1 = arg1
        var arg2 = arg2
        if (arg1 == null) {
            return arg2
        }
        if (arg2 == null) {
            return arg1
        }

        // if either argument is not an array, make it so
        if (arg1 !is List<*>) {
            arg1 = createSequence(arg1)
        }
        if (arg2 !is List<*>) {
            arg2 = Utils.JList(Arrays.asList(arg2))
        }

        // else
        //     // Arg2 was a list: add it as a list element (don't flatten)
        //     ((List)arg1).add((List)arg2);

        // Shortcut:
        if ((arg1 as List<*>).isEmpty() && (arg2 is RangeList)) return arg2

        arg1 = Utils.JList(arg1 as List<*>?) // create a new copy!
        if (arg2 is Utils.JList<*> && arg2.cons) (arg1 as MutableList<Any>).add(arg2)
        else (arg1 as MutableList<Any>).addAll(arg2 as List<Any>)
        return arg1
    }

    @JvmStatic
    fun isLambda(result: Any?): Boolean {
        return (result is Parser.Symbol && result._jsonata_lambda)
    }

    /**
     * Return value from an object for a given key
     * @param {Object} input - Object/Array
     * @param {String} key - Key in object
     * @returns {*} Value of key in object
     */
    @JvmStatic
    fun lookup(input: Any?, key: String?): Any? {
        // lookup the 'name' item in the input
        var result: Any? = null
        if (input is List<*>) {
            val _input = input
            result = createSequence()
            for (ii in _input.indices) {
                val res = lookup(_input[ii], key)
                if (res != null) {
                    if (res is List<*>) {
                        (result as MutableList<Any>).addAll(res as List<Any>)
                    } else {
                        (result as MutableList<Any>).add(res)
                    }
                }
            }
        } else if (input is Map<*, *>) { // && typeof input === 'object') {
            result = input[key]
            // Detect the case where the value is null:
            if (result == null && input.containsKey(key)) result = Jsonata.NULL_VALUE
        }
        return result
    }

    @JvmStatic
    fun test(a: String?, b: String?): String {
        return a + b
    }

    @JvmStatic
    fun getFunction(clz: Class<*>?, name: String?): Method? {
        val methods = Functions::class.java.methods
        for (m in methods) {
            // if (m.getModifiers() == (Modifier.STATIC | Modifier.PUBLIC) ) {
            //     System.out.println(m.getName());
            //     System.out.println(m.getParameterTypes());
            // }
            if (m.name == name) {
                return m
            }
        }
        return null
    }

    @Throws(Throwable::class)
    @JvmStatic
    fun call(clz: Class<*>?, instance: Any?, name: String?, args: List<Any?>?): Any? {
        return call(instance, getFunction(clz, name), args)
    }

    @Throws(Throwable::class)
    @JvmStatic
    fun call(instance: Any?, m: Method?, args: List<Any?>?): Any? {
        val types = m!!.parameterTypes
        val nargs = m.parameterTypes.size

        var callArgs: MutableList<Any?> = ArrayList(args)
        while (callArgs.size < nargs) {
            // Add default arg null if not enough args were provided
            callArgs.add(null)
        }

        // Special handling of one arg if function requires list:
        // Wrap the single arg (if != null) in a list with one element
        if (nargs > 0 && MutableList::class.java.isAssignableFrom(types[0]) && callArgs[0] !is List<*>) {
            val arg1 = callArgs[0]
            if (arg1 != null) {
                val wrap: MutableList<Any> = ArrayList<Any>()
                wrap.add(arg1)
                callArgs[0] = wrap

                //System.err.println("wrapped "+arg1+" as "+wrap);
            }
        }

        // If the function receives the args as JList:
        // i.e. a varargs fn like zip can use this
        if (nargs == 1 && types[0] == Utils.JList::class.java) {
            val allArgs: Utils.JList<*> = Utils.JList(args)
            callArgs = java.util.List.of(allArgs)
        }

        /*
        System.out.println("**** method " + m)
        for (i in callArgs.indices) {
            if (callArgs[i] == null) {
                System.out.println("**** arg " + i + " null");
            } else {
                System.out.println("**** arg " + i + " " + callArgs[i]!!.javaClass);
            }
        }
        */

        try {
            var res = m.invoke(null, *callArgs.toTypedArray())
            if (res is Number) res = convertNumber(res)!!
            return res
        } catch (e: IllegalAccessException) {
            throw Exception("Access error calling function " + m.name, e)
        } catch (e: IllegalArgumentException) {
            throw Exception("Argument error calling function " + m.name, e)
        } catch (e: InvocationTargetException) {
            //e.printStackTrace();
            throw e.targetException
        }
    }


    //
    // DateTime
    //
    /**
     * Converts an ISO 8601 timestamp to milliseconds since the epoch
     *
     * @param {string} timestamp - the timestamp to be converted
     * @param {string} [picture] - the picture string defining the format of the timestamp (defaults to ISO 8601)
     * @throws ParseException
     * @returns {Number} - milliseconds since the epoch
     */
    @Throws(ParseException::class)
    @JvmStatic
    fun dateTimeToMillis(timestamp: String?, picture: String?): Long? {
        // undefined inputs always return undefined
        var timestamp = timestamp ?: return null

        if (picture == null) {
            if ( /*StringUtils.*/isNumeric(timestamp)) {
                val sdf = SimpleDateFormat("yyyy")
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(timestamp).time
            }
            try {
                val len = timestamp.length
                if (len > 5) if (timestamp[len - 5] == '+' || timestamp[len - 5] == '-') if (Character.isDigit(
                        timestamp[len - 4]
                    ) && Character.isDigit(timestamp[len - 3]) && Character.isDigit(timestamp[len - 2]) && Character.isDigit(
                        timestamp[len - 1]
                    )
                ) timestamp = timestamp.substring(0, len - 2) + ':' + timestamp.substring(len - 2, len)
                return OffsetDateTime.parse(timestamp).toInstant().toEpochMilli()
            } catch (e: RuntimeException) {
                val ldt = LocalDate.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                return ldt.atStartOfDay().atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
            }
        } else {
            return parseDateTime(timestamp, picture)
        }
    }

    // Adapted from: org.apache.commons.lang3.StringUtils
    @JvmStatic
    fun isNumeric(cs: CharSequence?): Boolean {
        if (cs == null || cs.length == 0) {
            return false
        }
        val sz = cs.length
        for (i in 0 until sz) {
            if (!Character.isDigit(cs[i])) {
                return false
            }
        }
        return true
    }

    /**
     * Converts milliseconds since the epoch to an ISO 8601 timestamp
     * @param {Number} millis - milliseconds since the epoch to be converted
     * @param {string} [picture] - the picture string defining the format of the timestamp (defaults to ISO 8601)
     * @param {string} [timezone] - the timezone to format the timestamp in (defaults to UTC)
     * @returns {String} - the formatted timestamp
     */
    @JvmStatic
    fun dateTimeFromMillis(millis: Number?, picture: String?, timezone: String?): String? {
        // undefined inputs always return undefined
        if (millis == null) {
            return null
        }

        return formatDateTime(millis.toLong(), picture, timezone)
    }

    /**
     * Formats an integer as specified by the XPath fn:format-integer function
     * See https://www.w3.org/TR/xpath-functions-31/#func-format-integer
     * @param {number} value - the number to be formatted
     * @param {string} picture - the picture string that specifies the format
     * @returns {string} - the formatted number
     */
    @JvmStatic
    fun formatInteger(value: Number?, picture: String?): String? {
        if (value == null) {
            return null
        }
        return DateTimeUtils.formatInteger(value.toLong(), picture)
    }

    /**
     * parse a string containing an integer as specified by the picture string
     * @param {string} value - the string to parse
     * @param {string} picture - the picture string
     * @throws ParseException
     * @returns {number} - the parsed number
     */
    @Throws(ParseException::class, JException::class)
    @JvmStatic
    fun parseInteger(value: String?, picture: String?): Number? {
        var value = value
        var picture = picture
        if (value == null) {
            return null
        }

        // const formatSpec = analyseIntegerPicture(picture);
        // const matchSpec = generateRegex(formatSpec);
        // //const fullRegex = '^' + matchSpec.regex + '$';
        // //const matcher = new RegExp(fullRegex);
        // // TODO validate input based on the matcher regex
        // const result = matchSpec.parse(value);
        // return result;
        if (picture != null) {
            if (picture == "#") throw ParseException(
                "Formatting or parsing an integer as a sequence starting with \"#\" is not supported by this implementation",
                0
            )
            if (picture.endsWith(";o")) picture = picture.substring(0, picture.length - 2)
            if (picture == "a") return lettersToDecimal(value, 'a')
            if (picture == "A") return lettersToDecimal(value, 'A')
            if (picture == "i") return romanToDecimal(value.uppercase(Locale.getDefault()))
            if (picture == "I") return romanToDecimal(value)
            if (picture == "w") return wordsToLong(value)
            if (picture == "W" || picture == "wW" || picture == "Ww") return wordsToLong(value.lowercase(Locale.getDefault()))
            if (picture.indexOf(':') >= 0) {
                value = value.replace(':', ',')
                picture = picture.replace(':', ',')
            }
        }

        val formatter =
            if (picture != null) DecimalFormat(picture, DecimalFormatSymbols(Locale.US)) else DecimalFormat()
        return formatter.parse(value)
        //throw new RuntimeException("not implemented");
    }

    /**
     * Clones an object
     * @param {Object} arg - object to clone (deep copy)
     * @returns {*} - the cloned object
     */
    @JvmStatic
    fun functionClone(arg: Any?): Any? {
        // undefined inputs always return undefined
        if (arg == null) {
            return null
        }

        val res = parseJson(string(arg, false))
        return res
    }

    /**
     * parses and evaluates the supplied expression
     * @param {string} expr - expression to evaluate
     * @returns {*} - result of evaluating the expression
     */
    @JvmStatic
    fun functionEval(expr: String?, focus: Any?): Any? {
        // undefined inputs always return undefined
        if (expr == null) {
            return null
        }
        var input = Jsonata.current.get().input // =  this.input;
        if (focus != null) {
            input = focus
            // if the input is a JSON array, then wrap it in a singleton sequence so it gets treated as a single input
            if ((input is List<*>) && !isSequence(input)) {
                input = createSequence(input)
                (input as Utils.JList<*>).outerWrapper = true
            }
        }

        val ast: Jsonata
        try {
            ast = Jsonata.jsonata(expr)
        } catch (err: Throwable) {
            // error parsing the expression passed to $eval
            //populateMessage(err);
            throw JException(
                "D3120", -1
            )
        }
        var result: Any? = null
        try {
            result = ast.evaluate(input, Jsonata.current.get().environment)
        } catch (err: Throwable) {
            // error evaluating the expression passed to $eval
            //populateMessage(err);
            throw JException(
                "D3121", -1
            )
        }

        return result
    }

    //  environment.bind("now", defineFunction(function(picture, timezone) {
    //      return datetime.fromMillis(timestamp.getTime(), picture, timezone);
    //  }, "<s?s?:s>"));
    @JvmStatic
    fun now(picture: String?, timezone: String?): String? {
        val t = Jsonata.current.get().timestamp
        return dateTimeFromMillis(t, picture, timezone)
    }

    //  environment.bind("millis", defineFunction(function() {
    //      return timestamp.getTime();
    //  }, "<:n>"));
    @JvmStatic
    fun millis(): Long {
        val t = Jsonata.current.get().timestamp
        return t
    }

    data class RegexpMatch(val match: String?, val index: Int, val groups: List<String>?)
}
