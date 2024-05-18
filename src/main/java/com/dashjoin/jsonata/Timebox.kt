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

/**
 * Configure max runtime / max recursion depth.
 * See Frame.setRuntimeBounds - usually not used directly
 */
class Timebox @JvmOverloads constructor(expr: Jsonata.Frame, timeout: Long = 5000L, maxDepth: Int = 100) {
    var timeout: Long = 5000L
    var maxDepth: Int = 100

    var time: Long = System.currentTimeMillis()
    var depth: Int = 0

    /**
     * Protect the process/browser from a runnaway expression
     * i.e. Infinite loop (tail recursion), or excessive stack growth
     *
     * @param {Object} expr - expression to protect
     * @param {Number} timeout - max time in ms
     * @param {Number} maxDepth - max stack depth
     */
    init {
        this.timeout = timeout
        this.maxDepth = maxDepth

        // register callbacks
        expr.setEvaluateEntryCallback(object : Jsonata.EntryCallback {
            override fun callback(expr: Parser.Symbol?, input: Any?, environment: Jsonata.Frame) {
                if (environment.isParallelCall) return
                depth++
                checkRunnaway()
            }
        })
        expr.setEvaluateExitCallback(object : Jsonata.ExitCallback {
            override fun callback(expr: Parser.Symbol?, input: Any?, environment: Jsonata.Frame, result: Any?) {
                if (environment.isParallelCall) return
                depth--
                checkRunnaway()
            }
        })
    }

    fun checkRunnaway() {
        if (depth > maxDepth) {
            // stack too deep
            throw JException(
                "Stack overflow error: Check for non-terminating recursive function.  Consider rewriting as tail-recursive. Depth=$depth max=$maxDepth",
                -1
            )
            //stack: new Error().stack,
            //code: "U1001"
            //};
        }
        if (System.currentTimeMillis() - time > timeout) {
            // expression has run for too long
            throw JException("Expression evaluation timeout: Check for infinite loop", -1)
            //stack: new Error().stack,
            //code: "U1001"
            //};
        }
    }
}
