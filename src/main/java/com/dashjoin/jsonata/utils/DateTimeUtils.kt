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
// Derived from original JSONata4Java DateTimeUtils code under this license:
/*
 * (c) Copyright 2018, 2019 IBM Corporation
 * 1 New Orchard Road, 
 * Armonk, New York, 10504-1722
 * United States
 * +1 914 499 1900
 * support: Nathaniel Mills wnm3@us.ibm.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.dashjoin.jsonata.utils

import com.dashjoin.jsonata.Functions
import java.io.Serializable
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset
import java.time.temporal.IsoFields
import java.time.temporal.WeekFields
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

//package com.api.jsonata4java.expressions.utils;

// import org.apache.commons.lang3.ArrayUtils;
// import org.apache.commons.lang3.StringUtils;
// import org.apache.commons.lang3.tuple.ImmutablePair;
// import org.apache.commons.lang3.tuple.Pair;
object DateTimeUtils : Serializable {
    private const val serialVersionUID = 365963860104380193L

    private val few = arrayOf(
        "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    )
    private val ordinals = arrayOf(
        "Zeroth",
        "First",
        "Second",
        "Third",
        "Fourth",
        "Fifth",
        "Sixth",
        "Seventh",
        "Eighth",
        "Ninth",
        "Tenth",
        "Eleventh",
        "Twelfth",
        "Thirteenth",
        "Fourteenth",
        "Fifteenth",
        "Sixteenth",
        "Seventeenth",
        "Eighteenth",
        "Nineteenth"
    )
    private val decades = arrayOf(
        "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety", "Hundred"
    )
    private val magnitudes = arrayOf(
        "Thousand", "Million", "Billion", "Trillion"
    )

    fun numberToWords(value: Long, ordinal: Boolean): String {
        return lookup(value, false, ordinal)
    }

    private fun lookup(num: Long, prev: Boolean, ord: Boolean): String {
        var words = ""
        if (num <= 19) {
            words = (if (prev) " and " else "") + (if (ord) ordinals[num.toInt()] else few[num.toInt()])
        } else if (num < 100) {
            val tens = num.toInt() / 10
            val remainder = num.toInt() % 10
            words = (if (prev) " and " else "") + decades[tens - 2]
            if (remainder > 0) {
                words += "-" + lookup(remainder.toLong(), false, ord)
            } else if (ord) {
                words = words.substring(0, words.length - 1) + "ieth"
            }
        } else if (num < 1000) {
            val hundreds = num.toInt() / 100
            val remainder = num.toInt() % 100
            words = (if (prev) ", " else "") + few[hundreds] + " Hundred"
            if (remainder > 0) {
                words += lookup(remainder.toLong(), true, ord)
            } else if (ord) {
                words += "th"
            }
        } else {
            var mag = floor(log10(num.toDouble()) / 3).toInt()
            if (mag > magnitudes.size) {
                mag = magnitudes.size // the largest word
            }
            val factor = 10.0.pow((mag * 3).toDouble()).toLong()
            val mant = floor((num / factor).toDouble()).toInt()
            val remainder = num - mant * factor
            words = (if (prev) ", " else "") + lookup(mant.toLong(), false, false) + " " + magnitudes[mag - 1]
            if (remainder > 0) {
                words += lookup(remainder, true, ord)
            } else if (ord) {
                words += "th"
            }
        }
        return words
    }

    private val wordValues: MutableMap<String, Int?> = HashMap()

    init {
        for (i in few.indices) {
            wordValues[few[i].lowercase(Locale.getDefault())] = i
        }
        for (i in ordinals.indices) {
            wordValues[ordinals[i].lowercase(Locale.getDefault())] = i
        }
        for (i in decades.indices) {
            val lword = decades[i].lowercase(Locale.getDefault())
            wordValues[lword] = (i + 2) * 10
            wordValues[lword.substring(0, lword.length - 1) + "ieth"] = wordValues[lword]
        }
        wordValues["hundreth"] = 100
        for (i in magnitudes.indices) {
            val lword = magnitudes[i].lowercase(Locale.getDefault())
            val `val` = 10.0.pow(((i + 1) * 3).toDouble()).toInt()
            wordValues[lword] = `val`
            wordValues[lword + "th"] = `val`
        }
    }

    private val wordValuesLong: MutableMap<String, Long?> = HashMap()

    init {
        for (i in few.indices) {
            wordValuesLong[few[i].lowercase(Locale.getDefault())] = i.toLong()
        }
        for (i in ordinals.indices) {
            wordValuesLong[ordinals[i].lowercase(Locale.getDefault())] = i.toLong()
        }
        for (i in decades.indices) {
            val lword = decades[i].lowercase(Locale.getDefault())
            wordValuesLong[lword] = (i + 2).toLong() * 10
            wordValuesLong[lword.substring(0, lword.length - 1) + "ieth"] = wordValuesLong[lword]
        }
        wordValuesLong["hundredth"] = 100L
        wordValuesLong["hundreth"] = 100L
        for (i in magnitudes.indices) {
            val lword = magnitudes[i].lowercase(Locale.getDefault())
            val `val` = 10.0.pow(((i + 1) * 3).toDouble()).toLong()
            wordValuesLong[lword] = `val`
            wordValuesLong[lword + "th"] = `val`
        }
    }

    fun wordsToNumber(text: String): Int {
        val parts = text.split(",\\s|\\sand\\s|[\\s\\-]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val values = arrayOfNulls<Int>(parts.size)
        for (i in parts.indices) {
            values[i] = wordValues[parts[i]]
        }
        val segs = Stack<Int>()
        segs.push(0)
        for (value in values) {
            if (value!! < 100) {
                var top = segs.pop()
                if (top >= 1000) {
                    segs.push(top)
                    top = 0
                }
                segs.push(top + value)
            } else {
                segs.push(segs.pop() * value)
            }
        }
        return segs.stream().mapToInt { i: Int? -> i!! }.sum()
    }

    /**
     * long version of above
     */
    @JvmStatic
    fun wordsToLong(text: String): Long {
        val parts = text.split(",\\s|\\sand\\s|[\\s\\-]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val values = arrayOfNulls<Long>(parts.size)
        for (i in parts.indices) {
            values[i] = wordValuesLong[parts[i]]
        }
        val segs = Stack<Long>()
        segs.push(0L)
        for (value in values) {
            if (value!! < 100) {
                var top = segs.pop()
                if (top >= 1000) {
                    segs.push(top)
                    top = 0L
                }
                segs.push(top + value)
            } else {
                segs.push(segs.pop() * value)
            }
        }
        return segs.stream().mapToLong { i: Long? -> i!! }.sum()
    }

    private val romanNumerals = arrayOf(
        RomanNumeral(1000, "m"),
        RomanNumeral(900, "cm"),
        RomanNumeral(500, "d"),
        RomanNumeral(400, "cd"),
        RomanNumeral(100, "c"),
        RomanNumeral(90, "xc"),
        RomanNumeral(50, "l"),
        RomanNumeral(40, "xl"),
        RomanNumeral(10, "x"),
        RomanNumeral(9, "ix"),
        RomanNumeral(5, "v"),
        RomanNumeral(4, "iv"),
        RomanNumeral(1, "i")
    )

    private val romanValues = createRomanValues()

    private fun createRomanValues(): Map<String, Int> {
        val values: MutableMap<String, Int> = HashMap()
        values["M"] = 1000
        values["D"] = 500
        values["C"] = 100
        values["L"] = 50
        values["X"] = 10
        values["V"] = 5
        values["I"] = 1
        return values
    }

    private fun decimalToRoman(value: Int): String {
        for (i in romanNumerals.indices) {
            val numeral = romanNumerals[i]
            if (value >= numeral.value) {
                return numeral.letters + decimalToRoman(value - numeral.value)
            }
        }
        return ""
    }

    @JvmStatic
    fun romanToDecimal(roman: String): Int {
        var decimal = 0
        var max = 1
        for (i in roman.length - 1 downTo 0) {
            val digit = roman[i].toString()
            val value = romanValues[digit]!!
            if (value < max) {
                decimal -= value
            } else {
                max = value
                decimal += value
            }
        }
        return decimal
    }

    private fun decimalToLetters(value: Int, aChar: String): String {
        var value = value
        val letters = Vector<String>()
        val aCode = aChar[0]
        while (value > 0) {
            letters.insertElementAt(Character.toString(((value - 1) % 26 + aCode.code).toChar()), 0)
            value = (value - 1) / 26
        }
        return letters.stream().reduce("") { a: String, b: String -> a + b }
    }

    @JvmStatic
    fun formatInteger(value: Long, picture: String?): String {
        val format = analyseIntegerPicture(picture)
        return formatInteger(value, format)
    }

    private val suffix123 = createSuffixMap()

    private fun createSuffixMap(): Map<String, String> {
        val suffix: MutableMap<String, String> = HashMap()
        suffix["1"] = "st"
        suffix["2"] = "nd"
        suffix["3"] = "rd"
        return suffix
    }

    private fun formatInteger(value: Long, format: Format?): String {
        var value = value
        var formattedInteger = ""
        val negative = value < 0
        value = abs(value.toDouble()).toLong()
        when (format!!.primary) {
            formats.LETTERS -> formattedInteger =
                decimalToLetters(value.toInt(), if (format.case_type == tcase.UPPER) "A" else "a")

            formats.ROMAN -> {
                formattedInteger = decimalToRoman(value.toInt())
                if (format.case_type == tcase.UPPER) {
                    formattedInteger = formattedInteger.uppercase(Locale.getDefault())
                }
            }

            formats.WORDS -> {
                formattedInteger = numberToWords(value, format.ordinal)
                if (format.case_type == tcase.UPPER) {
                    formattedInteger = formattedInteger.uppercase(Locale.getDefault())
                } else if (format.case_type == tcase.LOWER) {
                    formattedInteger = formattedInteger.lowercase(Locale.getDefault())
                }
            }

            formats.DECIMAL -> {
                formattedInteger = "" + value
                val padLength = format.mandatoryDigits - formattedInteger.length
                if (padLength > 0) {
                    formattedInteger = Functions.leftPad(formattedInteger, format.mandatoryDigits, "0")!!
                }
                if (format.zeroCode != 0x30) {
                    val chars = formattedInteger.toCharArray()
                    var i = 0
                    while (i < chars.size) {
                        chars[i] = (chars[i].code + format.zeroCode - 0x30).toChar()
                        i++
                    }
                    formattedInteger = String(chars)
                }
                if (format.regular) {
                    val n = (formattedInteger.length - 1) / format.groupingSeparators.elementAt(0)!!.position
                    var i = n
                    while (i > 0) {
                        val pos = formattedInteger.length - i * format.groupingSeparators.elementAt(0)!!.position
                        formattedInteger = formattedInteger.substring(
                            0,
                            pos
                        ) + format.groupingSeparators.elementAt(0)!!.character + formattedInteger.substring(pos)
                        i--
                    }
                } else {
                    Collections.reverse(format.groupingSeparators)
                    for (separator in format.groupingSeparators) {
                        val pos = formattedInteger.length - separator!!.position
                        formattedInteger =
                            formattedInteger.substring(0, pos) + separator.character + formattedInteger.substring(pos)
                    }
                }

                if (format.ordinal) {
                    val lastDigit = formattedInteger.substring(formattedInteger.length - 1)
                    var suffix = suffix123[lastDigit]
                    if (suffix == null || (formattedInteger.length > 1 && formattedInteger[formattedInteger.length - 2] == '1')) {
                        suffix = "th"
                    }
                    formattedInteger += suffix
                }
            }

            formats.SEQUENCE -> throw RuntimeException(
                String.format(
                    Constants.ERR_MSG_SEQUENCE_UNSUPPORTED,
                    format.token
                )
            )
        }
        if (negative) {
            formattedInteger = "-$formattedInteger"
        }

        return formattedInteger
    }

    private val decimalGroups = intArrayOf(
        0x30,
        0x0660,
        0x06F0,
        0x07C0,
        0x0966,
        0x09E6,
        0x0A66,
        0x0AE6,
        0x0B66,
        0x0BE6,
        0x0C66,
        0x0CE6,
        0x0D66,
        0x0DE6,
        0x0E50,
        0x0ED0,
        0x0F20,
        0x1040,
        0x1090,
        0x17E0,
        0x1810,
        0x1946,
        0x19D0,
        0x1A80,
        0x1A90,
        0x1B50,
        0x1BB0,
        0x1C40,
        0x1C50,
        0xA620,
        0xA8D0,
        0xA900,
        0xA9D0,
        0xA9F0,
        0xAA50,
        0xABF0,
        0xFF10
    )

    @Suppress("unused")
    private fun analyseIntegerPicture(picture: String?): Format {
        val format = Format()
        val primaryFormat: String?
        val formatModifier: String
        val semicolon = picture!!.lastIndexOf(";")
        if (semicolon == -1) {
            primaryFormat = picture
        } else {
            primaryFormat = picture.substring(0, semicolon)
            formatModifier = picture.substring(semicolon + 1)
            if (formatModifier[0] == 'o') {
                format.ordinal = true
            }
        }

        when (primaryFormat) {
            "A" -> {
                format.case_type = tcase.UPPER
                format.primary = formats.LETTERS
            }

            "a" -> format.primary = formats.LETTERS
            "I" -> {
                format.case_type = tcase.UPPER
                format.primary = formats.ROMAN
            }

            "i" -> format.primary = formats.ROMAN
            "W" -> {
                format.case_type = tcase.UPPER
                format.primary = formats.WORDS
            }

            "Ww" -> {
                format.case_type = tcase.TITLE
                format.primary = formats.WORDS
            }

            "w" -> format.primary = formats.WORDS
            else -> {
                var zeroCode: Int? = null
                var mandatoryDigits = 0
                var optionalDigits = 0
                val groupingSeparators = Vector<GroupingSeparator?>()
                var separatorPosition = 0
                val formatCodepoints = primaryFormat.toCharArray()
                //ArrayUtils.reverse(formatCodepoints);
                var ix = formatCodepoints.size - 1
                while (ix >= 0) {
                    val codePoint = formatCodepoints[ix]
                    var digit = false
                    var i = 0
                    while (i < decimalGroups.size) {
                        val group = decimalGroups[i]
                        if (codePoint.code >= group && codePoint.code <= group + 9) {
                            digit = true
                            mandatoryDigits++
                            separatorPosition++
                            if (zeroCode == null) {
                                zeroCode = group
                            } else if (group != zeroCode) {
                                throw RuntimeException(Constants.ERR_MSG_DIFF_DECIMAL_GROUP)
                            }
                            break
                        }
                        i++
                    }
                    if (!digit) {
                        if (codePoint.code == 0x23) {
                            separatorPosition++
                            optionalDigits++
                        } else {
                            groupingSeparators.add(GroupingSeparator(separatorPosition, codePoint.toString()))
                        }
                    }
                    ix--
                }
                if (mandatoryDigits > 0) {
                    format.primary = formats.DECIMAL
                    format.zeroCode = zeroCode!!
                    format.mandatoryDigits = mandatoryDigits
                    format.optionalDigits = optionalDigits

                    val regular = getRegularRepeat(groupingSeparators)
                    if (regular > 0) {
                        format.regular = true
                        format.groupingSeparators.add(
                            GroupingSeparator(
                                regular,
                                groupingSeparators.elementAt(0)!!.character
                            )
                        )
                    } else {
                        format.regular = false
                        format.groupingSeparators = groupingSeparators
                    }
                } else {
                    format.primary = formats.SEQUENCE
                    format.token = primaryFormat
                }
            }

        }
        return format
    }

    private fun getRegularRepeat(separators: Vector<GroupingSeparator?>): Int {
        if (separators.size == 0) {
            return 0
        }

        val sepChar = separators.elementAt(0)!!.character
        for (i in 1 until separators.size) {
            if (separators.elementAt(i)!!.character != sepChar) {
                return 0
            }
        }

        val indexes = separators.stream().map { seperator: GroupingSeparator? -> seperator!!.position }
            .collect(Collectors.toList())
        val factor = indexes.stream()
            .reduce { a: Int, b: Int -> BigInteger.valueOf(a.toLong()).gcd(BigInteger.valueOf(b.toLong())).toInt() }
            .get()
        for (index in 1..indexes.size) {
            if (indexes.indexOf(index * factor) == -1) {
                return 0
            }
        }
        return factor
    }

    private val defaultPresentationModifiers = createDefaultPresentationModifiers()

    private fun createDefaultPresentationModifiers(): Map<Char, String> {
        val map: MutableMap<Char, String> = HashMap()
        map['Y'] = "1"
        map['M'] = "1"
        map['D'] = "1"
        map['d'] = "1"
        map['F'] = "n"
        map['W'] = "1"
        map['w'] = "1"
        map['X'] = "1"
        map['x'] = "1"
        map['H'] = "1"
        map['h'] = "1"
        map['P'] = "n"
        map['m'] = "01"
        map['s'] = "01"
        map['f'] = "1"
        map['Z'] = "01:01"
        map['z'] = "01:01"
        map['C'] = "n"
        map['E'] = "n"
        return map
    }

    private fun analyseDateTimePicture(picture: String): PictureFormat {
        val format = PictureFormat("datetime")
        var start = 0
        var pos = 0
        while (pos < picture.length) {
            if (picture[pos] == '[') {
                //check it's not a doubled [[
                if (picture[pos + 1] == '[') {
                    //literal [
                    format.addLiteral(picture, start, pos)
                    format.parts.add(SpecPart("literal", "["))
                    pos += 2
                    start = pos
                    continue
                }
                format.addLiteral(picture, start, pos)
                start = pos
                pos = picture.indexOf("]", start)
                if (pos == -1) {
                    throw RuntimeException(Constants.ERR_MSG_NO_CLOSING_BRACKET)
                }
                var marker = picture.substring(start + 1, pos)
                marker = java.lang.String.join(
                    "",
                    *marker.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                )
                val def = SpecPart("marker", marker[0])
                val comma = marker.lastIndexOf(",")
                var presMod: String
                if (comma != -1) {
                    val widthMod = marker.substring(comma + 1)
                    val dash = widthMod.indexOf("-")
                    var min: String
                    var max: String? = null
                    if (dash == -1) {
                        min = widthMod
                    } else {
                        min = widthMod.substring(0, dash)
                        max = widthMod.substring(dash + 1)
                    }
                    def.width = Pair(parseWidth(min), parseWidth(max))
                    presMod = marker.substring(1, comma)
                } else {
                    presMod = marker.substring(1)
                }
                if (presMod.length == 1) {
                    def.presentation1 = presMod
                } else if (presMod.length > 1) {
                    val lastChar = presMod[presMod.length - 1]
                    if ("atco".indexOf(lastChar) != -1) {
                        def.presentation2 = lastChar
                        if (lastChar == 'o') {
                            def.ordinal = true
                        }
                        def.presentation1 = presMod.substring(0, presMod.length - 1)
                    } else {
                        def.presentation1 = presMod
                    }
                } else {
                    def.presentation1 = defaultPresentationModifiers[def.component]
                }
                if (def.presentation1 == null) {
                    throw RuntimeException(String.format(Constants.ERR_MSG_UNKNOWN_COMPONENT_SPECIFIER, def.component))
                }
                if (def.presentation1!![0] == 'n') {
                    def.names = tcase.LOWER
                } else if (def.presentation1!![0] == 'N') {
                    if (def.presentation1!!.length > 1 && def.presentation1!![1] == 'n') {
                        def.names = tcase.TITLE
                    } else {
                        def.names = tcase.UPPER
                    }
                } else if ("YMDdFWwXxHhmsf".indexOf(def.component) != -1) {
                    var integerPattern = def.presentation1
                    if (def.presentation2 != null) {
                        integerPattern += ";" + def.presentation2
                    }
                    def.integerFormat = analyseIntegerPicture(integerPattern)
                    def.integerFormat!!.ordinal = def.ordinal
                    def.width?.first?.let {
                        if (def.integerFormat!!.mandatoryDigits < it) {
                            def.integerFormat!!.mandatoryDigits = it
                        }
                    }
                    if (def.component == 'Y') {
                        def.n = -1
                        def.width?.second?.let {
                            def.n = it
                            def.integerFormat!!.mandatoryDigits = def.n
                        } ?: run {
                            val w = def.integerFormat!!.mandatoryDigits + def.integerFormat!!.optionalDigits
                            if (w >= 2) {
                                def.n = w
                            }
                        }
                    }
                }
                if (def.component == 'Z' || def.component == 'z') {
                    def.integerFormat = analyseIntegerPicture(def.presentation1)
                    def.integerFormat!!.ordinal = def.ordinal
                }
                format.parts.add(def)
                start = pos + 1
            }
            pos++
        }
        format.addLiteral(picture, start, pos)
        return format
    }

    private fun parseWidth(wm: String?): Int? {
        return if (wm == null || wm == "*") {
            null
        } else {
            wm.toInt()
        }
    }

    private val days = arrayOf(
        "", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )
    private val months = arrayOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"
    )

    private var iso8601Spec: PictureFormat? = null

    @JvmStatic
    fun formatDateTime(millis: Long, picture: String?, timezone: String?): String {
        var offsetHours = 0
        var offsetMinutes = 0

        if (timezone != null) {
            val offset = timezone.toInt()
            offsetHours = offset / 100
            offsetMinutes = offset % 100
        }
        val formatSpec: PictureFormat?
        if (picture == null) {
            if (iso8601Spec == null) {
                iso8601Spec = analyseDateTimePicture("[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f001][Z01:01t]")
            }
            formatSpec = iso8601Spec
        } else {
            formatSpec = analyseDateTimePicture(picture)
        }

        val offsetMillis = (60 * offsetHours + offsetMinutes) * 60 * 1000
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis + offsetMillis), ZoneOffset.UTC)
        var result = ""
        for (part in formatSpec!!.parts) {
            result += if (part.type == "literal") {
                part.value
            } else {
                formatComponent(dateTime, part, offsetHours, offsetMinutes)
            }
        }

        return result
    }

    private fun formatComponent(
        date: LocalDateTime,
        markerSpec: SpecPart,
        offsetHours: Int,
        offsetMinutes: Int
    ): String {
        var componentValue = getDateTimeFragment(date, markerSpec.component)

        if ("YMDdFWwXxHhms".indexOf(markerSpec.component) != -1) {
            if (markerSpec.component == 'Y') {
                if (markerSpec.n != -1) {
                    componentValue = "" + (componentValue.toInt() % 10.0.pow(markerSpec.n.toDouble())).toInt()
                }
            }
            if (markerSpec.names != null) {
                componentValue = if (markerSpec.component == 'M' || markerSpec.component == 'x') {
                    months[componentValue.toInt() - 1]
                } else if (markerSpec.component == 'F') {
                    days[componentValue.toInt()]
                } else {
                    throw RuntimeException(
                        String.format(
                            Constants.ERR_MSG_INVALID_NAME_MODIFIER,
                            markerSpec.component
                        )
                    )
                }
                if (markerSpec.names == tcase.UPPER) {
                    componentValue = componentValue.uppercase(Locale.getDefault())
                } else if (markerSpec.names == tcase.LOWER) {
                    componentValue = componentValue.lowercase(Locale.getDefault())
                }
                markerSpec.width?.second?.let {
                    if (componentValue.length > it) {
                        componentValue = componentValue.substring(0, it)
                    }
                }
            } else {
                componentValue = formatInteger(componentValue.toLong(), markerSpec.integerFormat)
            }
        } else if (markerSpec.component == 'f') {
            componentValue = formatInteger(componentValue.toLong(), markerSpec.integerFormat)
        } else if (markerSpec.component == 'Z' || markerSpec.component == 'z') {
            val offset = offsetHours * 100 + offsetMinutes
            if (markerSpec.integerFormat!!.regular) {
                componentValue = formatInteger(offset.toLong(), markerSpec.integerFormat)
            } else {
                val numDigits = markerSpec.integerFormat!!.mandatoryDigits
                if (numDigits == 1 || numDigits == 2) {
                    componentValue = formatInteger(offsetHours.toLong(), markerSpec.integerFormat)
                    if (offsetMinutes != 0) {
                        componentValue += ":" + formatInteger(offsetMinutes.toLong(), "00")
                    }
                } else if (numDigits == 3 || numDigits == 4) {
                    componentValue = formatInteger(offset.toLong(), markerSpec.integerFormat)
                } else {
                    throw RuntimeException(Constants.ERR_MSG_TIMEZONE_FORMAT)
                }
            }
            if (offset >= 0) {
                componentValue = "+$componentValue"
            }
            if (markerSpec.component == 'z') {
                componentValue = "GMT$componentValue"
            }
            if (offset == 0 && markerSpec.presentation2 != null && markerSpec.presentation2 == 't') {
                componentValue = "Z"
            }
        } else if (markerSpec.component == 'P') {
            // §9.8.4.7 Formatting Other Components
            // Formatting P for am/pm
            // getDateTimeFragment() always returns am/pm lower case so check for UPPER here
            if (markerSpec.names == tcase.UPPER) {
                componentValue = componentValue.uppercase(Locale.getDefault())
            }
        }
        return componentValue
    }

    private fun getDateTimeFragment(date: LocalDateTime, component: Char): String {
        var componentValue = ""
        when (component) {
            'Y' -> componentValue = "" + date.year
            'M' -> componentValue = "" + date.monthValue
            'D' -> componentValue = "" + date.dayOfMonth
            'd' -> componentValue = "" + date.dayOfYear
            'F' -> componentValue = "" + date.dayOfWeek.value
            'W' -> componentValue = "" + date[IsoFields.WEEK_OF_WEEK_BASED_YEAR]
            'w' -> componentValue = "" + date[WeekFields.ISO.weekOfMonth()]
            'X' ->                 //TODO work these out once others verified
                componentValue = "" + date.year

            'x' -> componentValue = "" + date.monthValue
            'H' -> componentValue = "" + date.hour
            'h' -> {
                var hour = date.hour
                if (hour > 12) {
                    hour -= 12
                } else if (hour == 0) {
                    hour = 12
                }
                componentValue = "" + hour
            }

            'P' -> componentValue = if (date.hour < 12) "am" else "pm"
            'm' -> componentValue = "" + date.minute
            's' -> componentValue = "" + date.second
            'f' -> componentValue = "" + (date.nano / 1000000)
            'Z', 'z' -> {}
            'C' -> componentValue = "ISO"
            'E' -> componentValue = "ISO"
        }
        return componentValue
    }

    @JvmStatic
    fun parseDateTime(timestamp: String?, picture: String): Long? {
        val formatSpec = analyseDateTimePicture(picture)
        val matchSpec = generateRegex(formatSpec)
        var fullRegex = "^"
        for (part in matchSpec.parts) {
            fullRegex += "(" + part.regex + ")"
        }
        fullRegex += "$"
        val pattern = Pattern.compile(fullRegex, Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(timestamp)
        if (matcher.find()) {
            val dmA = 161
            val dmB = 130
            val dmC = 84
            val dmD = 72
            val tmA = 23
            val tmB = 47

            val components: MutableMap<Char, Int?> = HashMap()
            for (i in 1..matcher.groupCount()) {
                val mpart = matchSpec.parts[i - 1]
                try {
                    components[mpart.component] = mpart.parse(matcher.group(i))
                } catch (e: UnsupportedOperationException) {
                    //do nothing
                }
            }

            if (components.size == 0) {
                // nothing specified
                return null
            }

            var mask = 0

            for (part in "YXMxWwdD".toCharArray()) {
                mask = mask shl 1
                if (components[part] != null) {
                    mask += 1
                }
            }
            val dateA = isType(dmA, mask)
            val dateB = !dateA && isType(dmB, mask)
            val dateC = isType(dmC, mask)
            val dateD = !dateC && isType(dmD, mask)

            mask = 0
            for (part in "PHhmsf".toCharArray()) {
                mask = mask shl 1
                if (components[part] != null) {
                    mask += 1
                }
            }

            val timeA = isType(tmA, mask)
            val timeB = !timeA && isType(tmB, mask)

            val dateComps = if (dateB) "YB" else if (dateC) "XxwF" else if (dateD) "XWF" else "YMD"
            val timeComps = if (timeB) "Phmsf" else "Hmsf"
            val comps = dateComps + timeComps

            val now = LocalDateTime.now(ZoneOffset.UTC)

            var startSpecified = false
            var endSpecified = false
            for (part in comps.toCharArray()) {
                if (components[part] == null) {
                    if (startSpecified) {
                        components[part] = if ("MDd".indexOf(part) != -1) 1 else 0
                        endSpecified = true
                    } else {
                        components[part] = getDateTimeFragment(now, part).toInt()
                    }
                } else {
                    startSpecified = true
                    if (endSpecified) {
                        throw RuntimeException(Constants.ERR_MSG_MISSING_FORMAT)
                    }
                }
            }
            if (components['M'] != null && components['M']!! > 0) {
                components['M'] = components['M']!! - 1
            } else {
                components['M'] = 0
            }
            if (dateB) {
                var firstJan = LocalDateTime.of(components['Y']!!, Month.JANUARY, 1, 0, 0)
                firstJan = firstJan.withDayOfYear(components['d']!!)
                components['M'] = firstJan.monthValue - 1
                components['D'] = firstJan.dayOfMonth
            }
            if (dateC) {
                //TODO implement this
                //parsing this format not currently supported
                throw RuntimeException(Constants.ERR_MSG_MISSING_FORMAT)
            }
            if (dateD) {
                //TODO implement this
                // parsing this format (ISO week date) not currently supported
                throw RuntimeException(Constants.ERR_MSG_MISSING_FORMAT)
            }
            if (timeB) {
                components['H'] = if (components['h'] == 12) 0 else components['h']
                if (components['P'] == 1) {
                    components['H'] = components['H']!! + 12
                }
            }
            val cal = LocalDateTime.of(
                components['Y']!!,
                components['M']!! + 1,
                components['D']!!,
                components['H']!!,
                components['m']!!,
                components['s']!!,
                components['f']!! * 1000000
            )
            var millis = cal.toInstant(ZoneOffset.UTC).toEpochMilli()
            if (components['Z'] != null) {
                millis -= (components['Z']!! * 60 * 1000).toLong()
            } else if (components['z'] != null) {
                millis -= (components['z']!! * 60 * 1000).toLong()
            }
            return millis
        }
        return null
    }

    private fun isType(type: Int, mask: Int): Boolean {
        return ((type.inv() and mask) == 0) && (type and mask) != 0
    }

    private fun generateRegex(formatSpec: PictureFormat): PictureMatcher {
        val matcher = PictureMatcher()
        for (part in formatSpec.parts) {
            var res: MatcherPart
            if (part.type == "literal") {
                val p = Pattern.compile("[.*+?^\${}()|\\[\\]\\\\]")
                val m = p.matcher(part.value)

                val regex = m.replaceAll("\\\\$0")
                res = object : MatcherPart(regex) {
                    override fun parse(value: String): Int {
                        throw UnsupportedOperationException()
                    }
                }
            } else if (part.component == 'Z' || part.component == 'z') {
                val separator = part.integerFormat!!.groupingSeparators.size == 1 && part.integerFormat!!.regular
                var regex = ""
                if (part.component == 'z') {
                    regex = "GMT"
                }
                regex += "[-+][0-9]+"
                if (separator) {
                    regex += part.integerFormat!!.groupingSeparators[0]!!.character + "[0-9]+"
                }
                res = object : MatcherPart(regex) {
                    override fun parse(value: String): Int {
                        var value = value
                        if (part.component == 'z') {
                            value = value.substring(3)
                        }
                        var offsetHours = 0
                        var offsetMinutes = 0
                        if (separator) {
                            offsetHours = value.substring(
                                0,
                                value.indexOf(part.integerFormat!!.groupingSeparators[0]!!.character)
                            ).toInt()
                            offsetMinutes =
                                value.substring(value.indexOf(part.integerFormat!!.groupingSeparators[0]!!.character) + 1)
                                    .toInt()
                        } else {
                            val numdigits = value.length - 1
                            if (numdigits <= 2) {
                                offsetHours = value.toInt()
                            } else {
                                offsetHours = value.substring(0, 3).toInt()
                                offsetMinutes = value.substring(3).toInt()
                            }
                        }
                        return offsetHours * 60 + offsetMinutes
                    }
                }
            } else if (part.integerFormat != null) {
                res = generateRegex(part.component, part.integerFormat)
            } else {
                val regex = "[a-zA-Z]+"
                val lookup: MutableMap<String, Int> = HashMap()
                if (part.component == 'M' || part.component == 'x') {
                    for (i in months.indices) {
                        part.width?.second?.let {
                            lookup[months[i].substring(0, it)] = i + 1
                        } ?: run {
                            lookup[months[i]] = i + 1
                        }
                    }
                } else if (part.component == 'F') {
                    for (i in 1 until days.size) {
                        part.width?.second?.let {
                            lookup[days[i].substring(0, it)] = i
                        } ?: run {
                            lookup[days[i]] = i
                        }
                    }
                } else if (part.component == 'P') {
                    lookup["am"] = 0
                    lookup["AM"] = 0
                    lookup["pm"] = 1
                    lookup["PM"] = 1
                } else {
                    throw RuntimeException(String.format(Constants.ERR_MSG_INVALID_NAME_MODIFIER, part.component))
                }
                res = object : MatcherPart(regex) {
                    override fun parse(value: String): Int {
                        return lookup[value]!!
                    }
                }
            }
            res.component = part.component
            matcher.parts.add(res)
        }
        return matcher
    }

    private fun generateRegex(component: Char, formatSpec: Format?): MatcherPart {
        val matcher: MatcherPart
        val isUpper = formatSpec!!.case_type == tcase.UPPER
        when (formatSpec.primary) {
            formats.LETTERS -> {
                val regex = if (isUpper) "[A-Z]+" else "[a-z]+"
                matcher = object : MatcherPart(regex) {
                    override fun parse(value: String): Int {
                        return lettersToDecimal(value, if (isUpper) 'A' else 'a')
                    }
                }
            }

            formats.ROMAN -> {
                val regex = if (isUpper) "[MDCLXVI]+" else "[mdclxvi]+"
                matcher = object : MatcherPart(regex) {
                    override fun parse(value: String): Int {
                        return romanToDecimal(if (isUpper) value else value.uppercase(Locale.getDefault()))
                    }
                }
            }

            formats.WORDS -> {
                val words: MutableSet<String> = HashSet()
                words.addAll(wordValues.keys)
                words.add("and")
                words.add("[\\-, ]")
                val regex = "(?:" + java.lang.String.join("|", *words.toTypedArray<String>()) + ")+"
                matcher = object : MatcherPart(regex) {
                    override fun parse(value: String): Int {
                        return wordsToNumber(value.lowercase(Locale.getDefault()))
                    }
                }
            }

            formats.DECIMAL -> {
                var regex = "[0-9]+"
                when (component) {
                    'Y' -> {
                        regex = "[0-9]{2,4}"
                    }

                    'M', 'D', 'H', 'h', 'm', 's' -> {
                        regex = "[0-9]{1,2}"
                    }

                    else -> {}

                }
                if (formatSpec.ordinal) {
                    regex += "(?:th|st|nd|rd)"
                }
                matcher = object : MatcherPart(regex) {
                    override fun parse(value: String): Int {
                        var digits = value
                        if (formatSpec.ordinal) {
                            digits = value.substring(0, value.length - 2)
                        }
                        if (formatSpec.regular) {
                            digits =
                                java.lang.String.join("", *digits.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                                    .toTypedArray())
                        } else {
                            for (sep in formatSpec.groupingSeparators) {
                                digits = java.lang.String.join(
                                    "",
                                    *digits.split(sep!!.character.toRegex()).dropLastWhile { it.isEmpty() }
                                        .toTypedArray())
                            }
                        }
                        if (formatSpec.zeroCode != 0x30) {
                            val chars = digits.toCharArray()
                            var i = 0
                            while (i < chars.size) {
                                chars[i] = (chars[i].code - formatSpec.zeroCode + 0x30).toChar()
                                i++
                            }
                            digits = String(chars)
                        }
                        return digits.toInt()
                    }
                }
            }

            formats.SEQUENCE -> {
                throw RuntimeException(Constants.ERR_MSG_SEQUENCE_UNSUPPORTED)
            }

            else -> {
                throw RuntimeException(Constants.ERR_MSG_SEQUENCE_UNSUPPORTED)
            }
        }
        return matcher
    }

    @JvmStatic
    fun lettersToDecimal(letters: String, aChar: Char): Int {
        var decimal = 0
        val chars = letters.toCharArray()
        for (i in chars.indices) {
            decimal = (decimal + (chars[chars.size - i - 1].code - aChar.code + 1) * 26.0.pow(i.toDouble())).toInt()
        }
        return decimal
    }

    private class RomanNumeral(val value: Int, val letters: String)

    internal enum class formats(var value: String) {
        DECIMAL("decimal"), LETTERS("letters"), ROMAN("roman"), WORDS("words"), SEQUENCE("sequence")
    }

    internal enum class tcase(var value: String) {
        UPPER("upper"), LOWER("lower"), TITLE("title")
    }

    private class Format {
        @Suppress("unused")
        var type: String = "integer"
        var primary: formats = formats.DECIMAL
        var case_type: tcase = tcase.LOWER
        var ordinal: Boolean = false
        var zeroCode: Int = 0
        var mandatoryDigits: Int = 0
        var optionalDigits: Int = 0
        var regular: Boolean = false
        var groupingSeparators: Vector<GroupingSeparator?> = Vector()
        var token: String? = null
    }

    private class GroupingSeparator(var position: Int, var character: String)

    private class PictureFormat(@field:Suppress("unused") var type: String) {
        var parts: Vector<SpecPart> = Vector()

        fun addLiteral(picture: String, start: Int, end: Int) {
            if (end > start) {
                var literal = picture.substring(start, end)
                literal =
                    if (literal == "]]") // handle special case where picture ends with ]], split yields empty array
                        "]"
                    else java.lang.String.join(
                        "]",
                        *literal.split("]]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    )
                parts.add(SpecPart("literal", literal))
            }
        }
    }

    private class SpecPart {
        var type: String
        var value: String? = null
        var component: Char = 0.toChar()
        var width: Pair<Int?, Int?>? = null
        var presentation1: String? = null
        var presentation2: Char? = null
        var ordinal: Boolean = false
        var names: tcase? = null
        var integerFormat: Format? = null
        var n: Int = 0

        constructor(type: String, value: String?) {
            this.type = type
            this.value = value
        }

        constructor(type: String, component: Char) {
            this.type = type
            this.component = component
        }
    }

    private class PictureMatcher {
        var parts: Vector<MatcherPart> = Vector()
    }

    private abstract class MatcherPart(var regex: String) {
        var component: Char = 0.toChar()

        abstract fun parse(value: String): Int
    }
}
