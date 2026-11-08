package io.github.mangi.eta.agent.model

import com.sun.net.httpserver.HttpServer
import io.github.mangi.eta.agent.memory.AgentMemoryContext
import io.github.mangi.eta.agent.roleplay.CharacterCardCodec
import io.github.mangi.eta.agent.roleplay.RoleplayRunContext
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.agent.skill.SkillContext
import io.github.mangi.eta.agent.tool.AgentToolCapabilities
import io.github.mangi.eta.data.model.ProviderTypes
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplateRequestTest {
    @Test
    fun openAiProjectionsPreserveProviderAndTemplateExactlyOnce() {
        val config = config()
        val messages = messages(config)
        val systems = systems(messages)
        assertEquals("PROVIDER_PROMPT", systems[0])
        assertEquals(TEMPLATE, systems[1])
        val original = messages.toString()
        val expectedInstructions = systems.joinToString("\n\n")
        val chat = OpenAiChatCompletionsProvider.buildRequestJson(config, messages, JSONArray())
        // Chat merges the independent internal system sections into one protocol message.
        val chatMessages = chat.getJSONArray("messages")
        assertEquals(listOf(expectedInstructions), systems(chatMessages))
        assertEquals(1, Regex(Regex.escape(TEMPLATE)).findAll(systems(chatMessages).single()).count())
        assertFalse((0 until chatMessages.length()).map(chatMessages::getJSONObject)
            .filter { it.optString("role") != "system" }.any { it.toString().contains(TEMPLATE) })
        val responses = OpenAiResponsesProvider.buildRequestJson(config, messages, JSONArray())
        val instructions = responses.getString("instructions")
        assertEquals(expectedInstructions, instructions)
        assertEquals(1, Regex(Regex.escape(TEMPLATE)).findAll(instructions).count())
        assertFalse(responses.getJSONArray("input").toString().contains(TEMPLATE))
        assertEquals(original, messages.toString())
    }

    @Test
    fun anthropicRequestReceivesTheTemplateInSystemInsteadOfUserHistory() {
        val captured = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newSingleThreadExecutor()
        server.executor = executor
        server.createContext("/v1/messages") { exchange ->
            captured.set(exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) })
            val body = "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val config = config().copy(providerType = ProviderTypes.ANTHROPIC,
                baseUrl = "http://127.0.0.1:${server.address.port}")
            AnthropicMessagesProvider.complete(ProviderRequest(config, messages(config), JSONArray()), AgentRunController())
            val request = JSONObject(captured.get())
            val system = request.get("system").toString()
            assertTrue(system.contains("PROVIDER_PROMPT"))
            assertEquals(1, Regex(Regex.escape(TEMPLATE)).findAll(system).count())
            assertFalse(request.getJSONArray("messages").toString().contains(TEMPLATE))
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    @Test
    fun toolRoundsAndRootPrefixRebuildKeepExactlyOneFrozenTemplate() {
        var root = false
        var calls = 0
        val provider = object : AgentProviderClient {
            override val id = "template-fixture"
            override val capabilities = OpenAiChatCompletionsProvider.capabilities
            override fun complete(request: ProviderRequest, runController: AgentRunController,
                onEvent: (ProviderEvent) -> Unit): ProviderResponse {
                assertEquals(1, systems(request.messages).count { it == TEMPLATE })
                assertEquals("PROVIDER_PROMPT", systems(request.messages).first())
                calls++
                return if (calls == 1) ProviderResponse(JSONObject().put("role", "assistant").put("content", "")
                    .put("finish_reason", "tool_calls").put("tool_calls", JSONArray().put(JSONObject()
                        .put("id", "context").put("type", "function").put("function", JSONObject()
                            .put("name", "get_current_context").put("arguments", "{}")))))
                else ProviderResponse(JSONObject().put("role", "assistant").put("content", "done").put("finish_reason", "stop"))
            }
        }
        AgentModelClient.complete(config(), "run", provider = provider,
            capabilitiesProvider = { AgentToolCapabilities(rootAvailable = root) },
            toolExecutor = { root = true; AgentModelClient.ToolResult("{\"ok\":true}") })
        assertEquals(2, calls)
    }

    @Test
    fun roleplayReplyRewriteIncludesExplicitTemplateAndStillHasNoTools() {
        val roleplay = RoleplayRunContext("character", CharacterCardCodec.decodeJson(
            """{"name":"旅人","description":"安静的向导"}"""), "用户", "", contextWindow = 16_000)
        val provider = object : AgentProviderClient {
            override val id = "rewrite-template-fixture"
            override val capabilities = OpenAiChatCompletionsProvider.capabilities
            override fun complete(request: ProviderRequest, runController: AgentRunController,
                onEvent: (ProviderEvent) -> Unit): ProviderResponse {
                assertEquals(1, systems(request.messages).count { it == TEMPLATE })
                assertEquals(ProviderRequestPurpose.REPLY_REWRITE, request.purpose)
                assertEquals(0, request.effectiveTools.length())
                assertTrue(request.messages.toString().contains("旅人"))
                return ProviderResponse(JSONObject().put("role", "assistant").put("content", "改写后").put("finish_reason", "stop"))
            }
        }
        AgentModelClient.complete(config(), "原回复", roleplayContext = roleplay, rewriteReply = true,
            provider = provider, toolExecutor = { error("改写不得执行工具") })
    }

    @Test
    fun frozenTemplateIsNotSerializedIntoSavedOrWireConfiguration() {
        val encoded = Json.encodeToString(config())
        assertFalse(encoded.contains(TEMPLATE))
        assertFalse(encoded.contains("promptTemplate"))
        val restored = Json.decodeFromString<AgentModelClient.ModelConfig>(encoded)
        assertTrue(restored.promptTemplateText.isEmpty())
    }

    private fun config() = AgentModelClient.ModelConfig(
        baseUrl = "https://example.invalid/v1", apiKey = "fixture", model = "fixture-model",
        systemPrompt = "PROVIDER_PROMPT", promptTemplateId = "fixture-template", promptTemplateText = TEMPLATE,
    )

    private fun messages(config: AgentModelClient.ModelConfig) = AgentPromptBuilder.buildInitialMessages(
        config, "question", emptyList(), emptyList(), SkillContext.EMPTY, AgentMemoryContext.DISABLED)

    private fun systems(messages: JSONArray): List<String> = (0 until messages.length())
        .map(messages::getJSONObject).filter { it.optString("role") == "system" }.map { it.getString("content") }

    private companion object { const val TEMPLATE = "CUSTOM_TEMPLATE_ONCE" }
}
