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
package io.github.wadoon.tadoc

import KeYLexer
import de.uka.ilkd.key.nparser.JavaKeYLexer
import de.uka.ilkd.key.nparser.JavaKeYParser
import de.uka.ilkd.key.nparser.JavaKeYParserBaseVisitor
import io.github.wadoon.pp.*
import io.github.wadoon.tadoc.Symbol.Type.SORT
import kotlinx.html.Entities
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

const val INDENT = 4

fun pretty(ctx: ParserRuleContext, index: Index, currentContext: Symbol, usageIndex: UsageIndex): String {
    val d: Document = ctx.accept(PrettyPrinterDoc(index, currentContext, false, false, usageIndex))
    val sw = StringWriter()
    Engine.prettyQ(d, PrintWriter(sw), State(80, 0.8), 0, false)
    return sw.toString()
}

fun Iterable<Document>.concat(): Document = reduce { acc, document -> acc + document }
fun Iterable<Document>.join(delim: Document): Document = reduce { acc, document -> acc + delim + document }


class PrettyPrinterDoc(
    val index: Index,
    val currentContext: Symbol,
    val printReferences: Boolean = false,
    val printColor: Boolean = false,
    private val usageIndex: UsageIndex = HashMap()
) : JavaKeYParserBaseVisitor<Document>() {
    private val vocabulary = JavaKeYLexer(CharStreams.fromString("")).vocabulary
    private val tokenSymbols = index.filterIsInstance<TokenSymbol>()

    override fun aggregateResult(aggregate: Document?, nextResult: Document?) =
        (aggregate ?: empty) + (nextResult ?: empty)

    override fun visitTerminal(node: TerminalNode) = visitToken(node.symbol)

    private val rainbowColors = arrayOf(
        "#458588",
        "#b16286",
        "#cc241d",
        "#d65d0e",
        "#458588",
        "#b16286",
        "#cc241d",
        "#d65d0e",
        "#458588",
        "#b16286",
        "#cc241d",
        "#d65d0e",
        "#458588",
        "#b16286",
        "#cc241d",
        "#d65d0e"
    )

    private val parenthesisIds = LinkedList<Int>()
    private var parenthesisCounter = 0

    private fun visitToken(t: Token): Document {
        if (t.type == KeYLexer.DOC_COMMENT) return empty
        if (t.type == KeYLexer.LPAREN || t.type == KeYLexer.LBRACE || t.type == KeYLexer.LBRACKET)
            return openParenthesis(t)
        if (t.type == KeYLexer.RPAREN || t.type == KeYLexer.RBRACE || t.type == KeYLexer.RBRACKET)
            return closeParenthesis(t)
        if (t.type == KeYLexer.IDENT && t.text[0] == '#') {
            return printSpan(t.text, "schema-variable")
        }
        val s = tokenSymbols.find { it.tokenType == t.type }
        val text = if (s != null && printReferences)
            "<a class=\"token\" href=\"${s.href}\">${t.text}</a> "
        else
            t.text
        return printSpan(text, vocabulary.getDisplayName(t.type))
    }

    private fun printSpan(text: String, classes: String) =
        if (printColor)
            fancystring("<span class=\"token $classes\">$text</span>", text.length)
        else
            string(text)

    private fun openParenthesis(token: Token): Document {
        return if (printColor) {
            val p = ++parenthesisCounter
            parenthesisIds.push(p)
            val c = rainbowColors[p % rainbowColors.size]
            fancystring(
                "<span style=\"color:$c\" class=\"paired-element\" id=\"open-${p}\" mouseover=\"highlight($p)\">${token.text}</span>",
                token.text.length
            )
        } else {
            string(token.text)
        }
    }

    private fun closeParenthesis(token: Token): Document {
        return if (printColor) {
            val pop = parenthesisIds.pop()
            val c = rainbowColors[pop % rainbowColors.size]
            fancystring(
                "<span style=\"color:$c\" class=\"paired-element\" id=\"close-$pop\" mouseover=\"highlight($pop)\">${token.text}</span>",
                token.text.length
            )
        } else {
            string(token.text)
        }
    }

    override fun defaultResult() = empty

    override fun visitProblem(ctx: JavaKeYParser.ProblemContext?): Document {
        return super.visitProblem(ctx)
    }

    override fun visitOne_include_statement(ctx: JavaKeYParser.One_include_statementContext?): Document {
        return super.visitOne_include_statement(ctx)
    }

    override fun visitOne_include(ctx: JavaKeYParser.One_includeContext?): Document {
        return super.visitOne_include(ctx)
    }

    override fun visitOptions_choice(ctx: JavaKeYParser.Options_choiceContext?): Document {
        return super.visitOptions_choice(ctx)
    }

    override fun visitActivated_choice(ctx: JavaKeYParser.Activated_choiceContext?): Document {
        return super.visitActivated_choice(ctx)
    }

    override fun visitOption_decls(ctx: JavaKeYParser.Option_declsContext?): Document {
        return super.visitOption_decls(ctx)
    }

    override fun visitChoice(ctx: JavaKeYParser.ChoiceContext?): Document {
        return super.visitChoice(ctx)
    }

    override fun visitSort_decls(ctx: JavaKeYParser.Sort_declsContext?): Document {
        return super.visitSort_decls(ctx)
    }

    override fun visitOne_sort_decl(ctx: JavaKeYParser.One_sort_declContext): Document {
        var doc: Document = empty

        if (null != ctx.GENERIC()) {
            doc = doc + precede(ctx.GENERIC().text, break1)
        }

        if (null != ctx.PROXY()) {
            doc = doc + precede(ctx.PROXY().text, break1)
        }

        if (null != ctx.ABSTRACT()) {
            doc = doc + precede(ctx.ABSTRACT().text, break1)
        }

        doc += ctx.sortIds.simple_ident_dots_with_docs()
            .flowMap(comma + space) { string(Entities.it.text) }

        if (null != ctx.ONEOF()) {
            doc = doc + space + ctx.ONEOF().text + space +
                    ctx.oneof_sorts().sortId().surroundSeparateMap(
                        opening = lbrace, closing = rbrace,
                        indent = INDENT, space = 1
                    ) { ref(it.text, SORT) }
        }
        if (null != ctx.EXTENDS()) {
            doc = doc + space + ctx.EXTENDS().text + space +
                    ctx.sortExt.sortId().separateMap(comma + space) { it.accept(this) }
        }
        return doc + semi
    }

    private fun ref(text: String, vararg types: Symbol.Type): Document {
        if (!printReferences) return string(text) + space
        val s = index.find { b -> b.type in types && b.displayName == text }
        return if (s != null) {
            usageIndex.add(s, currentContext)
            fancystring("<a href=\"${s.href}\" class=\"symbol ${s.type.name}\">$text</a> ", text.length)
        } else {
            Tadoc.errordpln("Could not found symbol for $text : ${types.toList()}")
            string(text) + space
        }
    }

    override fun visitFile(ctx: JavaKeYParser.FileContext): Document =
        ctx.decls().accept(this) + hardline + (ctx.problem()?.accept(this) ?: blank(0))

    override fun visitDecls(ctx: JavaKeYParser.DeclsContext): Document =
        ctx.children.map { it.accept(this) }.reduce { a, b -> a + hardline + b }


    override fun visitSimple_ident_dots(ctx: JavaKeYParser.Simple_ident_dotsContext?): Document {
        return super.visitSimple_ident_dots(ctx)
    }

    override fun visitSimple_ident_dots_comma_list(ctx: JavaKeYParser.Simple_ident_dots_comma_listContext?): Document {
        return super.visitSimple_ident_dots_comma_list(ctx)
    }

    override fun visitExtends_sorts(ctx: JavaKeYParser.Extends_sortsContext?): Document {
        return super.visitExtends_sorts(ctx)
    }

    override fun visitOneof_sorts(ctx: JavaKeYParser.Oneof_sortsContext?): Document {
        return super.visitOneof_sorts(ctx)
    }

    override fun visitProg_var_decls(ctx: JavaKeYParser.Prog_var_declsContext?): Document {
        return super.visitProg_var_decls(ctx)
    }

    override fun visitString_literal(ctx: JavaKeYParser.String_literalContext?): Document {
        return super.visitString_literal(ctx)
    }

    override fun visitString_value(ctx: JavaKeYParser.String_valueContext?): Document {
        return super.visitString_value(ctx)
    }

    override fun visitSimple_ident(ctx: JavaKeYParser.Simple_identContext?): Document {
        return super.visitSimple_ident(ctx)
    }

    override fun visitSimple_ident_comma_list(ctx: JavaKeYParser.Simple_ident_comma_listContext?): Document {
        return super.visitSimple_ident_comma_list(ctx)
    }

    override fun visitSchema_var_decls(ctx: JavaKeYParser.Schema_var_declsContext): Document =
        docOf(ctx.SCHEMAVARIABLES()) + bblock(
            ctx.one_schema_var_decl().map { docOf(it) }.join(break0)
        )

    override fun visitOne_schema_var_decl(ctx: JavaKeYParser.One_schema_var_declContext): Document =
        joinChildren(ctx)

    private fun joinChildren(ctx: ParserRuleContext): Document =
        ctx.children.map { it.accept(this) }.join(space)

    override fun visitSchema_modifiers(ctx: JavaKeYParser.Schema_modifiersContext?): Document {
        return super.visitSchema_modifiers(ctx)
    }

    override fun visitOne_schema_modal_op_decl(ctx: JavaKeYParser.One_schema_modal_op_declContext?): Document {
        return super.visitOne_schema_modal_op_decl(ctx)
    }

    override fun visitPred_decl(ctx: JavaKeYParser.Pred_declContext?): Document {
        return super.visitPred_decl(ctx)
    }

    fun bblock(doc: Document) = braces(indent(hardline + doc) + hardline)

    override fun visitPred_decls(ctx: JavaKeYParser.Pred_declsContext): Document =
        docOf(ctx.PREDICATES()) + bblock(
            indent(
                ctx.pred_decl().map { docOf(it) }.join(hardline)
            )
        )

    override fun visitFunc_decl(ctx: JavaKeYParser.Func_declContext?): Document {
        return super.visitFunc_decl(ctx)
    }

    fun indent(doc: Document) = nest(INDENT, doc)
    override fun visitFunc_decls(ctx: JavaKeYParser.Func_declsContext): Document =
        docOf(ctx.FUNCTIONS()) + bblock(
            ctx.func_decl().map { docOf(it) }.join(hardline)
        )

    override fun visitArg_sorts_or_formula(ctx: JavaKeYParser.Arg_sorts_or_formulaContext): Document {
        return super.visitArg_sorts_or_formula(ctx)
    }

    override fun visitArg_sorts_or_formula_helper(ctx: JavaKeYParser.Arg_sorts_or_formula_helperContext?): Document {
        return super.visitArg_sorts_or_formula_helper(ctx)
    }

    override fun visitTransform_decl(ctx: JavaKeYParser.Transform_declContext?): Document {
        return super.visitTransform_decl(ctx)
    }

    override fun visitTransform_decls(ctx: JavaKeYParser.Transform_declsContext?): Document {
        return super.visitTransform_decls(ctx)
    }

    override fun visitArrayopid(ctx: JavaKeYParser.ArrayopidContext?): Document {
        return super.visitArrayopid(ctx)
    }

    override fun visitArg_sorts(ctx: JavaKeYParser.Arg_sortsContext?): Document {
        return super.visitArg_sorts(ctx)
    }

    override fun visitWhere_to_bind(ctx: JavaKeYParser.Where_to_bindContext?): Document {
        return super.visitWhere_to_bind(ctx)
    }

    override fun visitRuleset_decls(ctx: JavaKeYParser.Ruleset_declsContext): Document =
        docOf(ctx.HEURISTICSDECL()) + bblock(
            ctx.simple_ident_with_doc().map { docOf(it) }.join(string(",") + space)
        )

    override fun visitSortId(ctx: JavaKeYParser.SortIdContext): Document {
        return ref(ctx.text, SORT)
    }

    override fun visitId_declaration(ctx: JavaKeYParser.Id_declarationContext?): Document {
        return super.visitId_declaration(ctx)
    }

    override fun visitFuncpred_name(ctx: JavaKeYParser.Funcpred_nameContext): Document {
        return (if (ctx.DOUBLECOLON() != null) {
            accept(ctx.simple_ident_dots(0)) + ctx.DOUBLECOLON().text
        } else empty) + ref(
            ctx.name.text,
            Symbol.Type.PREDICATE, Symbol.Type.TRANSFORMER, Symbol.Type.FUNCTION
        )
    }

    override fun visitTermEOF(ctx: JavaKeYParser.TermEOFContext?): Document {
        return super.visitTermEOF(ctx)
    }

    override fun visitBoolean_literal(ctx: JavaKeYParser.Boolean_literalContext?): Document {
        return super.visitBoolean_literal(ctx)
    }

    override fun visitLiterals(ctx: JavaKeYParser.LiteralsContext?): Document {
        return super.visitLiterals(ctx)
    }

    override fun visitEquivalence_term(ctx: JavaKeYParser.Equivalence_termContext?): Document {
        return super.visitEquivalence_term(ctx)
    }

    override fun visitTermParen(ctx: JavaKeYParser.TermParenContext?): Document {
        return super.visitTermParen(ctx)
    }

    override fun visitArgument_list(ctx: JavaKeYParser.Argument_listContext?): Document {
        return super.visitArgument_list(ctx)
    }

    override fun visitInteger(ctx: JavaKeYParser.IntegerContext?): Document {
        return super.visitInteger(ctx)
    }

    override fun visitFloatLiteral(ctx: JavaKeYParser.FloatLiteralContext?): Document {
        return super.visitFloatLiteral(ctx)
    }

    override fun visitDoubleLiteral(ctx: JavaKeYParser.DoubleLiteralContext?): Document {
        return super.visitDoubleLiteral(ctx)
    }

    override fun visitRealLiteral(ctx: JavaKeYParser.RealLiteralContext?): Document {
        return super.visitRealLiteral(ctx)
    }

    override fun visitChar_literal(ctx: JavaKeYParser.Char_literalContext?): Document {
        return super.visitChar_literal(ctx)
    }

    override fun visitVarId(ctx: JavaKeYParser.VarIdContext?): Document {
        return super.visitVarId(ctx)
    }

    override fun visitVarIds(ctx: JavaKeYParser.VarIdsContext?): Document {
        return super.visitVarIds(ctx)
    }

    override fun visitTriggers(ctx: JavaKeYParser.TriggersContext?): Document {
        return super.visitTriggers(ctx)
    }

    private fun accept(ctx: ParseTree) = ctx.accept(this)
    private fun accept(ctx: Token) = visitToken(ctx)

    fun docOf(ctx: Token?, leading: Document = empty, trailing: Document = empty): Document =
        if (ctx == null) empty
        else leading + accept(ctx) + trailing

    fun docOf(ctx: TerminalNode?, leading: Document = empty, trailing: Document = empty): Document =
        if (ctx == null) empty
        else leading + visitToken(ctx.symbol) + trailing

    fun docOf(ctx: ParserRuleContext?, leading: Document = empty, trailing: Document = empty): Document =
        if (ctx == null) empty
        else leading + accept(ctx) + trailing

    override fun visitTaclet(ctx: JavaKeYParser.TacletContext): Document =
        docOf(ctx.LEMMA(), trailing = space) + docOf(ctx.name) + docOf(ctx.choices_) + space + bblock(
            docOf(ctx.form) + (
                    if (ctx.SCHEMAVAR().isNotEmpty()) {
                        concat(
                            (0 until ctx.SCHEMAVAR().size).map { i ->
                                docOf(ctx.SCHEMAVAR(i)) + space + docOf(ctx.one_schema_var_decl(i)) + semi + hardline
                            }
                        )
                    } else empty
                    )
                    + (ctx.assumesSeq?.let { docOf(ctx.ASSUMES()) + parens(accept(it)) + hardline } ?: empty)
                    + (ctx.find?.let { docOf(ctx.FIND()) + parens(space + accept(ctx.find) + space) + hardline }
                ?: empty)
                    + (if (ctx.SAMEUPDATELEVEL().isNotEmpty()) docOf(
                ctx.SAMEUPDATELEVEL().first(),
                trailing = hardline
            ) else empty)
                    + (if (ctx.INSEQUENTSTATE().isNotEmpty()) docOf(
                ctx.INSEQUENTSTATE().first(),
                trailing = hardline
            ) else empty)
                    + (if (ctx.ANTECEDENTPOLARITY().isNotEmpty()) docOf(
                ctx.ANTECEDENTPOLARITY().first(),
                trailing = hardline
            ) else empty)
                    + (if (ctx.INSEQUENTSTATE().isNotEmpty()) docOf(
                ctx.INSEQUENTSTATE().first(),
                trailing = hardline
            ) else empty)
                    + (if (ctx.VARCOND().isNotEmpty()) {
                group(
                    ctx.varexplist().concatMap {
                        docOf(ctx.VARCOND().first()) + lparen + docOf(it) + rparen + break1
                    }
                )
            } else empty)
                    + docOf(ctx.goalspecs()) + docOf(ctx.modifiers()) + hardline
        ) + semi

    override fun visitModifiers(ctx: JavaKeYParser.ModifiersContext): Document {
        var d: Document = empty
        repeat(ctx.rulesets().size) {
            d = d + docOf(ctx.rulesets(0)) + hardline
        }

        ctx.NONINTERACTIVE().firstOrNull().let {
            d += docOf(it, hardline)
        }

        ctx.dname?.let {
            d = d + docOf(ctx.DISPLAYNAME().first()) + accept(ctx.dname) + hardline
        }

        ctx.htext?.let {
            d = d + docOf(ctx.HELPTEXT().first()) + space + accept(it) + hardline
        }

        ctx.triggers().forEach { d = d + accept(it) }
        return d
    }

    override fun visitSeq(ctx: JavaKeYParser.SeqContext?): Document {
        return super.visitSeq(ctx)
    }

    override fun visitSeqEOF(ctx: JavaKeYParser.SeqEOFContext?): Document {
        return super.visitSeqEOF(ctx)
    }

    override fun visitTermorseq(ctx: JavaKeYParser.TermorseqContext?): Document {
        return super.visitTermorseq(ctx)
    }

    override fun visitSemisequent(ctx: JavaKeYParser.SemisequentContext?): Document {
        return super.visitSemisequent(ctx)
    }

    override fun visitVarexplist(ctx: JavaKeYParser.VarexplistContext?): Document {
        return super.visitVarexplist(ctx)
    }

    override fun visitVarexpId(ctx: JavaKeYParser.VarexpIdContext?): Document {
        return super.visitVarexpId(ctx)
    }

    override fun visitVarexp_argument(ctx: JavaKeYParser.Varexp_argumentContext?): Document {
        return super.visitVarexp_argument(ctx)
    }

    override fun visitVarexp(ctx: JavaKeYParser.VarexpContext?): Document {
        return super.visitVarexp(ctx)
    }

    override fun visitGoalspecs(ctx: JavaKeYParser.GoalspecsContext): Document =
        if (ctx.CLOSEGOAL() != null) {
            accept(ctx.CLOSEGOAL())
        } else {
            ctx.goalspecwithoption().separateMap(semi + hardline) { accept(it) }
        } + hardline

    override fun visitGoalspecwithoption(ctx: JavaKeYParser.GoalspecwithoptionContext): Document =
        if (ctx.option_list() == null) accept(ctx.goalspec())
        else docOf(ctx.option_list()) + space + nest(INDENT, braces(hardline + docOf(ctx.goalspec()))) + hardline

    override fun visitOption(ctx: JavaKeYParser.OptionContext?): Document {
        return super.visitOption(ctx)
    }

    override fun visitOption_list(ctx: JavaKeYParser.Option_listContext?): Document {
        return super.visitOption_list(ctx)
    }

    override fun visitGoalspec(ctx: JavaKeYParser.GoalspecContext?): Document {
        return super.visitGoalspec(ctx)
    }

    override fun visitReplacewith(ctx: JavaKeYParser.ReplacewithContext?): Document {
        return super.visitReplacewith(ctx)
    }

    override fun visitAdd(ctx: JavaKeYParser.AddContext?): Document {
        return super.visitAdd(ctx)
    }

    override fun visitAddrules(ctx: JavaKeYParser.AddrulesContext?): Document {
        return super.visitAddrules(ctx)
    }

    override fun visitAddprogvar(ctx: JavaKeYParser.AddprogvarContext?): Document {
        return super.visitAddprogvar(ctx)
    }

    override fun visitTacletlist(ctx: JavaKeYParser.TacletlistContext?): Document {
        return super.visitTacletlist(ctx)
    }

    override fun visitPvset(ctx: JavaKeYParser.PvsetContext?): Document {
        return super.visitPvset(ctx)
    }

    override fun visitRulesets(ctx: JavaKeYParser.RulesetsContext) =
        docOf(ctx.HEURISTICS()) + parens(
            ctx.ruleset().separateMap(comma + space) { accept(it) }
        )

    override fun visitRuleset(ctx: JavaKeYParser.RulesetContext) = ref(ctx.text, Symbol.Type.RULESET)

    override fun visitRulesOrAxioms(ctx: JavaKeYParser.RulesOrAxiomsContext) =
        docOf(ctx.AXIOMS() ?: ctx.RULES()) + docOf(ctx.option_list(), lparen, rparen) + bblock(
            ctx.taclet().map { docOf(it) }.join(hardline + hardline)
        )

}

