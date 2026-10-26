package dev.conferenceplanner.acceptance

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import dev.conferenceplanner.IntegrationTestBase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/** Acceptance check for exercise 1: the checkbox in the served UI drives the filter. */
@Tag("ui")
class AvailableOnlyUiTest : IntegrationTestBase() {

    @Test
    fun `checkbox hides the full session and unchecking shows it again`() {
        Playwright.create().use { playwright ->
            val channel = System.getProperty("ui.browserChannel", "chrome")
            val options = BrowserType.LaunchOptions().setHeadless(true)
            if (channel != "chromium") options.setChannel(channel)
            playwright.chromium().launch(options).use { browser ->
                val page = browser.newPage(Browser.NewPageOptions().setViewportSize(1280, 900))
                page.navigate("http://localhost:$port/")
                assertThat(page.locator("#sessions tbody tr")).hasCount(3)

                val checkbox = page.locator("input[type=checkbox]#available-only")
                assertThat(checkbox).hasCount(1)
                assertThat(checkbox).not().isChecked()
                checkbox.check()
                assertThat(page.locator("#sessions tbody tr")).hasCount(2)
                assertThat(page.locator("#sessions tbody tr[data-session-id='3']")).hasCount(0)
                checkbox.uncheck()
                assertThat(page.locator("#sessions tbody tr")).hasCount(3)
            }
        }
    }
}
