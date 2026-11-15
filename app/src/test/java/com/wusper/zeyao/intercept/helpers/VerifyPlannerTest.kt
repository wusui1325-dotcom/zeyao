package com.wusper.zeyao.intercept.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class VerifyPlannerTest {
    private fun base(
        appUiVisible: Boolean = false,
        armed: Boolean = true,
        targeted: Boolean = true,
        skipped: Boolean = false,
        cooled: Boolean = false,
        classifiable: Boolean = true,
        interceptAllPages: Boolean = false,
        askBeforeSummary: Boolean = false,
        cachedNews: Boolean? = null,
    ) = VerifyPlanInput(
        appUiVisible = appUiVisible,
        armed = armed,
        targeted = targeted,
        skipped = skipped,
        cooled = cooled,
        classifiable = classifiable,
        interceptAllPages = interceptAllPages,
        askBeforeSummary = askBeforeSummary,
        cachedNews = cachedNews,
    )

    @Test
    fun uncachedClassifiable_classifies() {
        assertEquals(VerifyPlan.CLASSIFY, VerifyPlanner.plan(base()))
    }

    @Test
    fun cachedNews_summarizesWithoutClassify() {
        assertEquals(VerifyPlan.SUMMARIZE, VerifyPlanner.plan(base(cachedNews = true)))
    }

    @Test
    fun cachedNotNews_aborts() {
        assertEquals(VerifyPlan.ABORT, VerifyPlanner.plan(base(cachedNews = false)))
    }

    @Test
    fun interceptAll_doesNotClassify() {
        assertEquals(VerifyPlan.SUMMARIZE, VerifyPlanner.plan(base(interceptAllPages = true)))
        assertEquals(
            VerifyPlan.ASK,
            VerifyPlanner.plan(base(interceptAllPages = true, askBeforeSummary = true)),
        )
    }

    @Test
    fun appUiOrDisarmedOrSkippedOrCooled_aborts() {
        assertEquals(VerifyPlan.ABORT, VerifyPlanner.plan(base(appUiVisible = true)))
        assertEquals(VerifyPlan.ABORT, VerifyPlanner.plan(base(armed = false)))
        assertEquals(VerifyPlan.ABORT, VerifyPlanner.plan(base(targeted = false)))
        assertEquals(VerifyPlan.ABORT, VerifyPlanner.plan(base(skipped = true)))
        assertEquals(VerifyPlan.ABORT, VerifyPlanner.plan(base(cooled = true)))
        assertEquals(VerifyPlan.ABORT, VerifyPlanner.plan(base(classifiable = false)))
    }
}
