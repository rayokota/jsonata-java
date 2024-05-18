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
// Derived from Javascript code under this license:
/**
 * © Copyright IBM Corp. 2016, 2017 All Rights Reserved
 * Project name: JSONata
 * This project is licensed under the MIT License, see LICENSE
 */
package com.dashjoin.jsonata

import com.dashjoin.jsonata.Functions.append
import com.dashjoin.jsonata.Functions.call
import com.dashjoin.jsonata.Functions.functionClone
import com.dashjoin.jsonata.Functions.getFunction
import com.dashjoin.jsonata.Functions.isLambda
import com.dashjoin.jsonata.Functions.lookup
import com.dashjoin.jsonata.Functions.sort
import com.dashjoin.jsonata.Functions.string
import com.dashjoin.jsonata.Functions.toBoolean
import com.dashjoin.jsonata.Functions.validateInput
import com.dashjoin.jsonata.Parser.Infix
import com.dashjoin.jsonata.Utils.RangeList
import com.dashjoin.jsonata.Utils.convertNulls
import com.dashjoin.jsonata.Utils.convertNumber
import com.dashjoin.jsonata.Utils.createSequence
import com.dashjoin.jsonata.Utils.isArrayOfNumbers
import com.dashjoin.jsonata.Utils.isArrayOfStrings
import com.dashjoin.jsonata.Utils.isFunction
import com.dashjoin.jsonata.Utils.isNumeric
import com.dashjoin.jsonata.Utils.isSequence
import com.dashjoin.jsonata.utils.Signature
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.Callable
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Collectors

/**
 * @module JSONata
 * @description JSON query and transformation language
 */
class Jsonata {
    // Start of Evaluator code
    interface EntryCallback {
        fun callback(expr: Parser.Symbol?, input: Any?, environment: Frame)
    }

    interface ExitCallback {
        fun callback(expr: Parser.Symbol?, input: Any?, environment: Frame, result: Any?)
    }

    class Frame(val parent: Frame?) {
        val bindings: MutableMap<String, Any?> = LinkedHashMap()

        var isParallelCall: Boolean = false

        fun bind(name: String, `val`: Any?) {
            bindings[name] = `val`
        }

        fun bind(name: String, function: JFunction) {
            bind(name, function as Any)
            if (function.signature != null) function.signature!!.functionName = name
        }

        fun <R> bind(name: String, lambda: Fn0<R>) {
            bind(name, lambda as Any)
        }

        fun <A, R> bind(name: String, lambda: Fn1<A, R>) {
            bind(name, lambda as Any)
        }

        fun <A, B, R> bind(name: String, lambda: Fn2<A, B, R>) {
            bind(name, lambda as Any)
        }

        fun lookup(name: String): Any? {
            // Important: if we have a null value,
            // return it
            if (bindings.containsKey(name)) return bindings[name]
            if (parent != null) return parent.lookup(name)
            return null
        }

        /**
         * Sets the runtime bounds for this environment
         *
         * @param timeout Timeout in millis
         * @param maxRecursionDepth Max recursion depth
         */
        fun setRuntimeBounds(timeout: Long, maxRecursionDepth: Int) {
            Timebox(this, timeout, maxRecursionDepth)
        }

        fun setEvaluateEntryCallback(cb: EntryCallback) {
            bind("__evaluate_entry", cb)
        }

        fun setEvaluateExitCallback(cb: ExitCallback) {
            bind("__evaluate_exit", cb)
        }
    }

    /**
     * Evaluate expression against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    fun evaluate(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any? {
        // Thread safety:
        // Make sure each evaluate is executed on an instance per thread
        return perThreadInstance._evaluate(expr, input, environment)
    }

    fun _evaluate(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any? {
        var result: Any? = null

        // Store the current input
        // This is required by Functions.functionEval for current $eval() input context
        this.input = input

        if (parser!!.dbg) println("eval expr=" + expr + " type=" + expr!!.type) //+" input="+input);


        val entryCallback = environment!!.lookup("__evaluate_entry")
        if (entryCallback != null) {
            (entryCallback as EntryCallback).callback(expr, input, environment)
        }

        if (expr!!.type != null) when (expr.type) {
            "path" -> result =  /* await */evaluatePath(expr, input, environment)
            "binary" -> result =  /* await */evaluateBinary(expr, input, environment)
            "unary" -> result =  /* await */evaluateUnary(expr, input, environment)
            "name" -> {
                result = evaluateName(expr, input, environment)
                if (parser.dbg) println("evalName $result")
            }

