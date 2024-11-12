package io.github.wadoon.tadoc.tpp.format

import de.uka.ilkd.key.nparser.KeYLexer
import de.uka.ilkd.key.nparser.KeYParser
import de.uka.ilkd.key.nparser.KeYParser.*
import de.uka.ilkd.key.nparser.KeYParserBaseVisitor
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.RuleNode
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.math.min
import kotlin.system.exitProcess

class KeYFileFormatter(private val ts: CommonTokenStream) : KeYParserBaseVisitor<Unit>() {
    val output: Output = Output()

    ////////////////////////////////////////////////////////////////////////////////////////////////
    override fun visitFile(ctx: FileContext) {
        // include prefix (comments) before the actual content
        processHiddenTokens(ts.getHiddenTokensToLeft(ctx.start.tokenIndex), output)
        super.visitFile(ctx)
    }

    override fun visitTerm(ctx: TermContext) {
        ExpressionVisitor(ts, output).visitTerm(ctx)
    }

    override fun visitOption_list(ctx: Option_listContext) {
        output.noSpaceBeforeNext()
        ExpressionVisitor(ts, output).visitOption_list(ctx)
    }

    override fun visitSchema_var_decls(ctx: Schema_var_declsContext) {
        for (i in 0 until ctx.childCount) {
            val child = ctx.getChild(i)
            if (child is TerminalNode) {
                val token = child.symbol.type
                if (token == SCHEMAVARIABLES) {
                    output.assertNewLineAndIndent()
                } else if (token == RBRACE) {
                    visit(child)
                    output.assertNewLine()
                    continue
                }
            }
            visit(child)
        }
    }

    override fun visitRulesOrAxioms(ctx: RulesOrAxiomsContext) {
        for (i in 0 until ctx.childCount) {
            val child = ctx.getChild(i)
            if (child is TerminalNode) {
                val token = child.symbol.type
                if (token == DOC_COMMENT || token == RULES || token == AXIOMS) {
                    output.assertNewLineAndIndent()
                } else if (token == RBRACE) {
                    visit(child)
                    output.assertNewLine()
                    continue
                }
            }
            visit(child)
        }
    }

    private fun visitChildren(node: RuleNode, startOffset: Int, endOffset: Int) {
        for (i in startOffset until endOffset) {
            val c = node.getChild(i)
            c.accept(this)
        }
    }

    override fun visitGoalspec(ctx: GoalspecContext) {
        var firstChild = 0
        output.assertNewLineAndIndent()
        if (ctx.name != null) {
            visit(ctx.name)
            visit(ctx.COLON())
            output.spaceBeforeNext()
            output.enterIndent()
            output.assertNewLineAndIndent()
            firstChild = 2
        }

        visitChildren(ctx, firstChild, ctx.childCount)
        if (ctx.name != null) {
            output.exitIndent()
        }
    }

    override fun visitModifiers(ctx: ModifiersContext) {
        for (i in 0 until ctx.childCount) {
            val child = ctx.getChild(i)
            if (child is TerminalNode) {
                val token = child.symbol.type
                if (token == NONINTERACTIVE) {
                    output.assertNewLineAndIndent()
                    visit(child)
                    continue
                }

                if (token == DISPLAYNAME || token == HELPTEXT) {
                    output.assertNewLineAndIndent()
                    visit(child)
                    output.spaceBeforeNext()
                    continue
                }
            }
            visit(child)
        }
    }

    override fun visitVarexplist(ctx: VarexplistContext) {
        val varexps = ctx.varexp()
        val commas = ctx.COMMA()
        var multiline = varexps.size > 3
        for (i in varexps.indices) {
            if (multiline) {
                output.assertNewLineAndIndent()
            }
            visit(varexps[i])
            if (i < commas.size) {
                visit(commas[i])
                if (!multiline && output.isNewLine) {
                    multiline = true
                }
            }
        }
    }

    override fun visitOne_include_statement(ctx: One_include_statementContext) {
        for (i in 0 until ctx.childCount) {
            val child = ctx.getChild(i)
            if (child is TerminalNode) {
                val token = child.symbol.type
                if (token == INCLUDE || token == INCLUDELDTS) {
                    output.assertNewLineAndIndent()
                    output.enterIndent()
                }

                if (token == SEMI) {
                    output.exitIndent()
                }
            }
            visit(child)
        }
    }

    override fun visitTaclet(ctx: TacletContext) {
        val n = ctx.childCount
        for (i in 0 until n) {
            val child = ctx.getChild(i)
            if (child is TerminalNode) {
                val token = child.symbol.type
                if (token == KeYLexer.DOC_COMMENT || token == KeYLexer.LEMMA || token == KeYLexer.IDENT || token == KeYLexer.ASSUMES || token == KeYLexer.FIND || token == KeYLexer.SAMEUPDATELEVEL || token == KeYLexer.ANTECEDENTPOLARITY || token == KeYLexer.SUCCEDENTPOLARITY || token == KeYLexer.INSEQUENTSTATE || token == KeYLexer.VARCOND) {
                    output.assertNewLineAndIndent()
                } else if (token == SCHEMAVAR) {
                    output.assertNewLineAndIndent()
                    visit(child)
                    output.spaceBeforeNext()
                    continue
                } else if (token == KeYLexer.RBRACE) {
                    output.assertNewLine()
                }
            } else if (child is RuleContext) {
                if (child is Option_listContext) {
                    output.spaceBeforeNext()
                }
            }

            visit(child)
        }

    }

