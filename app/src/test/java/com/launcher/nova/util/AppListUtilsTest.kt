package com.launcher.nova.util

import com.launcher.nova.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import android.graphics.drawable.Drawable

class AppListUtilsTest {

    private fun app(label: String, pkg: String = "com.example.${label.lowercase().filter { it.isLetterOrDigit() }}"): AppInfo {
        // Drawable is mocked — we never read it in these tests.
        return AppInfo(packageName = pkg, label = label, icon = mock(Drawable::class.java), activityName = "$pkg.MainActivity")
    }

    // ---------- filterByLabel ----------

    @Test
    fun `filterByLabel returns full list when query is blank`() {
        val apps = listOf(app("Calendar"), app("Camera"), app("Chrome"))
        assertEquals(apps, AppListUtils.filterByLabel(apps, ""))
        assertEquals(apps, AppListUtils.filterByLabel(apps, "   "))
    }

    @Test
    fun `filterByLabel matches case-insensitively`() {
        val apps = listOf(app("Calendar"), app("Camera"), app("Chrome"))
        val result = AppListUtils.filterByLabel(apps, "ca")
        assertEquals(2, result.size)
        assertTrue(result.any { it.label == "Calendar" })
        assertTrue(result.any { it.label == "Camera" })
    }

    @Test
    fun `filterByLabel matches substrings anywhere in label`() {
        val apps = listOf(app("Google Calendar"), app("Outlook Calendar"), app("Slack"))
        val result = AppListUtils.filterByLabel(apps, "calendar")
        assertEquals(2, result.size)
    }

    @Test
    fun `filterByLabel returns empty list when nothing matches`() {
        val apps = listOf(app("Calendar"), app("Camera"))
        assertTrue(AppListUtils.filterByLabel(apps, "spotify").isEmpty())
    }

    @Test
    fun `filterByLabel trims surrounding whitespace`() {
        val apps = listOf(app("Calendar"), app("Camera"))
        val result = AppListUtils.filterByLabel(apps, "  calendar  ")
        assertEquals(1, result.size)
        assertEquals("Calendar", result[0].label)
    }

    // ---------- sortAlphabetical ----------

    @Test
    fun `sortAlphabetical orders labels case-insensitively`() {
        val apps = listOf(app("Zoom"), app("calendar"), app("Camera"), app("Bitwarden"))
        val sorted = AppListUtils.sortAlphabetical(apps).map { it.label }
        assertEquals(listOf("Bitwarden", "calendar", "Camera", "Zoom"), sorted)
    }

    @Test
    fun `sortAlphabetical preserves order for identical labels (stable by package name)`() {
        val a = app("Photos", "com.google.android.apps.photos")
        val b = app("Photos", "com.samsung.android.app.photos")
        val sorted = AppListUtils.sortAlphabetical(listOf(b, a)).map { it.packageName }
        assertEquals(
            listOf("com.google.android.apps.photos", "com.samsung.android.app.photos"),
            sorted
        )
    }

    @Test
    fun `sortAlphabetical sinks blank-label apps to the end`() {
        val good = app("Chrome")
        val blank = app("", pkg = "com.broken.app")
        val sorted = AppListUtils.sortAlphabetical(listOf(blank, good))
        assertEquals("Chrome", sorted[0].label)
        assertEquals("", sorted[1].label)
    }

    @Test
    fun `sortAlphabetical returns empty list for empty input`() {
        assertTrue(AppListUtils.sortAlphabetical(emptyList()).isEmpty())
    }

    // ---------- filterAndSort ----------

    @Test
    fun `filterAndSort filters then sorts in one pass`() {
        val apps = listOf(app("Chrome"), app("Calendar"), app("Camera"), app("Slack"))
        val result = AppListUtils.filterAndSort(apps, "ca").map { it.label }
        assertEquals(listOf("Calendar", "Camera"), result)
    }

    @Test
    fun `filterAndSort empty query returns all apps sorted`() {
        val apps = listOf(app("Zoom"), app("Bitwarden"), app("Chrome"))
        val result = AppListUtils.filterAndSort(apps, "").map { it.label }
        assertEquals(listOf("Bitwarden", "Chrome", "Zoom"), result)
    }
}
