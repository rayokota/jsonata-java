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
 * © Copyright IBM Corp. 2016, 2018 All Rights Reserved
 * Project name: JSONata
 * This project is licensed under the MIT License, see LICENSE
 */
package com.dashjoin.jsonata

import com.dashjoin.jsonata.Utils.convertNumber
import com.dashjoin.jsonata.utils.Signature
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

//var parseSignature = require('./signature');
class Parser {
    @JvmField
    var dbg: Boolean = false

    // This parser implements the 'Top down operator precedence' algorithm developed by Vaughan R Pratt; http://dl.acm.org/citation.cfm?id=512931.
    // and builds on the Javascript framework described by Douglas Crockford at http://javascript.crockford.com/tdop/tdop.html
    // and in 'Beautiful Code', edited by Andy Oram and Greg Wilson, Copyright 2007 O'Reilly Media, Inc. 798-0-596-51004-6
    var source: String? = null
    var recover: Boolean = false

    //var parser = function (source, recover) {
    var node: Symbol? = null
    var lexer: Tokenizer? = null

    var symbolTable: HashMap<String, Symbol> = HashMap()
    var errors: MutableList<Exception> = ArrayList()

    fun remainingTokens(): List<Tokenizer.Token> {
        val remaining: MutableList<Tokenizer.Token> = ArrayList()
        if (node!!.id != "(end)") {
            val t = Tokenizer.Token()
            t.type = node!!.type
            t.value = node!!.value
            t.position = node!!.position
            remaining.add(t)
        }
        var nxt = lexer!!.next(false)
        while (nxt != null) {
            remaining.add(nxt)
            nxt = lexer!!.next(false)
        }
        return remaining
    }


    open inner class Symbol : Cloneable {
        //Symbol s;
        var id: String? = null
        @JvmField
        var type: String? = null
        @JvmField
        var value: Any? = null
        var bp: Int = 0
        var lbp: Int = 0

        @JvmField
        var position: Int = 0

        @JvmField
        var keepArray: Boolean = false // [

        @JvmField
        var descending: Boolean = false // ^
        @JvmField
        var expression: Symbol? = null // ^
        var seekingParent: MutableList<Symbol>? = null
        @JvmField
        var errors: List<Exception>? = null

        @JvmField
        var steps: MutableList<Symbol>? = null
        @JvmField
        var slot: Symbol? = null
        var nextFunction: Symbol? = null
        @JvmField
        var keepSingletonArray: Boolean = false
        @JvmField
        var consarray: Boolean = false
        var level: Int = 0
        @JvmField
        var focus: Any? = null
        @JvmField
        var token: Any? = null
        @JvmField
        var thunk: Boolean = false

        // Procedure:
        @JvmField
        var procedure: Symbol? = null
        @JvmField
        var arguments: MutableList<Symbol>? = null
        @JvmField
        var body: Symbol? = null
        @JvmField
        var predicate: MutableList<Symbol>? = null
        @JvmField
        var stages: MutableList<Symbol>? = null
        @JvmField
        var input: Any? = null
        @JvmField
        var environment: Jsonata.Frame? = null
        @JvmField
        var tuple: Any? = null
        @JvmField
        var expr: Any? = null
        @JvmField
        var group: Symbol? = null
        var name: Any? = null

        // Infix attributes
        @JvmField
        var lhs: Symbol? = null
        @JvmField
        var rhs: Symbol? = null

        // where rhs = list of Symbol pairs
        @JvmField
        var lhsObject: List<Array<Symbol>>? = null
        var rhsObject: List<Array<Symbol>>? = null

        // where rhs = list of Symbols
        var rhsTerms: List<Symbol>? = null
        @JvmField
        var terms: List<Symbol>? = null

        // Ternary operator:
        @JvmField
        var condition: Symbol? = null
        @JvmField
        var then: Symbol? = null
        @JvmField
        var _else: Symbol? = null

        @JvmField
        var expressions: MutableList<Symbol>? = null

        // processAST error handling
        var error: JException? = null
        @JvmField
        var signature: Any? = null

        // Prefix attributes
        @JvmField
        var pattern: Symbol? = null
        @JvmField
        var update: Symbol? = null
        @JvmField
        var delete: Symbol? = null

        // Ancestor attributes
        @JvmField
        var label: String? = null
        @JvmField
        var index: Any? = null
        @JvmField
        var _jsonata_lambda: Boolean = false
        @JvmField
        var ancestor: Symbol? = null


        open fun nud(): Symbol {
            // error - symbol has been invoked as a unary operator
            val _err = JException("S0211", position, value)

            if (recover) {
                /*
                err.remaining = remainingTokens();
                err.type = "error";
                errors.add(err);
                return err;
                */
                return object : Symbol("(error)") {
                    //JException err = _err;
                }
            } else {
                throw _err
            }
        }

        open fun led(left: Symbol): Symbol {
            throw Error("led not implemented")
        }

        //class Symbol {
        constructor()

        constructor(id: String?, bp: Int = 0) {
            this.id = id
            this.value = id
            this.bp = bp
            /* use register(Symbol) ! Otherwise inheritance doesn't work
            Symbol s = symbolTable.get(id);
            //bp = bp != 0 ? bp : 0;
            if (s != null) {
                if (bp >= s.lbp) {
                    s.lbp = bp;
                }
            } else {
                s = new Symbol();
                s.value = s.id = id;
                s.lbp = bp;
                symbolTable.put(id, s);
            }

*/
            //return s;
        }

        fun create(): Symbol? {
            // We want a shallow clone (do not duplicate outer class!)
            try {
                val cl = this.clone() as Symbol
                //System.err.println("cloning "+this+" clone="+cl);
                return cl
            } catch (e: CloneNotSupportedException) {
                // never reached
                if (dbg) e.printStackTrace()
                return null
            }
        }

        override fun toString(): String {
            return this.javaClass.simpleName + " " + id + " value=" + value
        }
    }