    override fun visitTerminal(node: TerminalNode) {
        val token = node.symbol.type

        val isLBrace =
            token == KeYLexer.LBRACE || token == KeYLexer.LPAREN || token == KeYLexer.LBRACKET
        if (isLBrace) {
            output.spaceBeforeNext()
        } else if (token == KeYLexer.RBRACE || token == KeYLexer.RPAREN || token == KeYLexer.RBRACKET) {
            output.noSpaceBeforeNext()
            output.exitIndent()
        }

        if (token == KeYLexer.AVOID || token == KeYLexer.SEQARROW) {
            output.spaceBeforeNext()
        }

        val noSpaceAround =
            token == KeYLexer.COLON || token == KeYLexer.DOT || token == KeYLexer.DOUBLECOLON
        val noSpaceBefore =
            token == KeYLexer.SEMI || token == KeYLexer.COMMA || token == KeYLexer.LPAREN
        if (noSpaceBefore || noSpaceAround) {
            output.noSpaceBeforeNext()
        }

        val text = node.symbol.text
        if (token == KeYLexer.DOC_COMMENT) {
            processIndentationInMLComment(text, output)
        } else {
            output.token(text)
        }

        if (isLBrace) {
            output.enterIndent()
        }

        if (!(isLBrace || noSpaceAround)) {
            output.spaceBeforeNext()
        }

        processHiddenTokensAfterCurrent(node.symbol, ts, output)
        return super.visitTerminal(node)!!
    }

