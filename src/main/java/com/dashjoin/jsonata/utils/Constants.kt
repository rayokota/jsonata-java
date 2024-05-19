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
package com.dashjoin.jsonata.utils

/**
 * Constants required by DateTimeUtils
 */
object Constants {
    const val ERR_MSG_SEQUENCE_UNSUPPORTED: String =
        "Formatting or parsing an integer as a sequence starting with %s is not supported by this implementation"
    const val ERR_MSG_DIFF_DECIMAL_GROUP: String =
        "In a decimal digit pattern, all digits must be from the same decimal group"
    const val ERR_MSG_NO_CLOSING_BRACKET: String = "No matching closing bracket ']' in date/time picture string"
    const val ERR_MSG_UNKNOWN_COMPONENT_SPECIFIER: String = "Unknown component specifier %s in date/time picture string"
    const val ERR_MSG_INVALID_NAME_MODIFIER: String =
        "The 'name' modifier can only be applied to months and days in the date/time picture string, not %s"
    const val ERR_MSG_TIMEZONE_FORMAT: String =
        "The timezone integer format specifier cannot have more than four digits"
    const val ERR_MSG_MISSING_FORMAT: String =
        "The date/time picture string is missing specifiers required to parse the timestamp"
    const val ERR_MSG_INVALID_OPTIONS_SINGLE_CHAR: String =
        "Argument 3 of function %s is invalid. The value of the %s property must be a single character"
    const val ERR_MSG_INVALID_OPTIONS_STRING: String =
        "Argument 3 of function %s is invalid. The value of the %s property must be a string"

    /**
     * Collection of decimal format strings that defined by xsl:decimal-format.
     *
     * <pre>
     * &lt;!ELEMENT xsl:decimal-format EMPTY&gt;
     * &lt;!ATTLIST xsl:decimal-format
     * name %qname; #IMPLIED
     * decimal-separator %char; "."
     * grouping-separator %char; ","
     * infinity CDATA "Infinity"
     * minus-sign %char; "-"
     * NaN CDATA "NaN"
     * percent %char; "%"
     * per-mille %char; "&#x2030;"
     * zero-digit %char; "0"
     * digit %char; "#"
     * pattern-separator %char; ";"
    </pre> *
     *
     * {http://www.w3.org/TR/xslt#format-number} to explain format-number in XSLT
     * Specification xsl.usage advanced
     */
    const val SYMBOL_DECIMAL_SEPARATOR: String = "decimal-separator"
    const val SYMBOL_GROUPING_SEPARATOR: String = "grouping-separator"
    const val SYMBOL_INFINITY: String = "infinity"
    const val SYMBOL_MINUS_SIGN: String = "minus-sign"
    const val SYMBOL_NAN: String = "NaN"
    const val SYMBOL_PERCENT: String = "percent"
    const val SYMBOL_PER_MILLE: String = "per-mille"
    const val SYMBOL_ZERO_DIGIT: String = "zero-digit"
    const val SYMBOL_DIGIT: String = "digit"
    const val SYMBOL_PATTERN_SEPARATOR: String = "pattern-separator"
}
