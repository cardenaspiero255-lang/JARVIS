package com.cardenaspiero255.jarvis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalGgufModelLeaseInstrumentedTest {
    @Test fun deletionBlocksUntilVerifiedModelConsumerReleasesItsLease() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalGgufModelStore(context)
        // Simulate an Activity recreation: the deleter has a distinct Store
        // instance while the old Activity still holds a native model lease.
        val recreatedActivityStore = LocalGgufModelStore(context)
        store.deleteModel()
        val model = File(context.filesDir, "private_gguf/selected.gguf")
        model.parentFile!!.mkdirs()
        val bytes = byteArrayOf(71, 71, 85, 70, 3, 0, 0, 0) + ByteArray(64)
        model.writeBytes(bytes)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertTrue(context.getSharedPreferences("jarvis_gguf_v1", 0).edit()
            .putString("sha256", sha).putLong("bytes", bytes.size.toLong()).commit())

        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val deletionCompleted = AtomicBoolean(false)
        val reader = Thread {
            try {
                store.withVerifiedModel { path ->
                    assertNotNull(path)
                    holding.countDown()
                    assertTrue(release.await(6, TimeUnit.SECONDS))
                    assertTrue(path!!.isFile)
                }
            } finally { completed.countDown() }
        }
        val remover = Thread {
            try { deletionCompleted.set(recreatedActivityStore.deleteModel()) }
            catch (_: Exception) { /* verified by assertion below */ }
        }
        try {
            reader.start()
            assertTrue(holding.await(5, TimeUnit.SECONDS))
            remover.start()
            Thread.sleep(200)
            assertFalse("deleteModel may not replace or remove a leased native model",
                deletionCompleted.get())
            assertTrue(model.isFile)
        } finally {
            release.countDown()
            reader.join(6_000)
            remover.join(6_000)
            store.deleteModel()
        }
        assertTrue(completed.await(1, TimeUnit.SECONDS))
        assertTrue(deletionCompleted.get())
        assertFalse(model.isFile)
    }
}
