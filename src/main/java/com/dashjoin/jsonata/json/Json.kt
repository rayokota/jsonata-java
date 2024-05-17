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
package com.dashjoin.jsonata.json

import com.dashjoin.jsonata.JException
import com.dashjoin.jsonata.Utils
import java.io.IOException
import java.io.Reader

/**
 * Vanilla JSON parser
 *
 * Uses classes JsonParser + JsonHandler from:
 * https://github.com/ralfstx/minimal-json
 */
object Json {
    /**
     * Parses the given JSON string
     *
     * @param json
     * @return Parsed object
     */
    @JvmStatic
    fun parseJson(json: String?): Any? {
        val handler = _JsonHandler()
        val jp = JsonParser(handler)
        jp.parse(json)
        return handler.value
    }

    /**
     * Parses the given JSON
     *
     * @param json
     * @return Parsed object
     * @throws IOException
     */
    @JvmStatic
    @Throws(IOException::class)
    fun parseJson(json: Reader?): Any? {
        val handler = _JsonHandler()
        val jp = JsonParser(handler)
        jp.parse(json, 65536)
        return handler.value
    }

    @Throws(Throwable::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val handler = _JsonHandler()

        val jp = JsonParser(handler)

        jp.parse("{\"a\":false}")

        println(handler.value)
    }

    class _JsonHandler : JsonHandler<List<*>, Map<*, *>>() {
        var value: Any? = null
            protected set

        override fun startArray(): List<*> {
            return ArrayList<Any>()
        }

        override fun startObject(): Map<*, *> {
            return LinkedHashMap<Any, Any>()
        }

        override fun endNull() {
            value = null
        }

        override fun endBoolean(bool: Boolean) {
            value = bool
        }

        override fun endString(string: String) {
            value = string
        }

        override fun endNumber(string: String) {
            val d = string.toDouble()
            try {
                value = Utils.convertNumber(d)
            } catch (e: JException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
        }

        override fun endArray(array: List<*>) {
            value = array
        }

        override fun endObject(`object`: Map<*, *>) {
            value = `object`
        }

        override fun endArrayValue(array: List<*>) {
            (array as MutableList<Any?>).add(value)
        }

        override fun endObjectValue(`object`: Map<*, *>, name: String?) {
            (`object` as MutableMap<Any?, Any?>)[name] = value
        }
    }
}
