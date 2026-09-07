package com.cos.lspit.gesture.config

/**
 * Immutable snapshot of the runtime veto switches. Defaults keep the proven
 * interception behavior (all enabled) when no configuration exists yet.
 */
data class GestureConfig(
    val masterEnabled: Boolean = true,
    val leftEnabled: Boolean = true,
    val rightEnabled: Boolean = true,
    val version: Int = 1,
) {
    companion object {
        /**
         * Parses provider cursor cells. Only an explicit 0 disables a switch;
         * null (missing column) and any other value fail back to enabled.
         */
        fun fromValues(master: Int?, left: Int?, right: Int?, version: Int?): GestureConfig =
            GestureConfig(
                masterEnabled = master != 0,
                leftEnabled = left != 0,
                rightEnabled = right != 0,
                version = version ?: 1,
            )
    }
}