    fun register(t: Symbol) {
        //if (t instanceof Infix || t instanceof InfixR) return;

        var s = symbolTable[t.id]
        if (s != null) {
            if (dbg) println("Symbol in table " + t.id + " " + s.javaClass.name + " -> " + t.javaClass.name)
            //symbolTable.put(t.id, t);
            if (t.bp >= s.lbp) {
                if (dbg) println("Symbol in table " + t.id + " lbp=" + s.lbp + " -> " + t.bp)
                s.lbp = t.bp
            }
        } else {
            s = t.create()
            s!!.id = t.id
            s.value = s.id
            s.lbp = t.bp
            symbolTable[t.id!!] = s
        }
    }

    fun handleError(err: JException): Symbol {
        if (recover) {
            err.remaining = remainingTokens()
            errors.add(err)
            //Symbol symbol = symbolTable.get("(error)");
            val node = Symbol()
            // FIXME node.error = err;
            //node.type = "(error)";
            return node
        } else {
            throw err
        }
    }

    //}
    fun advance(id: String? = null, infix: Boolean = false): Symbol {
        if (id != null && node!!.id != id) {
            val code = if (node!!.id == "(end)") {
                // unexpected end of buffer
                "S0203"
            } else {
                "S0202"
            }
            val err = JException(
                code,
                node!!.position,
                id,
                node!!.value
            )
            return handleError(err)
        }
        val next_token = lexer!!.next(infix)
        if (dbg) println("nextToken " + (next_token?.type))
        if (next_token == null) {
            node = symbolTable["(end)"]
            node!!.position = source!!.length
            return node!!
        }
        val value = next_token.value
        var type = next_token.type
        val symbol: Symbol?
        when (type) {
            "name", "variable" -> symbol = symbolTable["(name)"]
            "operator" -> {
                symbol = symbolTable["" + value]
                if (symbol == null) {
                    return handleError(
                        JException(
                            "S0204", next_token.position, value
                        )
                    )
                }
            }

            "string", "number", "value" -> symbol = symbolTable["(literal)"]
            "regex" -> {
                type = "regex"
                symbol = symbolTable["(regex)"]
            }

            else -> return handleError(
                JException(
                    "S0205", next_token.position, value
                )
            )
        }
        node = symbol!!.create()
        //Token node = new Token(); //Object.create(symbol);
        node!!.value = value
        node!!.type = type
        node!!.position = next_token.position
        if (dbg) println("advance $node")
        return node!!
    }

    // Pratt's algorithm
    fun expression(rbp: Int): Symbol {
        var left: Symbol
        var t = node
        advance(null, true)
        left = t!!.nud()
        while (rbp < node!!.lbp) {
            t = node
            advance(null, false)
            if (dbg) println("t=" + t + ", left=" + left.type)
            left = t!!.led(left)
        }
        return left
    }

    internal inner class Terminal(id: String?) : Symbol(id, 0) {
        override fun nud(): Symbol {
            return this
        }
    }

    /*
            var terminal = function (id) {
            var s = symbol(id, 0);
            s.nud = function () {
                return this;
            };
        };
        */
    // match infix operators
    // <expression> <operator> <expression>
    // left associative
    internal open inner class Infix(id: String?, bp: Int = 0) :
        Symbol(id, (if (bp != 0) bp else (if (id != null) Tokenizer.operators[id] else 0))!!) {
        override fun led(left: Symbol): Symbol {
            lhs = left
            rhs = expression(bp)
            type = "binary"
            return this
        }
    }


    internal inner class InfixAndPrefix(id: String?, bp: Int = 0) : Infix(id, bp), Cloneable {
        var prefix: Prefix

        init {
            prefix = Prefix(id)
        }

        override fun nud(): Symbol {
            return prefix.nud()
            // expression(70);
            // type="unary";
            // return this;
        }

        @Throws(CloneNotSupportedException::class)
        public override fun clone(): Any {
            val c: Any = super<Cloneable>.clone()
            // IMPORTANT: make sure to allocate a new Prefix!!!
            (c as InfixAndPrefix).prefix = Prefix(c.id)
            return c
        }
    }

    // match infix operators
    // <expression> <operator> <expression>
    // right associative
    internal open inner class InfixR(id: String?, bp: Int) //abstract Object led();
        : Symbol(id, bp)

