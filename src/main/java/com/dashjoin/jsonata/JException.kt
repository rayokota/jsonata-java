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

import java.util.*
import java.util.regex.Matcher

class JException(
    cause: Throwable?,
    /**
     * Returns the error code, i.e. S0201
     * @return
     */
    @JvmField var error: String,
    /**
     * Returns the error location (in characters)
     * @return
     */
    var location: Int,
    /**
     * Returns the current token
     * @return
     */
    var current: Any?,
    /**
     * Returns the expected token
     * @return
     */
    var expected: Any?
) : RuntimeException(msg(error, location, current, expected), cause) {
    @JvmOverloads
    constructor(error: String, location: Int = -1, currentToken: Any? = null, expected: Any? = null) : this(
        null,
        error,
        location,
        currentToken,
        expected
    )

    val detailedErrorMessage: String
        /**
         * Returns the error message with error details in the text.
         * Example: Syntax error: ")" {code=S0201 position=3}
         * @return
         */
        get() = msg(error, location, current, expected, true)

    // Recover
    var type: String? = null
    var remaining: List<Tokenizer.Token>? = null

    companion object {
        private const val serialVersionUID = -3354943281127831704L

        /**
         * Generate error message from given error code
         * Codes are defined in Jsonata.errorCodes
         *
         * Fallback: if error code does not exist, return a generic message
         *
         * @param error
         * @param location
         * @param arg1
         * @param arg2
         * @param details True = add error details as text, false = don't add details (use getters to retrieve details)
         * @return
         */
        /**
         * Generate error message from given error code
         * Codes are defined in Jsonata.errorCodes
         *
         * Fallback: if error code does not exist, return a generic message
         *
         * @param error
         * @param location
         * @param arg1
         * @param arg2
         * @return
         */
        @JvmOverloads
        fun msg(error: String, location: Int, arg1: Any?, arg2: Any?, details: Boolean = false): String {
            val message = Jsonata.errorCodes[error]
                ?: // unknown error code
                return "JSonataException " + error +
                        (if (details) " {code=unknown position=$location arg1=$arg1 arg2=$arg2}" else "")

            var formatted = message
            try {
                // Replace any {{var}} with Java format "%1$s"
                formatted = formatted.replaceFirst("\\{\\{\\w+\\}\\}".toRegex(), Matcher.quoteReplacement("\"%1\$s\""))
                formatted = formatted.replaceFirst("\\{\\{\\w+\\}\\}".toRegex(), Matcher.quoteReplacement("\"%2\$s\""))

                formatted = String.format(formatted, arg1, arg2)
            } catch (ex: IllegalFormatException) {
                ex.printStackTrace()
                // ignore
            }
            if (details) {
                formatted = "$formatted {code=$error"
                if (location >= 0) formatted += " position=$location"
                formatted += "}"
            }
            return formatted
        }
    }
}
