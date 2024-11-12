package io.github.wadoon.tadoc.tpp.format

class Output {
    private val output = StringBuilder()
    private var indentLevel = 0
    var isNewLine: Boolean = true
        private set
    private var spaceBeforeNextToken = false

    private fun indent() {
        output.append(getIndent(indentLevel))
        this.isNewLine = false
        this.spaceBeforeNextToken = false
    }

    private fun checkBeforeToken() {
        if (this.isNewLine) {
            indent()
        } else if (spaceBeforeNextToken) {
            output.append(' ')
            this.spaceBeforeNextToken = false
        }
    }

    fun spaceBeforeNext() {
        this.spaceBeforeNextToken = true
    }

    fun noSpaceBeforeNext() {
        this.spaceBeforeNextToken = false
    }

    fun token(value: String?) {
        checkBeforeToken()
        output.append(value)
    }

    fun token(value: Char) {
        checkBeforeToken()
        output.append(value)
    }

    fun enterIndent() {
        indentLevel++
    }

    fun exitIndent() {
        check(indentLevel != 0) { "Unmatched closing RPAREN." }
        indentLevel--
    }

    fun assertNewLineAndIndent() {
        assertNewLine()
        indent()
    }

    fun assertIndented() {
        if (isNewLine) {
            indent()
        }
    }

    fun newLine() {
        this.isNewLine = true
        output.append('\n')
    }

    fun assertNewLine() {
        if (!this.isNewLine) {
            newLine()
        }
    }

    override fun toString(): String {
        return output.toString()
    }

    companion object {
        const val INDENT_STEP: Int = 4
        private val INDENT_BUFFER = " ".repeat(100)

        fun getIndent(count: Int): String {
            // Substrings use a shared buffer
            return INDENT_BUFFER.substring(0, INDENT_STEP * count)
        }
    }
}
