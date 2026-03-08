package io.github.yafrl


private val walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)

actual fun caller(): CallSite? =
    walker.walk { frames ->
        frames
            .dropWhile { it.className.startsWith("io.github.yafrl") }
            .findFirst()
            .map {
                CallSite(
                    it.fileName,
                    it.lineNumber,
                    it.className,
                    it.methodName
                )
            }
            .get()
    }