/*******************************************************************************
 * Copyright (c) 2013, 2016 EclipseSource.
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
package com.dashjoin.jsonata.json

import java.io.IOException
import java.io.Reader
import java.io.StringReader
import kotlin.math.max
import kotlin.math.min

//package com.eclipsesource.json;

/**
 * A streaming parser for JSON text. The parser reports all events to a given handler.
 */
class JsonParser(handler: JsonHandler<*, *>?) {
    private val handler: JsonHandler<Any?, Any?>
    private var reader: Reader? = null
    private var buffer: CharArray = CharArray(0)
    private var bufferOffset = 0
    private var index = 0
    private var fill = 0
    private var line = 0
    private var lineOffset = 0
    private var current: Char? = null
    private var captureBuffer: StringBuilder? = null
    private var captureStart = 0
    private var nestingLevel = 0

    /*
   * |                      bufferOffset
   *                        v
   * [a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t]        < input
   *                       [l|m|n|o|p|q|r|s|t|?|?]    < buffer
   *                          ^               ^
   *                       |  index           fill
   */
    /**
     * Creates a new JsonParser with the given handler. The parser will report all parser events to
     * this handler.
     *
     * @param handler
     * the handler to process parser events
     */
    init {
        if (handler == null) {
            throw NullPointerException("handler is null")
        }
        this.handler = handler as JsonHandler<Any?, Any?>
        handler.parser = this
    }

    /**
     * Parses the given input string. The input must contain a valid JSON value, optionally padded
     * with whitespace.
     *
     * @param string
     * the input string, must be valid JSON
     * @throws ParseException
     * if the input is not valid JSON
     */
    fun parse(string: String?) {
        if (string == null) {
            throw NullPointerException("string is null")
        }
        val bufferSize = max(MIN_BUFFER_SIZE.toDouble(), min(DEFAULT_BUFFER_SIZE.toDouble(), string.length.toDouble()))
            .toInt()
        try {
            parse(StringReader(string), bufferSize)
        } catch (exception: IOException) {
            // StringReader does not throw IOException
            throw RuntimeException(exception)
        }
    }

    /**
     * Reads the entire input from the given reader and parses it as JSON. The input must contain a
     * valid JSON value, optionally padded with whitespace.
     *
     *
     * Characters are read in chunks into an input buffer of the given size. Hence, wrapping a reader
     * in an additional `BufferedReader` likely won't improve reading performance.
     *
     *
     * @param reader
     * the reader to read the input from
     * @param buffersize
     * the size of the input buffer in chars
     * @throws IOException
     * if an I/O error occurs in the reader
     * @throws ParseException
     * if the input is not valid JSON
     */
    /**
     * Reads the entire input from the given reader and parses it as JSON. The input must contain a
     * valid JSON value, optionally padded with whitespace.
     *
     *
     * Characters are read in chunks into a default-sized input buffer. Hence, wrapping a reader in an
     * additional `BufferedReader` likely won't improve reading performance.
     *
     *
     * @param reader
     * the reader to read the input from
     * @throws IOException
     * if an I/O error occurs in the reader
     * @throws ParseException
     * if the input is not valid JSON
     */
    @Throws(IOException::class)
    fun parse(reader: Reader?, buffersize: Int = DEFAULT_BUFFER_SIZE) {
        if (reader == null) {
            throw NullPointerException("reader is null")
        }
        require(buffersize > 0) { "buffersize is zero or negative" }
        this.reader = reader
        buffer = CharArray(buffersize)
        bufferOffset = 0
        index = 0
        fill = 0
        line = 1
        lineOffset = 0
        current = null
        captureStart = -1
        read()
        skipWhiteSpace()
        readValue()
        skipWhiteSpace()
        if (!isEndOfText) {
            throw error("Unexpected character")
        }
    }

    @Throws(IOException::class)
    private fun readValue() {
        when (current) {
            'n' -> readNull()
            't' -> readTrue()
            'f' -> readFalse()
            '"' -> readString()
            '[' -> readArray()
            '{' -> readObject()
            '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> readNumber()
            else -> throw expected("value")
        }
    }

    @Throws(IOException::class)
    private fun readArray() {
        val array = handler.startArray()
        read()
        if (++nestingLevel > MAX_NESTING_LEVEL) {
            throw error("Nesting too deep")
        }
        skipWhiteSpace()
        if (readChar(']')) {
            nestingLevel--
            handler.endArray(array)
            return
        }
        do {
            skipWhiteSpace()
            handler.startArrayValue(array)
            readValue()
            handler.endArrayValue(array)
            skipWhiteSpace()
        } while (readChar(','))
        if (!readChar(']')) {
            throw expected("',' or ']'")
        }
        nestingLevel--
        handler.endArray(array)
    }

    @Throws(IOException::class)
    private fun readObject() {
        val `object` = handler.startObject()
        read()
        if (++nestingLevel > MAX_NESTING_LEVEL) {
            throw error("Nesting too deep")
        }
        skipWhiteSpace()
        if (readChar('}')) {
            nestingLevel--
            handler.endObject(`object`)
            return
        }
        do {
            skipWhiteSpace()
            handler.startObjectName(`object`)
            val name = readName()
            handler.endObjectName(`object`, name)
            skipWhiteSpace()
            if (!readChar(':')) {
                throw expected("':'")
            }
            skipWhiteSpace()
            handler.startObjectValue(`object`, name)
            readValue()
            handler.endObjectValue(`object`, name)
            skipWhiteSpace()
        } while (readChar(','))
        if (!readChar('}')) {
            throw expected("',' or '}'")
        }
        nestingLevel--
        handler.endObject(`object`)
    }

    @Throws(IOException::class)
    private fun readName(): String {
        if (current != '"') {
            throw expected("name")
        }
        return readStringInternal()
    }