private operator fun Document.plus(doc: Document) = cat(this, doc)
private operator fun Document.plus(doc: String) = cat(this, string(doc))

/**
 *
 * @author Alexander Weigl
 * @version 1 (3/12/20)
 */
class PrettyPrinterStr(
    val index: Index,
    val currentContext: Symbol,
    val printReferences: Boolean = true,
    private val usageIndex: UsageIndex = HashMap()
) : JavaKeYParserBaseVisitor<String>() {

    private val vocabulary = JavaKeYLexer(CharStreams.fromString("")).vocabulary
    private val tokenSymbols = index.filterIsInstance<TokenSymbol>()

    override fun aggregateResult(aggregate: String?, nextResult: String?) =
        (aggregate ?: "") + (nextResult ?: "")

    override fun visitTerminal(node: TerminalNode) = visitToken(node.symbol)

    private fun visitToken(t: Token): String {
        if (t.type == KeYLexer.DOC_COMMENT) return ""
        if (t.type == KeYLexer.LPAREN || t.type == KeYLexer.LBRACE || t.type == KeYLexer.LBRACKET)
            return openParenthesis(t)
        if (t.type == KeYLexer.RPAREN || t.type == KeYLexer.RBRACE || t.type == KeYLexer.RBRACKET)
            return closeParenthesis(t)
        if (t.type == KeYLexer.IDENT && t.text[0] == '#') {
            return printSpan(t.text, "schema-variable")
        }
        val s = tokenSymbols.find { it.tokenType == t.type }
        val text = if (s != null && printReferences)
            "<a class=\"token\" href=\"${s.href}\">${t.text}</a> "
        else
            "${t.text} "
        return printSpan(text, vocabulary.getDisplayName(t.type))
    }

    private fun printSpan(text: String, classes: String) = "<span class=\"token $classes\">$text</span>"

    private val rainbowColors = arrayOf(
        "#458588",
        "#b16286",
        "#cc241d",
        "#d65d0e",
        "#458588",
        "#b16286",
        "#cc241d",
        "#d65d0e",
        "#458588",
        "#b16286",
        "#cc241d",
        "#d65d0e",
        "#458588",
        "#b16286",
        "#cc241d",
        "#d65d0e"
    )

    private val parenthesisIds = LinkedList<Int>()
    private var parenthesisCounter = 0
    private fun openParenthesis(token: Token): String {
        val p = ++parenthesisCounter
        parenthesisIds.push(p)
        val c = rainbowColors[p % rainbowColors.size]
        return "<span style=\"color:$c\" " +
                "class=\"paired-element\" id=\"open-${p}\" mouseover=\"highlight($p)\">${token.text}</span>"
    }

    private fun closeParenthesis(token: Token): String {
        val pop = parenthesisIds.pop()
        val c = rainbowColors[pop % rainbowColors.size]
        return "<span style=\"color:$c\" class=\"paired-element\" id=\"close-$pop\" mouseover=\"highlight($pop)\">${token.text}</span>"
    }

    override fun defaultResult() = " "

    override fun visitProblem(ctx: JavaKeYParser.ProblemContext?): String {
        return super.visitProblem(ctx)
    }

    override fun visitOne_include_statement(ctx: JavaKeYParser.One_include_statementContext?): String {
        return super.visitOne_include_statement(ctx)
    }

    override fun visitOne_include(ctx: JavaKeYParser.One_includeContext?): String {
        return super.visitOne_include(ctx)
    }

    override fun visitOptions_choice(ctx: JavaKeYParser.Options_choiceContext?): String {
        return super.visitOptions_choice(ctx)
    }

    override fun visitActivated_choice(ctx: JavaKeYParser.Activated_choiceContext?): String {
        return super.visitActivated_choice(ctx)
    }

    override fun visitOption_decls(ctx: JavaKeYParser.Option_declsContext?): String {
        return super.visitOption_decls(ctx)
    }

    override fun visitChoice(ctx: JavaKeYParser.ChoiceContext?): String {
        return super.visitChoice(ctx)
    }

    override fun visitSort_decls(ctx: JavaKeYParser.Sort_declsContext?): String {
        return super.visitSort_decls(ctx)
    }

    override fun visitOne_sort_decl(ctx: JavaKeYParser.One_sort_declContext): String =
        buildString {
            if (null != ctx.GENERIC()) {
                append(ctx.GENERIC().text).append(" ")
            }

            if (null != ctx.PROXY()) {
                append(ctx.PROXY().text).append(" ")
            }

            if (null != ctx.ABSTRACT()) {
                append(ctx.ABSTRACT().text).append(" ")
            }

            ctx.sortIds.simple_ident_dots_with_docs().joinTo(this, ", ") {
                it.text
            }

            if (null != ctx.ONEOF()) {
                append(" ")
                append(ctx.ONEOF().text)
                append(" ")
                ctx.oneof_sorts().sortId().joinTo(this, ", ", "{", "}") {
                    append(ref(it.text, SORT))
                }
            }
            if (null != ctx.EXTENDS()) {
                append(" ")
                append(ctx.EXTENDS().text).append(" ")
                ctx.sortExt.sortId().joinTo(this, ", ") {
                    it.accept(this@PrettyPrinterStr)
                }
            }
            append(ctx.SEMI().text)
        }

    private fun ref(text: String, vararg types: Symbol.Type): String {
        if (!printReferences) return "$text "
        val s = index.find { b -> b.type in types && b.displayName == text }
        return if (s != null) {
            usageIndex.add(s, currentContext)
            "<a href=\"${s.href}\" class=\"symbol ${s.type.name}\">$text</a> "
        } else
            "$text ".also {
                Tadoc.errordpln("Could not found symbol for $text : ${types.toList()}")
            }
    }

    override fun visitSimple_ident_dots(ctx: JavaKeYParser.Simple_ident_dotsContext?): String {
        return super.visitSimple_ident_dots(ctx)
    }

    override fun visitSimple_ident_dots_comma_list(ctx: JavaKeYParser.Simple_ident_dots_comma_listContext?): String {
        return super.visitSimple_ident_dots_comma_list(ctx)
    }

    override fun visitExtends_sorts(ctx: JavaKeYParser.Extends_sortsContext?): String {
        return super.visitExtends_sorts(ctx)
    }

    override fun visitOneof_sorts(ctx: JavaKeYParser.Oneof_sortsContext?): String {
        return super.visitOneof_sorts(ctx)
    }

    override fun visitProg_var_decls(ctx: JavaKeYParser.Prog_var_declsContext?): String {
        return super.visitProg_var_decls(ctx)
    }

    override fun visitString_literal(ctx: JavaKeYParser.String_literalContext?): String {
        return super.visitString_literal(ctx)
    }

    override fun visitString_value(ctx: JavaKeYParser.String_valueContext?): String {
        return super.visitString_value(ctx)
    }

    override fun visitSimple_ident(ctx: JavaKeYParser.Simple_identContext?): String {
        return super.visitSimple_ident(ctx)
    }

    override fun visitSimple_ident_comma_list(ctx: JavaKeYParser.Simple_ident_comma_listContext?): String {
        return super.visitSimple_ident_comma_list(ctx)
    }

    override fun visitSchema_var_decls(ctx: JavaKeYParser.Schema_var_declsContext?): String {
        return super.visitSchema_var_decls(ctx)
    }

    override fun visitOne_schema_var_decl(ctx: JavaKeYParser.One_schema_var_declContext?): String {
        return super.visitOne_schema_var_decl(ctx)
    }

    override fun visitSchema_modifiers(ctx: JavaKeYParser.Schema_modifiersContext?): String {
        return super.visitSchema_modifiers(ctx)
    }

    override fun visitOne_schema_modal_op_decl(ctx: JavaKeYParser.One_schema_modal_op_declContext?): String {
        return super.visitOne_schema_modal_op_decl(ctx)
    }

    override fun visitPred_decl(ctx: JavaKeYParser.Pred_declContext?): String {
        return super.visitPred_decl(ctx)
    }

    override fun visitPred_decls(ctx: JavaKeYParser.Pred_declsContext?): String {
        return super.visitPred_decls(ctx)
    }

    override fun visitFunc_decl(ctx: JavaKeYParser.Func_declContext?): String {
        return super.visitFunc_decl(ctx)
    }

    override fun visitFunc_decls(ctx: JavaKeYParser.Func_declsContext?): String {
        return super.visitFunc_decls(ctx)
    }

    override fun visitArg_sorts_or_formula(ctx: JavaKeYParser.Arg_sorts_or_formulaContext?): String {
        return super.visitArg_sorts_or_formula(ctx)
    }

    override fun visitArg_sorts_or_formula_helper(ctx: JavaKeYParser.Arg_sorts_or_formula_helperContext?): String {
        return super.visitArg_sorts_or_formula_helper(ctx)
    }

    override fun visitTransform_decl(ctx: JavaKeYParser.Transform_declContext?): String {
        return super.visitTransform_decl(ctx)
    }

    override fun visitTransform_decls(ctx: JavaKeYParser.Transform_declsContext?): String {
        return super.visitTransform_decls(ctx)
    }

    override fun visitArrayopid(ctx: JavaKeYParser.ArrayopidContext?): String {
        return super.visitArrayopid(ctx)
    }

    override fun visitArg_sorts(ctx: JavaKeYParser.Arg_sortsContext?): String {
        return super.visitArg_sorts(ctx)
    }

    override fun visitWhere_to_bind(ctx: JavaKeYParser.Where_to_bindContext?): String {
        return super.visitWhere_to_bind(ctx)
    }

    override fun visitRuleset_decls(ctx: JavaKeYParser.Ruleset_declsContext?): String {
        return super.visitRuleset_decls(ctx)
    }

    override fun visitSortId(ctx: JavaKeYParser.SortIdContext): String {
        return ref(ctx.text, SORT)
    }

    override fun visitId_declaration(ctx: JavaKeYParser.Id_declarationContext?): String {
        return super.visitId_declaration(ctx)
    }

    override fun visitFuncpred_name(ctx: JavaKeYParser.Funcpred_nameContext) = buildString {
        if (ctx.INT_LITERAL() != null) { // number
            appendn(ctx.INT_LITERAL())
        }

        if (ctx.DOUBLECOLON() != null) {
            appendn(ctx.simple_ident_dots(0))
            appendn(ctx.DOUBLECOLON())
        }

        append(
            ref(
                ctx.name.text,
                Symbol.Type.PREDICATE, Symbol.Type.TRANSFORMER, Symbol.Type.FUNCTION
            )
        )
    }


    override fun visitTermEOF(ctx: JavaKeYParser.TermEOFContext?): String {
        return super.visitTermEOF(ctx)
    }

    override fun visitBoolean_literal(ctx: JavaKeYParser.Boolean_literalContext?): String {
        return super.visitBoolean_literal(ctx)
    }

    override fun visitLiterals(ctx: JavaKeYParser.LiteralsContext?): String {
        return super.visitLiterals(ctx)
    }

    override fun visitEquivalence_term(ctx: JavaKeYParser.Equivalence_termContext?): String {
        return super.visitEquivalence_term(ctx)
    }

    override fun visitTermParen(ctx: JavaKeYParser.TermParenContext?): String {
        return super.visitTermParen(ctx)
    }

    override fun visitArgument_list(ctx: JavaKeYParser.Argument_listContext?): String {
        return super.visitArgument_list(ctx)
    }

    override fun visitChar_literal(ctx: JavaKeYParser.Char_literalContext?): String {
        return super.visitChar_literal(ctx)
    }

    override fun visitVarId(ctx: JavaKeYParser.VarIdContext?): String {
        return super.visitVarId(ctx)
    }

    override fun visitVarIds(ctx: JavaKeYParser.VarIdsContext?): String {
        return super.visitVarIds(ctx)
    }

    override fun visitTriggers(ctx: JavaKeYParser.TriggersContext?): String {
        return super.visitTriggers(ctx)
    }

    private fun accept(ctx: ParseTree) = ctx.accept(this)
    private fun accept(ctx: Token) = visitToken(ctx)
    private fun StringBuilder.appendn(ctx: ParseTree?) = if (ctx != null) append(accept(ctx)) else this
    private fun StringBuilder.appendn(ctx: Token?) = if (ctx != null) append(accept(ctx)) else this
    private fun StringBuilder.appendn(ctx: ParseTree?, suffix: String): StringBuilder {
        if (ctx != null) appendn(ctx).append(suffix)
        return this
    }

    override fun visitTaclet(ctx: JavaKeYParser.TacletContext) = buildString {
        appendn(ctx.LEMMA(), " ")
        appendn(ctx.name)

        if (ctx.choices_ != null) {
            append(accept(ctx.choices_))
        }
        append(" {\n")

        appendn(ctx.form)

        if (ctx.SCHEMAVAR().isNotEmpty()) {
            for (i in 0 until ctx.SCHEMAVAR().size) {
                appendn(ctx.SCHEMAVAR(i))
                    .append(" ")
                    .appendn(ctx.one_schema_var_decl(i))
                    .append(";\n")
            }
        }

        ctx.assumesSeq?.let {
            append(accept(ctx.ASSUMES()))
            append("(")
            append(accept(it))
            append(")\n")
        }

        ctx.find?.let {
            append(accept(ctx.FIND()))
            append("(")
            append(accept(ctx.find))
            append(")\n")
        }

        if (ctx.SAMEUPDATELEVEL().isNotEmpty())
            appendn(ctx.SAMEUPDATELEVEL().first(), "\n")
        if (ctx.INSEQUENTSTATE().isNotEmpty())
            appendn(ctx.INSEQUENTSTATE().first(), "\n")
        if (ctx.ANTECEDENTPOLARITY().isNotEmpty())
            appendn(ctx.ANTECEDENTPOLARITY().first(), "\n")
        if (ctx.INSEQUENTSTATE().isNotEmpty())
            appendn(ctx.INSEQUENTSTATE().first(), "\n")

        if (ctx.VARCOND().isNotEmpty()) {
            ctx.VARCOND().forEach {
                appendn(it)
            }
            // appendn(ctx.VARCOND()).append("(")
            // TODO weigl    .appendn(ctx.varexplist()).append(")\n")
        }
        appendn(ctx.goalspecs())
        appendn(ctx.modifiers())
        append("\n};")
    }

    override fun visitModifiers(ctx: JavaKeYParser.ModifiersContext) = buildString {
        repeat(ctx.rulesets().size) {
            appendn(ctx.rulesets(0)).append("\n")
        }

        ctx.NONINTERACTIVE().firstOrNull().let {
            appendn(it, "\n")
        }

        ctx.dname?.let {
            appendn(ctx.DISPLAYNAME().first()).append(" ").appendn(ctx.dname).append("\n")
        }

        ctx.htext?.let {
            appendn(ctx.HELPTEXT().first()).append(" ").append(it).append("\n")
        }

        ctx.triggers().forEach { appendn(it) }
    }

    override fun visitSeq(ctx: JavaKeYParser.SeqContext?): String {
        return super.visitSeq(ctx)
    }

    override fun visitSeqEOF(ctx: JavaKeYParser.SeqEOFContext?): String {
        return super.visitSeqEOF(ctx)
    }

    override fun visitTermorseq(ctx: JavaKeYParser.TermorseqContext?): String {
        return super.visitTermorseq(ctx)
    }

    override fun visitSemisequent(ctx: JavaKeYParser.SemisequentContext?): String {
        return super.visitSemisequent(ctx)
    }

    override fun visitVarexplist(ctx: JavaKeYParser.VarexplistContext?): String {
        return super.visitVarexplist(ctx)
    }

    override fun visitVarexpId(ctx: JavaKeYParser.VarexpIdContext?): String {
        return super.visitVarexpId(ctx)
    }

    override fun visitVarexp_argument(ctx: JavaKeYParser.Varexp_argumentContext?): String {
        return super.visitVarexp_argument(ctx)
    }

    override fun visitVarexp(ctx: JavaKeYParser.VarexpContext?): String {
        return super.visitVarexp(ctx)
    }

    override fun visitModality_term(ctx: JavaKeYParser.Modality_termContext?): String {
        return super.visitModality_term(ctx)
    }

    override fun visitGoalspecs(ctx: JavaKeYParser.GoalspecsContext) =
        if (ctx.CLOSEGOAL() != null) {
            accept(ctx.CLOSEGOAL())
        } else {
            ctx.goalspecwithoption().joinToString(";\n") { accept(it) }
        } + "\n"

    override fun visitGoalspecwithoption(ctx: JavaKeYParser.GoalspecwithoptionContext) =
        if (ctx.option_list() == null) accept(ctx.goalspec())
        else buildString { appendn(ctx.option_list()).append(" {\n").appendn(ctx.goalspec()).append("}\n") }

    override fun visitOption(ctx: JavaKeYParser.OptionContext?): String {
        return super.visitOption(ctx)
    }

    override fun visitOption_list(ctx: JavaKeYParser.Option_listContext?): String {
        return super.visitOption_list(ctx)
    }

    override fun visitGoalspec(ctx: JavaKeYParser.GoalspecContext?): String {
        return super.visitGoalspec(ctx)
    }

    override fun visitReplacewith(ctx: JavaKeYParser.ReplacewithContext?): String {
        return super.visitReplacewith(ctx)
    }

    override fun visitAdd(ctx: JavaKeYParser.AddContext?): String {
        return super.visitAdd(ctx)
    }

    override fun visitAddrules(ctx: JavaKeYParser.AddrulesContext?): String {
        return super.visitAddrules(ctx)
    }

    override fun visitAddprogvar(ctx: JavaKeYParser.AddprogvarContext?): String {
        return super.visitAddprogvar(ctx)
    }

    override fun visitTacletlist(ctx: JavaKeYParser.TacletlistContext?): String {
        return super.visitTacletlist(ctx)
    }

    override fun visitPvset(ctx: JavaKeYParser.PvsetContext?): String {
        return super.visitPvset(ctx)
    }

    override fun visitRulesets(ctx: JavaKeYParser.RulesetsContext) = buildString {
        appendn(ctx.HEURISTICS())
        append(" (")
        ctx.ruleset().joinTo(this, ", ") { accept(it) }
        append(")")
    }

    override fun visitRuleset(ctx: JavaKeYParser.RulesetContext) = ref(ctx.text, Symbol.Type.RULESET)
}