    // match prefix operators
    // <operator> <expression>
    internal open inner class Prefix  //public List<Symbol[]> lhs;
        (id: String?) : Symbol(id) {
        //Symbol _expression;
        override fun nud(): Symbol {
            expression = expression(70)
            type = "unary"
            return this
        }
    }

    // tail call optimization
    // this is invoked by the post parser to analyse lambda functions to see
    // if they make a tail call.  If so, it is replaced by a thunk which will
    // be invoked by the trampoline loop during function application.
    // This enables tail-recursive functions to be written without growing the stack
    fun tailCallOptimize(expr: Symbol): Symbol {
        val result: Symbol
        if (expr.type == "function" && expr.predicate == null) {
            val thunk = Symbol()
            thunk.type = "lambda"
            thunk.thunk = true
            thunk.arguments = mutableListOf<Symbol>()
            thunk.position = expr.position
            thunk.body = expr
            result = thunk
        } else if (expr.type == "condition") {
            // analyse both branches
            expr.then = tailCallOptimize(expr.then!!)
            if (expr._else != null) {
                expr._else = tailCallOptimize(expr._else!!)
            }
            result = expr
        } else if (expr.type == "block") {
            // only the last expression in the block
            val length = expr.expressions!!.size
            if (length > 0) {
                if (expr.expressions !is ArrayList<*>) expr.expressions = ArrayList(
                    expr.expressions
                )
                expr.expressions!![length - 1] = tailCallOptimize(expr.expressions!![length - 1])
            }
            result = expr
        } else {
            result = expr
        }
        return result
    }

    var ancestorLabel: Int = 0
    var ancestorIndex: Int = 0
    var ancestry: MutableList<Symbol> = ArrayList()

