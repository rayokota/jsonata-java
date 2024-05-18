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

import com.dashjoin.jsonata.Jsonata.JFunction
import com.dashjoin.jsonata.Jsonata.JFunctionCallable
import java.util.*

object Utils {
    @JvmStatic
    fun isNumeric(v: Any?): Boolean {
        if (v is Long) return true
        var isNum = false
        if (v is Number) {
            val d = v.toDouble()
            isNum = !java.lang.Double.isNaN(d)
            if (isNum && !java.lang.Double.isFinite(d)) {
                throw JException("D1001", 0, v)
            }
        }
        return isNum
    }

    @JvmStatic
    fun isArrayOfStrings(v: Any?): Boolean {
        val result = false
        if (v is Collection<*>) {
            for (o in v) if (o !is String) return false
            return true
        }
        return false
    }

    @JvmStatic
    fun isArrayOfNumbers(v: Any?): Boolean {
        val result = false
        if (v is Collection<*>) {
            for (o in v) if (!isNumeric(o)) return false
            return true
        }
        return false
    }

    @JvmStatic
    fun isFunction(o: Any?): Boolean {
        return o is JFunction || o is JFunctionCallable
    }

    var NONE: Any = Any()

    /**
     * Create an empty sequence to contain query results
     * @returns {Array} - empty sequence
     */
    @JvmStatic
    fun createSequence(el: Any? = NONE): MutableList<Any?> {
        val sequence = JList<Any?>()
        sequence.sequence = true
        if (el !== NONE) {
            if (el is List<*> && el.size == 1) sequence.add(
                el[0]!!
            )
            else  // This case does NOT exist in Javascript! Why?
                sequence.add(el)
        }
        return sequence
    }

    @JvmStatic
    fun isSequence(result: Any?): Boolean {
        return result is JList<*> && result.sequence
    }

    @JvmStatic
    fun convertNumber(n: Number): Number? {
        // Use long if the number is not fractional
        if (!isNumeric(n)) return null
        if (n.toLong().toDouble() == n.toDouble()) {
            val l = n.toLong()
            return if (l.toInt().toLong() == l) l.toInt()
            else l
        }
        return n.toDouble()
    }

    @JvmStatic
    fun checkUrl(str: String) {
        var isHigh = false
        for (i in 0 until str.length) {
            val wasHigh = isHigh
            isHigh = Character.isHighSurrogate(str[i])
            if (wasHigh && isHigh) throw JException("Malformed URL", i)
        }
        if (isHigh) throw JException("Malformed URL", 0)
    }

    private fun convertValue(`val`: Any?): Any? {
        return if (`val` !== Jsonata.NULL_VALUE) `val` else null
    }

    private fun convertMapNulls(res: MutableMap<String?, Any?>) {
        for (e in res.entries) {
            val `val` = e.value
            val l = convertValue(`val`)
            if (l !== `val`) e.setValue(l)
            recurse(`val`)
        }
    }

    private fun convertListNulls(res: MutableList<Any?>) {
        for (i in res.indices) {
            val `val` = res[i]
            val l = convertValue(`val`)
            if (l !== `val`) res[i] = l
            recurse(`val`)
        }
    }

    private fun recurse(`val`: Any?) {
        if (`val` is Map<*, *>) convertMapNulls(`val` as MutableMap<String?, Any?>)
        if (`val` is List<*>) convertListNulls(`val` as MutableList<Any?>)
    }

    @JvmStatic
    fun convertNulls(res: Any?): Any? {
        recurse(res)
        return convertValue(res)
    }

    /**
     * adapted from org.json.JSONObject https://github.com/stleary/JSON-java
     */
    @JvmStatic
    fun quote(string: String, w: StringBuilder) {
        var b: Char
        var c = 0.toChar()
        var hhhh: String
        val len = string.length

        var i = 0
        while (i < len) {
            b = c
            c = string[i]
            when (c) {
                '\\', '"' -> {
                    w.append('\\')
                    w.append(c)
                }

                '\b' -> w.append("\\b")
                '\t' -> w.append("\\t")
                '\n' -> w.append("\\n")
                '\u000C' -> w.append("\\f")
                '\r' -> w.append("\\r")
                else -> if (c < ' ' || (c >= '\u0080' && c < '\u00a0')
                    || (c >= '\u2000' && c < '\u2100')
                ) {
                    w.append("\\u")
                    hhhh = Integer.toHexString(c.code)
                    w.append("0000", 0, 4 - hhhh.length)
                    w.append(hhhh)
                } else {
                    w.append(c)
                }
            }
            i += 1
        }
    }

    class JList<E> : ArrayList<E> {
        constructor() : super()
        constructor(capacity: Int) : super(capacity)
        constructor(c: Collection<E>?) : super(c)

        // Jsonata specific flags
        var sequence: Boolean = false

        var outerWrapper: Boolean = false

        var tupleStream: Boolean = false

        var keepSingleton: Boolean = false

        var cons: Boolean = false
    }

    /**
     * List representing an int range [a,b]
     * Both sides are included. Read-only + immutable.
     *
     * Used for late materialization of ranges.
     */
    class RangeList(left: Long, right: Long) : AbstractList<Number?>() {
        val a: Long
        val b: Long
        val _size: Int

        init {
            assert(left <= right)
            assert(right - left < Int.MAX_VALUE)
            a = left
            b = right
            _size = (b - a + 1).toInt()
        }

        override val size: Int
            get() = _size

        override fun get(index: Int): Number? {
            if (index < size) {
                try {
                    return convertNumber(a + index)
                } catch (e: JException) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                }
            }
            throw IndexOutOfBoundsException(index)
        }
    }
}
