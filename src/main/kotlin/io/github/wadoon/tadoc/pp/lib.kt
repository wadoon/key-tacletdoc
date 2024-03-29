/* key-tools are extension for the KeY theorem prover.
 * Copyright (C) 2021  Alexander Weigl
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * For the complete terms of the GNU General Public License, please see this URL:
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
@file:Suppress("unused", "KDocUnresolvedReference")

package io.github.wadoon.tadoc.pp

import io.github.wadoon.tadoc.pp.Document.Group
import io.github.wadoon.tadoc.pp.Document.HardLine
import io.github.wadoon.tadoc.pp.Document.IfFlat
import java.io.PrintWriter
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.*
import kotlin.Boolean
import kotlin.Double
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.Pair
import kotlin.Suppress
import kotlin.Unit
import kotlin.text.String

/** A point is a pair of a line number and a column number. */
typealias Point = Pair<Int, Int>

/** A range is a pair of points. */
typealias PointRange = Pair<Point, Point>

/** A type of integers with infinity. Infinity is encoded as [max_int]. */
@JvmInline
value class Requirement(val value: Int) {
    val isInfinity: Boolean
        get() = value == infinity.value

    /** Addition of integers with infinity. */
    operator fun plus(y: Requirement) =
        if (isInfinity || y.isInfinity) infinity
        else Requirement(value + y.value)

    /* Comparison between an integer with infinity and a normal integer. */
    operator fun compareTo(y: Int) = value - y // x<=ys
}

val infinity = Requirement(Integer.MAX_VALUE)

/* ------------------------------------------------------------------------- */

/* Printing blank space. This is used both internally (to emit indentation
characters) and via the public combinator [blank]. */

const val blank_length = 80

/** The rendering engine maintains the following internal state. Its structure
is subject to change in future versions of the library. Nevertheless, it is
exposed to the user who wishes to define custom documents. */
data class State(
    /** The line width. This parameter is fixed throughout the execution of
    the renderer. */
    val width: Int,
    /** The ribbon width. This parameter is fixed throughout the execution of
    the renderer. */
    val ribbon: Int,
    /** The number of blanks that were printed at the beginning of the current
    line. This field is updated (only) when a hardline is emitted. It is
    used (only) to determine whether the ribbon width constraint is
    respected. */
    var lastIndent: Int = 0,
    /** The current line. This field is updated (only) when a hardline is
    emitted. It is not used by the pretty-printing engine itself. */
    var line: Int = 0,
    /** The current column. This field must be updated whenever something is
    sent to the output channel. It is used (only) to determine whether the
    width constraint is respected. */
    var column: Int = 0
) {
    constructor(width: Int, rfrac: Double) : this(width, max(0, min(width, (width * rfrac).toInt())))
}

/** A custom document is defined by implementing the following methods. */
interface CustomDocument {
    /** A custom document must publish the width (i.e., the number of columns)
    that it would like to occupy if it is printed on a single line (that is,
    in flattening mode). The special value [infinity] means that this
    document cannot be printed on a single line; this value causes any
    groups that contain this document to be dissolved. This method should
    in principle work in constant time. */
    val requirement: Requirement

    /** The method [pretty] is used by the main rendering algorithm. It has
    access to the output channel and to the algorithm's internal state, as
    described above. In addition, it receives the current indentation level
    and the current flattening mode (on or off). If flattening mode is on,
    then the document must be printed on a single line, in a manner that is
    consistent with the Requirement that was published ahead of time. If
    flattening mode is off, then there is no such obligation. The state must
    be updated in a manner that is consistent with what is sent to the
    output channel. */
    fun pretty(o: PrintWriter, s: State, i: Int, b: Boolean)

    /** The method [compact] is used by the compact rendering algorithm. It has
    access to the output channel only. */
    fun compact(o: PrintWriter)
}

/** Here is the algebraic data type of documents. It is analogous to Daan
Leijen's version, but the binary constructor [Union] is replaced with
the unary constructor [Group], and the constant [Line] is replaced with
more general constructions, namely [IfFlat], which provides alternative
forms depending on the current flattening mode, and [HardLine], which
represents a newline character, and causes a failure in flattening mode. */

sealed class Document {
    /** [Empty] is the empty document. */
    object Empty : Document()

    /** [Char c] is a document that consists of the single character [c]. We
    enforce the invariant that [c] is not a newline character. */
    data class Char(val char: kotlin.Char) : Document()

    /** [String s] is a document that consists of just the string [s]. We
    assume, but do not check, that this string does not contain a newline
    character. [String] is a special case of [FancyString], which takes up
    less space in memory. */
    data class String(val s: kotlin.String) : Document()