    companion object {
        /** Maximum newlines between tokens (2 equals to 1 empty line)  */
        private const val MAX_NEWLINES_BETWEEN = 2

        ////////////////////////////////////////////////////////////////////////////////////////////////
        fun processHiddenTokens(tokens: List<Token>?, output: Output) {
            if (tokens == null) return

            for (t in tokens) {
                val text = t.text
                if (t.type == KeYLexer.WS) {
                    val nls = countNLs(text)
                    for (k in 0 until min(nls.toDouble(), MAX_NEWLINES_BETWEEN.toDouble())
                        .toInt()) {
                        output.newLine()
                    }
                } else {
                    val normalized: String = text.replace("\t".toRegex(), Output.getIndent(1))
                    when (t.type) {
                        KeYLexer.SL_COMMENT -> processIndentationInSLComment(normalized, output)
                        KeYLexer.COMMENT_END -> processIndentationInMLComment(normalized, output)
                        else -> throw IllegalStateException("unexpected hidden token type " + t.type)
                    }
                }
            }
        }

        fun processHiddenTokensAfterCurrent(
            currentToken: Token, ts: CommonTokenStream,
            output: Output
        ) {
            // add hidden tokens after the current token (whitespace, comments etc.)
            val list = ts.getHiddenTokensToRight(currentToken.tokenIndex)
            processHiddenTokens(list, output)
        }

        fun processIndentationInMLComment(text: String, output: Output) {
            // Normalize and split
            val lines = text.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            // Find minimal indent shared among all lines except the first
            // Doc like comments start with * in every line except the first
            var minIndent = Int.MAX_VALUE
            var isDocLike = true
            for (i in 1 until lines.size) {
                val line = lines[i]
                val stripped = line.trimStart()
                // Empty lines are ignored for this
                if (stripped.isNotEmpty()) {
                    minIndent = min(minIndent.toDouble(), (line.length - stripped.length).toDouble()).toInt()
                    isDocLike = isDocLike and stripped.startsWith("*")
                }
            }

            // Remove /* and */
            lines[0] = lines[0].substring(2).trimStart()
            val lastLine = lines[lines.size - 1]
            lines[lines.size - 1] = lastLine.substring(0, lastLine.length - 2)

            output.token("/*")
            // Skip space if we start with another *, e.g. /**
            if (!lines[0].startsWith("*") && !lines[0].startsWith("!")) {
                output.spaceBeforeNext()
            }
            for (i in lines.indices) {
                var line = lines[i]
                line = if (i != 0) {
                    // Watch out for empty line when removing the common indent
                    if (line.isEmpty()) line else line.substring(minIndent)
                } else {
                    line.trimStart()
                }
                line = line.trimEnd()

                // Print nonempty line
                if (line.isNotEmpty()) {
                    output.assertIndented()
                    if (isDocLike && i != 0) {
                        output.spaceBeforeNext()
                        line = line.trimStart()
                    }
                    output.token(line)
                }
                if (i != lines.size - 1) {
                    output.newLine()
                } else {
                    // Add space for doc like comments
                    if (isDocLike && !line.endsWith("*")) {
                        output.assertIndented()
                        output.spaceBeforeNext()
                    }
                }
            }

            output.token("*/")
            output.spaceBeforeNext()
        }

        private fun processIndentationInSLComment(text: String, output: Output) {
            output.spaceBeforeNext()
            var trimmed = text.trimEnd()
            // Normalize actual comment content
            if (trimmed.startsWith("//")) {
                trimmed = trimmed.substring(2)
                output.token("//")
                if (!trimmed.startsWith(" ") && !trimmed.startsWith("/")) {
                    output.spaceBeforeNext()
                }
            }
            if (trimmed.isNotEmpty()) {
                output.token(trimmed)
            }
            output.newLine()
        }

        private fun countNLs(text: String): Int {
            return text.chars().filter { x: Int -> x == '\n'.code }.count().toInt()
        }

        /**
         * Entry level method to the formatter.
         * The formatter uses System.lineSeparator as line separator and accepts any line separator as
         * input.
         *
         * @param text the input text
         * @return the formatted text *or null*, if the input was not parseable
         */
        private fun format(text: String): String? {
            val `in` = CharStreams.fromString(text.replace("\\r\\n?".toRegex(), "\n"))
            val lexer = KeYLexer(`in`)
            lexer.tokenFactory = CommonTokenFactory(true)

            val tokens = CommonTokenStream(lexer)
            tokens.fill()

            val parser = KeYParser(tokens)
            val ctx = parser.file()
            if (parser.numberOfSyntaxErrors > 0) {
                return null
            }

            val formatter = KeYFileFormatter(tokens)
            formatter.visitFile(ctx)
            val formatted: String = formatter.output.toString().trim() + "\n"
            return formatted.replace("\n".toRegex(), System.lineSeparator())
        }

        ////// Test functions below //////
        @Throws(IOException::class)
        private fun formatSingleFile(input: Path, output: Path) {
            val content = Files.readString(input)
            val formatted = format(content)

            if (formatted == null) {
                System.err.println("Failed to format $input")
                return
            }

            if (formatted != format(formatted)) {
                System.err.println("Formatter is not convergent on $input")
            }

            val noWhitespaceContent = content.replace("\\s+".toRegex(), "")
            val noWhitespaceFormatted = formatted.replace("\\s+".toRegex(), "")
            if (noWhitespaceContent != noWhitespaceFormatted) {
                System.err.println("File changed: $input")
            }

            Files.writeString(output, formatted)
        }

        @Throws(IOException::class)
        private fun formatSingleFileInSameDir(input: Path) {
            val fileName = input.fileName.toString()
            if (!fileName.endsWith(".key")) {
                System.err.println("Ignoring non key file $input")
                return
            }
            val stem = fileName.substring(0, fileName.length - 4)
            val output = input.resolveSibling("$stem.format.key")
            formatSingleFile(input, output)
        }

        @Throws(IOException::class)
        private fun formatSingleFileTo(input: Path, outputDir: Path) {
            val output = outputDir.resolve(input.fileName)
            formatSingleFile(input, output)
        }

        @Suppress("unused")
        @Throws(IOException::class)
        private fun formatDirectoryTest(dir: Path) {
            val outDir = dir.parent.resolve("output")
            // noinspection ResultOfMethodCallIgnored
            outDir.toFile().mkdirs()
            Files.list(dir).use { s ->
                s.forEach { p: Path ->
                    val file = dir.resolve(p.fileName)
                    try {
                        val name = file.fileName.toString()
                        if (name.endsWith(".format.format.key")) {
                            // noinspection ResultOfMethodCallIgnored
                            file.toFile().delete()
                            return@forEach
                        }
                        formatSingleFileInSameDir(file)
                        if (!name.endsWith(".format.key")) {
                            formatSingleFileTo(file, outDir)
                        }
                    } catch (e: Exception) {
                        System.err.println("Exception while processing $file")
                        throw RuntimeException(e)
                    }
                }
            }
        }

        private fun formatOrCheckInPlace(file: Path, format: Boolean): Boolean {
            try {
                val content = Files.readString(file)
                val formatted = format(content)
                if (formatted == null) {
                    System.err.println("Failed to format $file")
                    return false
                }

                val differs = content != formatted
                if (differs) {
                    if (format) {
                        Files.writeString(file, formatted)
                    } else {
                        System.err.println("$file is not formatted correctly")
                        return false
                    }
                }
            } catch (e: Exception) {
                System.err.println("Exception while processing $file")
                e.printStackTrace()
                return false
            }
            return true
        }

        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size != 2 || (args[0] != "format" && args[0] != "check")) {
                System.err.println("Usage:")
                System.err.println("* format a directory or a file: format <path>")
                System.err.println("* check a directory or a file: check <path>")
                exitProcess(3)
            }

            val format = args[0] == "format"
            val path = Paths.get(args[1])
            val file = path.toFile()
            if (!file.exists()) {
                System.err.println("Input path does not exist")
                exitProcess(2)
            }

            var files: List<Path>
            if (file.isDirectory) {
                Files.list(path).use { s ->
                    files = s.collect(Collectors.toList())
                }
            } else {
                files = listOf(path)
            }

            var valid = true
            for (f in files) {
                valid = valid and formatOrCheckInPlace(f, format)
            }

            if (!valid) {
                exitProcess(1)
            }
        }
    }
}
