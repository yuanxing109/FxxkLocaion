package org.xiyu.fxxklocation

import android.os.IBinder
import android.os.Parcel
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

// ============================================================
//  LAYER 3: Optional per-app hooks (only for apps in LSPosed scope)
//
//  Mock flag stripping and GNSS injection are now handled entirely
//  in system_server — no per-app hook needed for those.
//  This file only contains step sensor spoofing, which requires
//  in-process hook because SensorEvents are dispatched locally.
//
//  Only apps explicitly added to LSPosed scope get step spoofing.
//  For basic GPS/GNSS spoofing, only "system" + FL is needed.
// ============================================================

/**
 * Install optional per-app hooks. Currently only step sensor spoofing.
 * Called for every app in LSPosed scope that isn't FL or system_server.
 */
internal fun ModuleMain.hookAntiMockDetection() {
    hookStepSensorInjection()
}

// ============================================================
//  Step sensor spoofing — Layer 3 (target apps)
//  Intercepts SensorManager.registerListener for TYPE_STEP_COUNTER(19)
//  and TYPE_STEP_DETECTOR(18), feeds fake events matching FL step sim.
// ============================================================
@Volatile
private var stepFeederStarted = false
private val stepListeners = java.util.concurrent.CopyOnWriteArrayList<Any>()
private val stepSensorTypes = java.util.concurrent.ConcurrentHashMap<Any, Int>()

internal fun ModuleMain.hookStepSensorInjection() {
    try {
        val smClass = Class.forName("android.hardware.SensorManager")
        val sensorClass = Class.forName("android.hardware.Sensor")
        val listenerClass = Class.forName("android.hardware.SensorEventListener")

        val captureHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    if (param.throwable != null) return
                    val listener = param.args[0] ?: return
                    val sensor = param.args[1] ?: return
                    if (!listenerClass.isInstance(listener)) return
                    if (!sensorClass.isInstance(sensor)) return
                    val sensorType = sensorClass.getMethod("getType").invoke(sensor) as Int
                    if (sensorType == 19 || sensorType == 18) {
                        if (!stepListeners.contains(listener)) {
                            stepListeners.add(listener)
                            stepSensorTypes[listener] = sensorType
                            log("[STEP-L3] captured step listener type=$sensorType (total=${stepListeners.size})")
                            ensureStepFeederRunning()
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        for (m in smClass.declaredMethods) {
            if (m.name != "registerListener") continue
            val params = m.parameterTypes
            if (params.size < 2) continue
            if (!listenerClass.isAssignableFrom(params[0])) continue
            if (!sensorClass.isAssignableFrom(params[1])) continue
            XposedBridge.hookMethod(m, captureHook)
        }

        try {
            val ssmClass = Class.forName("android.hardware.SystemSensorManager")
            for (m in ssmClass.declaredMethods) {
                if (m.name != "registerListenerImpl") continue
                val params = m.parameterTypes
                if (params.size < 2) continue
                if (!listenerClass.isAssignableFrom(params[0])) continue
                if (!sensorClass.isAssignableFrom(params[1])) continue
                XposedBridge.hookMethod(m, captureHook)
                log("[STEP-L3] also hooked SystemSensorManager.registerListenerImpl")
            }
        } catch (_: Throwable) {}

        for (m in smClass.declaredMethods) {
            if (m.name != "unregisterListener") continue
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val listener = param.args?.firstOrNull() ?: return
                    stepListeners.remove(listener)
                    stepSensorTypes.remove(listener)
                }
            })
        }

        log("[STEP-L3] step sensor hooks installed")
    } catch (e: Throwable) {
        log("[STEP-L3] hookStepSensorInjection failed: $e")
    }
}

