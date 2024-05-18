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
// Derived from original JSONata4Java Signature code under this license:
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
import com.dashjoin.jsonata.JException
import com.dashjoin.jsonata.Utils
import java.io.Serializable
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.lang.model.type.NullType

/**
 * Manages signature related functions
 */
class Signature(signature: String, var functionName: String) : Serializable {
    class Param {
        var type: String? = null
        var regex: String? = null
        var context: Boolean = false
        var array: Boolean = false
        var subtype: String? = null
        var contextRegex: String? = null

        override fun toString(): String {
            return "Param $type regex=$regex ctx=$context array=$array"
        }
    }

    var _param: Param = Param()

    var _params: MutableList<Param> = ArrayList()
    var _prevParam: Param = _param
    var _regex: Pattern? = null
    var _signature: String = ""

    init {
        parseSignature(signature)
    }

    fun findClosingBracket(str: String, start: Int, openSymbol: Char, closeSymbol: Char): Int {
        // returns the position of the closing symbol (e.g. bracket) in a string
        // that balances the opening symbol at position start
        var depth = 1
        var position = start
        while (position < str.length) {
            position++
            val symbol = str[position]
            if (symbol == closeSymbol) {
                depth--
                if (depth == 0) {
                    // we're done
                    break // out of while loop
                }
            } else if (symbol == openSymbol) {
                depth++
            }
        }
        return position
    }

    fun getSymbol(value: Any?): String {
        val symbol = if (value == null) {
            "m"
        } else {
            // first check to see if this is a function
            if (Utils.isFunction(value) || Functions.isLambda(value) || (value is Pattern)) { //} instanceof JFunction || value instanceof Function) {
                "f"
            } else if (value is String) "s"
            else if (value is Number) "n"
            else if (value is Boolean) "b"
            else if (value is List<*>) "a"
            else if (value is Map<*, *>) "o"
            else if (value is NullType) // Uli: is this used???
                "l"
            else  // any value can be undefined, but should be allowed to match
                "m" // m for missing
        }
        return symbol
    }

    fun next() {
        _params.add(_param)
        _prevParam = _param
        _param = Param()
    }

    /**
     * Parses a function signature definition and returns a validation function
     *
     * @param {string}
     * signature - the signature between the <angle brackets>
     * @returns validation pattern
    </angle> */
    fun parseSignature(signature: String): Pattern? {
        // create a Regex that represents this signature and return a function that when
        // invoked,
        // returns the validated (possibly fixed-up) arguments, or throws a validation
        // error
        // step through the signature, one symbol at a time
        var position = 1
        while (position < signature.length) {
            val symbol = signature[position]
            if (symbol == ':') {
                // TODO figure out what to do with the return type
                // ignore it for now
                break
            }

            when (symbol) {
                's', 'n', 'b', 'l', 'o' -> {
                    // object
                    _param.regex = ("[" + symbol + "m]")
                    _param.type = ("" + symbol)
                    next()
                }

                'a' -> {
                    // array
                    // normally treat any value as singleton array
                    _param.regex = ("[asnblfom]")
                    _param.type = ("" + symbol)
                    _param.array = (true)
                    next()
                }

                'f' -> {
                    // function
                    _param.regex = ("f")
                    _param.type = ("" + symbol)
                    next()
                }

                'j' -> {
                    // any JSON type
                    _param.regex = ("[asnblom]")
                    _param.type = ("" + symbol)
                    next()
                }

                'x' -> {
                    // any type
                    _param.regex = ("[asnblfom]")
                    _param.type = ("" + symbol)
                    next()
                }

                '-' -> {
                    // use context if _param not supplied
                    _prevParam.context = true
                    _prevParam.regex += "?"
                }

                '?', '+' -> {
                    // one or more
                    _prevParam.regex += symbol
                }

                '(' -> {
                    // choice of types
                    // search forward for matching ')'
                    val endParen = findClosingBracket(signature, position, '(', ')')
                    val choice = signature.substring(position + 1, endParen)
                    if (choice.indexOf("<") == -1) {
                        // no _parameterized types, simple regex
                        _param.regex = ("[" + choice + "m]")
                    } else {
                        // TODO harder
                        throw RuntimeException("Choice groups containing parameterized types are not supported")
                    }
                    _param.type = ("($choice)")
                    position = endParen
                    next()
                }

                '<' -> {
                    // type _parameter - can only be applied to 'a' and 'f'
                    val test = _prevParam.type
                    if (test != null) {
                        val type: String = test //.asText();
                        if (type == "a" || type == "f") {
                            // search forward for matching '>'
                            val endPos = findClosingBracket(signature, position, '<', '>')
                            _prevParam.subtype = signature.substring(position + 1, endPos)
                            position = endPos
                        } else {
                            throw RuntimeException("Type parameters can only be applied to functions and arrays")
                        }
                    } else {
                        throw RuntimeException("Type parameters can only be applied to functions and arrays")
                    }
                }
            }
            position++
        } // end while processing symbols in signature


        var regexStr = "^"
        for (param in _params) {
            regexStr += "(" + param.regex + ")"
        }
        regexStr += "$"

        _regex = null
        try {
            _regex = Pattern.compile(regexStr)
            _signature = regexStr
        } catch (pse: PatternSyntaxException) {
            throw RuntimeException(pse.localizedMessage)
        }
        return _regex
    }