    init {
        register(Terminal("(end)"))
        register(Terminal("(name)"))
        register(Terminal("(literal)"))
        register(Terminal("(regex)"))
        register(Symbol(":"))
        register(Symbol(";"))
        register(Symbol(","))
        register(Symbol(")"))
        register(Symbol("]"))
        register(Symbol("}"))
        register(Symbol("..")) // range operator
        register(Infix(".")) // map operator
        register(Infix("+")) // numeric addition
        register(InfixAndPrefix("-")) // numeric subtraction

        // unary numeric negation
        register(object : Infix("*") {
            // field wildcard (single level)
            override fun nud(): Symbol {
                type = "wildcard"
                return this
            }
        }) // numeric multiplication
        register(Infix("/")) // numeric division
        register(object : Infix("%") {
            // parent operator
            override fun nud(): Symbol {
                type = "parent"
                return this
            }
        }) // numeric modulus
        register(Infix("=")) // equality
        register(Infix("<")) // less than
        register(Infix(">")) // greater than
        register(Infix("!=")) // not equal to
        register(Infix("<=")) // less than or equal
        register(Infix(">=")) // greater than or equal
        register(Infix("&")) // string concatenation

        register(object : Infix("and") {
            // allow as terminal
            override fun nud(): Symbol {
                return this
            }
        }) // Boolean AND
        register(object : Infix("or") {
            // allow as terminal
            override fun nud(): Symbol {
                return this
            }
        }) // Boolean OR
        register(object : Infix("in") {
            // allow as terminal
            override fun nud(): Symbol {
                return this
            }
        }) // is member of array
        // merged Infix: register(new Terminal("and")); // the 'keywords' can also be used as terminals (field names)
        // merged Infix: register(new Terminal("or")); //
        // merged Infix: register(new Terminal("in")); //
        // merged Infix: register(new Prefix("-")); // unary numeric negation
        register(Infix("~>")) // function application

        register(object : InfixR("(error)", 10) {
            override fun led(left: Symbol): Symbol {
                throw UnsupportedOperationException("TODO", null)
            }
        })

        // field wildcard (single level)
        // merged with Infix *
        // register(new Prefix("*") {
        //     @Override Symbol nud() {
        //         type = "wildcard";
        //         return this;
        //     }
        // });

        // descendant wildcard (multi-level)
        register(object : Prefix("**") {
            override fun nud(): Symbol {
                type = "descendant"
                return this
            }
        })

        // parent operator
        // merged with Infix %
        // register(new Prefix("%") {
        //     @Override Symbol nud() {
        //         type = "parent";
        //         return this;
        //     }
        // });

        // function invocation
        register(object : Infix("(", Tokenizer.operators["("]!!) {
            override fun led(left: Symbol): Symbol {
                // left is is what we are trying to invoke
                this.procedure = left
                this.type = "function"
                this.arguments = ArrayList()
                if (node!!.id != ")") {
                    while (true) {
                        if ("operator" == node!!.type && node!!.id == "?") {
                            // partial function application
                            this.type = "partial"
                            arguments!!.add(node!!)
                            advance("?")
                        } else {
                            arguments!!.add(expression(0))
                        }
                        if (node!!.id != ",") break
                        advance(",")
                    }
                }
                advance(")", true)
                // if the name of the function is 'function' or λ, then this is function definition (lambda function)
                if (left.type == "name" && (left.value == "function" || left.value == "\u03BB")) {
                    // all of the args must be VARIABLE tokens
                    //int index = 0;
                    for (arg in arguments!!) {
                        //this.arguments.forEach(function (arg, index) {
                        if (arg.type != "variable") {
                            return handleError(
                                JException(
                                    "S0208",
                                    arg.position,
                                    arg.value //,
                                    //index + 1
                                )
                            )
                        }
                        //index++;
                    }
                    this.type = "lambda"
                    // is the next token a '<' - if so, parse the function signature
                    if (node!!.id == "<") {
                        var depth = 1
                        var sig = "<"
                        while (depth > 0 && node!!.id != "{" && node!!.id != "(end)") {
                            val tok = advance()
                            if (tok.id == ">") {
                                depth--
                            } else if (tok.id == "<") {
                                depth++
                            }
                            sig += tok.value
                        }
                        advance(">")
                        this.signature = Signature(sig, "lambda")
                    }
                    // parse the function body
                    advance("{")
                    this.body = expression(0)
                    advance("}")
                }
                return this
            }

            //});
            // parenthesis - block expression
            // Note: in Java both nud and led are in same class!
            //register(new Prefix("(") {
            override fun nud(): Symbol {
                if (dbg) println("Prefix (")
                val expressions: MutableList<Symbol> = ArrayList()
                while (node!!.id != ")") {
                    expressions.add(this@Parser.expression(0))
                    if (node!!.id != ";") {
                        break
                    }
                    advance(";")
                }
                advance(")", true)
                this.type = "block"
                this.expressions = expressions
                return this
            }
        })

        // array constructor

        // merged: register(new Prefix("[") {        
        register(object : Infix("[", Tokenizer.operators["["]!!) {
            override fun nud(): Symbol {
                val a: MutableList<Symbol> = ArrayList()
                if (node!!.id != "]") {
                    while (true) {
                        var item = this@Parser.expression(0)
                        if (node!!.id == "..") {
                            // range operator
                            val range = Symbol()
                            range.type = "binary"
                            range.value = ".."
                            range.position = node!!.position
                            range.lhs = item
                            advance("..")
                            range.rhs = expression(0)
                            item = range
                        }
                        a.add(item)
                        if (node!!.id != ",") {
                            break
                        }
                        advance(",")
                    }
                }
                advance("]", true)
                this.expressions = a
                this.type = "unary"
                return this
            }

            //});
            // filter - predicate or array index
            //register(new Infix("[", Tokenizer.operators.get("[")) {
            override fun led(left: Symbol): Symbol {
                if (node!!.id == "]") {
                    // empty predicate means maintain singleton arrays in the output
                    var step: Symbol? = left
                    while (step != null && step.type == "binary" && step.value == "[") {
                        step = (step as Infix).lhs
                    }
                    step!!.keepArray = true
                    advance("]")
                    return left
                } else {
                    this.lhs = left
                    this.rhs = expression(Tokenizer.operators["]"]!!)
                    this.type = "binary"
                    advance("]", true)
                    return this
                }
            }
        })

        // order-by
        register(object : Infix("^", Tokenizer.operators["^"]!!) {
            override fun led(left: Symbol): Symbol {
                advance("(")
                val terms: MutableList<Symbol> = ArrayList()
                while (true) {
                    val term: Symbol = Symbol()
                    term.descending = false

                    if (node!!.id == "<") {
                        // ascending sort
                        advance("<")
                    } else if (node!!.id == ">") {
                        // descending sort
                        term.descending = true
                        advance(">")
                    } else {
                        //unspecified - default to ascending
                    }
                    term.expression = this@Parser.expression(0)
                    terms.add(term)
                    if (node!!.id != ",") {
                        break
                    }
                    advance(",")
                }
                advance(")")
                this.lhs = left
                this.rhsTerms = terms
                this.type = "binary"
                return this
            }
        })

        register(object : Infix("{", Tokenizer.operators["{"]!!) {
            // merged register(new Prefix("{") {
            override fun nud(): Symbol {
                return objectParser(null)
            }

            // });
            // register(new Infix("{", Tokenizer.operators.get("{")) {
            override fun led(left: Symbol): Symbol {
                return objectParser(left)
            }
        })

        // bind variable
        register(object : InfixR(":=", Tokenizer.operators[":="]!!) {
            override fun led(left: Symbol): Symbol {
                if (left.type != "variable") {
                    return handleError(
                        JException(
                            "S0212",
                            left.position,
                            left.value
                        )
                    )
                }
                this.lhs = left
                this.rhs =
                    expression(Tokenizer.operators[":="]!! - 1) // subtract 1 from bindingPower for right associative operators
                this.type = "binary"
                return this
            }
        })

        // focus variable bind
        register(object : Infix("@", Tokenizer.operators["@"]!!) {
            override fun led(left: Symbol): Symbol {
                this.lhs = left
                this.rhs = expression(Tokenizer.operators["@"]!!)
                if (rhs!!.type != "variable") {
                    return handleError(
                        JException(
                            "S0214",
                            rhs!!.position,
                            "@"
                        )
                    )
                }
                this.type = "binary"
                return this
            }
        })

        // index (position) variable bind
        register(object : Infix("#", Tokenizer.operators["#"]!!) {
            override fun led(left: Symbol): Symbol {
                this.lhs = left
                this.rhs = expression(Tokenizer.operators["#"]!!)
                if (rhs!!.type != "variable") {
                    return handleError(
                        JException(
                            "S0214",
                            rhs!!.position,
                            "#"
                        )
                    )
                }
                this.type = "binary"
                return this
            }
        })

        // if/then/else ternary operator ?:
        register(object : Infix("?", Tokenizer.operators["?"]!!) {
            override fun led(left: Symbol): Symbol {
                this.type = "condition"
                this.condition = left
                this.then = expression(0)
                if (node!!.id == ":") {
                    // else condition
                    advance(":")
                    this._else = expression(0)
                }
                return this
            }
        })

        // object transformer
        register(object : Prefix("|") {
            override fun nud(): Symbol {
                this.type = "transform"
                this.pattern = this@Parser.expression(0)
                advance("|")
                this.update = this@Parser.expression(0)
                if (node!!.id == ",") {
                    advance(",")
                    this.delete = this@Parser.expression(0)
                }
                advance("|")
                return this
            }
        })
    }

