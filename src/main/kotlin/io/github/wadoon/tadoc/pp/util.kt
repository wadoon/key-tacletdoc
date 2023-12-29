@file:Suppress("unused")

package io.github.wadoon.tadoc.pp

import java.util.*

/* ------------------------------------------------------------------------- */

/* The above algebraic data type is not exposed to the user. Instead, we
expose the following functions. These functions construct a raw document
and compute its Requirement, so as to obtain a document. */

val empty = Document.Empty
fun char(c: Char) = Document.Char(c).also { require(c != '\n') }
val space = Document.Blank(1)
fun string(s: String) = Document.String(s)
fun fancysubstring(s: String, ofs: Int, len: Int, apparentLength: Int) =
    if (len == 0) empty
    else Document.FancyString(s, ofs, len, apparentLength)

fun substring(s: String, ofs: Int, len: Int) = fancysubstring(s, ofs, len, len)
fun fancystring(s: String, apparentLength: Int) = fancysubstring(s, 0, s.length, apparentLength)

/* The following function was stolen from [Batteries]. */
fun utf8_length(s: String): Int {
    fun length_aux(s: String, c: Int, i: Int): Int {
        if (i >= s.length) return c
        val n = s[i]
        val k =
            if (n < 0x80.toChar()) 1 else if (n < 0xe0.toChar()) 2 else if (n < 0xf0.toChar()) 3 else 4
        return length_aux(s, (c + 1), (i + k))
    }
    return length_aux(s, 0, 0)
}

fun utf8string(s: String) = fancystring(s, utf8_length(s))

// fun utf8format(f: String) = Printf.ksprintf utf8string f
val hardline = Document.HardLine
fun blank(n: Int) = if (n == 0) empty else Document.Blank(n)

fun ifflat(doc1: Document, doc2: Document) = Document.IfFlat(doc1, doc2)
/* Avoid nesting [IfFlat] in the left-hand side of [IfFlat], as this
is redundant.
when( doc1) {
        | IfFlat(doc1, _)
        | doc1
    ->
        IfFlat(doc1, doc2)}*/

fun internal_break(i: Int) = ifflat(blank(i), hardline)
val break0 = internal_break(0)
val break1 = internal_break(1)
val breakable_space = break1
val softbreak = break0

fun break_(i: Int) = when (i) {
    0 -> break0
    1 -> break1
    else -> internal_break(i)
}

fun cat(x: Document, y: Document) = // ^^
    if (x is Document.Empty) y else if (y is Document.Empty) x else Document.Cat(Requirement(x) + Requirement(y), x, y)

fun nest(i: Int, x: Document) =
    // assert (i >= 0);
    Document.Nest(Requirement(x), i, x)

fun group(x: Document): Document {
    val req = Requirement(x)
    /* Minor optimisation: an infinite Requirement dissolves a group. */
    return if (req.isInfinity) x else Document.Group(req, x)
}

fun align(x: Document) = Document.Align(Requirement(x), x)

fun range(hook: (PointRange) -> Unit, x: Document) = Document.Range(Requirement(x), hook, x)

/*let custom c =
assert (c#Requirement >= 0);
Custom c*/

/** This function expresses the following invariant: if we are in flattening
mode, then we must be within bounds, i.e. the width and ribbon width
constraints must be respected. */
fun ok(state: State, flatten: Boolean) =
    !flatten || state.column <= state.width && state.column <= state.lastIndent + state.ribbon

val lparen = char('(')
val rparen = char(')')
val langle = char('<')
val rangle = char('>')
val lbrace = char('{')
val rbrace = char('}')
val lbracket = char('[')
val rbracket = char(']')
val squote = char('\'')
val dquote = char('"')
val bquote = char('`')
val semi = char(';')
val colon = char(':')
val comma = char(',')
val dot = char('.')
val sharp = char('#')
val slash = char('/')
val backslash = char('\\')
val equals = char('=')
val qmark = char('?')
val tilde = char('~')
val at = char('@')
val percent = char('%')
val dollar = char('$')
val caret = char('^')
val ampersand = char('&')
val star = char('*')
val plus = char('+')
val minus = char('-')
val underscore = char('_')
val bang = char('!')
val bar = char('|')

fun twice(doc: Document) = cat(doc, doc)

fun repeat(n: Int, doc: Document) =
    when (n) {
        0 -> empty
        1 -> doc
        else -> (1..n).map { doc }.fold(empty, ::cat)
    }

fun precede(l: Document, x: Document) = cat(l, x)
fun precede(l: String, x: Document) = cat(string(l), x)
fun terminate(r: Document, x: Document) = cat(x, r)
fun enclose(l: Document, x: Document, r: Document) = cat(cat(l, x), r)
fun squotes(x: Document) = enclose(squote, x, squote)
fun dquotes(x: Document) = enclose(dquote, x, dquote)
fun bquotes(x: Document) = enclose(bquote, x, bquote)
fun braces(x: Document) = enclose(lbrace, x, rbrace)
fun parens(x: Document) = enclose(lparen, x, rparen)
fun angles(x: Document) = enclose(langle, x, rangle)
fun brackets(x: Document) = enclose(lbracket, x, rbracket)

/** A variant of [fold] that keeps track of the element index. */
fun <A, B> foldli(f: (Int, B, A) -> B, accu: B, xs: List<A>): B {
    return xs.foldIndexed(accu, f)
}