    fun throwValidationError(badArgs: List<*>?, badSig: String?, functionName: String?) {
        // to figure out where this went wrong we need apply each component of the
        // regex to each argument until we get to the one that fails to match
        var partialPattern: String? = "^"

        var goodTo = 0
        for (index in _params.indices) {
            partialPattern += _params[index].regex
            val tester = Pattern.compile(partialPattern)
            val match = tester.matcher(badSig)
            if (!match.matches()) {
                // failed here
                throw JException("T0410", -1, (goodTo + 1), functionName)
            }
            goodTo = match.end()
        }
        // if it got this far, it's probably because of extraneous arguments (we
        // haven't added the trailing '$' in the regex yet.
        throw JException("T0410", -1, (goodTo + 1), functionName)
    }

    fun validate(_args: Any, context: Any?): Any? {
        val result = ArrayList<Any>()

        val args = _args as List<*>
        var suppliedSig = ""
        for (arg in args) suppliedSig += getSymbol(arg)

        val isValid = _regex!!.matcher(suppliedSig)
        if (isValid != null && isValid.matches()) {
            val validatedArgs = ArrayList<Any?>()
            var argIndex = 0
            val index = 0
            for (_param in _params) {
                val param = _param
                var arg = if (argIndex < args.size) args[argIndex] else null
                val match = isValid.group(index + 1)
                if ("" == match) {
                    if (param.context && param.regex != null) {
                        // substitute context value for missing arg
                        // first check that the context value is the right type
                        val contextType = getSymbol(context)
                        // test contextType against the regex for this arg (without the trailing ?)
                        if (Pattern.matches(param.regex, contextType)) {
                            //if (param.contextRegex.test(contextType)) {
                            validatedArgs.add(context)
                        } else {
                            // context value not compatible with this argument
                            throw JException(
                                "T0411", -1,
                                context,
                                argIndex + 1
                            )
                        }
                    } else {
                        validatedArgs.add(arg)
                        argIndex++
                    }
                } else {
                    // may have matched multiple args (if the regex ends with a '+'
                    // split into single tokens
                    val singles = match.split("".toRegex()).filter { !it.isEmpty() }.toTypedArray()
                    for (single in singles) {
                        //match.split('').forEach(function (single) {
                        if (param.type == "a") {
                            if (single == "m") {
                                // missing (undefined)
                                arg = null
                            } else {
                                arg = if (argIndex < args.size) args[argIndex] else null
                                var arrayOK = true
                                // is there type information on the contents of the array?
                                if (param.subtype != null) {
                                    if (single != "a" && match != param.subtype) {
                                        arrayOK = false
                                    } else if (single == "a") {
                                        val argArr = arg as List<*>?
                                        if (argArr!!.size > 0) {
                                            val itemType = getSymbol(argArr[0])
                                            if (itemType != "" + param.subtype!![0]) { // TODO recurse further
                                                arrayOK = false
                                            } else {
                                                // make sure every item in the array is this type
                                                for (o in argArr) {
                                                    if (getSymbol(o) != itemType) {
                                                        arrayOK = false
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!arrayOK) {
                                    throw JException(
                                        "T0412", -1,
                                        arg,  //argIndex + 1,
                                        param.subtype //arraySignatureMapping[param.subtype]
                                    )
                                }
                                // the function expects an array. If it's not one, make it so
                                if (single != "a") {
                                    arg = listOf(arg)
                                }
                            }
                            validatedArgs.add(arg)
                            argIndex++
                        } else {
                            validatedArgs.add(arg)
                            argIndex++
                        }
                    }
                }
            }
            return validatedArgs
        }
        throwValidationError(args, suppliedSig, functionName)
        return null // dead code -> compiler happy
    }

    val numberOfArgs: Int
        get() = _params.size

    val minNumberOfArgs: Int
        /**
         * Returns the minimum # of arguments.
         * I.e. the # of all non-optional arguments.
         */
        get() {
            var res = 0
            for (p in _params) if (!p.regex!!.contains("?")) res++
            return res
        } /*
    ArrayNode validate(String functionName, ExprListContext args, ExpressionsVisitor expressionVisitor) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        String suppliedSig = "";
        for (Iterator<ExprContext> it = args.expr().iterator(); it.hasNext();) {
            ExprContext arg = it.next();
            suppliedSig += getSymbol(arg);
        }
        Matcher isValid = _regex.matcher(suppliedSig);
        if (isValid != null) {
            ArrayNode validatedArgs = JsonNodeFactory.instance.arrayNode();
            int argIndex = 0;
            int index = 0;
            for (Iterator<JsonNode> it = _params.iterator(); it.hasNext();) {
                ObjectNode param = (ObjectNode) it.next();
                JsonNode arg = expressionVisitor.visit(args.expr(argIndex));
                String match = isValid.group(index + 1);
                if ("".equals(match)) {
                    boolean useContext = (param.get("context") != null && param.get("context").asBoolean());
                    if (useContext) {
                        // substitute context value for missing arg
                        // first check that the context value is the right type
                        JsonNode context = expressionVisitor.getVariable("$");
                        String contextType = getSymbol(context);
                        // test contextType against the regex for this arg (without the trailing ?)
                        if (Pattern.matches(param.get("regex").asText(), contextType)) {
                            validatedArgs.add(context);
                        } else {
                            // context value not compatible with this argument
                            throw new EvaluateRuntimeException("Context value is not a compatible type with argument \""
                                + argIndex + 1 + "\" of function \"" + functionName + "\"");
                        }
                    } else {
                        validatedArgs.add(arg);
                        argIndex++;
                    }
                } else {
                    // may have matched multiple args (if the regex ends with a '+'
                    // split into single tokens
                    String[] singles = match.split("");
                    for (String single : singles) {
                        if ("a".equals(param.get("type").asText())) {
                            if ("m".equals(single)) {
                                // missing (undefined)
                                arg = null;
                            } else {
                                arg = expressionVisitor.visit(args.expr(argIndex));
                                boolean arrayOK = true;
                                // is there type information on the contents of the array?
                                String subtype = "undefined";
                                JsonNode testSubType = param.get("subtype");
                                if (testSubType != null) {
                                    subtype = testSubType.asText();
                                    if ("a".equals(single) == false && match.equals(subtype) == false) {
                                        arrayOK = false;
                                    } else if ("a".equals(single)) {
                                        ArrayNode argArray = (ArrayNode) arg;
                                        if (argArray.size() > 0) {
                                            String itemType = getSymbol(argArray.get(0));
                                            if (itemType.equals(subtype) == false) { // TODO recurse further
                                                arrayOK = false;
                                            } else {
                                                // make sure every item in the array is this type
                                                ArrayNode differentItems = JsonNodeFactory.instance.arrayNode();
                                                for (Object val : argArray) {
                                                    if (itemType.equals(getSymbol(val)) == false) {
                                                        differentItems.add(expressionVisitor.visit((ExprListContext) val));
                                                    }
                                                }
                                                ;
                                                arrayOK = (differentItems.size() == 0);
                                            }
                                        }
                                    }
                                }
                                if (!arrayOK) {
                                    JsonNode type = s_arraySignatureMapping.get(subtype);
                                    if (type == null) {
                                        type = JsonNodeFactory.instance.nullNode();
                                    }
                                    throw new EvaluateRuntimeException("Argument \"" + (argIndex + 1) + "\" of function \""
                                        + functionName + "\" must be an array of \"" + type.asText() + "\"");
                                }
                                // the function expects an array. If it's not one, make it so
                                if ("a".equals(single) == false) {
                                    ArrayNode wrappedArg = JsonNodeFactory.instance.arrayNode();
                                    wrappedArg.add(arg);
                                    arg = wrappedArg;
                                }
                            }
                            validatedArgs.add(arg);
                            argIndex++;
                        } else {
                            validatedArgs.add(arg);
                            argIndex++;
                        }
                    }
                }
                index++;
            }
            return validatedArgs;
        }
        throwValidationError(args, suppliedSig, functionName);
        // below just for the compiler as a runtime exception is thrown above
        return result;
    };
*/

    companion object {
        private const val serialVersionUID = -450755246855587271L

        @JvmStatic
        fun main(args: Array<String>) {
            val s = Signature("<s-:s>", "test") //<s-(sf)(sf)n?:s>");
            println(s._params)
        }
    }
}