    fun seekParent(node: Symbol, slot: Symbol): Symbol {
        var slot = slot
        when (node.type) {
            "name", "wildcard" -> {
                slot!!.level--
                if (slot.level == 0) {
                    if (node.ancestor == null) {
                        node.ancestor = slot
                    } else {
                        // reuse the existing label
                        ancestry[slot.index as Int]!!.slot!!.label = node.ancestor!!.label
                        node.ancestor = slot
                    }
                    node.tuple = true
                }
            }

            "parent" -> slot!!.level++
            "block" ->                 // look in last expression in the block
                if (node.expressions!!.size > 0) {
                    node.tuple = true
                    slot = seekParent(node.expressions!![node.expressions!!.size - 1], slot)
                }

            "path" -> {
                // last step in path
                node.tuple = true
                var index = node.steps!!.size - 1
                slot = seekParent(node.steps!![index--], slot)
                while (slot!!.level > 0 && index >= 0) {
                    // check previous steps
                    slot = seekParent(node.steps!![index--], slot)
                }
            }

            else ->                 // error - can't derive ancestor
                throw JException(
                    "S0217",
                    node.position,
                    node.type
                )
        }
        return slot
    }


    fun pushAncestry(result: Symbol, value: Symbol?) {
        if (value == null) return  // Added NPE check

        if (value.seekingParent != null || value.type == "parent") {
            val slots: MutableList<Symbol> = if ((value.seekingParent != null)) value.seekingParent!! else ArrayList()
            if (value.type == "parent") {
                slots.add(value.slot!!)
            }
            if (result.seekingParent == null) {
                result.seekingParent = slots
            } else {
                result.seekingParent!!.addAll(slots)
            }
        }
    }

    fun resolveAncestry(path: Symbol) {
        var index = path.steps!!.size - 1
        val laststep = path.steps!![index]
        val slots: MutableList<Symbol> = if ((laststep.seekingParent != null)) laststep.seekingParent!! else ArrayList()
        if (laststep.type == "parent") {
            slots.add(laststep.slot!!)
        }
        for (`is` in slots.indices) {
            var slot = slots[`is`]
            index = path.steps!!.size - 2
            while (slot.level > 0) {
                if (index < 0) {
                    if (path.seekingParent == null) {
                        path.seekingParent = ArrayList(Arrays.asList(slot))
                    } else {
                        path.seekingParent!!.add(slot)
                    }
                    break
                }
                // try previous step
                var step = path.steps!![index--]
                // multiple contiguous steps that bind the focus should be skipped
                while (index >= 0 && step.focus != null && path.steps!![index].focus != null) {
                    step = path.steps!![index--]
                }
                slot = seekParent(step, slot)
            }
        }
    }

