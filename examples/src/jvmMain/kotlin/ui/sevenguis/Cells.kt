package ui.sevenguis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.yafrl.Signal
import io.github.yafrl.compose.YafrlCompose
import io.github.yafrl.compose.composeState
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope

object Cells {
    private val COLUMNS = ('A'..'E').toList()
    private val ROWS = (0..9).toList()
    private val ALL_CELLS = COLUMNS.flatMap { col -> ROWS.map { row -> "$col$row" } }

    data class State(
        val raw: Map<String, String>,
        val editing: String?
    )

    // --- Formula evaluator ---

    private fun evaluate(id: String, raw: Map<String, String>, visited: Set<String> = emptySet()): String {
        if (id in visited) return "CIRC"
        val formula = raw[id] ?: return ""
        if (!formula.startsWith("=")) return formula
        return try {
            val result = evalExpr(formula.drop(1).trim(), raw, visited + id)
            if (result == result.toLong().toDouble()) result.toLong().toString()
            else "%.4g".format(result)
        } catch (e: Exception) {
            "ERR"
        }
    }

    private fun evalExpr(expr: String, raw: Map<String, String>, visited: Set<String>): Double {
        val tokens = tokenize(expr)
        return parseAddSub(tokens, 0, raw, visited).value
    }

    private data class ParseResult(val value: Double, val pos: Int)

    private fun parseAddSub(tokens: List<String>, start: Int, raw: Map<String, String>, visited: Set<String>): ParseResult {
        var (value, pos) = parseMulDiv(tokens, start, raw, visited)
        while (pos < tokens.size && (tokens[pos] == "+" || tokens[pos] == "-")) {
            val op = tokens[pos]
            val (right, nextPos) = parseMulDiv(tokens, pos + 1, raw, visited)
            value = if (op == "+") value + right else value - right
            pos = nextPos
        }
        return ParseResult(value, pos)
    }

    private fun parseMulDiv(tokens: List<String>, start: Int, raw: Map<String, String>, visited: Set<String>): ParseResult {
        var (value, pos) = parseAtom(tokens, start, raw, visited)
        while (pos < tokens.size && (tokens[pos] == "*" || tokens[pos] == "/")) {
            val op = tokens[pos]
            val (right, nextPos) = parseAtom(tokens, pos + 1, raw, visited)
            value = if (op == "*") value * right else value / right
            pos = nextPos
        }
        return ParseResult(value, pos)
    }

    private fun parseAtom(tokens: List<String>, start: Int, raw: Map<String, String>, visited: Set<String>): ParseResult {
        if (start >= tokens.size) throw IllegalArgumentException("Unexpected end")
        val token = tokens[start]
        if (token == "(") {
            val (value, pos) = parseAddSub(tokens, start + 1, raw, visited)
            if (pos >= tokens.size || tokens[pos] != ")") throw IllegalArgumentException("Missing )")
            return ParseResult(value, pos + 1)
        }
        // Cell reference: letter + digit(s)
        if (token.length >= 2 && token[0].isUpperCase() && token[1].isDigit()) {
            val cellVal = evaluate(token, raw, visited)
            val num = cellVal.toDoubleOrNull() ?: throw IllegalArgumentException("Cell $token is not numeric")
            return ParseResult(num, start + 1)
        }
        // Numeric literal
        val num = token.toDoubleOrNull() ?: throw IllegalArgumentException("Unknown token: $token")
        return ParseResult(num, start + 1)
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            when {
                expr[i].isWhitespace() -> i++
                expr[i] in "+-*/()" -> { tokens.add(expr[i].toString()); i++ }
                expr[i].isDigit() || expr[i] == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(expr.substring(start, i))
                }
                expr[i].isUpperCase() -> {
                    val start = i
                    i++ // consume letter
                    while (i < expr.length && expr[i].isDigit()) i++
                    tokens.add(expr.substring(start, i))
                }
                else -> throw IllegalArgumentException("Unknown char: ${expr[i]}")
            }
        }
        return tokens
    }

    // --- ViewModel ---

    class ViewModel(timeline: Timeline) : TimelineScope(timeline) {
        val cellEdited = externalEvent<Pair<String, String>>()
        val cellFocused = externalEvent<String>()
        val cellBlurred = externalEvent<Unit>()

        val state: Signal<State> = Signal.fold(
            State(emptyMap(), null),
            on(cellEdited) { s, (id, text) -> s.copy(raw = s.raw + (id to text)) },
            on(cellFocused) { s, id -> s.copy(editing = id) },
            on(cellBlurred) { s, _ -> s.copy(editing = null) }
        )

        val display: Signal<Map<String, String>> = state.map { s ->
            ALL_CELLS.associateWith { id -> evaluate(id, s.raw) }
        }
    }

    // --- View ---

    private val cellWidth = 80.dp
    private val cellHeight = 30.dp
    private val headerBg = Color(0xFFEEEEEE)
    private val cellBorder = Modifier.border(0.5.dp, Color.LightGray)
    private val cellTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

    @Composable
    fun View() = YafrlCompose {
        val vm = remember { ViewModel(timeline) }
        val state by remember { vm.state.composeState(timeline) }
        val display by remember { vm.display.composeState(timeline) }

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // Header row
            Row {
                Box(modifier = Modifier.width(30.dp).height(cellHeight).background(headerBg).then(cellBorder))
                for (col in COLUMNS) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.width(cellWidth).height(cellHeight).background(headerBg).then(cellBorder)
                    ) {
                        Text("$col", style = cellTextStyle)
                    }
                }
            }

            for (row in ROWS) {
                Row {
                    // Row number
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.width(30.dp).height(cellHeight).background(headerBg).then(cellBorder)
                    ) {
                        Text("$row", style = cellTextStyle)
                    }

                    for (col in COLUMNS) {
                        val cellId = "$col$row"
                        val isEditing = state.editing == cellId
                        val displayText = if (isEditing) state.raw[cellId] ?: "" else display[cellId] ?: ""

                        Box(
                            modifier = Modifier.width(cellWidth).height(cellHeight).then(cellBorder).padding(2.dp)
                        ) {
                            BasicTextField(
                                value = displayText,
                                onValueChange = { vm.cellEdited.send(cellId to it) },
                                singleLine = true,
                                textStyle = cellTextStyle,
                                modifier = Modifier
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) vm.cellFocused.send(cellId)
                                        else if (state.editing == cellId) vm.cellBlurred.send(Unit)
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun main() {
    application {
        val windowState = rememberWindowState(width = 530.dp, height = 400.dp)
        Window(onCloseRequest = ::exitApplication, state = windowState, title = "Cells") {
            Cells.View()
        }
    }
}
