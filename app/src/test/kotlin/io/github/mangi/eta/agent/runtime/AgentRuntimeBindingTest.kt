package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import io.github.mangi.eta.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36], application = Application::class)
class AgentRuntimeBindingTest {
    @Test fun serviceIntentResolvesInsideTheInstalledApplication() {
        val context = RuntimeEnvironment.getApplication()
        val intent = AgentRuntimeWire.serviceIntent()
        assertEquals(BuildConfig.APPLICATION_ID, intent.component?.packageName)
        assertEquals(AgentRuntimeService::class.java.name, intent.component?.className)
        val resolved = context.packageManager.resolveService(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertNotNull("Runtime service must resolve from the merged app manifest", resolved)
        assertEquals(BuildConfig.APPLICATION_ID, resolved!!.serviceInfo.packageName)
        assertEquals(AgentRuntimeService::class.java.name, resolved.serviceInfo.name)
        assertTrue(resolved.serviceInfo.enabled)
        assertTrue(resolved.serviceInfo.applicationInfo.enabled)
    }

    @Test fun boundRuntimeRespondsThroughMessengerWithoutAModelRequest() {
        val controller = Robolectric.buildService(AgentRuntimeService::class.java).create()
        try {
            val binder = checkNotNull(controller.get().onBind(AgentRuntimeWire.serviceIntent())) {
                "The binding action must return the Runtime Binder"
            }
            val replies = mutableListOf<Pair<Int, String>>()
            val receiver = Messenger(object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(message: Message) {
                    replies += message.what to AgentRuntimeWire.runIdFromBundle(message.data)
                }
            })
            Messenger(binder).send(Message.obtain(null, AgentRuntimeWire.MSG_QUERY_ACTIVE_RUN).apply {
                replyTo = receiver
                sendingUid = Process.myUid()
            })
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(AgentRuntimeWire.MSG_QUERY_ACTIVE_RUN_RESPONSE to ""), replies)
        } finally {
            controller.destroy()
        }
    }
}