    // post-parse stage
    // the purpose of this is to add as much semantic value to the parse tree as possible
    // in order to simplify the work of the evaluator.
    // This includes flattening the parts of the AST representing location paths,
    // converting them to arrays of steps which in turn may contain arrays of predicates.
    // following this, nodes containing '.' and '[' should be eliminated from the AST.
    fun processAST(expr: Symbol?): Symbol? {
        var result = expr
        if (expr == null) return null
        if (dbg) println(" > processAST type=" + expr.type + " value='" + expr.value + "'")
        when (if (expr.type != null) expr.type else "(null)") {
            "binary" -> {
                when ("" + expr.value) {
                    "." -> {
                        val lstep = processAST((expr as Infix).lhs)

                        if (lstep!!.type == "path") {
                            result = lstep
                        } else {
                            result = Infix(null)
                            result.type = "path"
                            result.steps = ArrayList(Arrays.asList(lstep))
                            //result = {type: 'path', steps: [lstep]};
                        }
                        if (lstep.type == "parent") {
                            result!!.seekingParent = ArrayList(
                                Arrays.asList(
                                    lstep.slot
                                )
                            )
                        }
                        val rest = processAST(expr.rhs)
                        if (rest!!.type == "function" && rest.procedure!!.type == "path" && rest.procedure!!.steps!!.size == 1 && rest.procedure!!.steps!![0].type == "name" && result!!.steps!![result.steps!!.size - 1].type == "function") {
                            // next function in chain of functions - will override a thenable
                            result.steps!![result.steps!!.size - 1].nextFunction =
                                rest.procedure!!.steps!![0].value as Symbol?
                        }
                        if (rest.type == "path") {
                            result!!.steps!!.addAll(rest.steps!!)
                        } else {
                            if (rest.predicate != null) {
                                rest.stages = rest.predicate
                                rest.predicate = null
                                //delete rest.predicate;
                            }
                            result!!.steps!!.add(rest)
                        }
                        // any steps within a path that are string literals, should be changed to 'name'
                        for (step in result.steps!!) {
                            if (step.type == "number" || step.type == "value") {
                                // don't allow steps to be numbers or the values true/false/null
                                throw JException(
                                    "S0213",
                                    step.position,
                                    step.value
                                )
                            }
                            //System.out.println("step "+step+" type="+step.type);
                            if (step.type == "string") step.type = "name"
                            // for (var lit : step.steps) {
                            //     System.out.println("step2 "+lit+" type="+lit.type);
                            //     lit.type = "name";
                            // }
                        }


                        // any step that signals keeping a singleton array, should be flagged on the path
                        if (result.steps!!.stream().filter { step: Symbol -> step.keepArray == true }
                                .count() > 0) {
                            result.keepSingletonArray = true
                        }
                        // if first step is a path constructor, flag it for special handling
                        val firststep = result.steps!![0]
                        if (firststep.type == "unary" && ("" + firststep.value) == "[") {
                            firststep.consarray = true
                        }
                        // if the last step is an array constructor, flag it so it doesn't flatten
                        val laststep = result.steps!![result.steps!!.size - 1]
                        if ((laststep.type == "unary") && ("" + laststep.value) == "[") {
                            laststep.consarray = true
                        }
                        resolveAncestry(result)
                    }

                    "[" -> {
                        if (dbg) println("binary [")
                        // predicated step
                        // LHS is a step or a predicated step
                        // RHS is the predicate expr
                        result = processAST((expr as Infix).lhs)
                        var step = result
                        var type = "predicate"
                        if (result!!.type == "path") {
                            step = result.steps!![result.steps!!.size - 1]
                            type = "stages"
                        }
                        if (step!!.group != null) {
                            throw JException(
                                "S0209",  //stack: (new Error()).stack,
                                expr.position
                            )
                        }
                        // if (typeof step[type] === 'undefined') {
                        //     step[type] = [];
                        // }
                        if (type == "stages") {
                            if (step.stages == null) step.stages = ArrayList()
                        } else {
                            if (step.predicate == null) step.predicate = ArrayList()
                        }

                        val predicate = processAST(expr.rhs)
                        if (predicate!!.seekingParent != null) {
                            val _step = step
                            predicate.seekingParent!!.forEach(Consumer { slot: Symbol? ->
                                if (slot!!.level == 1) {
                                    seekParent(_step, slot)
                                } else {
                                    slot.level--
                                }
                            })
                            pushAncestry(step, predicate)
                        }
                        val s: Symbol = Symbol()
                        s.type = "filter"
                        s.expr = predicate
                        s.position = expr.position

                        // FIXED:
                        // this logic is required in Java to fix
                        // for example test: flattening case 045
                        // otherwise we lose the keepArray flag
                        if (expr.keepArray) step.keepArray = true

                        if (type == "stages") step.stages!!.add(s)
                        else step.predicate!!.add(s)
                    }

                    "{" -> {
                        // group-by
                        // LHS is a step or a predicated step
                        // RHS is the object constructor expr
                        result = processAST(expr.lhs)
                        if (result!!.group != null) {
                            throw JException(
                                "S0210",  //stack: (new Error()).stack,
                                expr.position
                            )
                        }
                        // object constructor - process each pair
                        result.group = Symbol()
                        result.group!!.lhsObject = expr.rhsObject!!.stream().map { pair: Array<Symbol> ->
                            arrayOf(
                                processAST(
                                    pair[0]
                                )!!, processAST(pair[1])!!
                            )
                        }.collect(Collectors.toList())
                        result.group!!.position = expr.position
                    }

                    "^" -> {
                        // order-by
                        // LHS is the array to be ordered
                        // RHS defines the terms
                        result = processAST(expr.lhs)
                        if (result!!.type != "path") {
                            val _res: Symbol = Symbol()
                            _res.type = "path"
                            _res.steps = ArrayList()
                            _res.steps!!.add(result)
                            result = _res
                        }
                        val sortStep: Symbol = Symbol()
                        sortStep.type = "sort"
                        sortStep.position = expr.position
                        sortStep.terms = expr.rhsTerms!!.stream().map { terms: Symbol ->
                            val expression = processAST(terms.expression)
                            pushAncestry(sortStep, expression)
                            val res: Symbol = Symbol()
                            res.descending = terms.descending
                            res.expression = expression
                            res
                        }.collect(Collectors.toList())
                        result.steps!!.add(sortStep)
                        resolveAncestry(result)
                    }

                    ":=" -> {
                        result = Symbol()
                        result!!.type = "bind"
                        result.value = expr.value
                        result.position = expr.position
                        result.lhs = processAST(expr.lhs)
                        result.rhs = processAST(expr.rhs)
                        pushAncestry(result, result.rhs)
                    }

                    "@" -> {
                        result = processAST(expr.lhs)
                        var step = result
                        if (result!!.type == "path") {
                            step = result.steps!![result.steps!!.size - 1]
                        }
                        // throw error if there are any predicates defined at this point
                        // at this point the only type of stages can be predicates
                        if (step!!.stages != null || step.predicate != null) {
                            throw JException(
                                "S0215",  //stack: (new Error()).stack,
                                expr.position
                            )
                        }
                        // also throw if this is applied after an 'order-by' clause
                        if (step.type == "sort") {
                            throw JException(
                                "S0216",  //stack: (new Error()).stack,
                                expr.position
                            )
                        }
                        if (expr.keepArray) {
                            step.keepArray = true
                        }
                        step.focus = expr.rhs!!.value
                        step.tuple = true
                    }

                    "#" -> {
                        result = processAST(expr.lhs)
                        var step = result
                        if (result!!.type == "path") {
                            step = result.steps!![result.steps!!.size - 1]
                        } else {
                            val _res: Symbol = Symbol()
                            _res.type = "path"
                            _res.steps = ArrayList()
                            _res.steps!!.add(result)
                            result = _res
                            if (step!!.predicate != null) {
                                step!!.stages = step.predicate
                                step!!.predicate = null
                            }
                        }
                        if (step.stages == null) {
                            step.index = expr.rhs!!.value // name of index variable = String
                        } else {
                            val _res: Symbol = Symbol()
                            _res.type = "index"
                            _res.value = expr.rhs!!.value
                            _res.position = expr.position
                            step.stages!!.add(_res)
                        }
                        step.tuple = true
                    }

                    "~>" -> {
                        result = Symbol()
                        result!!.type = "apply"
                        result.value = expr.value
                        result.position = expr.position
                        result.lhs = processAST(expr.lhs)
                        result.rhs = processAST(expr.rhs)
                    }

                    else -> {
                        val _result = Infix(null)
                        _result.type = expr.type
                        _result.value = expr.value
                        _result.position = expr.position
                        _result.lhs = processAST(expr.lhs)
                        _result.rhs = processAST(expr.rhs)
                        pushAncestry(_result, _result.lhs)
                        pushAncestry(_result, _result.rhs)
                        result = _result
                    }
                }
            }

            "unary" -> {
                result = Symbol()
                result!!.type = expr.type
                result.value = expr.value
                result.position = expr.position
                // expr.value might be Character!
                val exprValue = "" + expr.value
                if (exprValue == "[") {
                    if (dbg) println("unary [ $result")
                    // array constructor - process each item
                    val _result = result
                    result.expressions = expr.expressions!!.stream().map<Symbol?> { item: Symbol? ->
                        val value = processAST(item)
                        pushAncestry(_result, value)
                        value
                    }.collect(Collectors.toList<Symbol?>())
                } else if (exprValue == "{") {
                    // object constructor - process each pair
                    //throw new Error("processAST {} unimpl");
                    val _result = result
                    result.lhsObject = expr.lhsObject!!.stream().map<Array<Symbol>> { pair: Array<Symbol> ->
                        val key = processAST(pair[0])!!
                        pushAncestry(_result, key)
                        val value = processAST(pair[1])!!
                        pushAncestry(_result, value)
                        arrayOf<Symbol>(key, value)
                    }.collect(Collectors.toList<Array<Symbol>>())
                } else {
                    // all other unary expressions - just process the expression
                    result.expression = processAST(expr.expression)
                    // if unary minus on a number, then pre-process
                    if (exprValue == "-" && result.expression!!.type == "number") {
                        result = result.expression
                        result!!.value = convertNumber(-(result.value as Number?)!!.toDouble())
                        if (dbg) println("unary - value=" + result.value)
                    } else {
                        pushAncestry(result, result.expression)
                    }
                }
            }

            "function", "partial" -> {
                result = Symbol()
                result.type = expr.type
                result.name = expr.name
                result.value = expr.value
                result.position = expr.position
                val _result = result
                result.arguments = expr.arguments!!.stream().map<Symbol?> { arg: Symbol? ->
                    val argAST = processAST(arg)
                    pushAncestry(_result, argAST)
                    argAST
                }.collect(Collectors.toList<Symbol?>())
                result.procedure = processAST(expr.procedure)
            }

            "lambda" -> {
                result = Symbol()
                result.type = expr.type
                result.arguments = expr.arguments
                result.signature = expr.signature
                result.position = expr.position
                val body = processAST(expr.body)
                result.body = tailCallOptimize(body!!)
            }

            "condition" -> {
                result = Symbol()
                result.type = expr.type
                result.position = expr.position
                result.condition = processAST(expr.condition)
                pushAncestry(result, result.condition)
                result.then = processAST(expr.then)
                pushAncestry(result, result.then)
                if (expr._else != null) {
                    result._else = processAST(expr._else)
                    pushAncestry(result, result._else)
                }
            }

            "transform" -> {
                result = Symbol()
                result.type = expr.type
                result.position = expr.position
                result.pattern = processAST(expr.pattern)
                result.update = processAST(expr.update)
                if (expr.delete != null) {
                    result.delete = processAST(expr.delete)
                }
            }

            "block" -> {
                result = Symbol()
                result.type = expr.type
                result.position = expr.position
                // array of expressions - process each one
                val __result = result
                result.expressions = expr.expressions!!.stream().map<Symbol?> { item: Symbol? ->
                    val part = processAST(item)
                    pushAncestry(__result, part)
                    if (part!!.consarray || (part.type == "path" && part.steps!![0].consarray)) {
                        __result.consarray = true
                    }
                    part
                }.collect(Collectors.toList<Symbol?>())
            }

            "name" -> {
                result = Symbol()
                result.type = "path"
                result.steps = ArrayList()
                result.steps!!.add(expr)
                if (expr.keepArray) {
                    result.keepSingletonArray = true
                }
            }

            "parent" -> {
                result = Symbol()
                result.type = "parent"
                result.slot = Symbol()
                result.slot!!.label = "!" + ancestorLabel++
                result.slot!!.level = 1
                result.slot!!.index = ancestorIndex++
                //slot: { label: '!' + ancestorLabel++, level: 1, index: ancestorIndex++ } };
                ancestry.add(result)
            }

            "string", "number", "value", "wildcard", "descendant", "variable", "regex" -> result = expr
            "operator" ->                 // the tokens 'and' and 'or' might have been used as a name rather than an operator
                if (expr.value == "and" || expr.value == "or" || expr.value == "in") {
                    expr.type = "name"
                    result = processAST(expr)
                } else  /* istanbul ignore else */ if (("" + expr.value) == "?") {
                    // partial application
                    result = expr
                } else {
                    throw JException(
                        "S0201",  //stack: (new Error()).stack,
                        expr.position,
                        expr.value
                    )
                }

            "error" -> {
                result = expr
                if (expr.lhs != null) {
                    result = processAST(expr.lhs)
                }
            }

            else -> {
                var code = "S0206"
                /* istanbul ignore else */
                if (expr.id == "(end)") {
                    code = "S0207"
                }
                val err = JException(
                    code,
                    expr.position,
                    expr.value
                )
                if (recover) {
                    errors.add(err)
                    val ret = Symbol()
                    ret.type = "error"
                    ret.error = err
                    return ret
                } else {
                    //err.stack = (new Error()).stack;
                    throw err
                }
            }
        }
        if (expr.keepArray) {
            result!!.keepArray = true
        }
        return result
    }

