package de.uka.ilkd.key.nparser.format

import de.uka.ilkd.key.nparser.KeYLexer
import de.uka.ilkd.key.nparser.ParsingFacade
import org.antlr.v4.runtime.Token
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object KeYPrettyPrinter {
    private const val INDENT_STEP = 4

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val testDir = Paths.get("/home/wolfram/Desktop/tmp")
        Files.list(testDir.resolve("rules")).forEach { p: Path ->
            try {
                Files.writeString(
                    testDir.resolve("testoutput").resolve(p.fileName),
                    prettyPrint(p)
                )
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    @Throws(IOException::class)
    private fun prettyPrint(input: Path): StringBuilder {
        val builder = StringBuilder()
        val lexer = ParsingFacade.createLexer(input)
        val tokens = lexer.allTokens
        var indent = 0
        var cur = 0
        for (token in tokens) {
            var text = token.text
            when (token.type) {
                KeYLexer.LPAREN, KeYLexer.LBRACE -> indent++
                KeYLexer.RPAREN, KeYLexer.RBRACE -> if (indent == 0) {
                    System.err.printf(
                        "Mmmh. Mismatched parentheses/braces at %d/%d.",
                        token.line, token.charPositionInLine
                    )
                } else {
                    indent--
                }

                KeYLexer.WS -> {
                    val nls = countNLs(text)
                    if (nls > 0) {
                        var i = indent
                        if (cur < tokens.size - 1) {
                            val nextTy = tokens[cur + 1].type
                            if (nextTy == KeYLexer.RPAREN || nextTy == KeYLexer.RBRACE) i--
                        }
                        text = multi(nls, "\n") + multi(INDENT_STEP * i, " ")
                    }
                }
            }
            builder.append(text)
            cur++
        }
        return builder
    }

    private fun processIndentationInSLComment(t: Token, indent: Int): String {
        return """
            ${t.text.trim { it <= ' ' }}
            ${multi(INDENT_STEP * indent, " ")}
            """.trimIndent()
    }

    private fun countNLs(text: String): Int {
        return text.chars().filter { x: Int -> x == '\n'.code }.count().toInt()
    }

    private fun multi(count: Int, s: String): String {
        return java.lang.String.join("", Collections.nCopies(count, s))
    }
}
