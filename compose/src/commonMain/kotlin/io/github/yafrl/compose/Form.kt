package io.github.yafrl.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import arrow.optics.Lens

/**
 * Utility to facilitate the creation of re-usable data entry forms with a strongly-typed
 *  API in Jetpack Compose.
 *
 * A [Form] is basically just a wrapper for a composable function of type
 *  `@Composable (A) -> A` -- which is intended to be the API for a data entry
 *  form where the data being entered corresponds to the immutable class `A`.
 *
 *
 **/
class Form<A>(
    val contents: @Composable (A) -> A
) {
    companion object {
        /**
         * Basic builder for a [Form].
         *
         * To build a basic non-composite [Form] (e.x. something like a text entry field), you
         *  can simply use the [FormScope.currentValue] property in scope of the builder and bind
         *  that to your UI:
         *
         * ```kotlin
         * class StringEntry(val fieldLabel: String): Form<String> by (Form.build {
         *     var text by currentValue
         *
         *     Column {
         *         Text(fieldLabel)
         *         BasicTextField(
         *             value = fieldLabel,
         *             onValueChanged = { newText ->
         *                 text = newText
         *             }
         *         )
         *     }
         * })
         * ```
         *
         * However, one of the main advantages of [Form]s over raw composable functions is the
         *  ability to embed sub-forms when building the entry form for a larger type without
         *  much ceremony.
         *
         * Like most things in Jetpack Compose, [Form] works with immutable data types. We use
         *  the [arrow-optics]() ksp plugin and the `@Optics` annotation to automatically generate
         *  [Lens]es that let us easily reference a "part" of our form. For example, given the following:
         *
         * ```kotlin
         * @Optics
         * data class User(
         *     val firstName: String,
         *     val lastName: String,
         *     val birthday: LocalDate
         * ) {
         *     companion object
         * }
         * ```
         *
         * we can do the following to build a user entry form:
         *
         * ```kotlin
         * class UserEntryForm(): Form<User> by (Form.build {
         *     StringEntry("First name:")
         *         .bind(User.firstName)
         *
         *     StringEntry("Last name:")
         *         .bind(User.lastName)
         *
         *     LocalDateEntry()
         *         .bind(User.birthday)
         * })
         * ```
         *
         * which can then be used like any other composable in the rest of your application
         *  with `.contents()`.
         **/
        fun <A> build(
            contents: @Composable FormScope<A>.() -> Unit
        ): Form<A> = Form { initialValue ->
            val currentValue = remember { mutableStateOf(initialValue) }

            val scope = remember {
                object : FormScope<A> {
                    override val currentValue: MutableState<A>
                        get() = currentValue

                    @Composable
                    override fun <B> Form<B>.bind(lens: Lens<A, B>) {
                        val result = this.contents.invoke(lens.get(currentValue.value))

                        currentValue.value = lens.set(currentValue.value, result)
                    }
                }
            }

            scope.contents()

            currentValue.value
        }
    }
}

interface FormScope<A> {
    /**
     *
     **/
    val currentValue: MutableState<A>

    /**
     * Include a sub-form into the larger form being built with [Form.build].
     **/
    @Composable
    fun <B> Form<B>.bind(lens: Lens<A, B>)
}