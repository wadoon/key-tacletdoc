package io.github.wadoon.tadoc

import com.github.javaparser.ParserConfiguration
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.javadoc.JavadocBlockTag
import de.uka.ilkd.key.nparser.varexp.AbstractTacletBuilderCommand
import de.uka.ilkd.key.nparser.varexp.ArgumentType
import de.uka.ilkd.key.nparser.varexp.ConstructorBasedBuilder
import de.uka.ilkd.key.nparser.varexp.TacletBuilderManipulators
import kotlinx.html.*
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.jvm.optionals.getOrNull

data class Arguments(
    val negationSupport: Boolean,
    val argumentType: List<ArgumentType>,
    val documentation: List<String>,
    val cdoc: String
) {
    fun indexSymbol(vm: VarcondMeta) = Symbol.varcond(vm.name, vm.safeTarget + argumentType.size)
}

data class VarcondMeta(
    val name: String, val overloadedArgs: List<Arguments>,
    val documentation: String
) {
    val safeTarget: String = name.replace('\\', '_').trim().uppercase()
    fun indexSymbol() = Symbol.varcond(name, safeTarget)
}

fun normalizeCmdName(it: String) = if (it.startsWith("\\")) it.replace("\\\\", "\\") else "\\" + it

private val ConstructorBasedBuilder.isNegationSupported: Boolean
    get() {
        val declaredField = ConstructorBasedBuilder::class.java.getDeclaredField("negationSupported")
        declaredField.isAccessible = true
        return declaredField.get(this) as Boolean
    }

private val ConstructorBasedBuilder.relevantClazz: Class<*>
    get() {
        val declaredField = ConstructorBasedBuilder::class.java.getDeclaredField("clazz")
        declaredField.isAccessible = true
        return declaredField.get(this) as Class<*>
    }

private val ConstructorBasedBuilder.triggerName: String
    get() {
        val declaredField = AbstractTacletBuilderCommand::class.java.getDeclaredField("triggerName")
        declaredField.isAccessible = true
        return declaredField.get(this) as String
    }

class VarcondDocModule(val index: Index, val usageIndex: UsageIndex) {
    private var varconds: List<VarcondMeta>

    fun page(target:File) = VarcondDocPage(target, index, varconds)
    fun addToIndex() {
        varconds.forEach {
            index += it.indexSymbol()
            /*it.overloadedArgs.forEach { arg ->
                index += arg.indexSymbol(it)
            }*/
        }
    }

    init {
        val config = ParserConfiguration()
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
        StaticJavaParser.setConfiguration(config)

        val g = TacletBuilderManipulators.getConditionBuilders()
            .filterIsInstance<ConstructorBasedBuilder>()
            .groupBy { normalizeCmdName(it.triggerName) }
            .toSortedMap()

        varconds = g.map { (name, cmds) ->
            val cmds = cmds.sortedBy { it.argumentTypes.size }
            val clazz: Class<*> = cmds.first().relevantClazz
            val sourceCode = getSourceCode(clazz.getName())
            val clazzDoc = clazzDoc(sourceCode)
            val overloads = cmds.map { cmd ->
                val (argNames, cdoc) = constructorJavadoc(sourceCode, cmd.argumentTypes, cmd.isNegationSupported)
                Arguments(cmd.isNegationSupported, cmd.argumentTypes.toList(), argNames, cdoc)
            }

            VarcondMeta(name, overloads, clazzDoc)
        }
    }

}

class VarcondDocPage(target : File, index: Index, val varconds: List<VarcondMeta>) :
    DefaultPage(target, "Reference for Variable Conditions", index) {
    override fun content(div: DIV) {
        div.h1 { +"Variable Conditions" }
        varconds.forEach {
            div.section {
                id = it.indexSymbol().anchor
                printVarcond(it)
            }
        }
    }

    fun SECTION.printVarcond(meta: VarcondMeta) {
        h2 { +meta.name }

        p {
            p { unsafe { +meta.documentation } }
            div("signatures") {
                strong { +"Signatures" }
                ul {
                    meta.overloadedArgs.forEach { arg ->
                        val (neg, types, argNames, doc) = arg
                        li {
                            id = arg.indexSymbol(meta).target
                            val arguments = argNames.zip(types).joinToString(", ") { (a, b) -> "$a$b" }
                            div { code{+"${meta.name}($arguments)"} }
                            if (neg) {
                                div { code{+"\\not${meta.name}($arguments)" } }
                            }
                            p { unsafe { +doc } }
                        }
                    }
                }
            }
        }
    }
}


private fun constructorJavadoc(
    sourceCode: CompilationUnit,
    types: Array<ArgumentType>,
    supportNegation: Boolean
): Pair<List<String>, String> {
    val typeArray = types.map { type -> type.clazz.getSimpleName() }.toMutableList()
    if (supportNegation) typeArray.add("boolean")

    val constructorDeclaration: ConstructorDeclaration? = sourceCode.primaryType.flatMap {
        it.getConstructorByParameterTypes(*typeArray.toTypedArray())
    }.orElse(null)


    val names = constructorDeclaration
        ?.let { it.parameters.map { obj -> "${obj!!.nameAsString} " } }
        ?: types.map { "" }

    val jd = constructorDeclaration?.javadoc?.getOrNull()
        ?.let {
            it.description.toText() + "\n" +
                    it.blockTags
                        .filter { tag -> tag!!.type == JavadocBlockTag.Type.PARAM }
                        .joinToString("\n") { tag -> "* `${tag!!.name.orElse("")}` ${tag.toText()}" }
        }?.processJavadoc()?.indentBy("  ") ?: ""

    return names to jd
}

private fun String.indentBy(indent: String) = indent + this.replace("\n", "\n$indent")

fun String.processJavadoc(): String = replace("^\\s+\\*".toRegex(), "")
    .replace("\\{@link (.+?)\\}".toRegex(), "<code>$1</code>")
    .replace("\\{@code (.+?)\\}".toRegex(), "<code>$1</code>")

private fun clazzDoc(sourceCode: CompilationUnit): String {
    return sourceCode.primaryType.getOrNull()
        ?.let { it.javadoc?.getOrNull() }
        ?.description?.toText()
        ?.processJavadoc() ?: ""
}

private fun getSourceCode(name: String): CompilationUnit {
    val bases = listOf(
        Paths.get("key/key.core/src/main/java/"),
        Paths.get("key/key.ncore/src/main/java/"),
        Paths.get("../key/key.core/src/main/java/"),
        Paths.get("../key/key.ncore/src/main/java/")
    )

    val rel = name.replace('.', '/') + ".java"
    for (basis in bases) {
        val x = basis.resolve(rel)
        if (x.exists()) {
            try {
                return StaticJavaParser.parse(x)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }
    return CompilationUnit()
}
