package io.github.wadoon.tadoc.tpp.format

import de.uka.ilkd.key.nparser.KeYLexer
import de.uka.ilkd.key.nparser.KeYParser
import de.uka.ilkd.key.nparser.KeYParser.IfThenElseTermContext
import de.uka.ilkd.key.nparser.KeYParser.Unary_minus_termContext
import de.uka.ilkd.key.nparser.KeYParserBaseVisitor
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.TerminalNode
import java.util.*
import kotlin.math.min

class ExpressionVisitor(private val ts: CommonTokenStream, private val output: Output) : KeYParserBaseVisitor<Unit>() {
    override fun visitTerminal(node: TerminalNode) {
        val token = node.symbol.type

        val isLBrace =
            token == KeYLexer.LBRACE || token == KeYLexer.LPAREN || token == KeYLexer.LBRACKET || token == KeYLexer.LGUILLEMETS
        if (token == KeYLexer.RBRACE || token == KeYLexer.RPAREN || token == KeYLexer.RBRACKET || token == KeYLexer.RGUILLEMETS) {
            output.noSpaceBeforeNext()
            output.exitIndent()
        }

        val isOperator = Arrays.stream(OPERATORS).anyMatch { v: Int -> v == token }
        val isUnaryMinus = token == KeYLexer.MINUS &&
                node.parent is Unary_minus_termContext
        // Unary minus has a "soft" leading space, we allow it if the token before wants it but
        // don't require it
        if ((isOperator && !isUnaryMinus) || token == KeYLexer.AVOID) {
            output.spaceBeforeNext()
        }

        val text = node.symbol.text
        if (token == KeYLexer.MODALITY) {
            outputModality(text, output)
        } else {
            output.token(text)
        }

        if (!isLBrace && ((isOperator && !isUnaryMinus) || (
                    token == KeYLexer.COMMA) || (
                    token == KeYLexer.SUBST) || (
                    token == KeYLexer.AVOID) || (
                    token == KeYLexer.EXISTS) || (
                    token == KeYLexer.FORALL) || (
                    token == KeYLexer.SEMI))
        ) {
            output.spaceBeforeNext()
        }

        if (isLBrace) {
            output.enterIndent()
        }

        KeYFileFormatter.processHiddenTokensAfterCurrent(node.symbol, ts, output)
        return super.visitTerminal(node)!!
    }

    override fun visitIfThenElseTerm(ctx: IfThenElseTermContext) {
        for (i in 0 until ctx.childCount) {
            val child = ctx.getChild(i)
            if (child is TerminalNode) {
                val token = child.symbol.type
                if (token == KeYParser.THEN) {
                    output.enterIndent()
                }

                if (token == KeYParser.THEN || token == KeYParser.ELSE) {
                    output.spaceBeforeNext()
                }
            }
            visit(child)
        }
        output.exitIndent()
    }

    companion object {
        private val OPERATORS = intArrayOf(
            KeYLexer.LESS,
            KeYLexer.LESSEQUAL,
            KeYLexer.GREATER,
            KeYLexer.GREATEREQUAL,
            KeYLexer.EQUALS,
            KeYLexer.NOT_EQUALS,
            KeYLexer.IMP,
            KeYLexer.SEQARROW,
            KeYLexer.NOT_EQUALS,
            KeYLexer.AND,
            KeYLexer.OR,
            KeYLexer.PARALLEL,
            KeYLexer.EXP,
            KeYLexer.PERCENT,
            KeYLexer.STAR,
            KeYLexer.MINUS,
            KeYLexer.PLUS,
            KeYLexer.EQV,
            KeYLexer.ASSIGN,
        )

        private fun outputModality(text: String, output: Output) {
            val normalized: String = text.replace("\t".toRegex(), Output.getIndent(1))
            val lines = normalized.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            lines[0] = lines[0].trim { it <= ' ' }

            // Find the smallest indent of all lines except the first
            var minIndent = Int.MAX_VALUE
            for (i in 1 until lines.size) {
                val line = lines[i]
                lines[i] = line.trimEnd()
                val indent = line.length - line.trimStart().length
                minIndent = min(minIndent.toDouble(), indent.toDouble()).toInt()
            }

            output.token(lines[0])
            if (lines.size > 1) {
                output.enterIndent()

                for (i in 1 until lines.size) {
                    output.newLine()
                    val line = lines[i]
                    if (line.isNotEmpty()) {
                        output.token(line.substring(minIndent))
                    }
                }
                output.exitIndent()
            }
            output.spaceBeforeNext()
        }
    }
}