private fun ensureStepFeederRunning() {
    if (stepFeederStarted) return
    stepFeederStarted = true

    Thread {
        try {
            val sensorEventCls = Class.forName("android.hardware.SensorEvent")
            val listenerCls = Class.forName("android.hardware.SensorEventListener")
            val onChangedMethod = listenerCls.getMethod("onSensorChanged", sensorEventCls)

            val seCtor = sensorEventCls.getDeclaredConstructor(Int::class.javaPrimitiveType)
            seCtor.isAccessible = true

            val valuesField = sensorEventCls.getField("values")
            val timestampField = sensorEventCls.getField("timestamp")
            val sensorField = sensorEventCls.getField("sensor")
            val accuracyField = sensorEventCls.getField("accuracy")

            val smService = try {
                val atMethod = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                val app = atMethod.invoke(null) as? android.content.Context
                app?.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
            } catch (_: Throwable) { null }

            val stepCounterSensor = smService?.getDefaultSensor(19)
            val stepDetectorSensor = smService?.getDefaultSensor(18)

            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getMethod("getService", String::class.java)

            log("[STEP-L3] feeder started (counter=${stepCounterSensor != null}, detector=${stepDetectorSensor != null})")

            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var localStepAccum = 0L
            var lastInjectTime = 0L

            Thread.sleep(1000)

            while (!Thread.interrupted()) {
                if (stepListeners.isEmpty()) {
                    Thread.sleep(500)
                    continue
                }

                // Query service_fl_ml for mocking status and step speed via binder IPC
                var isMocking = false
                var stepSpeed = 3.0f
                try {
                    val binder = getService.invoke(null, "service_fl_ml") as? IBinder
                    if (binder != null) {
                        val mockData = Parcel.obtain()
                        val mockReply = Parcel.obtain()
                        try {
                            mockData.writeInterfaceToken(FL_AIDL_DESCRIPTOR)
                            binder.transact(22, mockData, mockReply, 0)
                            mockReply.readException()
                            isMocking = mockReply.readInt() != 0
                        } finally {
                            mockData.recycle()
                            mockReply.recycle()
                        }
                        if (isMocking) {
                            val speedData = Parcel.obtain()
                            val speedReply = Parcel.obtain()
                            try {
                                speedData.writeInterfaceToken(FL_AIDL_DESCRIPTOR)
                                binder.transact(15, speedData, speedReply, 0)
                                speedReply.readException()
                                val s = speedReply.readFloat()
                                if (s > 0.1f) stepSpeed = s
                            } finally {
                                speedData.recycle()
                                speedReply.recycle()
                            }
                        }
                    }
                } catch (_: Throwable) {}

                if (!isMocking) {
                    Thread.sleep(500)
                    continue
                }

                val speed = stepSpeed.coerceIn(0.5f, 30.0f)
                val strideLength = 0.7
                val stepsPerSecond = speed / strideLength
                val intervalMs = (1000.0 / stepsPerSecond).toLong().coerceIn(100, 2000)

                val now = System.currentTimeMillis()
                if (lastInjectTime == 0L) lastInjectTime = now
                val elapsed = (now - lastInjectTime) / 1000.0
                val stepsToAdd = (elapsed * stepsPerSecond).toLong()
                if (stepsToAdd > 0) {
                    localStepAccum += stepsToAdd
                    lastInjectTime = now
                }

                for (listener in stepListeners) {
                    val type = stepSensorTypes[listener] ?: 19
                    handler.post {
                        try {
                            val event = seCtor.newInstance(1)
                            val vals = valuesField.get(event) as FloatArray
                            if (type == 19) {
                                vals[0] = localStepAccum.toFloat()
                            } else {
                                vals[0] = 1.0f
                            }
                            timestampField.setLong(event, android.os.SystemClock.elapsedRealtimeNanos())
                            accuracyField.setInt(event, 3)
                            val sRef = if (type == 19) stepCounterSensor else stepDetectorSensor
                            if (sRef != null) sensorField.set(event, sRef)
                            onChangedMethod.invoke(listener, event)
                        } catch (_: Throwable) {}
                    }
                }

                Thread.sleep(intervalMs)
            }
        } catch (_: InterruptedException) {
        } catch (e: Throwable) {
            log("[STEP-L3] feeder error: $e")
        }
    }.apply {
        name = "FL-StepFeed"
        isDaemon = true
        start()
    }
}
