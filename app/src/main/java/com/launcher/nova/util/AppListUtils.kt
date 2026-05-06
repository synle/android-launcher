package com.launcher.nova.util

import com.launcher.nova.model.AppInfo

/**
 * Pure-Kotlin filter/sort helpers for app lists. Does NOT touch any Android framework
 * APIs so it can be unit-tested on the JVM without Robolectric or instrumentation.
 *
 * Wired into [com.launcher.nova.LauncherActivity] / [com.launcher.nova.viewmodel.LauncherViewModel]
 * if/when search and sorting are surfaced in the UI.
 */
object AppListUtils {

    /**
     * Returns apps whose label contains [query] (case-insensitive).
     * A blank query returns the original list unchanged.
     */
    fun filterByLabel(apps: List<AppInfo>, query: String): List<AppInfo> {
        val q = query.trim()
        if (q.isEmpty()) return apps
        val lower = q.lowercase()
        return apps.filter { it.label.lowercase().contains(lower) }
    }

    /**
     * Returns apps sorted alphabetically by label (case-insensitive). Stable across
     * already-sorted input. Empty/blank labels sink to the end.
     */
    fun sortAlphabetical(apps: List<AppInfo>): List<AppInfo> {
        return apps.sortedWith(
            compareBy(
                { it.label.isBlank() },
                { it.label.lowercase() },
                { it.packageName }
            )
        )
    }

    /**
     * Convenience: filter then sort. Single pass for the UI: search-as-you-type → render.
     */
    fun filterAndSort(apps: List<AppInfo>, query: String): List<AppInfo> {
        return sortAlphabetical(filterByLabel(apps, query))
    }
}