    fun objectParser(left: Symbol?): Symbol {
        val res = if (left != null) Infix("{") else Prefix("{")

        val a: MutableList<Array<Symbol>> = ArrayList()
        if (node!!.id != "}") {
            while (true) {
                val n = this@Parser.expression(0)
                advance(":")
                val v = this@Parser.expression(0)
                val pair = arrayOf(n, v)
                a.add(pair) // holds an array of name/value expression pairs
                if (node!!.id != ",") {
                    break
                }
                advance(",")
            }
        }
        advance("}", true)
        if (left == null) { //typeof left === 'undefined') {
            // NUD - unary prefix form
            (res as Prefix).lhsObject = a
            res.type = "unary"
        } else {
            // LED - binary infix form
            (res as Infix).lhs = left
            res.rhsObject = a
            res.type = "binary"
        }
        return res
    }

    fun parse(jsonata: String?): Symbol {
        source = jsonata

        // now invoke the tokenizer and the parser and return the syntax tree
        lexer = Tokenizer(source!!)
        advance()
        // parse the tokens
        var expr: Symbol? = expression(0)
        if (node!!.id != "(end)") {
            val err = JException(
                "S0201",
                node!!.position,
                node!!.value
            )
            handleError(err)
        }

        expr = processAST(expr)

        if (expr!!.type == "parent" || expr.seekingParent != null) {
            // error - trying to derive ancestor at top level
            throw JException(
                "S0217",
                expr.position,
                expr.type
            )
        }

        if (errors.size > 0) {
            expr.errors = errors
        }

        return expr
    }

    companion object {
        fun <T> clone(`object`: T): T {
            try {
                val bOut = ByteArrayOutputStream()
                val out = ObjectOutputStream(bOut)
                out.writeObject(`object`)
                out.close()

                val `in` = ObjectInputStream(ByteArrayInputStream(bOut.toByteArray()))
                val copy = `in`.readObject() as T
                `in`.close()

                return copy
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }
}