    /** [FancyString (s, ofs, len, apparent_length)] is a (portion of a) string
    that may contain fancy characters: color escape characters, UTF-8 or
    multi-byte characters, etc. Thus, the apparent length (which corresponds
    to what will be visible on screen) differs from the length (which is a
    number of bytes, and is reported by [String.length]). We assume, but do
    not check, that fancystrings do not contain a newline character. */
    data class FancyString(
        val s: kotlin.String,
        val ofs: Int,
        val len: Int,
        val apparentLength: Int
    ) : Document() {
        constructor(s: kotlin.String, apparentLength: Int) : this(s, 0, s.length, apparentLength)
    }

    /** [Blank n] is a document that consists of [len] blank characters. */
    data class Blank(val len: Int) : Document()

    /** When in flattening mode, [IfFlat (d1, d2)] turns into the document
    [d1]. When not in flattening mode, it turns into the document [d2]. */
    data class IfFlat(val doc1: Document, val doc2: Document) : Document()

    /** When in flattening mode, [HardLine] causes a failure, which requires
    backtracking all the way until the stack is empty. When not in flattening
    mode, it represents a newline character, followed with an appropriate
    number of indentation. A common way of using [HardLine] is to only use it
    directly within the right branch of an [IfFlat] construct. */
    object HardLine : Document()

    /** The following constructors store their space Requirement. This is the
    document's apparent length, if printed in flattening mode. This
    information is computed in a bottom-up manner when the document is
    constructed.

    In other words, the space Requirement is the number of columns that the
    document needs in order to fit on a single line. We express this value in
    the set of `integers extended with infinity', and use the value
    [infinity] to indicate that the document cannot be printed on a single
    line.

    Storing this information at [Group] nodes is crucial, as it allows us to
    avoid backtracking and buffering.

    Storing this information at other nodes allows the function [Requirement]
    to operate in constant time. This means that the bottom-up computation of
    requirements takes linear time. */

    /** [Cat (req, doc1, doc2)] is the concatenation of the documents [doc1] and
    [doc2]. The space Requirement [req] is the sum of the requirements of
    [doc1] and [doc2]. */
    data class Cat(val req: Requirement, val doc1: Document, val doc2: Document) : Document()

    /** [Nest (req, j, doc)] is the document [doc], in which the indentation
    level has been increased by [j], that is, in which [j] blanks have been
    inserted after every newline character. The space Requirement [req] is
    the same as the Requirement of [doc]. */
    data class Nest(val req: Requirement, val j: Int, val doc: Document) : Document()

    /** [Group (req, doc)] represents an alternative: it is either a flattened
    form of [doc], in which occurrences of [Group] disappear and occurrences
    of [IfFlat] resolve to their left branch, or [doc] itself. The space
    Requirement [req] is the same as the Requirement of [doc]. */
    data class Group(val req: Requirement, val doc: Document) : Document()

    /** [Align (req, doc)] increases the indentation level to reach the current
    column.  Thus, the document [doc] is rendered within a box whose upper
    left corner is the current position. The space Requirement [req] is the
    same as the Requirement of [doc]. */
    data class Align(val req: Requirement, val doc: Document) : Document()

    /** [Range (req, hook, doc)] is printed like [doc]. After it is printed, the
    function [hook] is applied to the range that is occupied by [doc] in the
    output. */
    data class Range(val req: Requirement, val fn: (PointRange) -> Unit, val doc: Document) : Document()

    /** [Custom (req, f)] is a document whose appearance is user-defined. */
    data class Custom(val doc: CustomDocument) : Document()
}

/** Retrieving or computing the space Requirement of a document. */
tailrec fun Requirement(x: Document): Requirement =
    when (x) {
        is Document.Empty -> Requirement(0)
        is Document.Char -> Requirement(1)
        is Document.String -> Requirement(x.s.length)
        is Document.FancyString -> Requirement(x.apparentLength)
        is Document.Blank -> Requirement(x.len)
        /* In flattening mode, the Requirement of [ifflat x y] is just the
        Requirement of its flat version, [x]. */
        /* The smart constructor [ifflat] ensures that [IfFlat] is never nested
        in the left-hand side of [IfFlat], so this recursive call is not a
        problem; the function [Requirement] has constant time complexity. */
        is Document.IfFlat -> Requirement(x.doc1)
        /* A hard line cannot be printed in flattening mode. */
        is Document.HardLine -> infinity

        /* These nodes store their Requirement -- which is computed when the
        node is constructed -- so as to allow us to answer in constant time
        here. */
        is Document.Cat -> x.req
        is Document.Nest -> x.req
        is Document.Group -> x.req
        is Document.Align -> x.req
        is Document.Range -> x.req
        is Document.Custom -> x.doc.requirement
    }
