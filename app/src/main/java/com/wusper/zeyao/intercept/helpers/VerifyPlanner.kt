package com.wusper.zeyao.intercept.helpers

/**
 * Pure decision for the verify / classify hot path. No I/O.
 * Handoff vs silent abort is left to the caller ([handoffIfNotNews]).
 */
enum class VerifyPlan {
    /** Not armed, skipped, cooled, not classifiable, cached not-news, or app UI in front. */
    ABORT,
    /** Intercept-all + ask-before-summary. */
    ASK,
    /** Cached news, or intercept-all without asking — open the 擇要 page. */
    SUMMARIZE,
    /** Show the 驗證中 float and classify with the flash model. */
    CLASSIFY,
}

data class VerifyPlanInput(
    val appUiVisible: Boolean,
    val armed: Boolean,
    val targeted: Boolean,
    val skipped: Boolean,
    val cooled: Boolean,
    val classifiable: Boolean,
    val interceptAllPages: Boolean,
    val askBeforeSummary: Boolean,
    val cachedNews: Boolean?,
)

object VerifyPlanner {
    fun plan(input: VerifyPlanInput): VerifyPlan {
        if (input.appUiVisible) return VerifyPlan.ABORT
        if (!input.armed || !input.targeted) return VerifyPlan.ABORT
        if (input.skipped) return VerifyPlan.ABORT
        if (input.interceptAllPages) {
            if (input.cooled) return VerifyPlan.ABORT
            return if (input.askBeforeSummary) VerifyPlan.ASK else VerifyPlan.SUMMARIZE
        }
        if (!input.classifiable || input.cooled) return VerifyPlan.ABORT
        return when (input.cachedNews) {
            true -> VerifyPlan.SUMMARIZE
            false -> VerifyPlan.ABORT
            null -> VerifyPlan.CLASSIFY
        }
    }
}

/**
 * Result of the in-flight classify job, including the URL after any
 * shortener resolve so the 擇要 page / handoff uses the final article.
 *
 * [news] is true (open 擇要), false (mark not-news and hand off), or
 * null (fail-open / pass through without poisoning the cache).
 */
data class VerifyOutcome(
    val url: String,
    val news: Boolean?,
)