    @Throws(IOException::class)
    private fun readNull() {
        handler.startNull()
        read()
        readRequiredChar('u')
        readRequiredChar('l')
        readRequiredChar('l')
        handler.endNull()
    }

    @Throws(IOException::class)
    private fun readTrue() {
        handler.startBoolean()
        read()
        readRequiredChar('r')
        readRequiredChar('u')
        readRequiredChar('e')
        handler.endBoolean(true)
    }

    @Throws(IOException::class)
    private fun readFalse() {
        handler.startBoolean()
        read()
        readRequiredChar('a')
        readRequiredChar('l')
        readRequiredChar('s')
        readRequiredChar('e')
        handler.endBoolean(false)
    }

    @Throws(IOException::class)
    private fun readRequiredChar(ch: Char) {
        if (!readChar(ch)) {
            throw expected("'$ch'")
        }
    }

    @Throws(IOException::class)
    private fun readString() {
        handler.startString()
        handler.endString(readStringInternal())
    }

    @Throws(IOException::class)
    private fun readStringInternal(): String {
        read()
        startCapture()
        while (current != '"') {
            if (current == '\\') {
                pauseCapture()
                readEscape()
                startCapture()
            } else if (current == null) {
                throw expected("valid string character")
            } else {
                read()
            }
        }
        val string = endCapture()
        read()
        return string
    }

    @Throws(IOException::class)
    private fun readEscape() {
        read()
        when (current) {
            '"', '/', '\\' -> captureBuffer!!.append(current.toString())
            'b' -> captureBuffer!!.append('\b')
            'f' -> captureBuffer!!.append('\u000C')
            'n' -> captureBuffer!!.append('\n')
            'r' -> captureBuffer!!.append('\r')
            't' -> captureBuffer!!.append('\t')
            'u' -> {
                val hexChars = CharArray(4)
                var i = 0
                while (i < 4) {
                    read()
                    if (!isHexDigit) {
                        throw expected("hexadecimal digit")
                    }
                    hexChars[i] = current!!
                    i++
                }
                captureBuffer!!.append(String(hexChars).toInt(16).toChar())
            }

            else -> throw expected("valid escape sequence")
        }
        read()
    }

    @Throws(IOException::class)
    private fun readNumber() {
        handler.startNumber()
        startCapture()
        readChar('-')
        val firstDigit = current
        if (!readDigit()) {
            throw expected("digit")
        }
        if (firstDigit != '0') {
            while (readDigit()) {
            }
        }
        readFraction()
        readExponent()
        handler.endNumber(endCapture())
    }

    @Throws(IOException::class)
    private fun readFraction(): Boolean {
        if (!readChar('.')) {
            return false
        }
        if (!readDigit()) {
            throw expected("digit")
        }
        while (readDigit()) {
        }
        return true
    }

    @Throws(IOException::class)
    private fun readExponent(): Boolean {
        if (!readChar('e') && !readChar('E')) {
            return false
        }
        if (!readChar('+')) {
            readChar('-')
        }
        if (!readDigit()) {
            throw expected("digit")
        }
        while (readDigit()) {
        }
        return true
    }

    @Throws(IOException::class)
    private fun readChar(ch: Char): Boolean {
        if (current != ch) {
            return false
        }
        read()
        return true
    }

    @Throws(IOException::class)
    private fun readDigit(): Boolean {
        if (!isDigit) {
            return false
        }
        read()
        return true
    }

    @Throws(IOException::class)
    private fun skipWhiteSpace() {
        while (isWhiteSpace) {
            read()
        }
    }

    @Throws(IOException::class)
    private fun read() {
        if (index == fill) {
            if (captureStart != -1) {
                captureBuffer!!.append(buffer, captureStart, fill - captureStart)
                captureStart = 0
            }
            bufferOffset += fill
            fill = reader!!.read(buffer, 0, buffer.size)
            index = 0
            if (fill == -1) {
                current = null
                index++
                return
            }
        }
        if (current == '\n') {
            line++
            lineOffset = bufferOffset + index
        }
        current = buffer[index++]
    }

    private fun startCapture() {
        if (captureBuffer == null) {
            captureBuffer = StringBuilder()
        }
        captureStart = index - 1
    }

    private fun pauseCapture() {
        val end = if (current == null) index else index - 1
        captureBuffer!!.append(buffer, captureStart, end - captureStart)
        captureStart = -1
    }

    private fun endCapture(): String {
        val start = captureStart
        val end = index - 1
        captureStart = -1
        if (captureBuffer!!.isNotEmpty()) {
            captureBuffer!!.append(buffer, start, end - start)
            val captured = captureBuffer.toString()
            captureBuffer!!.setLength(0)
            return captured
        }
        return String(buffer, start, end - start)
    }

    val location: Location
        get() {
            val offset = bufferOffset + index - 1
            val column = offset - lineOffset + 1
            return Location(offset, line, column)
        }

    private fun expected(expected: String): ParseException {
        if (isEndOfText) {
            return error("Unexpected end of input")
        }
        return error("Expected $expected")
    }

    private fun error(message: String): ParseException {
        return ParseException(message, location)
    }

    private val isWhiteSpace: Boolean
        get() = current == ' ' || current == '\t' || current == '\n' || current == '\r'

    private val isDigit: Boolean
        get() = current != null && (current!! in '0'..'9')

    private val isHexDigit: Boolean
        get() = current != null && (current!! in '0'..'9' || current!! in 'a'..'f' || current!! in 'A'..'F')

    private val isEndOfText: Boolean
        get() = current == null

    companion object {
        private const val MAX_NESTING_LEVEL = 1000
        private const val MIN_BUFFER_SIZE = 10
        private const val DEFAULT_BUFFER_SIZE = 1024
    }
}