            "string", "number", "value" -> result = evaluateLiteral(expr) //, input, environment);
            "wildcard" -> result = evaluateWildcard(expr, input) //, environment);
            "descendant" -> result = evaluateDescendants(expr, input) //, environment);
            "parent" -> result = environment.lookup(expr.slot!!.label!!)
            "condition" -> result =  /* await */evaluateCondition(expr, input, environment)
            "block" -> result =  /* await */evaluateBlock(expr, input, environment)
            "bind" -> result =  /* await */evaluateBindExpression(expr, input, environment)
            "regex" -> result = evaluateRegex(expr) //, input, environment);
            "function" -> result =  /* await */evaluateFunction(expr, input, environment, null)
            "variable" -> result = evaluateVariable(expr, input, environment)
            "lambda" -> result = evaluateLambda(expr, input, environment)
            "partial" -> result =  /* await */evaluatePartialApplication(expr, input, environment)
            "apply" -> result =  /* await */evaluateApplyExpression(expr, input, environment)
            "transform" -> result = evaluateTransformExpression(expr, input, environment)
        }

        if (expr.predicate != null) for (ii in expr.predicate!!.indices) {
            result =  /* await */evaluateFilter(expr.predicate!![ii].expr, result, environment)
        }

        if (expr.type != "path" && expr.group != null) {
            result =  /* await */evaluateGroupExpression(expr.group, result, environment)
        }

        val exitCallback = environment.lookup("__evaluate_exit")
        if (exitCallback != null) {
            (exitCallback as ExitCallback).callback(expr, input, environment, result)
        }


        // mangle result (list of 1 element -> 1 element, empty list -> null)
        if (result != null && isSequence(result) && !(result as Utils.JList<*>).tupleStream) {
            val _result = result
            if (expr.keepArray) {
                _result.keepSingleton = true
            }
            if (_result.isEmpty()) {
                result = null
            } else if (_result.size == 1) {
                result = if (_result.keepSingleton) _result else _result[0]
            }
        }

        return result
    }

    /**
     * Evaluate path expression against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    /* async */
    fun evaluatePath(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any? {
        var inputSequence: List<*>?
        // expr is an array of steps
        // if the first step is a variable reference ($...), including root reference ($$),
        //   then the path is absolute rather than relative
        inputSequence = if (input is List<*> && expr!!.steps!![0].type != "variable") {
            input
        } else {
            // if input is not an array, make it so
            createSequence(input)
        }

        var resultSequence: Any? = null
        var isTupleStream = false
        var tupleBindings: List<Map<*, *>>? = null

        // evaluate each step in turn
        for (ii in expr!!.steps!!.indices) {
            val step = expr.steps!![ii]

            if (step.tuple != null) {
                isTupleStream = true
            }

            // if the first step is an explicit array constructor, then just evaluate that (i.e. don"t iterate over a context array)
            if (ii == 0 && step.consarray) {
                resultSequence =  /* await */evaluate(step, inputSequence, environment) as List<*>?
            } else {
                if (isTupleStream) {
                    tupleBindings =  /* await */
                        evaluateTupleStep(step, inputSequence, tupleBindings as List<Map<String, Any>>?, environment) as List<Map<*,*>>?
                } else {
                    resultSequence =  /* await */
                        evaluateStep(step, inputSequence, environment, ii == expr.steps!!.size - 1)
                }
            }

            if (!isTupleStream && (resultSequence == null || (resultSequence as List<*>).size == 0)) {
                break
            }

            if (step.focus == null) {
                inputSequence = resultSequence as List<*>?
            }
        }

        if (isTupleStream) {
            if (expr.tuple != null) {
                // tuple stream is carrying ancestry information - keep this
                resultSequence = tupleBindings
            } else {
                resultSequence = createSequence()
                for (ii in tupleBindings!!.indices) {
                    (resultSequence as MutableList<Any?>).add(tupleBindings[ii]["@"])
                }
            }
        }

        if (expr.keepSingletonArray) {
            // If we only got an ArrayList, convert it so we can set the keepSingleton flag

            if (resultSequence !is Utils.JList<*>) resultSequence = Utils.JList(resultSequence as List<*>?)

            // if the array is explicitly constructed in the expression and marked to promote singleton sequences to array
            if ((resultSequence is Utils.JList<*>) && resultSequence.cons && !resultSequence.sequence) {
                resultSequence = createSequence(resultSequence)
            }
            (resultSequence as Utils.JList<*>).keepSingleton = true
        }

        if (expr.group != null) {
            resultSequence =  /* await */
                evaluateGroupExpression(expr.group, if (isTupleStream) tupleBindings else resultSequence, environment)
        }

        return resultSequence
    }

    fun createFrameFromTuple(environment: Frame?, tuple: Map<String, Any>?): Frame {
        val frame = createFrame(environment)
        if (tuple != null) for (prop in tuple.keys) {
            frame.bind(prop, tuple[prop]!!)
        }
        return frame
    }

    /**
     * Evaluate a step within a path
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @param {boolean} lastStep - flag the last step in a path
     * @returns {*} Evaluated input data
     */
    /* async */
    fun evaluateStep(expr: Parser.Symbol, input: Any?, environment: Frame?, lastStep: Boolean): Any? {
        var result: Any?
        if (expr.type == "sort") {
            result =  /* await */evaluateSortExpression(expr, input, environment)
            if (expr.stages != null) {
                result =  /* await */evaluateStages(expr.stages, result!!, environment)
            }
            return result
        }

        result = createSequence()

        for (ii in (input as List<*>?)!!.indices) {
            var res =  /* await */evaluate(
                expr,
                input!![ii], environment
            )
            if (expr.stages != null) {
                for (ss in expr.stages!!.indices) {
                    res =  /* await */evaluateFilter(expr.stages!![ss].expr, res, environment)
                }
            }
            if (res != null) {
                (result as MutableList<Any?>).add(res)
            }
        }

        var resultSequence = createSequence()
        if (lastStep && (result as List<*>).size == 1 && (result[0] is List<*>) && !isSequence(
                result[0]
            )
        ) {
            resultSequence = result[0] as MutableList<Any?>
        } else {
            // flatten the sequence
            for (res in result) {
                if (res !is List<*> || (res is Utils.JList<*> && res.cons)) {
                    // it's not an array - just push into the result sequence
                    resultSequence.add(res)
                } else {
                    // res is a sequence - flatten it into the parent sequence
                    resultSequence.addAll(res as List<Any>)
                }
            }
        }

        return resultSequence
    }

    /* async */
    fun evaluateStages(stages: List<Parser.Symbol>?, input: Any, environment: Frame?): Any {
        var result = input
        for (ss in stages!!.indices) {
            val stage = stages[ss]
            when (stage.type) {
                "filter" -> result =  /* await */evaluateFilter(stage.expr, result, environment)
                "index" -> {
                    var ee = 0
                    while (ee < (result as List<*>).size) {
                        val tuple = result[ee]!!
                        (tuple as MutableMap<String, Any>)["" + stage.value] = ee
                        ee++
                    }
                }
            }
        }
        return result
    }

    /**
     * Evaluate a step within a path
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} tupleBindings - The tuple stream
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    /* async */
    fun evaluateTupleStep(
        expr: Parser.Symbol,
        input: List<*>?,
        tupleBindings: List<Map<String, Any>>?,
        environment: Frame?
    ): Any? {
        var tupleBindings: List<Map<String, Any>>? = tupleBindings
        var result: MutableList<*>? = null
        if (expr.type == "sort") {
            if (tupleBindings != null) {
                result =  /* await */evaluateSortExpression(expr, tupleBindings, environment) as MutableList<*>?
            } else {
                val sorted =  /* await */evaluateSortExpression(expr, input, environment) as List<*>?
                result = createSequence()
                (result as Utils.JList<*>).tupleStream = true
                for (ss in sorted!!.indices) {
                    val tuple = java.util.Map.of(
                        "@", sorted!![ss],
                        expr.index, ss
                    )
                    result.add(tuple)
                }
            }
            if (expr.stages != null) {
                result =  /* await */evaluateStages(expr.stages, result!!, environment) as MutableList<*>
            }
            return result
        }

        result = createSequence()
        (result as Utils.JList<*>).tupleStream = true
        var stepEnv = environment
        if (tupleBindings == null) {
            tupleBindings = input!!.stream().filter { item: Any? -> item != null }.map(
                Function { item: Any? -> java.util.Map.of("@", item) })
                .collect(Collectors.toList<Any>()) as List<Map<String, Any>>
        }

        for (ee in tupleBindings!!.indices) {
            stepEnv = createFrameFromTuple(environment, tupleBindings[ee])
            val _res =  /* await */evaluate(expr, tupleBindings[ee]["@"], stepEnv)
            // res is the binding sequence for the output tuple stream
            if (_res != null) { //(typeof res !== "undefined") {
                var res: MutableList<*>
                if (_res !is List<*>) {
                    res = ArrayList<Any>()
                    res.add(_res)
                } else {
                    res = _res as MutableList<*>
                }
                for (bb in res.indices) {
                    val tuple: MutableMap<Any, Any> = LinkedHashMap<Any, Any>()
                    tuple.putAll(tupleBindings[ee])
                    //Object.assign(tuple, tupleBindings[ee]);
                    if ((res is Utils.JList<*>) && res.tupleStream) {
                        tuple.putAll(res[bb] as Map<String, Any>)
                    } else {
                        if (expr.focus != null) {
                            tuple[expr.focus!!] = res[bb]!!
                            tuple["@"] = tupleBindings[ee]["@"]!!
                        } else {
                            tuple["@"] = res[bb]!!
                        }
                        if (expr.index != null) {
                            tuple[expr.index!!] = bb
                        }
                        if (expr.ancestor != null) {
                            tuple[expr.ancestor!!.label!!] = tupleBindings[ee]["@"]!!
                        }
                    }
                    result.add(tuple)
                }
            }
        }

        if (expr.stages != null) {
            result =  /* await */evaluateStages(expr.stages, result, environment) as MutableList<*>
        }

        return result
    }

    /**
     * Apply filter predicate to input data
     * @param {Object} predicate - filter expression
     * @param {Object} input - Input data to apply predicates against
     * @param {Object} environment - Environment
     * @returns {*} Result after applying predicates
     */
    /* async */
    fun evaluateFilter(_predicate: Any?, input: Any?, environment: Frame?): Any {
        var input = input
        val predicate = _predicate as Parser.Symbol?
        var results = createSequence()
        if (input is Utils.JList<*> && input.tupleStream) {
            (results as Utils.JList<*>).tupleStream = true
        }
        if (input !is List<*>) { // isArray
            input = createSequence(input)
        }
        if (predicate!!.type == "number") {
            var index = (predicate.value as Number?)!!.toInt() // round it down - was Math.floor
            if (index < 0) {
                // count in from end of array
                index = (input as List<*>).size + index
            }
            val item = if (index < (input as List<*>).size) input[index] else null
            if (item != null) {
                if (item is List<*>) {
                    results = item as MutableList<Any?>
                } else {
                    results.add(item)
                }
            }
        } else {
            for (index in (input as List<*>).indices) {
                val item = input[index]
                var context: Any? = item
                var env = environment
                if (input is Utils.JList<*> && input.tupleStream) {
                    context = (item as Map<*, *>)["@"]
                    env = createFrameFromTuple(environment, item as Map<String, Any>)
                }
                var res =  /* await */evaluate(predicate, context, env)
                if (isNumeric(res)) {
                    res = createSequence(res)
                }
                if (isArrayOfNumbers(res)) {
                    for (ires in (res as List<*>?)!!) {
                        // round it down
                        var ii = (ires as Number).toInt() // Math.floor(ires);
                        if (ii < 0) {
                            // count in from end of array
                            ii = input.size + ii
                        }
                        if (ii == index) {
                            results.add(item)
                        }
                    }
                } else if (boolize(res)) { // truthy
                    results.add(item)
                }
            }
        }
        return results
    }

    /**
     * Evaluate binary expression against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    /* async */
    fun evaluateBinary(_expr: Parser.Symbol?, input: Any?, environment: Frame?): Any? {
        val expr = _expr as Infix?
        var result: Any? = null
        val lhs =  /* await */evaluate(expr!!.lhs, input, environment)
        val op = "" + expr.value

        if (op == "and" || op == "or") {
            //defer evaluation of RHS to allow short-circuiting

            val evalrhs: Callable<*> =  /* async */
                Callable { evaluate(expr.rhs, input, environment) }

            try {
                return  /* await */evaluateBooleanExpression(lhs, evalrhs, op)
            } catch (err: Exception) {
                if (err !is JException) throw JException("Unexpected", expr.position)
                //err.position = expr.position;
                //err.token = op;
                throw err
            }
        }

        val rhs =  /* await */evaluate(expr.rhs, input, environment) //evalrhs();
        try {
            result = when (op) {
                "+", "-", "*", "/", "%" -> evaluateNumericExpression(lhs, rhs, op)
                "=", "!=" -> evaluateEqualityExpression(lhs, rhs, op)
                "<", "<=", ">", ">=" -> evaluateComparisonExpression(lhs, rhs, op)
                "&" -> evaluateStringConcat(lhs, rhs)
                ".." -> evaluateRangeExpression(lhs, rhs)
                "in" -> evaluateIncludesExpression(lhs, rhs)
                else -> throw JException("Unexpected operator $op", expr.position)
            }
        } catch (err: Exception) {
            //err.position = expr.position;
            //err.token = op;
            throw err
        }
        return result
    }

    /**
     * Evaluate unary expression against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    /* async */
    fun evaluateUnary(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any? {
        var result: Any? = null

        when ("" + expr!!.value) {
            "-" -> {
                result =  /* await */evaluate(expr.expression, input, environment)
                result = if (result == null) { //(typeof result === "undefined") {
                    null
                } else if (isNumeric(result)) {
                    convertNumber(-(result as Number).toDouble())
                } else {
                    throw JException(
                        "D1002",  //stack: (new Error()).stack,
                        expr.position,
                        expr.value,
                        result
                    )
                }
            }

            "[" -> {
                // array constructor - evaluate each item
                result = Utils.JList<Any>() // [];
                var idx = 0
                for (item in expr.expressions!!) {
                    environment!!.isParallelCall = idx > 0
                    val value = evaluate(item, input, environment)
                    if (value != null) {
                        if (("" + item.value) == "[") (result as MutableList<Any>?)!!.add(value)
                        else result = append(result, value)
                    }
                    idx++
                }
                if (expr.consarray) {
                    if (result !is Utils.JList<*>) result = Utils.JList(result as List<*>?)
                    //System.out.println("const "+result);
                    (result as Utils.JList<*>).cons = true
                }
            }

            "{" ->                 // object constructor - apply grouping
                result =  /* await */evaluateGroupExpression(expr, input, environment)
        }
        return result
    }

    /**
     * Evaluate name object against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    fun evaluateName(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any? {
        // lookup the "name" item in the input
        return lookup(input, expr!!.value as String?)
    }

    /**
     * Evaluate literal against input data
     * @param {Object} expr - JSONata expression
     * @returns {*} Evaluated input data
     */
    fun evaluateLiteral(expr: Parser.Symbol?): Any? {
        return if (expr!!.value != null) expr.value else NULL_VALUE
    }

    /**
     * Evaluate wildcard against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @returns {*} Evaluated input data
     */
    fun evaluateWildcard(expr: Parser.Symbol?, input: Any?): Any? {
        var input = input
        var results: MutableList<Any?> = createSequence()
        if ((input is Utils.JList<*>) && input.outerWrapper && (input.size > 0)) {
            input = input[0]
        }
        if (input != null && input is Map<*, *>) { // typeof input === "object") {
            for (key in input.keys) {
                // Object.keys(input).forEach(Object (key) {
                var value = input[key]
                if ((value is List<*>)) {
                    value = flatten(value, null)
                    results = append(results, value) as MutableList<Any?>
                } else {
                    results!!.add(value)
                }
            }
        } else if (input is List<*>) {
            // Java: need to handle List separately
            for (value in input) {
                if ((value is List<*>)) {
                    var v = flatten(value, null)
                    results = append(results, v) as MutableList<Any?>
                } else if (value is Map<*, *>) {
                    // Call recursively do decompose the map
                    results!!.addAll((evaluateWildcard(expr, value) as List<Any>?)!!)
                } else {
                    results!!.add(value!!)
                }
            }
        }

        // result = normalizeSequence(results);
        return results
    }

    /**
     * Returns a flattened array
     * @param {Array} arg - the array to be flatten
     * @param {Array} flattened - carries the flattened array - if not defined, will initialize to []
     * @returns {Array} - the flattened array
     */
    fun flatten(arg: Any, flattened: MutableList<Any>?): Any {
        var flattened = flattened
        if (flattened == null) {
            flattened = ArrayList<Any>()
        }
        if (arg is List<*>) {
            for (item in arg) {
                flatten(item!!, flattened)
            }
        } else {
            flattened.add(arg)
        }
        return flattened
    }

    /**
     * Evaluate descendants against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @returns {*} Evaluated input data
     */
    fun evaluateDescendants(expr: Parser.Symbol?, input: Any?): Any? {
        var result: Any? = null
        val resultSequence = createSequence()
        if (input != null) {
            // traverse all descendants of this object/array
            recurseDescendants(input, resultSequence)
            result = if (resultSequence.size == 1) {
                resultSequence[0]
            } else {
                resultSequence
            }
        }
        return result
    }

    /**
     * Recurse through descendants
     * @param {Object} input - Input data
     * @param {Object} results - Results
     */
    fun recurseDescendants(input: Any?, results: MutableList<Any?>) {
        // this is the equivalent of //* in XPath
        if (input != null && input !is List<*>) {
            results.add(input)
        }
        if (input is List<*>) {
            for (member in input) { //input.forEach(Object (member) {
                recurseDescendants(member, results)
            }
        } else if (input != null && input is Map<*, *>) {
            //Object.keys(input).forEach(Object (key) {
            for (key in input.keys) {
                recurseDescendants(input[key], results)
            }
        }
    }

    /**
     * Evaluate numeric expression against input data
     * @param {Object} lhs - LHS value
     * @param {Object} rhs - RHS value
     * @param {Object} op - opcode
     * @returns {*} Result
     */
    fun evaluateNumericExpression(_lhs: Any?, _rhs: Any?, op: String?): Any? {
        var result = 0.0

        if (_lhs != null && !isNumeric(_lhs)) {
            throw JException(
                "T2001", -1,
                op, _lhs
            )
        }
        if (_rhs != null && !isNumeric(_rhs)) {
            throw JException(
                "T2002", -1,
                op, _rhs
            )
        }

        if (_lhs == null || _rhs == null) {
            // if either side is undefined, the result is undefined
            return null
        }

        //System.out.println("op22 "+op+" "+_lhs+" "+_rhs);
        val lhs = (_lhs as Number).toDouble()
        val rhs = (_rhs as Number).toDouble()

        when (op) {
            "+" -> result = lhs + rhs
            "-" -> result = lhs - rhs
            "*" -> result = lhs * rhs
            "/" -> result = lhs / rhs
            "%" -> result = lhs % rhs
        }
        return convertNumber(result)
    }

    /**
     * Evaluate equality expression against input data
     * @param {Object} lhs - LHS value
     * @param {Object} rhs - RHS value
     * @param {Object} op - opcode
     * @returns {*} Result
     */
    fun evaluateEqualityExpression(lhs: Any?, rhs: Any?, op: String?): Any? {
        var lhs = lhs
        var rhs = rhs
        var result: Any? = null

        // type checks
        val ltype = lhs?.javaClass?.simpleName
        val rtype = rhs?.javaClass?.simpleName

        if (ltype == null || rtype == null) {
            // if either side is undefined, the result is false
            return false
        }

        // JSON might come with integers,
        // convert all to double...
        // FIXME: semantically OK?
        if (lhs is Number) lhs = lhs.toDouble()
        if (rhs is Number) rhs = rhs.toDouble()

        when (op) {
            "=" -> result = lhs == rhs // isDeepEqual(lhs, rhs);
            "!=" -> result = lhs != rhs // !isDeepEqual(lhs, rhs);
        }
        return result
    }

    /**
     * Evaluate comparison expression against input data
     * @param {Object} lhs - LHS value
     * @param {Object} rhs - RHS value
     * @param {Object} op - opcode
     * @returns {*} Result
     */
    fun evaluateComparisonExpression(lhs: Any?, rhs: Any?, op: String?): Any? {
        var lhs = lhs
        var rhs = rhs
        var result: Any? = null

        // type checks
        val lcomparable = lhs == null || lhs is String || lhs is Number
        val rcomparable = rhs == null || rhs is String || rhs is Number

        // if either aa or bb are not comparable (string or numeric) values, then throw an error
        if (!lcomparable || !rcomparable) {
            throw JException(
                "T2010",
                0,  //position,
                //stack: (new Error()).stack,
                op, lhs ?: rhs
            )
        }

        // if either side is undefined, the result is undefined
        if (lhs == null || rhs == null) {
            return null
        }


        //if aa and bb are not of the same type
        if (lhs.javaClass != rhs.javaClass) {
            if (lhs is Number && rhs is Number) {
                // Java : handle Double / Integer / Long comparisons
                // convert all to double -> loss of precision (64-bit long to double) be a problem here?
                lhs = lhs.toDouble()
                rhs = rhs.toDouble()
            } else throw JException(
                "T2009",
                0,  // location?
                // stack: (new Error()).stack,
                lhs,
                rhs
            )
        }

        val _lhs = lhs as Comparable<Any>

        when (op) {
            "<" -> result = _lhs.compareTo(rhs) < 0
            "<=" -> result = _lhs.compareTo(rhs) <= 0 //lhs <= rhs;
            ">" -> result = _lhs.compareTo(rhs) > 0 // lhs > rhs;
            ">=" -> result = _lhs.compareTo(rhs) >= 0 // lhs >= rhs;
        }
        return result
    }

    /**
     * Inclusion operator - in
     *
     * @param {Object} lhs - LHS value
     * @param {Object} rhs - RHS value
     * @returns {boolean} - true if lhs is a member of rhs
     */
    fun evaluateIncludesExpression(lhs: Any?, rhs: Any?): Any {
        var rhs = rhs
        var result = false

        if (lhs == null || rhs == null) {
            // if either side is undefined, the result is false
            return false
        }

        if (rhs !is List<*>) {
            val _rhs = ArrayList<Any>()
            _rhs.add(rhs)
            rhs = _rhs
        }

        for (i in (rhs as List<*>?)!!.indices) {
            if ((rhs as List<*>?)!![i] == lhs) {
                result = true
                break
            }
        }

        return result
    }

    /**
     * Evaluate boolean expression against input data
     * @param {Object} lhs - LHS value
     * @param {Function} evalrhs - Object to evaluate RHS value
     * @param {Object} op - opcode
     * @returns {*} Result
     */
    /* async */
    @Throws(Exception::class)
    fun evaluateBooleanExpression(lhs: Any?, evalrhs: Callable<*>, op: String?): Any? {
        var result: Any? = null

        val lBool = boolize(lhs)

        when (op) {
            "and" -> result = lBool && boolize( /* await */evalrhs.call())
            "or" -> result = lBool || boolize( /* await */evalrhs.call())
        }
        return result
    }

    /**
     * Evaluate string concatenation against input data
     * @param {Object} lhs - LHS value
     * @param {Object} rhs - RHS value
     * @returns {string|*} Concatenated string
     */
    fun evaluateStringConcat(lhs: Any?, rhs: Any?): Any {
        val result: String

        var lstr: String? = ""
        var rstr: String? = ""
        if (lhs != null) {
            lstr = string(lhs, null)
        }
        if (rhs != null) {
            rstr = string(rhs, null)
        }

        result = lstr + rstr
        return result
    }

    internal data class GroupEntry(var data: Any? = null, var exprIndex: Int = 0)

    /**
     * Evaluate group expression against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {{}} Evaluated input data
     */
    /* async */
    fun evaluateGroupExpression(expr: Parser.Symbol?, _input: Any?, environment: Frame?): Any {
        var _input = _input
        val result = LinkedHashMap<Any, Any>()
        val groups = LinkedHashMap<Any, GroupEntry?>()
        val reduce = if ((_input is Utils.JList<*>) && _input.tupleStream) true else false
        // group the input sequence by "key" expression
        if (_input !is List<*>) {
            _input = createSequence(_input)
        }
        val input = _input as MutableList<Any?>

        // if the array is empty, add an undefined entry to enable literal JSON object to be generated
        if (input.isEmpty()) {
            input.add(null)
        }

        for (itemIndex in input.indices) {
            val item = input[itemIndex]
            val env = if (reduce) createFrameFromTuple(environment, item as Map<String, Any>?) else environment!!
            for (pairIndex in expr!!.lhsObject!!.indices) {
                val pair = expr.lhsObject!![pairIndex]
                val key =  /* await */evaluate(pair[0], if (reduce) (item as Map<*, *>)["@"] else item, env)
                // key has to be a string
                if (key != null && key !is String) {
                    throw JException(
                        "T1003",  //stack: (new Error()).stack,
                        expr.position,
                        key
                    )
                }

                if (key != null) {
                    val entry = GroupEntry(item, pairIndex)
                    if (groups[key] != null) {
                        // a value already exists in this slot
                        if (groups[key]!!.exprIndex != pairIndex) {
                            // this key has been generated by another expression in this group
                            // when multiple key expressions evaluate to the same key, then error D1009 must be thrown
                            throw JException(
                                "D1009",  //stack: (new Error()).stack,
                                expr.position,
                                key
                            )
                        }

                        // append it as an array
                        groups[key]!!.data = append(groups[key]!!.data, item)
                    } else {
                        groups[key] = entry
                    }
                }
            }
        }

        // iterate over the groups to evaluate the "value" expression
        //let generators = /* await */ Promise.all(Object.keys(groups).map(/* async */ (key, idx) => {
        var idx = 0
        for ((key, entry) in groups) {
            var context = entry!!.data
            var env = environment
            if (reduce) {
                val tuple = reduceTupleStream(entry.data)
                context = (tuple as Map<String, Any>)["@"]
                (tuple as MutableMap<String, Any>).remove("@")
                env = createFrameFromTuple(environment, tuple)
            }
            env!!.isParallelCall = idx > 0
            //return [key, /* await */ evaluate(expr.lhs[entry.exprIndex][1], context, env)];
            val res = evaluate(expr!!.lhsObject!![entry.exprIndex][1], context, env)
            if (res != null) result[key] = res

            idx++
        }

        //  for (let generator of generators) {
        //      var [key, value] = /* await */ generator;
        //      if(typeof value !== "undefined") {
        //          result[key] = value;
        //      }
        //  }
        return result
    }

    fun reduceTupleStream(_tupleStream: Any?): Any? {
        if (_tupleStream !is List<*>) {
            return _tupleStream
        }
        val tupleStream: List<Map<String, Any>> = _tupleStream as List<Map<String, Any>>

        val result = LinkedHashMap<Any, Any?>()
        result.putAll(tupleStream[0])

        //Object.assign(result, tupleStream[0]);
        for (ii in 1 until tupleStream.size) {
            val el = tupleStream[ii]
            for (prop in el.keys) {
                //             for(const prop in tupleStream[ii]) {

                result[prop] = append(result[prop], el[prop])

                //               result[prop] = fn.append(result[prop], tupleStream[ii][prop]);
            }
        }
        return result
    }

    /**
     * Evaluate range expression against input data
     * @param {Object} lhs - LHS value
     * @param {Object} rhs - RHS value
     * @returns {Array} Resultant array
     */
    fun evaluateRangeExpression(lhs: Any?, rhs: Any?): Any? {
        val result: Any? = null

        if (lhs != null && (lhs !is Long && lhs !is Int)) {
            throw JException(
                "T2003",  //stack: (new Error()).stack,
                -1,
                lhs
            )
        }
        if (rhs != null && (rhs !is Long && rhs !is Int)) {
            throw JException(
                "T2004",  //stack: (new Error()).stack,
                -1,
                rhs
            )
        }

        if (rhs == null || lhs == null) {
            // if either side is undefined, the result is undefined
            return result
        }

        val _lhs = (lhs as Number).toLong()
        val _rhs = (rhs as Number).toLong()

        if (_lhs > _rhs) {
            // if the lhs is greater than the rhs, return undefined
            return result
        }

        // limit the size of the array to ten million entries (1e7)
        // this is an implementation defined limit to protect against
        // memory and performance issues.  This value may increase in the future.
        val size = _rhs - _lhs + 1
        if (size > 1e7) {
            throw JException(
                "D2014",  //stack: (new Error()).stack,
                -1,
                size
            )
        }

        return RangeList(_lhs, _rhs)
    }

    /**
     * Evaluate bind expression against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    /* async */
    fun evaluateBindExpression(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any? {
        // The RHS is the expression to evaluate
        // The LHS is the name of the variable to bind to - should be a VARIABLE token (enforced by parser)
        val value =  /* await */evaluate(expr!!.rhs, input, environment)
        environment!!.bind("" + expr.lhs!!.value, value)
        return value
    }

    /**
     * Evaluate condition against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    /* async */
    fun evaluateCondition(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any? {
        var result: Any? = null
        val condition =  /* await */evaluate(expr!!.condition, input, environment)
        if (boolize(condition)) {
            result =  /* await */evaluate(expr.then, input, environment)
        } else if (expr._else != null) {
            result =  /* await */evaluate(expr._else, input, environment)
        }
        return result
    }

    /**
     * Evaluate block against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    /* async */
    fun evaluateBlock(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any? {
        var result: Any? = null
        // create a new frame to limit the scope of variable assignments
        // TODO, only do this if the post-parse stage has flagged this as required
        val frame = createFrame(environment)
        // invoke each expression in turn
        // only return the result of the last one
        for (ex in expr!!.expressions!!) {
            result =  /* await */evaluate(ex, input, frame)
        }

        return result
    }

    /**
     * Prepare a regex
     * @param {Object} expr - expression containing regex
     * @returns {Function} Higher order Object representing prepared regex
     */
    fun evaluateRegex(expr: Parser.Symbol?): Any? {
        // Note: in Java we just use the compiled regex Pattern
        // The apply functions need to take care to evaluate
        return expr!!.value
    }

    /**
     * Evaluate variable against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    fun evaluateVariable(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any? {
        // lookup the variable value in the environment
        var result: Any? = null
        // if the variable name is empty string, then it refers to context value
        if (expr!!.value == "") {
            // Empty string == "$" !
            result = if (input is Utils.JList<*> && input.outerWrapper) input[0] else input
        } else {
            result = environment!!.lookup(expr.value as String)
            if (parser!!.dbg) println("variable name=" + expr.value + " val=" + result)
        }
        return result
    }

    /**
     * sort / order-by operator
     * @param {Object} expr - AST for operator
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Ordered sequence
     */
    /* async */
    fun evaluateSortExpression(expr: Parser.Symbol, input: Any?, environment: Frame?): Any? {
        val result: Any?

        // evaluate the lhs, then sort the results in order according to rhs expression
        val lhs = input as List<*>?
        val isTupleSort = if ((input is Utils.JList<*> && input.tupleStream)) true else false

        // sort the lhs array
        // use comparator function
        val comparator: Comparator<*> =
            Comparator<Any?> { a, b -> // expr.terms is an array of order-by in priority order
                var comp = 0
                var index = 0
                while (comp == 0 && index < expr.terms!!.size) {
                    val term = expr.terms!![index]
                    //evaluate the sort term in the context of a
                    var context = a
                    var env = environment
                    if (isTupleSort) {
                        context = (a as Map<String, Any>)["@"]
                        env = createFrameFromTuple(environment, a)
                    }
                    val aa =  /* await */evaluate(term.expression, context, env)

                    //evaluate the sort term in the context of b
                    context = b
                    env = environment
                    if (isTupleSort) {
                        context = (b as Map<String, Any>)["@"]
                        env = createFrameFromTuple(environment, b)
                    }
                    val bb =  /* await */evaluate(term.expression, context, env)


                    // type checks
                    //  var atype = typeof aa;
                    //  var btype = typeof bb;
                    // undefined should be last in sort order
                    if (aa == null) {
                        // swap them, unless btype is also undefined
                        comp = if ((bb == null)) 0 else 1
                        index++
                        continue
                    }
                    if (bb == null) {
                        comp = -1
                        index++
                        continue
                    }


                    // if aa or bb are not string or numeric values, then throw an error
                    if (!(aa is Number || aa is String) ||
                        !(bb is Number || bb is String)
                    ) {
                        throw JException(
                            "T2008",
                            expr.position,
                            aa,
                            bb
                        )
                    }


                    //if aa and bb are not of the same type
                    var sameType = false
                    if (aa is Number && bb is Number) sameType = true
                    else if (aa.javaClass.isAssignableFrom(bb.javaClass) ||
                        bb.javaClass.isAssignableFrom(aa.javaClass)
                    ) {
                        sameType = true
                    }

                    if (!sameType) {
                        throw JException(
                            "T2007",
                            expr.position,
                            aa,
                            bb
                        )
                    }
                    comp = if (aa == bb) {
                        // both the same - move on to next term
                        index++
                        continue
                    } else if ((aa as Comparable<Any>).compareTo(bb) < 0) {
                        -1
                    } else {
                        1
                    }
                    if (term.descending == true) {
                        comp = -comp
                    }
                    index++
                }
                // only swap a & b if comp equals 1
                // return comp == 1;
                comp
            }


        //  var focus = {
        //      environment: environment,
        //      input: input
        //  };
        //  // the `focus` is passed in as the `this` for the invoked function
        //  result = /* await */ fn.sort.apply(focus, [lhs, comparator]);
        result = sort(lhs, comparator)
        return result
    }

    /**
     * create a transformer function
     * @param {Object} expr - AST for operator
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} tranformer function
     */
    fun evaluateTransformExpression(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any {
        // create a Object to implement the transform definition
        val transformer: JFunctionCallable = object : JFunctionCallable {
            override fun call(input: Any?, args: List<*>?): Any? {
                // /* async */ Object (obj) { // signature <(oa):o>
                val obj = args!![0] ?: return null

                // undefined inputs always return undefined


                // this Object returns a copy of obj with changes specified by the pattern/operation
                val result = functionClone(obj)

                var _matches =  /* await */evaluate(expr!!.pattern, result, environment)
                if (_matches != null) {
                    if (_matches !is List<*>) {
                        _matches = ArrayList(java.util.List.of(_matches))
                    }
                    val matches = _matches as List<*>
                    for (ii in matches.indices) {
                        val match = matches[ii]!!
                        // evaluate the update value for each match
                        val update =  /* await */evaluate(expr.update, match, environment)

                        // update must be an object
                        //var updateType = typeof update;
                        //if(updateType != null)
                        if (update != null) {
                            if (update !is Map<*, *>) {
                                // throw type error
                                throw JException(
                                    "T2011",
                                    expr.update!!.position,
                                    update
                                )
                            }
                            // merge the update
                            for (prop in update.keys) {
                                (match as MutableMap<Any?, Any?>)[prop] = update[prop]
                            }
                        }

                        // delete, if specified, must be an array of strings (or single string)
                        if (expr.delete != null) {
                            var deletions =  /* await */evaluate(expr.delete, match, environment)
                            if (deletions != null) {
                                val `val`: Any = deletions
                                if (deletions !is List<*>) {
                                    deletions = ArrayList(java.util.List.of(deletions))
                                }
                                if (!isArrayOfStrings(deletions)) {
                                    // throw type error
                                    throw JException(
                                        "T2012",
                                        expr.delete!!.position,
                                        `val`
                                    )
                                }
                                val _deletions = deletions as List<*>
                                for (jj in _deletions.indices) {
                                    if (match is MutableMap<*, *>) {
                                        match.remove(_deletions[jj])
                                        //delete match[deletions[jj]];
                                    }
                                }
                            }
                        }
                    }
                }
                return result
            }
        }

        return JFunction(transformer, "<(oa):o>")
    }

    /**
     * Apply the Object on the RHS using the sequence on the LHS as the first argument
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    /* async */
    fun evaluateApplyExpression(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any? {
        var result: Any? = null


        var lhs =  /* await */evaluate(expr!!.lhs, input, environment)

        // Map null to NULL_VALUE before applying to functions
        // TODO: fix more generically!
        if (lhs == null) lhs = NULL_VALUE

        if (expr.rhs!!.type == "function") {
            //Symbol applyTo = new Symbol(); applyTo.context = lhs;
            // this is a Object _invocation_; invoke it with lhs expression as the first argument
            result =  /* await */evaluateFunction(expr.rhs, input, environment, lhs)
        } else {
            val func =  /* await */evaluate(expr.rhs, input, environment)

            if (!isFunctionLike(func) &&
                !isFunctionLike(lhs)
            ) {
                throw JException(
                    "T2006",  //stack: (new Error()).stack,
                    expr.position,
                    func
                )
            }

            if (isFunctionLike(lhs)) {
                // this is Object chaining (func1 ~> func2)
                // λ($f, $g) { λ($x){ $g($f($x)) } }
                val chain =  /* await */evaluate(chainAST(), null, environment)
                val args: MutableList<Any> = ArrayList<Any>()
                args.add(lhs)
                args.add(func!!) // == [lhs, func]
                result =  /* await */apply(chain, args, null, environment)
            } else {
                val args: MutableList<Any> = ArrayList<Any>()
                args.add(lhs) // == [lhs]
                result =  /* await */apply(func, args, null, environment)
            }
        }

        return result
    }

    fun isFunctionLike(o: Any?): Boolean {
        return isFunction(o) || isLambda(o) || (o is Pattern)
    }

    val perThreadInstance: Jsonata
        /**
         * Returns a per thread instance of this parsed expression.
         *
         * @return
         */
        get() {
            var threadInst = current.get()
            // Fast path
            if (threadInst != null) return threadInst

            synchronized(this) {
                threadInst = current.get()
                if (threadInst == null) {
                    threadInst = Jsonata(this)
                    current.set(threadInst)
                }
                return threadInst
            }
        }

    /**
     * Evaluate Object against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    /* async */
    fun evaluateFunction(expr: Parser.Symbol?, input: Any?, environment: Frame?, applytoContext: Any?): Any? {
        var result: Any? = null

        // this.current is set by getPerThreadInstance() at this point

        // create the procedure
        // can"t assume that expr.procedure is a lambda type directly
        // could be an expression that evaluates to a Object (e.g. variable reference, parens expr etc.
        // evaluate it generically first, then check that it is a function.  Throw error if not.
        val proc =  /* await */evaluate(expr!!.procedure, input, environment)

        if (proc == null && expr.procedure!!.type === "path" && environment!!.lookup(expr.procedure!!.steps!![0].value as String) != null) {
            // help the user out here if they simply forgot the leading $
            throw JException(
                "T1005",  //stack: (new Error()).stack,
                expr.position,
                expr.procedure!!.steps!![0].value
            )
        }

        val evaluatedArgs: MutableList<Any?> = ArrayList()

        if (applytoContext != null) {
            evaluatedArgs.add(applytoContext)
        }
        // eager evaluation - evaluate the arguments
        for (jj in expr.arguments!!.indices) {
            val arg =  /* await */evaluate(expr.arguments!![jj], input, environment)
            if (isFunction(arg) || isLambda(arg)) {
                // wrap this in a closure
                // Java: not required, already a JFunction
                //  const closure = /* async */ Object (...params) {
                //      // invoke func
                //      return /* await */ apply(arg, params, null, environment);
                //  };
                //  closure.arity = getFunctionArity(arg);

                // JFunctionCallable fc = (ctx,params) ->
                //     apply(arg, params, null, environment);

                // JFunction cl = new JFunction(fc, "<o:o>");

                //Object cl = apply(arg, params, null, environment);

                evaluatedArgs.add(arg)
            } else {
                evaluatedArgs.add(arg)
            }
        }
        // apply the procedure
        val procName =
            if (expr.procedure!!.type === "path") expr.procedure!!.steps!![0].value else expr.procedure!!.value

        // Error if proc is null
        if (proc == null) throw JException("T1006", expr.position, procName)

        try {
            if (proc is Parser.Symbol) {
                proc.token = procName
                proc.position = expr.position
            }
            result =  /* await */apply(proc, evaluatedArgs, input, environment)
        } catch (jex: JException) {
            if (jex.location < 0) {
                // add the position field to the error
                jex.location = expr.position
            }
            if (jex.current == null) {
                // and the Object identifier
                jex.current = expr.token
            }
            throw jex
        } catch (err: Exception) {
            if (err !is RuntimeException) throw RuntimeException(err)
            //err.printStackTrace();
            throw err
            // new JException(err, "Error calling function "+procName, expr.position, null, null); //err;
        }
        return result
    }

    /**
     * Apply procedure or function
     * @param {Object} proc - Procedure
     * @param {Array} args - Arguments
     * @param {Object} input - input
     * @param {Object} environment - environment
     * @returns {*} Result of procedure
     */
    /* async */
    fun apply(proc: Any?, args: Any?, input: Any?, environment: Any?): Any? {
        var result =  /* await */applyInner(proc, args, input, environment)
        while (isLambda(result) && (result as Parser.Symbol?)!!.thunk == true) {
            // trampoline loop - this gets invoked as a result of tail-call optimization
            // the Object returned a tail-call thunk
            // unpack it, evaluate its arguments, and apply the tail call
            val next =  /* await */evaluate(
                result!!.body!!.procedure,
                result!!.input,
                result!!.environment
            )
            if (result!!.body!!.procedure!!.type === "variable") {
                if (next is Parser.Symbol) // Java: not if JFunction
                    next.token =
                        result!!.body!!.procedure!!.value
            }
            if (next is Parser.Symbol) // Java: not if JFunction
                next.position =
                    result!!.body!!.procedure!!.position
            val evaluatedArgs = ArrayList<Any?>()
            for (ii in result!!.body!!.arguments!!.indices) {
                evaluatedArgs.add( /* await */evaluate(
                    result!!.body!!.arguments!![ii],
                    result!!.input,
                    result!!.environment
                )
                )
            }

            result =  /* await */applyInner(next, evaluatedArgs, input, environment)
        }
        return result
    }

    /**
     * Apply procedure or function
     * @param {Object} proc - Procedure
     * @param {Array} args - Arguments
     * @param {Object} input - input
     * @param {Object} environment - environment
     * @returns {*} Result of procedure
     */
    /* async */
    fun applyInner(proc: Any?, args: Any?, input: Any?, environment: Any?): Any? {
        var result: Any? = null
        try {
            var validatedArgs = args
            if (proc != null) {
                validatedArgs = validateArguments(proc, args, input)
            }

            if (isLambda(proc)) {
                result =  /* await */applyProcedure(proc, validatedArgs)
            } /* FIXME: need in Java??? else if (proc && proc._jsonata_Object == true) {
                 var focus = {
                     environment: environment,
                     input: input
                 };
                 // the `focus` is passed in as the `this` for the invoked function
                 result = proc.implementation.apply(focus, validatedArgs);
                 // `proc.implementation` might be a generator function
                 // and `result` might be a generator - if so, yield
                 if (isIterable(result)) {
                     result = result.next().value;
                 }
                 if (isPromise(result)) {
                     result = /await/ result;
                 } 
             } */ else if (proc is JFunction) {
                // typically these are functions that are returned by the invocation of plugin functions
                // the `input` is being passed in as the `this` for the invoked function
                // this is so that functions that return objects containing functions can chain
                // e.g. /* await */ (/* await */ $func())

                // handling special case of Javascript:
                // when calling a function with fn.apply(ctx, args) and args = [undefined]
                // Javascript will convert to undefined (without array)

                if ((validatedArgs is List<*>) && validatedArgs.size == 1 && validatedArgs[0] == null) {
                    //validatedArgs = null;
                }

                result = proc.call(input, validatedArgs as List<*>?)
                //  if (isPromise(result)) {
                //      result = /* await */ result;
                //  }
            } else if (proc is JLambda) {
                // System.err.println("Lambda "+proc);
                val _args = validatedArgs as List<*>?
                if (proc is Fn0<*>) {
                    result = proc.get()
                } else if (proc is Fn1<*, *>) {
                    result = (proc as Fn1<Any?, Any?>).apply(if (_args!!.size <= 0) null else _args[0])
                } else if (proc is Fn2<*, *, *>) {
                    result =
                        (proc as Fn2<Any?, Any?, Any?>).apply(if (_args!!.size <= 0) null else _args[0], if (_args.size <= 1) null else _args[1])
                }
            } else if (proc is Pattern) {
                val _res: MutableList<Any> = ArrayList<Any>()
                for (s in (validatedArgs as List<String>)!!) {
                    //System.err.println("PAT "+proc+" input "+s);
                    if (proc.matcher(s).find()) {
                        //System.err.println("MATCH");
                        _res.add(s)
                    }
                }
                result = _res
            } else {
                println("Proc not found $proc")
                throw JException(
                    "T1006", 0 //stack: (new Error()).stack
                )
            }
        } catch (err: JException) {
            //  if(proc) {
            //      if (typeof err.token == "undefined" && typeof proc.token !== "undefined") {
            //          err.token = proc.token;
            //      }
            //      err.position = proc.position;
            //  }
            throw err
        }
        return result
    }

    /**
     * Evaluate lambda against input data
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {{lambda: boolean, input: *, environment: *, arguments: *, body: *}} Evaluated input data
     */
    fun evaluateLambda(expr: Parser.Symbol?, input: Any?, environment: Frame?): Any {
        // make a Object (closure)
        val procedure = parser!!.Symbol()

        procedure._jsonata_lambda = true
        procedure.input = input
        procedure.environment = environment
        procedure.arguments = expr!!.arguments
        procedure.signature = expr.signature
        procedure.body = expr.body

        if (expr.thunk == true) procedure.thunk = true


        // procedure.apply = /* async */ function(self, args) {
        //     return /* await */ apply(procedure, args, input, !!self ? self.environment : environment);
        // };
        return procedure
    }

    /**
     * Evaluate partial application
     * @param {Object} expr - JSONata expression
     * @param {Object} input - Input data to evaluate against
     * @param {Object} environment - Environment
     * @returns {*} Evaluated input data
     */
    /* async */
    fun evaluatePartialApplication(expr: Parser.Symbol?, input: Any?, environment: Frame?): Parser.Symbol {
        // partially apply a function
        var result: Any? = null
        // evaluate the arguments
        val evaluatedArgs = ArrayList<Any?>()
        for (ii in expr!!.arguments!!.indices) {
            val arg = expr.arguments!![ii]
            if (arg.type == "operator" && (arg.value == "?")) {
                evaluatedArgs.add(arg)
            } else {
                evaluatedArgs.add( /* await */evaluate(arg, input, environment))
            }
        }
        // lookup the procedure
        val proc =  /* await */evaluate(expr.procedure, input, environment)
        if (proc != null && expr.procedure!!.type == "path" && environment!!.lookup(expr.procedure!!.steps!![0].value as String) != null) {
            // help the user out here if they simply forgot the leading $
            throw JException(
                "T1007",
                expr.position,
                expr.procedure!!.steps!![0].value
            )
        }
        result = if (isLambda(proc)) {
            partialApplyProcedure(proc as Parser.Symbol?, evaluatedArgs as List<Parser.Symbol?>)
        } else if (isFunction(proc)) {
            partialApplyNativeFunction(proc as JFunction?,  /*.implementation*/evaluatedArgs)
            //  } else if (typeof proc === "function") {
            //      result = partialApplyNativeFunction(proc, evaluatedArgs);
        } else {
            throw JException(
                "T1008",  //stack: (new Error()).stack,
                expr.position,
                if (expr.procedure!!.type == "path") expr.procedure!!.steps!![0].value else expr.procedure!!.value
            )
        }
        return result
    }

    /**
     * Validate the arguments against the signature validator (if it exists)
     * @param {Function} signature - validator function
     * @param {Array} args - Object arguments
     * @param {*} context - context value
     * @returns {Array} - validated arguments
     */
    fun validateArguments(signature: Any, args: Any?, context: Any?): Any? {
        var validatedArgs = args
        if (isFunction(signature)) {
            validatedArgs = (signature as JFunction).validate(args, context)
        } else if (isLambda(signature)) {
            val sig = ((signature as Parser.Symbol).signature as Signature?)
            if (sig != null) validatedArgs = sig.validate(args!!, context)
        }
        return validatedArgs
    }

    /**
     * Apply procedure
     * @param {Object} proc - Procedure
     * @param {Array} args - Arguments
     * @returns {*} Result of procedure
     */
    /* async */
    fun applyProcedure(_proc: Any?, _args: Any?): Any? {
        val args = _args as List<*>?
        val proc = _proc as Parser.Symbol?
        var result: Any? = null
        val env = createFrame(proc!!.environment)
        for (i in proc.arguments!!.indices) {
            if (i >= args!!.size) break
            env.bind("" + proc.arguments!![i].value, args[i])
        }
        if (proc.body is Parser.Symbol) {
            result = evaluate(proc.body, proc.input, env)
        } else throw Error("Cannot execute procedure: " + proc + " " + proc.body)
        //  if (typeof proc.body === "function") {
        //      // this is a lambda that wraps a native Object - generated by partially evaluating a native
        //      result = /* await */ applyNativeFunction(proc.body, env);
        return result
    }

    /**
     * Partially apply procedure
     * @param {Object} proc - Procedure
     * @param {Array} args - Arguments
     * @returns {{lambda: boolean, input: *, environment: {bind, lookup}, arguments: Array, body: *}} Result of partially applied procedure
     */
    fun partialApplyProcedure(proc: Parser.Symbol?, args: List<Any?>): Parser.Symbol {
        // create a closure, bind the supplied parameters and return a Object that takes the remaining (?) parameters
        // Note Uli: if no env, bind to default env so the native functions can be found
        val env = createFrame(if (proc!!.environment != null) proc.environment else this.environment)
        val unboundArgs = ArrayList<Parser.Symbol>()
        var index = 0
        for (param in proc.arguments!!) {
//         proc.arguments.forEach(Object (param, index) {
            val arg: Any? = if (index < args.size) args[index] else null
            if ((arg == null) || (arg is Parser.Symbol && (("operator" == arg.type) && "?" == arg.value))) {
                unboundArgs.add(param)
            } else {
                env.bind(param.value as String, arg)
            }
            index++
        }
        val procedure = parser!!.Symbol()
        procedure._jsonata_lambda = true
        procedure.input = proc.input
        procedure.environment = env
        procedure.arguments = unboundArgs
        procedure.body = proc.body

        return procedure
    }

    /**
     * Partially apply native function
     * @param {Function} native - Native function
     * @param {Array} args - Arguments
     * @returns {{lambda: boolean, input: *, environment: {bind, lookup}, arguments: Array, body: *}} Result of partially applying native function
     */
    fun partialApplyNativeFunction(_native: JFunction?, args: List<Any?>): Parser.Symbol {
        // create a lambda Object that wraps and invokes the native function
        // get the list of declared arguments from the native function
        // this has to be picked out from the toString() value


        //var body = "function($a,$c) { $substring($a,0,$c) }";


        val sigArgs: MutableList<String> = ArrayList<String>()
        val partArgs: MutableList<Any> = ArrayList<Any>()
        for (i in 0 until _native!!.numberOfArgs) {
            val argName = "$" + ('a'.code + i).toChar()
            sigArgs.add(argName)
            if (i >= args.size || args[i] == null) partArgs.add(argName)
            else partArgs.add(args[i]!!)
        }

        var body = "function(" + java.lang.String.join(", ", sigArgs) + "){"
        body += "$" + _native.functionName + "(" + java.lang.String.join(", ", sigArgs) + ") }"

        if (parser!!.dbg) println("partial trampoline = $body")

        //  var sigArgs = getNativeFunctionArguments(_native);
        //  sigArgs = sigArgs.stream().map(sigArg -> {
        //      return "$" + sigArg;
        //  }).toList();
        //  var body = "function(" + String.join(", ", sigArgs) + "){ _ }";
        val bodyAST = parser.parse(body)

        //bodyAST.body = _native;
        val partial = partialApplyProcedure(bodyAST, args)
        return partial
    }

    /**
     * Apply native function
     * @param {Object} proc - Procedure
     * @param {Object} env - Environment
     * @returns {*} Result of applying native function
     */
    /* async */
    fun applyNativeFunction(proc: JFunction?, env: Frame?): Any? {
        // Not called in Java - JFunction call directly calls native function
        return null
    }

    /**
     * Get native Object arguments
     * @param {Function} func - Function
     * @returns {*|Array} Native Object arguments
     */
    fun getNativeFunctionArguments(func: JFunction?): List<*>? {
        // Not called in Java
        return null
    }

    /**
     * parses and evaluates the supplied expression
     * @param {string} expr - expression to evaluate
     * @returns {*} - result of evaluating the expression
     */
    /* async */ //Object functionEval(String expr, Object focus) {
    // moved to Functions !
    //}
    /**
     * Clones an object
     * @param {Object} arg - object to clone (deep copy)
     * @returns {*} - the cloned object
     */
    //Object functionClone(Object arg) {
    // moved to Functions !
    //}
    /**
     * Create frame
     * @param {Object} enclosingEnvironment - Enclosing environment
     * @returns {{bind: bind, lookup: lookup}} Created frame
     */
    @JvmOverloads
    fun createFrame(enclosingEnvironment: Frame? = null): Frame {
        return Frame(enclosingEnvironment)

        // The following logic is in class Frame:
        //  var bindings = {};
        //  return {
        //      bind: Object (name, value) {
        //          bindings[name] = value;
        //      },
        //      lookup: Object (name) {
        //          var value;
        //          if(bindings.hasOwnProperty(name)) {
        //              value = bindings[name];
        //          } else if (enclosingEnvironment) {
        //              value = enclosingEnvironment.lookup(name);
        //          }
        //          return value;
        //      },
        //      timestamp: enclosingEnvironment ? enclosingEnvironment.timestamp : null,
        //      async: enclosingEnvironment ? enclosingEnvironment./* async */ : false,
        //      isParallelCall: enclosingEnvironment ? enclosingEnvironment.isParallelCall : false,
        //      global: enclosingEnvironment ? enclosingEnvironment.global : {
        //          ancestry: [ null ]
        //      }
        //  };
    }

    interface JLambda

    fun interface FnVarArgs<R> : JLambda, Function<List<*>?, R>

    fun interface Fn0<R> : JLambda, Supplier<R>

    fun interface Fn1<A, R> : JLambda, Function<A, R>

    fun interface Fn2<A, B, R> : JLambda, BiFunction<A, B, R>

    fun interface Fn3<A, B, C, R> : JLambda {
        fun apply(a: A, b: B, c: C): R
    }

    fun interface Fn4<A, B, C, D, R> : JLambda {
        fun apply(a: A, b: B, c: C, d: D): R
    }

    fun interface Fn5<A, B, C, D, E, R> : JLambda {
        fun apply(a: A, b: B, c: C, d: D, e: E): R
    }

    fun interface Fn6<A, B, C, D, E, F, R> : JLambda {
        fun apply(a: A, b: B, c: C, d: D, e: E, f: F): R
    }

    fun interface Fn7<A, B, C, D, E, F, G, R> : JLambda {
        fun apply(a: A, b: B, c: C, d: D, e: E, f: F, g: G): R
    }

    fun interface Fn8<A, B, C, D, E, F, G, H, R> : JLambda {
        fun apply(a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H): R
    }

    /**
     * JFunction callable Lambda interface
     */
    interface JFunctionCallable {
        @Throws(Throwable::class)
        fun call(input: Any?, args: List<*>?): Any?
    }

    interface JFunctionSignatureValidation {
        fun validate(args: Any?, context: Any?): Any?
    }

    /**
     * JFunction definition class
     */
    class JFunction : JFunctionCallable, JFunctionSignatureValidation {
        var function: JFunctionCallable? = null
        var functionName: String? = null
        var signature: Signature? = null
        var method: Method? = null
        var methodInstance: Any? = null

        constructor(function: JFunctionCallable, signature: String?) {
            this.function = function
            if (signature != null) // use classname as default, gets overwritten once the function is registered
                this.signature = Signature(signature, function.javaClass.name)
        }

        constructor(functionName: String, signature: String?, clz: Class<*>?, instance: Any?, implMethodName: String) {
            this.functionName = functionName
            this.signature = Signature(signature!!, functionName)
            this.method = getFunction(clz, implMethodName)
            this.methodInstance = instance
            if (method == null) {
                System.err.println("Function not implemented: $functionName impl=$implMethodName")
            }
        }

        override fun call(input: Any?, args: List<*>?): Any? {
            try {
                return if (function != null) {
                    function!!.call(input, args)
                } else {
                    call(methodInstance, method, args)
                }
            } catch (e: JException) {
                throw e
            } catch (e: InvocationTargetException) {
                throw RuntimeException(e.targetException)
            } catch (e: Throwable) {
                if (e is RuntimeException) throw e
                throw RuntimeException(e)
                //throw new JException(e, "T0410", -1, args, functionName);
            }
        }

        override fun validate(args: Any?, context: Any?): Any? {
            return if (signature != null) signature!!.validate(args!!, context)
            else args
        }

        val numberOfArgs: Int
            get() = if (method != null) method!!.parameterTypes.size else 0
    }

    /**
     * lookup a message template from the catalog and substitute the inserts.
     * Populates `err.message` with the substituted message. Leaves `err.message`
     * untouched if code lookup fails.
     * @param {string} err - error code to lookup
     * @returns {undefined} - `err` is modified in place
     */
    fun populateMessage(err: Exception): Exception {
        //  var template = errorCodes[err.code];
        //  if(typeof template !== "undefined") {
        //      // if there are any handlebars, replace them with the field references
        //      // triple braces - replace with value
        //      // double braces - replace with json stringified value
        //      var message = template.replace(/\{\{\{([^}]+)}}}/g, function() {
        //          return err[arguments[1]];
        //      });
        //      message = message.replace(/\{\{([^}]+)}}/g, function() {
        //          return JSON.stringify(err[arguments[1]]);
        //      });
        //      err.message = message;
        //  }
        // Otherwise retain the original `err.message`
        return err
    }

    var errors: List<Exception>? = null
    var environment: Frame
    var ast: Parser.Symbol? = null
    var timestamp: Long
    var input: Any? = null

    /**
     * Internal constructor
     * @param expr
     */
    internal constructor(expr: String?) { // boolean optionsRecover) {
        try {
            ast = parser!!.parse(expr) //, optionsRecover);
            errors = ast!!.errors
            ast!!.errors = null //delete ast.errors;
        } catch (err: JException) {
            // insert error message into structure
            //populateMessage(err); // possible side-effects on `err`
            throw err
        }
        environment = createFrame(staticFrame)

        timestamp = System.currentTimeMillis() // will be overridden on each call to evalute()

        // Note: now and millis are implemented in Functions
        //  environment.bind("now", defineFunction(function(picture, timezone) {
        //      return datetime.fromMillis(timestamp.getTime(), picture, timezone);
        //  }, "<s?s?:s>"));
        //  environment.bind("millis", defineFunction(function() {
        //      return timestamp.getTime();
        //  }, "<:n>"));

        // FIXED: options.RegexEngine not implemented in Java
        //  if(options && options.RegexEngine) {
        //      jsonata.RegexEngine = options.RegexEngine;
        //  } else {
        //      jsonata.RegexEngine = RegExp;
        //  }

        // Set instance for this thread
        current.set(this)
    }

    /**
     * Creates a clone of the given Jsonata instance.
     * Package-private copy constructor used to create per thread instances.
     *
     * @param other
     */
    internal constructor(other: Jsonata) {
        this.ast = other.ast
        this.environment = other.environment
        this.timestamp = other.timestamp
    }

    /**
     * Checks whether input validation is active
     */
    /**
     * Enable or disable input validation
     * @param validateInput
     */
    /**
     * Flag: validate input objects to comply with JSON types
     */
    var isValidateInput: Boolean = true

    /* async */
    @JvmOverloads
    fun evaluate(input: Any?, bindings: Frame? = null): Any? { // FIXME:, callback) {
        // throw if the expression compiled with syntax errors
        var input = input
        if (errors != null) {
            throw JException("S0500", 0)
        }

        val exec_env: Frame
        if (bindings != null) {
            //var exec_env;
            // the variable bindings have been passed in - create a frame to hold these
            exec_env = createFrame(environment)
            for (v in bindings.bindings.keys) {
                exec_env.bind(v, bindings.lookup(v)!!)
            }
        } else {
            exec_env = environment
        }
        // put the input document into the environment as the root object
        exec_env.bind("$", input)

        // capture the timestamp and put it in the execution environment
        // the $now() and $millis() functions will return this value - whenever it is called
        timestamp = System.currentTimeMillis()

        //exec_env.timestamp = timestamp;

        // if the input is a JSON array, then wrap it in a singleton sequence so it gets treated as a single input
        if ((input is List<*>) && !isSequence(input)) {
            input = createSequence(input)
            (input as Utils.JList<*>).outerWrapper = true
        }

        if (isValidateInput) validateInput(input)

        var it: Any?
        try {
            it =  /* await */evaluate(ast, input, exec_env)
            //  if (typeof callback === "function") {
            //      callback(null, it);
            //  }
            it = convertNulls(it)
            return it
        } catch (err: Exception) {
            // insert error message into structure
            populateMessage(err) // possible side-effects on `err`
            throw err
        }
    }

    fun assign(name: String, value: Any?) {
        environment.bind(name, value)
    }

    fun registerFunction(name: String, implementation: JFunction) {
        environment.bind(name, implementation)
    }

    fun <R> registerFunction(name: String, implementation: Fn0<R>) {
        environment.bind(name, implementation)
    }

    fun <A, R> registerFunction(name: String, implementation: Fn1<A, R>) {
        environment.bind(name, implementation)
    }

    fun <A, B, R> registerFunction(name: String, implementation: Fn2<A, B, R>) {
        environment.bind(name, implementation)
    }

    val parser: Parser? = getParser()

    companion object {
        var staticFrame: Frame = Frame(null) // = createFrame(null);

        @JvmField
        val NULL_VALUE: Any = object : Any() {
            override fun toString(): String {
                return "null"
            }
        }

        fun boolize(value: Any?): Boolean {
            val booledValue = toBoolean(value)
            return booledValue ?: false
        }

        var chainAST: Parser.Symbol? = null // = new Parser().parse("function($f, $g) { function($x){ $g($f($x)) } }");

        fun chainAST(): Parser.Symbol? {
            if (chainAST == null) {
                // only create on demand
                chainAST = Parser().parse("function(\$f, \$g) { function(\$x){ \$g(\$f(\$x)) } }")
            }
            return chainAST
        }

        val current: ThreadLocal<Jsonata> = ThreadLocal()

        /**
         * Creates a Object definition
         * @param {Function} func - Object implementation in Javascript
         * @param {string} signature - JSONata Object signature definition
         * @returns {{implementation: *, signature: *}} Object definition
         */
        @JvmOverloads
        fun defineFunction(func: String, signature: String?, funcImplMethod: String = func): JFunction {
            val fn = JFunction(func, signature, Functions::class.java, null, funcImplMethod)
            staticFrame.bind(func, fn)
            return fn
        }

        fun function(
            name: String,
            signature: String?,
            clazz: Class<*>?,
            instance: Any?,
            methodName: String
        ): JFunction {
            return JFunction(name, signature, clazz, instance, methodName)
        }

        fun <R> function(name: String?, func: FnVarArgs<R>, signature: String?): JFunction {
            return JFunction(toJFunctionCallable(func), signature)
        }

        fun <R> toJFunctionCallable(func: FnVarArgs<R>): JFunctionCallable {
            return object : JFunctionCallable {
                override fun call(input: Any?, args: List<*>?): Any? {
                    return func.apply(args)
                }
            }
        }

        fun <A, R> function(name: String?, func: Fn0<R>, signature: String?): JFunction {
            return JFunction(toJFunctionCallable(func), signature)
        }

        fun <R> toJFunctionCallable(func: Fn0<R>): JFunctionCallable {
            return object : JFunctionCallable {
                override fun call(input: Any?, args: List<*>?): Any? {
                    return func.get()
                }
            }
        }

        fun <A, R> function(name: String?, func: Fn1<A, R>, signature: String?): JFunction {
            return JFunction(toJFunctionCallable(func), signature)
        }

        fun <A, R> toJFunctionCallable(func: Fn1<A, R>): JFunctionCallable {
            return object : JFunctionCallable {
                override fun call(input: Any?, args: List<*>?): Any? {
                    return func.apply(args!![0] as A)
                }
            }
        }

        fun <A, B, R> function(name: String?, func: Fn2<A, B, R>, signature: String?): JFunction {
            return JFunction(toJFunctionCallable(func), signature)
        }

        fun <A, B, R> toJFunctionCallable(func: Fn2<A, B, R>): JFunctionCallable {
            return object : JFunctionCallable {
                override fun call(input: Any?, args: List<*>?): Any? {
                    return func.apply(args!![0] as A, args!![1] as B)
                }
            }
        }

        fun <A, B, C, R> function(name: String?, func: Fn3<A, B, C, R>, signature: String?): JFunction {
            return JFunction(toJFunctionCallable(func), signature)
        }

        fun <A, B, C, R> toJFunctionCallable(func: Fn3<A, B, C, R>): JFunctionCallable {
            return object : JFunctionCallable {
                override fun call(input: Any?, args: List<*>?): Any? {
                    return func.apply(args!![0] as A, args!![1] as B, args!![2] as C)
                }
            }
        }

        fun <A, B, C, D, R> function(name: String?, func: Fn4<A, B, C, D, R>, signature: String?): JFunction {
            return JFunction(toJFunctionCallable(func), signature)
        }

        fun <A, B, C, D, R> toJFunctionCallable(func: Fn4<A, B, C, D, R>): JFunctionCallable {
            return object : JFunctionCallable {
                override fun call(input: Any?, args: List<*>?): Any? {
                    return func.apply(args!![0] as A, args!![1] as B, args!![2] as C, args!![3] as D)
                }
            }
        }

        fun <A, B, C, D, E, R> function(name: String?, func: Fn5<A, B, C, D, E, R>, signature: String?): JFunction {
            return JFunction(toJFunctionCallable(func), signature)
        }

        fun <A, B, C, D, E, R> toJFunctionCallable(func: Fn5<A, B, C, D, E, R>): JFunctionCallable {
            return object : JFunctionCallable {
                override fun call(input: Any?, args: List<*>?): Any? {
                    return func.apply(args!![0] as A, args!![1] as B, args!![2] as C, args!![3] as D, args!![4] as E)
                }
            }
        }

        fun <A, B, C, D, E, F, R> function(
            name: String?,
            func: Fn6<A, B, C, D, E, F, R>,
            signature: String?
        ): JFunction {
            return JFunction(toJFunctionCallable(func), signature)
        }

        fun <A, B, C, D, E, F, R> toJFunctionCallable(func: Fn6<A, B, C, D, E, F, R>): JFunctionCallable {
            return object : JFunctionCallable {
                override fun call(input: Any?, args: List<*>?): Any? {
                    return func.apply(
                        args!![0] as A, args!![1] as B,
                        args!![2] as C, args!![3] as D, args!![4] as E,
                        args!![5] as F
                    )
                }
            }
        }

        fun <A, B, C, D, E, F, G, R> function(
            name: String?,
            func: Fn7<A, B, C, D, E, F, G, R>,
            signature: String?
        ): JFunction {
            return JFunction(toJFunctionCallable(func), signature)
        }

        fun <A, B, C, D, E, F, G, R> toJFunctionCallable(func: Fn7<A, B, C, D, E, F, G, R>): JFunctionCallable {
            return object : JFunctionCallable {
                override fun call(input: Any?, args: List<*>?): Any? {
                    return func.apply(
                        args!![0] as A, args!![1] as B,
                        args!![2] as C, args!![3] as D, args!![4] as E,
                        args!![5] as F, args!![6] as G
                    )
                }
            }
        }

        fun <A, B, C, D, E, F, G, H, R> function(
            name: String?,
            func: Fn8<A, B, C, D, E, F, G, H, R>,
            signature: String?
        ): JFunction {
            return JFunction(toJFunctionCallable(func), signature)
        }

        fun <A, B, C, D, E, F, G, H, R> toJFunctionCallable(func: Fn8<A, B, C, D, E, F, G, H, R>): JFunctionCallable {
            return object : JFunctionCallable {
                override fun call(input: Any?, args: List<*>?): Any? {
                    return func.apply(
                        args!![0] as A, args!![1] as B,
                        args!![2] as C, args!![3] as D, args!![4] as E,
                        args!![5] as F, args!![6] as G, args!![7] as H
                    )
                }
            }
        }

        // Function registration
        fun registerFunctions() {
            defineFunction("sum", "<a<n>:n>")
            defineFunction("count", "<a:n>")
            defineFunction("max", "<a<n>:n>")
            defineFunction("min", "<a<n>:n>")
            defineFunction("average", "<a<n>:n>")
            defineFunction("string", "<x-b?:s>")
            defineFunction("substring", "<s-nn?:s>")
            defineFunction("substringBefore", "<s-s:s>")
            defineFunction("substringAfter", "<s-s:s>")
            defineFunction("lowercase", "<s-:s>")
            defineFunction("uppercase", "<s-:s>")
            defineFunction("length", "<s-:n>")
            defineFunction("trim", "<s-:s>")
            defineFunction("pad", "<s-ns?:s>")
            defineFunction("match", "<s-f<s:o>n?:a<o>>")
            defineFunction("contains", "<s-(sf):b>") // TODO <s-(sf<s:o>):b>
            defineFunction("replace", "<s-(sf)(sf)n?:s>") // TODO <s-(sf<s:o>)(sf<o:s>)n?:s>
            defineFunction("split", "<s-(sf)n?:a<s>>") // TODO <s-(sf<s:o>)n?:a<s>>
            defineFunction("join", "<a<s>s?:s>")
            defineFunction("formatNumber", "<n-so?:s>")
            defineFunction("formatBase", "<n-n?:s>")
            defineFunction("formatInteger", "<n-s:s>")
            defineFunction("parseInteger", "<s-s:n>")
            defineFunction("number", "<(nsb)-:n>")
            defineFunction("floor", "<n-:n>")
            defineFunction("ceil", "<n-:n>")
            defineFunction("round", "<n-n?:n>")
            defineFunction("abs", "<n-:n>")
            defineFunction("sqrt", "<n-:n>")
            defineFunction("power", "<n-n:n>")
            defineFunction("random", "<:n>")
            defineFunction("boolean", "<x-:b>", "toBoolean")
            defineFunction("not", "<x-:b>")
            defineFunction("map", "<af>")
            defineFunction("zip", "<a+>")
            defineFunction("filter", "<af>")
            defineFunction("single", "<af?>")
            defineFunction("reduce", "<afj?:j>", "foldLeft") // TODO <f<jj:j>a<j>j?:j>
            defineFunction("sift", "<o-f?:o>")
            defineFunction("keys", "<x-:a<s>>")
            defineFunction("lookup", "<x-s:x>")
            defineFunction("append", "<xx:a>")
            defineFunction("exists", "<x:b>")
            defineFunction("spread", "<x-:a<o>>")
            defineFunction("merge", "<a<o>:o>")
            defineFunction("reverse", "<a:a>")
            defineFunction("each", "<o-f:a>")
            defineFunction("error", "<s?:x>")
            defineFunction("assert", "<bs?:x>", "assertFn")
            defineFunction("type", "<x:s>")
            defineFunction("sort", "<af?:a>")
            defineFunction("shuffle", "<a:a>")
            defineFunction("distinct", "<x:x>")
            defineFunction("base64encode", "<s-:s>")
            defineFunction("base64decode", "<s-:s>")
            defineFunction("encodeUrlComponent", "<s-:s>")
            defineFunction("encodeUrl", "<s-:s>")
            defineFunction("decodeUrlComponent", "<s-:s>")
            defineFunction("decodeUrl", "<s-:s>")
            defineFunction("eval", "<sx?:x>", "functionEval")
            defineFunction("toMillis", "<s-s?:n>", "dateTimeToMillis")
            defineFunction("fromMillis", "<n-s?s?:s>", "dateTimeFromMillis")
            defineFunction("clone", "<(oa)-:o>", "functionClone")

            defineFunction("now", "<s?s?:s>")
            defineFunction("millis", "<:n>")

            //  environment.bind("now", defineFunction(function(picture, timezone) {
            //      return datetime.fromMillis(timestamp.getTime(), picture, timezone);
            //  }, "<s?s?:s>"));
            //  environment.bind("millis", defineFunction(function() {
            //      return timestamp.getTime();
            //  }, "<:n>"));
        }

        /**
         * Error codes
         *
         * Sxxxx    - Static errors (compile time)
         * Txxxx    - Type errors
         * Dxxxx    - Dynamic errors (evaluate time)
         * 01xx    - tokenizer
         * 02xx    - parser
         * 03xx    - regex parser
         * 04xx    - Object signature parser/evaluator
         * 10xx    - evaluator
         * 20xx    - operators
         * 3xxx    - functions (blocks of 10 for each function)
         */
        @JvmField
        var errorCodes: HashMap<String, String> = object : HashMap<String, String>() {
            init {
                put("S0101", "String literal must be terminated by a matching quote")
                put("S0102", "Number out of range: {{token}}")
                put("S0103", "Unsupported escape sequence: \\{{token}}")
                put("S0104", "The escape sequence \\u must be followed by 4 hex digits")
                put("S0105", "Quoted property name must be terminated with a backquote (`)")
                put("S0106", "Comment has no closing tag")
                put("S0201", "Syntax error: {{token}}")
                put("S0202", "Expected {{value}}, got {{token}}")
                put("S0203", "Expected {{value}} before end of expression")
                put("S0204", "Unknown operator: {{token}}")
                put("S0205", "Unexpected token: {{token}}")
                put("S0206", "Unknown expression type: {{token}}")
                put("S0207", "Unexpected end of expression")
                put("S0208", "Parameter {{value}} of Object definition must be a variable name (start with $)")
                put("S0209", "A predicate cannot follow a grouping expression in a step")
                put("S0210", "Each step can only have one grouping expression")
                put("S0211", "The symbol {{token}} cannot be used as a unary operator")
                put("S0212", "The left side of := must be a variable name (start with $)")
                put("S0213", "The literal value {{value}} cannot be used as a step within a path expression")
                put("S0214", "The right side of {{token}} must be a variable name (start with $)")
                put("S0215", "A context variable binding must precede any predicates on a step")
                put("S0216", "A context variable binding must precede the \"order-by\" clause on a step")
                put("S0217", "The object representing the \"parent\" cannot be derived from this expression")
                put("S0301", "Empty regular expressions are not allowed")
                put("S0302", "No terminating / in regular expression")
                put("S0402", "Choice groups containing parameterized types are not supported")
                put("S0401", "Type parameters can only be applied to functions and arrays")
                put("S0500", "Attempted to evaluate an expression containing syntax error(s)")
                put("T0410", "Argument {{index}} of Object {{token}} does not match Object signature")
                put("T0411", "Context value is not a compatible type with argument {{index}} of Object {{token}}")
                put("T0412", "Argument {{index}} of Object {{token}} must be an array of {{type}}")
                put("D1001", "Number out of range: {{value}}")
                put("D1002", "Cannot negate a non-numeric value: {{value}}")
                put("T1003", "Key in object structure must evaluate to a string; got: {{value}}")
                put("D1004", "Regular expression matches zero length string")
                put("T1005", "Attempted to invoke a non-function. Did you mean \${{{token}}}?")
                put("T1006", "Attempted to invoke a non-function")
                put("T1007", "Attempted to partially apply a non-function. Did you mean \${{{token}}}?")
                put("T1008", "Attempted to partially apply a non-function")
                put("D1009", "Multiple key definitions evaluate to same key: {{value}}")
                put(
                    "T1010",
                    "The matcher Object argument passed to Object {{token}} does not return the correct object structure"
                )
                put("T2001", "The left side of the {{token}} operator must evaluate to a number")
                put("T2002", "The right side of the {{token}} operator must evaluate to a number")
                put("T2003", "The left side of the range operator (..) must evaluate to an integer")
                put("T2004", "The right side of the range operator (..) must evaluate to an integer")
                put(
                    "D2005",
                    "The left side of := must be a variable name (start with $)"
                ) // defunct - replaced by S0212 parser error
                put("T2006", "The right side of the Object application operator ~> must be a function")
                put("T2007", "Type mismatch when comparing values {{value}} and {{value2}} in order-by clause")
                put("T2008", "The expressions within an order-by clause must evaluate to numeric or string values")
                put(
                    "T2009",
                    "The values {{value}} and {{value2}} either side of operator {{token}} must be of the same data type"
                )
                put(
                    "T2010",
                    "The expressions either side of operator {{token}} must evaluate to numeric or string values"
                )
                put(
                    "T2011",
                    "The insert/update clause of the transform expression must evaluate to an object: {{value}}"
                )
                put(
                    "T2012",
                    "The delete clause of the transform expression must evaluate to a string or array of strings: {{value}}"
                )
                put(
                    "T2013",
                    "The transform expression clones the input object using the \$clone() function.  This has been overridden in the current scope by a non-function."
                )
                put(
                    "D2014",
                    "The size of the sequence allocated by the range operator (..) must not exceed 1e6.  Attempted to allocate {{value}}."
                )
                put("D3001", "Attempting to invoke string Object on Infinity or NaN")
                put("D3010", "Second argument of replace Object cannot be an empty string")
                put("D3011", "Fourth argument of replace Object must evaluate to a positive number")
                put("D3012", "Attempted to replace a matched string with a non-string value")
                put("D3020", "Third argument of split Object must evaluate to a positive number")
                put("D3030", "Unable to cast value to a number: {{value}}")
                put("D3040", "Third argument of match Object must evaluate to a positive number")
                put("D3050", "The second argument of reduce Object must be a Object with at least two arguments")
                put("D3060", "The sqrt Object cannot be applied to a negative number: {{value}}")
                put(
                    "D3061",
                    "The power Object has resulted in a value that cannot be represented as a JSON number: base={{value}}, exponent={{exp}}"
                )
                put(
                    "D3070",
                    "The single argument form of the sort Object can only be applied to an array of strings or an array of numbers.  Use the second argument to specify a comparison function"
                )
                put("D3080", "The picture string must only contain a maximum of two sub-pictures")
                put(
                    "D3081",
                    "The sub-picture must not contain more than one instance of the \"decimal-separator\" character"
                )
                put("D3082", "The sub-picture must not contain more than one instance of the \"percent\" character")
                put("D3083", "The sub-picture must not contain more than one instance of the \"per-mille\" character")
                put("D3084", "The sub-picture must not contain both a \"percent\" and a \"per-mille\" character")
                put(
                    "D3085",
                    "The mantissa part of a sub-picture must contain at least one character that is either an \"optional digit character\" or a member of the \"decimal digit family\""
                )
                put(
                    "D3086",
                    "The sub-picture must not contain a passive character that is preceded by an active character and that is followed by another active character"
                )
                put(
                    "D3087",
                    "The sub-picture must not contain a \"grouping-separator\" character that appears adjacent to a \"decimal-separator\" character"
                )
                put("D3088", "The sub-picture must not contain a \"grouping-separator\" at the end of the integer part")
                put(
                    "D3089",
                    "The sub-picture must not contain two adjacent instances of the \"grouping-separator\" character"
                )
                put(
                    "D3090",
                    "The integer part of the sub-picture must not contain a member of the \"decimal digit family\" that is followed by an instance of the \"optional digit character\""
                )
                put(
                    "D3091",
                    "The fractional part of the sub-picture must not contain an instance of the \"optional digit character\" that is followed by a member of the \"decimal digit family\""
                )
                put(
                    "D3092",
                    "A sub-picture that contains a \"percent\" or \"per-mille\" character must not contain a character treated as an \"exponent-separator\""
                )
                put(
                    "D3093",
                    "The exponent part of the sub-picture must comprise only of one or more characters that are members of the \"decimal digit family\""
                )
                put("D3100", "The radix of the formatBase Object must be between 2 and 36.  It was given {{value}}")
                put(
                    "D3110",
                    "The argument of the toMillis Object must be an ISO 8601 formatted timestamp. Given {{value}}"
                )
                put("D3120", "Syntax error in expression passed to Object eval: {{value}}")
                put("D3121", "Dynamic error evaluating the expression passed to Object eval: {{value}}")
                put(
                    "D3130",
                    "Formatting or parsing an integer as a sequence starting with {{value}} is not supported by this implementation"
                )
                put("D3131", "In a decimal digit pattern, all digits must be from the same decimal group")
                put("D3132", "Unknown component specifier {{value}} in date/time picture string")
                put(
                    "D3133",
                    "The \"name\" modifier can only be applied to months and days in the date/time picture string, not {{value}}"
                )
                put("D3134", "The timezone integer format specifier cannot have more than four digits")
                put("D3135", "No matching closing bracket \"]\" in date/time picture string")
                put("D3136", "The date/time picture string is missing specifiers required to parse the timestamp")
                put("D3137", "{{{message}}}")
                put("D3138", "The \$single() Object expected exactly 1 matching result.  Instead it matched more.")
                put("D3139", "The \$single() Object expected exactly 1 matching result.  Instead it matched 0.")
                put("D3140", "Malformed URL passed to \${{{functionName}}}(): {{value}}")
                put("D3141", "{{{message}}}")
            }
        }

        init {
            registerFunctions()
        }

        /**
         * JSONata
         * @param {Object} expr - JSONata expression
         * @returns Evaluated expression
         * @throws JException An exception if an error occured.
         */
        @JvmStatic
        fun jsonata(expression: String?): Jsonata {
            return Jsonata(expression)
        }

        val _parser: ThreadLocal<Parser> = ThreadLocal()
        @Synchronized
        fun getParser(): Parser? {
            var p = _parser.get()
            if (p != null) return p
            _parser.set(Parser().also { p = it })
            return p
        }
    }
}
