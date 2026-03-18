package org.xiyu.fxxklocation

import android.location.Location
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Modifier

// ============================================================
//  BACKUP: Hook UsageStatsManager.queryUsageStats
//  Returns EMPTY list to blind foreground detection.
// ============================================================
internal fun ModuleMain.installUsageStatsHook() {
    try {
        val usmClass = android.app.usage.UsageStatsManager::class.java
        XposedHelpers.findAndHookMethod(
            usmClass,
            "queryUsageStats",
            Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val trace = android.util.Log.getStackTraceString(Throwable())
                        if (trace.contains("androidx.appcompat.view.widget")) {
                            param.result = ArrayList<android.app.usage.UsageStats>()
                        }
                    } catch (_: Throwable) {}
                }
            })
        log("[SYS] UsageStatsManager.queryUsageStats hooked (conditional empty list)")
    } catch (e: Throwable) {
        log("[SYS] UsageStats hook FAILED: ${e.message}")
    }

    // Clear stale cachedResult in ForegroundDetect (if accessible)
    try {
        val fdClass = Class.forName("androidx.appcompat.view.widget.\u0EAB")
        // Find static List<String> field (cachedResult is the only one)
        val cachedField = fdClass.declaredFields.firstOrNull {
            Modifier.isStatic(it.modifiers) && it.type == List::class.java
        }
        if (cachedField != null) {
            cachedField.isAccessible = true
            cachedField.set(null, null)
            log("[SYS] ForegroundDetect.cachedResult cleared (${cachedField.name})")
        }
    } catch (e: Throwable) {
        log("[SYS] ForegroundDetect cache clear: ${e.javaClass.simpleName}")
    }
}

// Framework-level fallback: prevent test provider removal
internal fun ModuleMain.installFrameworkFallbackHooks() {
    try {
        XposedHelpers.findAndHookMethod(
            android.location.LocationManager::class.java,
            "removeTestProvider",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        if (bypassRemoveProvider.get() == true) return
                        val provider = param.args[0] as? String
                        if (provider == "gps" || provider == "network") {
                            param.result = null
                        }
                    } catch (_: Throwable) {}
                }
            })
        log("[SYS-FB] removeTestProvider hooked")
    } catch (e: Throwable) {
        log("[SYS-FB] removeTestProvider FAILED: ${e.message}")
    }
    try {
        XposedHelpers.findAndHookMethod(
            android.location.LocationManager::class.java,
            "clearTestProviderLocation",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val provider = param.args[0] as? String
                        if (provider == "gps" || provider == "network") {
                            param.result = null
                        }
                    } catch (_: Throwable) {}
                }
            })
        log("[SYS-FB] clearTestProviderLocation hooked")
    } catch (e: Throwable) {
        log("[SYS-FB] clearTestProviderLocation FAILED: ${e.message}")
    }
}

// ============================================================
//  Strip mock flag at framework level (system_server).
// ============================================================
internal fun ModuleMain.installMockFlagStrip() {
    // Strategy 1: Hook Location.writeToParcel to clear mock flag before sending to apps
    try {
        XposedHelpers.findAndHookMethod(
            Location::class.java, "writeToParcel",
            android.os.Parcel::class.java, Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val loc = param.thisObject as Location
                        stripMockFlag(loc)
                    } catch (_: Throwable) {}
                }
            })
        log("[SYS-MOCK] writeToParcel hook installed")
    } catch (e: Throwable) {
        log("[SYS-MOCK] writeToParcel hook FAILED: $e")
    }

    // Strategy 2: Prevent mock flag from being set at all
    for (methodName in arrayOf("setMock", "setIsFromMockProvider")) {
        try {
            XposedHelpers.findAndHookMethod(
                Location::class.java, methodName,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = false
                    }
                })
            log("[SYS-MOCK] $methodName hook installed")
        } catch (_: Throwable) {}
    }
}

internal fun stripMockFlag(loc: Location) {
    for (fieldName in arrayOf("mIsMock", "mIsFromMockProvider")) {
        try {
            val f = Location::class.java.getDeclaredField(fieldName)
            f.isAccessible = true
            f.setBoolean(loc, false)
        } catch (_: Throwable) {}
    }
    try { loc.extras?.remove("mockProvider") } catch (_: Throwable) {}
}
