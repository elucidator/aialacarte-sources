package dev.conferenceplanner

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Locator
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Paths

/** Drives the served HTML with a real browser. Run with: ./gradlew uiTest  (-Pbrowser=chromium to use a Playwright-managed browser) */
@Tag("ui")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UiFlowTest : IntegrationTestBase() {

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private val screenshots = Paths.get(System.getProperty("ui.screenshotDir", "build/ui-screenshots"))

    @BeforeAll
    fun openBrowser() {
        playwright = Playwright.create()
        val channel = System.getProperty("ui.browserChannel", "chrome")
        val options = BrowserType.LaunchOptions().setHeadless(true)
        if (channel != "chromium") options.setChannel(channel)
        browser = playwright.chromium().launch(options)
        screenshots.toFile().mkdirs()
    }

    @AfterAll
    fun closeBrowser() {
        browser.close()
        playwright.close()
    }

    private fun newPage(): Page {
        val page = browser.newPage(Browser.NewPageOptions().setViewportSize(1280, 900))
        page.navigate("http://localhost:$port/")
        page.selectOption("#identity", "alice")
        return page
    }

    private fun sessionRow(page: Page, id: Long) = page.locator("#sessions tbody tr[data-session-id='$id']")

    private fun shot(page: Page, name: String) = page.screenshot(Page.ScreenshotOptions().setPath(screenshots.resolve("$name.png")).setFullPage(true))

    @Test
    fun `session list shows the seed data with a Book action`() {
        val page = newPage()
        assertThat(page.locator("#sessions tbody tr")).hasCount(3)
        assertThat(sessionRow(page, 1)).containsText("Debugging Distributed Systems")
        assertThat(sessionRow(page, 1).locator("td")).hasText(arrayOf("Debugging Distributed Systems", "Room A", "30", "22", "8", "Book"))
        assertThat(sessionRow(page, 2).locator("td")).hasText(arrayOf("Practical Kotlin", "Room B", "20", "19", "1", "Book"))
        assertThat(sessionRow(page, 3).locator("td")).hasText(arrayOf("Understanding Databases", "Room C", "40", "40", "0", "Book"))
        assertThat(sessionRow(page, 3).getByRole(AriaRole.BUTTON)).isDisabled()
        assertThat(page.locator("#bookings tbody")).containsText("No active bookings")
        shot(page, "01-session-list")
        page.close()
    }

    @Test
    fun `book, see it under My bookings, then cancel`() {
        val page = newPage()
        sessionRow(page, 2).getByRole(AriaRole.BUTTON, Locator.GetByRoleOptions().setName("Book")).click()
        assertThat(page.locator("#status")).hasClass("success")
        assertThat(page.locator("#status")).containsText("Booked 'Practical Kotlin'")
        assertThat(sessionRow(page, 2).locator("td").nth(3)).hasText("20")
        assertThat(sessionRow(page, 2).locator("td").nth(4)).hasText("0")
        assertThat(page.locator("#bookings tbody tr")).hasCount(1)
        assertThat(page.locator("#bookings tbody tr").first()).containsText("Practical Kotlin")
        shot(page, "02-booked")

        page.locator("#bookings tbody tr").first().getByRole(AriaRole.BUTTON, Locator.GetByRoleOptions().setName("Cancel")).click()
        assertThat(page.locator("#status")).hasClass("success")
        assertThat(page.locator("#status")).containsText("Cancelled booking")
        assertThat(page.locator("#bookings tbody")).containsText("No active bookings")
        assertThat(sessionRow(page, 2).locator("td").nth(4)).hasText("1")
        shot(page, "03-cancelled")
        page.close()
    }

    @Test
    fun `error state is visible when the backend rejects a booking`() {
        // Bob already holds a place in session 1; a second booking is rejected by the backend.
        http.book("bob", 1, "ui-test-bob-session-1")
        val page = newPage()
        page.selectOption("#identity", "bob")
        assertThat(page.locator("#bookings tbody tr")).hasCount(1)
        sessionRow(page, 1).getByRole(AriaRole.BUTTON, Locator.GetByRoleOptions().setName("Book")).click()
        assertThat(page.locator("#status")).hasClass("error")
        assertThat(page.locator("#status")).containsText("ALREADY_BOOKED")
        shot(page, "04-error-state")
        page.close()
    }

    @Test
    fun `loading state is shown while requests are in flight`() {
        val page = browser.newPage()
        page.route("**/api/sessions*") { route -> Thread.sleep(700); route.resume() }
        page.navigate("http://localhost:$port/")
        assertThat(page.locator("#status")).hasClass("loading")
        assertThat(page.locator("#status")).hasText("Loading…")
        shot(page, "05-loading-state")
        assertThat(page.locator("#sessions tbody tr")).hasCount(3)
        page.close()
    }

    @Test
    fun `demo panel shows confirmations, attempts and gateway acceptances`() {
        http.book("alice", 1, "ui-test-alice-session-1")
        val page = browser.newPage(Browser.NewPageOptions().setViewportSize(1280, 900))
        page.navigate("http://localhost:$port/demo.html")
        assertThat(page.locator("#confirmations")).containsText("PENDING")
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Gateway: simulate lost acknowledgement")).click()
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Dispatch pending confirmations")).click()
        assertThat(page.locator("#attempts")).containsText("TIMEOUT")
        assertThat(page.locator("#gateway tbody tr, #gateway tr")).hasCount(1)
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Gateway: restore acknowledgements")).click()
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Dispatch pending confirmations")).click()
        assertThat(page.locator("#attempts")).containsText("DUPLICATE_ACKNOWLEDGED")
        assertThat(page.locator("#confirmations")).containsText("DELIVERED")
        assertThat(page.locator("#gateway tr")).hasCount(1)
        shot(page, "06-demo-panel-deduplicated-retry")
        page.close()
    }
}
