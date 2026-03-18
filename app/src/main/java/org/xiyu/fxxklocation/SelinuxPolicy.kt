package org.xiyu.fxxklocation

@Volatile
internal var selinuxPolicyPatched = false

/**
 * Apply targeted SELinux policy rules so ServiceManager add/find works
 * for our custom service names. SELinux stays Enforcing — only adds
 * specific allow rules.
 *
 * Auto-detects root manager:
 *   KernelSU  → ksud sepolicy patch
 *   Magisk    → magiskpolicy --live
 *   APatch    → apd sepolicy patch  (same syntax as ksud)
 *   Fallback  → setenforce 0 as last resort
 */
internal fun applySELinuxPolicy(): Boolean {
    if (selinuxPolicyPatched) return true
    synchronized(ModuleMain::class.java) {
        if (selinuxPolicyPatched) return true
        try {
            // Rules needed:
            // 1. system_server can add services of type default_android_service
            // 2. any app domain can find services of type default_android_service
            val rules = listOf(
                "allow system_server default_android_service service_manager { add find }",
                "allow * default_android_service service_manager { find }"
            )

            // Detect root manager and build commands
            val policyTool = detectPolicyTool()
            if (policyTool != null) {
                for (rule in rules) {
                    val cmd = policyTool.buildCommand(rule)
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                    val exit = p.waitFor()
                    if (exit != 0) {
                        val err = p.errorStream.bufferedReader().readText()
                        log("[SEPOL] rule failed (exit=$exit): $cmd -> $err")
                    }
                }
                selinuxPolicyPatched = true
                log("[SEPOL] policy patched via ${policyTool.name} (SELinux stays Enforcing)")
                return true
            }

            // No policy tool found — try magiskpolicy in PATH as last attempt
            var anySuccess = false
            for (rule in rules) {
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "magiskpolicy --live \"$rule\""))
                    val exit = p.waitFor()
                    if (exit == 0) anySuccess = true
                } catch (_: Throwable) {}
            }
            if (anySuccess) {
                selinuxPolicyPatched = true
                log("[SEPOL] policy patched via generic magiskpolicy fallback")
                return true
            }
            log("[SEPOL] no policy tool found and fallback failed — SELinux may block service registration")
            return false
        } catch (e: Throwable) {
            log("[SEPOL] policy patch failed: $e")
            return false
        }
    }
}

internal data class PolicyTool(val name: String, val buildCommand: (String) -> String)

internal fun detectPolicyTool(): PolicyTool? {
    // KernelSU: ksud sepolicy patch "<rule>"
    for (path in arrayOf("/data/adb/ksu/bin/ksud", "/data/adb/ksud")) {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -x $path && echo ok"))
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            if (out == "ok") {
                return PolicyTool("ksud") { rule -> "$path sepolicy patch \"$rule\"" }
            }
        } catch (_: Throwable) {}
    }
    // APatch: apd sepolicy patch "<rule>"
    for (path in arrayOf("/data/adb/ap/bin/apd", "/data/adb/apd")) {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -x $path && echo ok"))
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            if (out == "ok") {
                return PolicyTool("apd") { rule -> "$path sepolicy patch \"$rule\"" }
            }
        } catch (_: Throwable) {}
    }
    // Magisk: magiskpolicy --live "<rule>"
    try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "which magiskpolicy"))
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        if (out.isNotEmpty()) {
            return PolicyTool("magiskpolicy") { rule -> "magiskpolicy --live \"$rule\"" }
        }
    } catch (_: Throwable) {}
    return null
}
