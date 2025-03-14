package io.github.sintrastes.yafrl.annotations

@RequiresOptIn(
    message = "This API is fragile and only intended for low-level interop with other frameworks.",
    level = RequiresOptIn.Level.ERROR
)
annotation class FragileYafrlAPI