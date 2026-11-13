package com.example.ai

import java.util.concurrent.atomic.AtomicInteger

/** True while a heavy on-phone model (Whisper or Gemma) is working, so the 3D drawing and the wake listener step aside. */
object PhoneLoad {
    private val count = AtomicInteger(0)
    val busy: Boolean get() = count.get() > 0
    fun enter() { count.incrementAndGet() }
    fun exit() { count.updateAndGet { if (it > 0) it - 1 else 0 } }
    inline fun <T> during(block: () -> T): T { enter(); try { return block() } finally { exit() } }
}
