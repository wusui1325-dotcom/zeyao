package com.wusper.zeyao.data

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.wusper.zeyao.BuildConfig

object BrowserHandoff {

    fun isZeyaoDefaultBrowser(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                return roles.isRoleHeld(RoleManager.ROLE_BROWSER)
            }
        }
        val current = DefaultBrowser.packageName(context)
        return current == BuildConfig.APPLICATION_ID
    }

    enum class DefaultBrowserRequestStep {
        REQUEST_ROLE,
        MANAGE_DEFAULT_APPS,
        APP_OPEN_BY_DEFAULT,
        APPLICATION_DETAILS,
    }

    /**
     * Build the ordered screens to open when the user taps「設為預設瀏覽器」.
     *
     * [createRequestRoleIntent] is a silent no-op when the role is already held, and
     * the system requires that intent to be started with startActivityForResult.
     * Always keep a fallback so the tap opens a screen where the default can be changed.
     */
    fun planDefaultBrowserRequest(
        sdkInt: Int,
        roleAvailable: Boolean,
        roleHeld: Boolean,
    ): List<DefaultBrowserRequestStep> {
        val steps = ArrayList<DefaultBrowserRequestStep>(4)
        if (sdkInt >= 29 && roleAvailable && !roleHeld) {
            steps.add(DefaultBrowserRequestStep.REQUEST_ROLE)
        }
        if (sdkInt >= 24) {
            steps.add(DefaultBrowserRequestStep.MANAGE_DEFAULT_APPS)
        }
        if (sdkInt >= 31) {
            steps.add(DefaultBrowserRequestStep.APP_OPEN_BY_DEFAULT)
        }
        steps.add(DefaultBrowserRequestStep.APPLICATION_DETAILS)
        return steps
    }

    fun defaultBrowserSettingsIntents(context: Context): List<Pair<DefaultBrowserRequestStep, Intent>> {
        val sdkInt = Build.VERSION.SDK_INT
        var roleAvailable = false
        var roleHeld = false
        if (sdkInt >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null) {
                roleAvailable = roles.isRoleAvailable(RoleManager.ROLE_BROWSER)
                roleHeld = roles.isRoleHeld(RoleManager.ROLE_BROWSER)
            }
        }
        return planDefaultBrowserRequest(sdkInt, roleAvailable, roleHeld).mapNotNull { step ->
            intentFor(context, step)?.let { step to it }
        }
    }

    fun intentFor(context: Context, step: DefaultBrowserRequestStep): Intent? {
        return when (step) {
            DefaultBrowserRequestStep.REQUEST_ROLE -> {
                if (Build.VERSION.SDK_INT < 29) return null
                val roles = context.getSystemService(RoleManager::class.java) ?: return null
                roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            }
            DefaultBrowserRequestStep.MANAGE_DEFAULT_APPS -> {
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }
            DefaultBrowserRequestStep.APP_OPEN_BY_DEFAULT -> {
                if (Build.VERSION.SDK_INT < 31) return null
                Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            DefaultBrowserRequestStep.APPLICATION_DETAILS -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }
    }

    fun listBrowsers(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val flags = PackageManager.MATCH_ALL
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(query, flags)
        }
        val seen = LinkedHashSet<String>()
        val out = ArrayList<Pair<String, String>>()
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == BuildConfig.APPLICATION_ID || !seen.add(pkg)) continue
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            out.add(pkg to label)
        }
        return out.sortedBy { it.second.lowercase() }
    }

    fun resolveHandoffPackage(context: Context, preferred: String?): String? {
        val listed = listBrowsers(context).map { it.first }
        if (!preferred.isNullOrBlank() && preferred != BuildConfig.APPLICATION_ID && preferred in listed) {
            return preferred
        }
        val known = listed.firstOrNull { it in BrowserPackages.KNOWN }
        return known ?: listed.firstOrNull()
    }

    fun open(context: Context, url: String, preferredPackage: String?): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val pkg = resolveHandoffPackage(context, preferredPackage)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (!pkg.isNullOrBlank()) setPackage(pkg)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            if (pkg.isNullOrBlank()) return false
            val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(fallback)
                true
            }.getOrDefault(false)
        }
    }
}
