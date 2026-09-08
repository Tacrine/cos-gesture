package com.cos.lspit.gesture.config

/**
 * Immutable snapshot of the runtime veto switches and gesture-bar options.
 *
 * The three veto switches (master/left/right) keep the proven fail-open
 * default: interception stays active unless an explicit 0 arrives.
 *
 * [mbackEnabled] is deliberately fail-closed: it defaults to false for any
 * missing/unparseable value, because enabling mBack means actively taking
 * over Back/Home injection and must never happen by accident.
 *
 * [barWidthDp] is nullable; null means "leave the system hint-bar width
 * untouched" (this is also the fail-closed path for a missing column), while
 * an in-range value customises the bar length.
 *
 * [barOnlyEnabled] is fail-closed: when true, the system bottom gestures
 * (home / recents / app-switcher) only fire when the finger first lands on
 * the hint bar, shrinking the trigger zone to reduce accidental swipes.
 */
data class GestureConfig(
    val masterEnabled: Boolean = true,
    val leftEnabled: Boolean = true,
    val rightEnabled: Boolean = true,
    val mbackEnabled: Boolean = false,
    val barWidthDp: Int? = null,
    val barOnlyEnabled: Boolean = false,
    val hintTapShieldEnabled: Boolean = false,
        val barHiddenEnabled: Boolean = false,
        val version: Int = 4,
    ) {
        companion object {
            /** Accepted customisable hint-bar width range, in dp. */
            const val BAR_WIDTH_MIN_DP = 40
            const val BAR_WIDTH_MAX_DP = 160
            const val BAR_WIDTH_DEFAULT_DP = 100

            /**
             * Parses provider cursor cells. Only an explicit 0 disables a veto
             * switch; null (missing column) and any other value fail back to
             * enabled. mback and barOnly are fail-closed (null/non-1 => false).
             * barWidthDp only binds when present and within range, otherwise null.
             */
            fun fromValues(
                master: Int?,
                left: Int?,
                right: Int?,
                mback: Int?,
                barWidthDp: Int?,
                barOnly: Int?,
                hintTapShield: Int?,
                barHidden: Int?,
                version: Int?,
            ): GestureConfig =
                GestureConfig(
                    masterEnabled = master != 0,
                    leftEnabled = left != 0,
                    rightEnabled = right != 0,
                    mbackEnabled = mback == 1,
                    barWidthDp = barWidthDp
                        ?.takeIf { it in BAR_WIDTH_MIN_DP..BAR_WIDTH_MAX_DP },
                    barOnlyEnabled = barOnly == 1,
                    hintTapShieldEnabled = hintTapShield == 1,
                    barHiddenEnabled = barHidden == 1,
                    version = version ?: 4,
                )
        }
    }
