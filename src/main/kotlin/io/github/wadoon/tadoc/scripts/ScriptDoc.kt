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
package io.github.wadoon.tadoc.scripts

import de.uka.ilkd.key.macros.ProofMacro
import de.uka.ilkd.key.scripts.ProofScriptCommand
import io.github.wadoon.tadoc.DefaultPage
import io.github.wadoon.tadoc.Index
import io.github.wadoon.tadoc.Markdown.markdown
import io.github.wadoon.tadoc.Symbol
import io.github.wadoon.tadoc.UsageIndex
import kotlinx.html.*
import java.io.File
import java.util.*

class ScriptDocModule(val index: Index, val usageIndex: UsageIndex) {
    val commands = ServiceLoader.load(ProofScriptCommand::class.java).toList()

    fun page(target: File) = ScriptDoc(target, index, commands)
    fun addToIndex() {
        commands.forEach {
            index += it.indexSymbol()
        }
    }
}

private fun ProofScriptCommand.indexSymbol() =
    Symbol.scriptCommand(this.name, this.category)

class ScriptDoc(target: File, index: Index, val commands: List<ProofScriptCommand>) :
    DefaultPage(target, "Reference for Proof Scripts", index) {

    override fun content(div: DIV) {
        div.writePreamble()
        div.writeCommand()
        div.writeMacros()
    }

    private fun DIV.writePreamble() {
        h1 { +"Reference for Proof Scripts" }
        p { +"*Generated on ${Date()}" }

        style {
            +"""
                .synopsis { background: lightgray; }
                .doc { border-left: solid .5in orange; padding-left:1ex;}
            """.trimIndent()
        }
    }

    private val FORBBIDEN_COMMANDS = setOf("exit", "focus", "javascrpt", "leave", "let")

    private fun DIV.writeMacros() {
        val macros = ServiceLoader.load(ProofMacro::class.java)
            .filterNotNull()
            .filter { it.scriptCommandName != null }
            .toMutableSet()
            .sortedBy { it.scriptCommandName }
        section {
            h2 { +"Macros" }
            for (t in macros) {
                div {
                    h3("name macro") { +t.scriptCommandName }
                    p {
                        +"Original name ${t.name} in ${t.category}"
                    }
                    div("doc") {unsafe {  +t.description } }
                }
            }
        }
    }

    private fun SECTION.helpForCommand(c: ProofScriptCommand) {
        h3 { +c.name }
        div {
            div("synopsis") {
                code {
                    +c.name
                    for (a in c.arguments) {
                        +" "
                        if (a.isFlag) {
                            +"[${a.name}]"
                        } else {
                            val arg =
                                if (a.name.startsWith("#")) {
                                    "<${a.type.simpleName.uppercase(Locale.getDefault())}>"
                                } else {
                                    "${a.name}=<${a.type.simpleName.uppercase(Locale.getDefault())}>"
                                }
                            if (!a.isRequired) {
                                +"[$arg]"
                            } else {
                                +arg
                            }
                        }
                    }
                }
            }
            div {
                details {
                    summary { +"Documentation" }
                    div("doc") {
                        markdown(c.documentation)
                    }
                }
            }

            p {
                h4 { +"Arguments:" }
                ul {
                    for (a in c.arguments) {
                        li {
                            div {
                                code { +"${a.name} : ${a.type.simpleName.uppercase()}" }

                                if (a.isRequired) {
                                    +" ("
                                    b { +"required" }
                                    +")"
                                }
                            }
                            div { +(a.documentation ?: " not available") }

                        }
                    }
                }
            }
        }
    }

    private fun DIV.writeCommand() {
        commands.sortedWith(Comparator.comparing { it.name })
        div {
            h2 { +"Commands" }
            for (t in commands) {
                if (t.name !in FORBBIDEN_COMMANDS) {
                    section { this.helpForCommand(t) }
                }
            }
        }
    }
}
