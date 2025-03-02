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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import de.uka.ilkd.key.nparser.KeYLexer
import de.uka.ilkd.key.nparser.KeYParser
import de.uka.ilkd.key.nparser.ParsingFacade
import de.uka.ilkd.key.util.parsing.SyntaxErrorReporter.ParserException
import io.github.wadoon.tadoc.scripts.ScriptDoc
import org.antlr.v4.runtime.CharStreams
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*


object Tadoc {
    private const val logFormat = "[%5d] %s%s%s"
    private val startTime = System.currentTimeMillis()

    @JvmStatic
    fun main(args: Array<String>) {
        GenDoc().main(args)
    }

    fun putln(s: String, colorOn: String = "", colorOff: String = "") =
        println(String.format(logFormat, (System.currentTimeMillis() - startTime), colorOn, s, colorOff))

    const val ESC = 27.toChar()
    fun putln(s: String, color: Int) = putln(s, "$ESC[${color}m", "$ESC[0m")
    fun errorln(s: String) = putln(s, 33)
    private var printedErrors = mutableSetOf<String>()
    fun errordpln(s: String) {
        if (s !in printedErrors) {
            printedErrors.add(s); errorln(s)
        }
    }
}

interface GenDocStep {
    fun prepare()
    fun manifest()
}

class GenDoc : CliktCommand() {
    val outputFolder by option("-o", "--output", help = "output folder", metavar = "FOLDER")
        .file().default(File("target"))

    val inputFiles by argument("taclet-file", help = "")
        .file().multiple(required = false)

    val tacletFiles: List<Path> by lazy {
        if (useDefaultClasspath) {
            val packagePath = "de/uka/ilkd/key/proof/rules"
            System.getProperty("java.class.path", ".")
                .split(File.pathSeparator.toRegex())
                .filter { it.isNotBlank() }
                .flatMap { collectAll(Paths.get(it), packagePath) }
        } else {
            collectAll(inputFiles)
        }
    }

    val useDefaultClasspath by option("--use-default-classpath").flag()

    private val usageIndex: UsageIndex = HashMap()

    private val symbols = Index().also {
        val l = KeYLexer(CharStreams.fromString(""))
        (0..l.vocabulary.maxTokenType)
            .filter { l.vocabulary.getLiteralName(it) != null }
            .forEach { t ->
                l.vocabulary.getSymbolicName(t)?.let { name ->
                    it += Symbol.token(name, t)
                }
            }
    }

    override fun run() {
        outputFolder.mkdirs()
        copyStaticFiles()
        tacletFiles.map(::index).zip(tacletFiles)
            .forEach { (ctx, f) -> ctx?.let { run(it, f) } }
        ScriptDoc(symbols)
        generateIndex()
    }

    private fun copyStaticFiles() {
        copyStaticFile("style.css")
        copyStaticFile("pure.min.css")
        copyStaticFile("grid-responsive-min.css")
    }

    private fun copyStaticFile(s: String) {
        javaClass.getResourceAsStream("/static/$s")?.use { input ->
            File(outputFolder, s).outputStream().use { out ->
                input.copyTo(out)
            }
        }
    }


    private fun index(f: Path): KeYParser.FileContext? {
        Tadoc.putln("Parsing $f")
        try {
            val ast = ParsingFacade.parseFile(f)
            val ctx = ParsingFacade.getParseRuleContext(ast)
            val self = f.nameWithoutExtension + ".html"
            Tadoc.putln("Indexing $f")
            ctx.accept(Indexer(self, symbols))
            return ctx
        } catch (e: ParserException) {
            Tadoc.putln("Could not parse $f\n${e.message}")
            return null
        }
    }

    fun run(ctx: KeYParser.FileContext, f: Path) {
        try {
            Tadoc.putln("Analyze: $f")
            val target = File(outputFolder, f.nameWithoutExtension + ".html")
            DocumentationFile(target, f, ctx, symbols, usageIndex).manifest()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generateIndex() {
        val f = File(outputFolder, "index.html")
        IndexPage(f, symbols).manifest()

        val uif = File(outputFolder, "usage.html")
        UsageIndexFile(uif, symbols, usageIndex).manifest()
    }
}

private fun collectAll(inputFiles: Iterable<File>): List<Path> = inputFiles.flatMap { file ->
    when {
        file.isDirectory() ->
            file.walkTopDown().filter { it.name.endsWith(".key") }
                .map { it.toPath() }
                .toList()

        else -> listOf(file.toPath())
    }
}

private fun collectAll(path: Path, packagePath: String): Iterable<Path> {
    if (path.extension == "jar") {
        val fs = FileSystems.newFileSystem(path)
        val folder = fs.getPath(packagePath)
        if (folder.exists() && folder.isDirectory()) {
            return folder.listDirectoryEntries("*.key")
        } else {
            fs.close()
        }
    }
    return listOf()
}
