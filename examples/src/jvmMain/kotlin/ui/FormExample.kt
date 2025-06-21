package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import arrow.optics.optics
import io.github.yafrl.compose.Form

@optics
data class User(
    val firstName: String,
    val lastName: String
) {
    companion object
}

fun PersonEntry() = Form.build<User> {
    Column {
        StringEntry("First Name:")
            .bind(User.firstName)

        StringEntry("Last Name:")
            .bind(User.lastName)
    }
}

fun StringEntry(label: String) = Form.build<String> {
    var text by currentValue

    Column {
        Text(label)
        TextField(
            value = text,
            onValueChange = { newText ->
                text = newText
            }
        )
    }
}

fun main(args: Array<String>) {
    application {
        val state = rememberWindowState(
            width = 330.dp,
            height = 270.dp
        )

        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Form Test"
        ) {
            PersonEntry().contents(
                User("Bob", "Smith")
            )
        }
    }
}