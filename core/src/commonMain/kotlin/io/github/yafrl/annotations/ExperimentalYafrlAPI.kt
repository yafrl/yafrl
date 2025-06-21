package io.github.yafrl.annotations

@RequiresOptIn(
    message = "This API is experimental and should be used with caution.",
    level = RequiresOptIn.Level.ERROR
)
annotation class ExperimentalYafrlAPI