/* Working with lists of documents. */

/** We take advantage of the fact that [^^] operates in constant
time, regardless of the size of its arguments. The document
that is constructed is essentially a reversed list (i.e., a
tree that is biased towards the left). This is not a problem;
when pretty-printing this document, the engine will descend
along the left branch, pushing the nodes onto its stack as
it goes down, effectively reversing the list again. */
fun concat(docs: List<Document>) = docs.fold(empty, ::cat)

fun separate(sep: Document, docs: List<Document>): Document =
    foldli({ i, accu: Document, doc: Document -> if (i == 0) doc else cat(cat(accu, sep), doc) }, empty, docs)

fun <T> concat_map(f: (T) -> Document, xs: List<T>) = xs.map(f).reduce(::cat)

fun <T> concat_map(xs: List<T>, f: (T) -> Document) = xs.map(f).reduce(::cat)

fun <T> separate_map(sep: Document, xs: List<T>, f: (T) -> Document) = separate_map(sep, f, xs)

fun <T> separate_map(sep: Document, f: (T) -> Document, xs: List<T>) =
    foldli(
        { i, accu: Document, x: T ->
            if (i == 0) f(x) else cat(cat(accu, sep), f(x))
        },
        empty, xs
    )

fun separate2(sep: Document, last_sep: Document, docs: List<Document>) =
    foldli(
        { i, accu: Document, doc: Document ->
            if (i == 0) doc
            else cat(accu, cat(if (i < docs.size - 1) sep else last_sep, doc))
        },
        empty, docs
    )

fun <T> optional(f: (T) -> Document, x: Optional<T>) = x.map(f).orElse(empty)

/* This variant of [String.index_from] returns an option. */
fun index_from(s: String, i: Int, c: Char) = s.indexOf(c, i)

/* [lines s] chops the string [s] into a list of lines, which are turned
into documents. */
fun lines(s: String) = s.split("\n").map { string(it) }

fun arbitrary_string(s: String) = separate(break1, lines(s))

/** [split ok s] splits the string [s] at every occurrence of a character
that satisfies the predicate [ok]. The substrings thus obtained are
turned into documents, and a list of documents is returned. No information
is lost: the concatenation of the documents yields the original string.
This code is not UTF-8 aware. */
fun split(chars: (Char) -> Boolean, s: String): List<Document> {
    val d = arrayListOf<Document>()
    var lastIndex = 0
    s.toCharArray().forEachIndexed { idx, c ->
        if (chars(c)) {
            d.add(substring(s, lastIndex, idx))
            lastIndex = idx
        }
    }
    if (lastIndex != s.length - 1) {
        d.add(substring(s, lastIndex, s.length))
    }
    return d
}

/** [words s] chops the string [s] into a list of words, which are turned
into documents. */
fun words(s: String) = s.split("\\s").map { it.strip() }.map { ::string }

fun <T> flow_map(sep: Document, docs: List<T>, f: (T) -> Document) = flow_map(sep, f, docs)

fun <T> flow_map(sep: Document, f: (T) -> Document, docs: List<T>) =
    foldli(
        { i: Int, accu: Document, doc: T ->
            if (i == 0) f(doc)
            else cat(
                accu,
                /* This idiom allows beginning a new line if [doc] does not
                fit on the current line. */
                group(
                    cat(sep, f(doc))
                )
            )
        }, empty, docs
    )

fun flow(sep: Document, docs: List<Document>) = flow_map(sep, { it }, docs)
fun url(s: String) = flow(break_(0), split({ it == '/' || it == '.' }, s))

/* -------------------------------------------------------------------------- */
/* Alignment and indentation. */

fun hang(i: Int, d: Document) = align(nest(i, d))

infix fun Document.slash(y: Document) = cat(cat(this, break1), y)
infix fun Document.`^^`(y: Document) = cat(this, y)
fun prefix(n: Int, b: Int, x: Document, y: Document) = group(x `^^` nest(n, (break_(b) `^^` y)))
infix fun Document.prefixed(y: Document) = prefix(2, 1, this, y) // ^//^
fun jump(n: Int, b: Int, y: Document) = group(nest(n, break_(b) `^^` y))

fun `infix`(n: Int, b: Int, op: Document, x: Document, y: Document) = prefix(n, b, x `^^` blank(b) `^^` op, y)

fun surround(n: Int, b: Int, opening: Document, contents: Document, closing: Document) =
    group(opening `^^` nest(n, (break_(b) `^^` contents) `^^` break_(b) `^^` closing))

fun soft_surround(n: Int, b: Int, opening: Document, contents: Document, closing: Document) =
    group(
        opening `^^` nest(n, group(break_(b) `^^` contents) `^^` group((break_(b) `^^` closing)))
    )

fun surround_separate(
    n: Int,
    b: Int,
    `void`: Document,
    opening: Document,
    sep: Document,
    closing: Document,
    docs: List<Document>
) =
    if (docs.isEmpty()) `void`
    else surround(n, b, opening, separate(sep, docs), closing)

fun <T> surround_separate_map(
    n: Int,
    b: Int,
    `void`: Document,
    opening: Document,
    sep: Document,
    closing: Document,
    xs: List<T>,
    f: (T) -> Document
) = if (xs.isEmpty()) `void` else surround(n, b, opening, separate_map(sep, f, xs), closing)
