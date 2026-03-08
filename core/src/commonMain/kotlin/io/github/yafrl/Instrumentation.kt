package io.github.yafrl

import kotlinx.serialization.Serializable

@Serializable
data class CallSite(
    val file: String?,
    val line: Int,
    val className: String,
    val method: String
)

/**
 * Get access to the call-site of where in the user's application yafrl code
 *  was invoked from. Used for debugging purposes.
 *
 * Currently only works on JVM. Returns null on other platforms.
 **/
expect fun caller(): CallSite?