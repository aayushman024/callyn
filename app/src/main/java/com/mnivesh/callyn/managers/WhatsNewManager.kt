package com.mnivesh.callyn.managers

import android.content.Context
import android.content.SharedPreferences
import com.mnivesh.callyn.api.version

/**
 * Represents an individual feature or improvement item in a release.
 */
data class WhatsNewFeature(
    val title: String,
    val description: String
)

/**
 * Represents release notes for a specific app version.
 *
 * @property versionName The version name string matching app version (e.g., "2.4.0").
 * @property title Heading for this release notes sheet/dialog.
 * @property features List of feature highlight points for this version.
 * @property skipPopup When true, suppresses the automatic popup for this version.
 */
data class WhatsNewVersion(
    val versionName: String,
    val title: String = "What's New in v$versionName",
    val features: List<WhatsNewFeature>,
    val skipPopup: Boolean = false
)

/**
 * Service/Manager responsible for managing What's New release notes and popup display logic.
 * Ensures the popup is shown exactly once per new version unless `skipPopup` is enabled.
 */
class WhatsNewManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "WhatsNewPrefs"
        private const val KEY_LAST_SEEN_VERSION = "last_seen_version"

        /**
         * Hardcoded list of release notes across versions.
         * To skip showing the popup for a specific version, set `skipPopup = true`.
         */
        val RELEASES = listOf(
            WhatsNewVersion(
                versionName = "2.4.0",
                title = "What's New in v2.4.0",
                features = listOf(
                    WhatsNewFeature(
                        title = "Hands-Free Calling",
                        description = "Use seamless calling from the Operations Dashboard in just a click."
                    ),
                    WhatsNewFeature(
                        title = "Favourite Work Contacts",
                        description = "You can now mark you work contacts as favourite for a quicker access."
                    ),
                    WhatsNewFeature(
                        title = "In-Call Indicator",
                        description = "Now you can see a live in-call indicator in app during your calls, tapping which you can easily switch to the call screen."
                    ),
                    WhatsNewFeature(
                        title = "Under the Hood Improvements",
                        description = "Various performance improvements and bug fixes under the hood."
                    ),
                ),
                skipPopup = false
            ),
        )
    }

    /**
     * Retrieves the release notes for the specified version name, or null if not found.
     */
    fun getWhatsNewForVersion(versionName: String = version): WhatsNewVersion? {
        return RELEASES.firstOrNull { it.versionName == versionName }
    }

    /**
     * Retrieves the latest release notes available in the registry.
     */
    fun getLatestWhatsNew(): WhatsNewVersion? {
        return RELEASES.firstOrNull()
    }

    /**
     * Checks whether the What's New popup should be displayed for the given version.
     *
     * Returns true if:
     * 1. Release notes exist for [currentVersion].
     * 2. [WhatsNewVersion.skipPopup] is false.
     * 3. The user has not already seen the popup for [currentVersion].
     */
    fun shouldShowWhatsNew(currentVersion: String = version): Boolean {
        val lastSeenVersion = prefs.getString(KEY_LAST_SEEN_VERSION, null)

        // Already seen this version's What's New
        if (lastSeenVersion == currentVersion) {
            return false
        }

        val release = getWhatsNewForVersion(currentVersion)

        // If no notes exist or skipPopup is true, mark as seen and don't show
        if (release == null || release.skipPopup) {
            markWhatsNewSeen(currentVersion)
            return false
        }

        return true
    }

    /**
     * Marks the current (or specified) version as seen so the popup is not shown again.
     */
    fun markWhatsNewSeen(currentVersion: String = version) {
        prefs.edit().putString(KEY_LAST_SEEN_VERSION, currentVersion).apply()
    }
}
