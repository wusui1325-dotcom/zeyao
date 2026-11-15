package com.wusper.zeyao.data

import com.wusper.zeyao.data.BrowserHandoff.DefaultBrowserRequestStep
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserHandoffTest {
    @Test
    fun plan_requestsRoleThenFallbacks_whenRoleAvailableAndNotHeld() {
        assertEquals(
            listOf(
                DefaultBrowserRequestStep.REQUEST_ROLE,
                DefaultBrowserRequestStep.MANAGE_DEFAULT_APPS,
                DefaultBrowserRequestStep.APP_OPEN_BY_DEFAULT,
                DefaultBrowserRequestStep.APPLICATION_DETAILS,
            ),
            BrowserHandoff.planDefaultBrowserRequest(
                sdkInt = 34,
                roleAvailable = true,
                roleHeld = false,
            ),
        )
    }

    @Test
    fun plan_skipsRoleRequest_whenAlreadyHeld() {
        assertEquals(
            listOf(
                DefaultBrowserRequestStep.MANAGE_DEFAULT_APPS,
                DefaultBrowserRequestStep.APP_OPEN_BY_DEFAULT,
                DefaultBrowserRequestStep.APPLICATION_DETAILS,
            ),
            BrowserHandoff.planDefaultBrowserRequest(
                sdkInt = 34,
                roleAvailable = true,
                roleHeld = true,
            ),
        )
    }

    @Test
    fun plan_skipsRoleRequest_whenRoleUnavailable() {
        assertEquals(
            listOf(
                DefaultBrowserRequestStep.MANAGE_DEFAULT_APPS,
                DefaultBrowserRequestStep.APP_OPEN_BY_DEFAULT,
                DefaultBrowserRequestStep.APPLICATION_DETAILS,
            ),
            BrowserHandoff.planDefaultBrowserRequest(
                sdkInt = 34,
                roleAvailable = false,
                roleHeld = false,
            ),
        )
    }

    @Test
    fun plan_omitsOpenByDefault_beforeApi31() {
        assertEquals(
            listOf(
                DefaultBrowserRequestStep.REQUEST_ROLE,
                DefaultBrowserRequestStep.MANAGE_DEFAULT_APPS,
                DefaultBrowserRequestStep.APPLICATION_DETAILS,
            ),
            BrowserHandoff.planDefaultBrowserRequest(
                sdkInt = 30,
                roleAvailable = true,
                roleHeld = false,
            ),
        )
    }

    @Test
    fun plan_usesSettingsFallbacks_beforeRoleManager() {
        assertEquals(
            listOf(
                DefaultBrowserRequestStep.MANAGE_DEFAULT_APPS,
                DefaultBrowserRequestStep.APPLICATION_DETAILS,
            ),
            BrowserHandoff.planDefaultBrowserRequest(
                sdkInt = 28,
                roleAvailable = false,
                roleHeld = false,
            ),
        )
    }
}
