/*******************************************************************************
 * Copyright (c) 2016 EclipseSource.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.dashjoin.jsonata.json //package com.eclipsesource.json;


/**
 * A handler for parser events. Instances of this class can be given to a [JsonParser]. The
 * parser will then call the methods of the given handler while reading the input.
 *
 *
 * The default implementations of these methods do nothing. Subclasses may override only those
 * methods they are interested in. They can use `getLocation()` to access the current
 * character position of the parser at any point. The `start*` methods will be called
 * while the location points to the first character of the parsed element. The `end*`
 * methods will be called while the location points to the character position that directly follows
 * the last character of the parsed element. Example:
 *
 *
 * <pre>
 * ["lorem ipsum"]
 * ^            ^
 * startString  endString
</pre> *
 *
 *
 * Subclasses that build an object representation of the parsed JSON can return arbitrary handler
 * objects for JSON arrays and JSON objects in [.startArray] and [.startObject].
 * These handler objects will then be provided in all subsequent parser events for this particular
 * array or object. They can be used to keep track the elements of a JSON array or object.
 *
 *
 * @param <A>
 * The type of handlers used for JSON arrays
 * @param <O>
 * The type of handlers used for JSON objects
 * @see JsonParser
</O></A> */
abstract class JsonHandler<A, O> {
    var parser: JsonParser? = null

    protected val location: Location
        /**
         * Returns the current parser location.
         *
         * @return the current parser location
         */
        get() = parser!!.location

    /**
     * Indicates the beginning of a `null` literal in the JSON input. This method will be
     * called when reading the first character of the literal.
     */
    open fun startNull() {
    }

    /**
     * Indicates the end of a `null` literal in the JSON input. This method will be called
     * after reading the last character of the literal.
     */
    open fun endNull() {
    }

    /**
     * Indicates the beginning of a boolean literal (`true` or `false`) in the
     * JSON input. This method will be called when reading the first character of the literal.
     */
    open fun startBoolean() {
    }

    /**
     * Indicates the end of a boolean literal (`true` or `false`) in the JSON
     * input. This method will be called after reading the last character of the literal.
     *
     * @param value
     * the parsed boolean value
     */
    open fun endBoolean(value: Boolean) {
    }

    /**
     * Indicates the beginning of a string in the JSON input. This method will be called when reading
     * the opening double quote character (`'"'`).
     */
    open fun startString() {
    }

    /**
     * Indicates the end of a string in the JSON input. This method will be called after reading the
     * closing double quote character (`'"'`).
     *
     * @param string
     * the parsed string
     */
    open fun endString(string: String) {
    }

    /**
     * Indicates the beginning of a number in the JSON input. This method will be called when reading
     * the first character of the number.
     */
    open fun startNumber() {
    }

    /**
     * Indicates the end of a number in the JSON input. This method will be called after reading the
     * last character of the number.
     *
     * @param string
     * the parsed number string
     */
    open fun endNumber(string: String) {
    }

    /**
     * Indicates the beginning of an array in the JSON input. This method will be called when reading
     * the opening square bracket character (`'['`).
     *
     *
     * This method may return an object to handle subsequent parser events for this array. This array
     * handler will then be provided in all calls to [ startArrayValue()][.startArrayValue], [endArrayValue()][.endArrayValue], and
     * [endArray()][.endArray] for this array.
     *
     *
     * @return a handler for this array, or `null` if not needed
     */
    open fun startArray(): A? {
        return null
    }

    /**
     * Indicates the end of an array in the JSON input. This method will be called after reading the
     * closing square bracket character (`']'`).
     *
     * @param array
     * the array handler returned from [.startArray], or `null` if not
     * provided
     */
    open fun endArray(array: A) {
    }

    /**
     * Indicates the beginning of an array element in the JSON input. This method will be called when
     * reading the first character of the element, just before the call to the `start`
     * method for the specific element type ([.startString], [.startNumber], etc.).
     *
     * @param array
     * the array handler returned from [.startArray], or `null` if not
     * provided
     */
    open fun startArrayValue(array: A) {
    }

    /**
     * Indicates the end of an array element in the JSON input. This method will be called after
     * reading the last character of the element value, just after the `end` method for the
     * specific element type (like [endString()][.endString], [ endNumber()][.endNumber], etc.).
     *
     * @param array
     * the array handler returned from [.startArray], or `null` if not
     * provided
     */
    open fun endArrayValue(array: A) {
    }

    /**
     * Indicates the beginning of an object in the JSON input. This method will be called when reading
     * the opening curly bracket character (`'{'`).
     *
     *
     * This method may return an object to handle subsequent parser events for this object. This
     * object handler will be provided in all calls to [ startObjectName()][.startObjectName], [endObjectName()][.endObjectName],
     * [startObjectValue()][.startObjectValue],
     * [endObjectValue()][.endObjectValue], and [ endObject()][.endObject] for this object.
     *
     *
     * @return a handler for this object, or `null` if not needed
     */
    open fun startObject(): O? {
        return null
    }

    /**
     * Indicates the end of an object in the JSON input. This method will be called after reading the
     * closing curly bracket character (`'}'`).
     *
     * @param object
     * the object handler returned from [.startObject], or null if not provided
     */
    open fun endObject(`object`: O) {
    }

    /**
     * Indicates the beginning of the name of an object member in the JSON input. This method will be
     * called when reading the opening quote character ('&quot;') of the member name.
     *
     * @param object
     * the object handler returned from [.startObject], or `null` if not
     * provided
     */
    open fun startObjectName(`object`: O) {
    }

    /**
     * Indicates the end of an object member name in the JSON input. This method will be called after
     * reading the closing quote character (`'"'`) of the member name.
     *
     * @param object
     * the object handler returned from [.startObject], or null if not provided
     * @param name
     * the parsed member name
     */
    open fun endObjectName(`object`: O, name: String?) {
    }

    /**
     * Indicates the beginning of the name of an object member in the JSON input. This method will be
     * called when reading the opening quote character ('&quot;') of the member name.
     *
     * @param object
     * the object handler returned from [.startObject], or `null` if not
     * provided
     * @param name
     * the member name
     */
    open fun startObjectValue(`object`: O, name: String?) {
    }

    /**
     * Indicates the end of an object member value in the JSON input. This method will be called after
     * reading the last character of the member value, just after the `end` method for the
     * specific member type (like [endString()][.endString], [ endNumber()][.endNumber], etc.).
     *
     * @param object
     * the object handler returned from [.startObject], or null if not provided
     * @param name
     * the parsed member name
     */
    open fun endObjectValue(`object`: O, name: String?) {
    }
}