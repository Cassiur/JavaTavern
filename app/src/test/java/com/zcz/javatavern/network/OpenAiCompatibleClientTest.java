package com.zcz.javatavern.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.zcz.javatavern.data.GenerationParams;

import org.json.JSONObject;
import org.junit.Test;

/**
 * 验证采样参数透传的 JSON 序列化：
 * 显式设置的参数必须进入请求体，留空的参数必须被省略。
 */
public final class OpenAiCompatibleClientTest {

    @Test
    public void allParamsSet_areSerialized() throws Exception {
        GenerationParams params = new GenerationParams(
                0.7, 0.9, 512, 0.3, -0.1);
        JSONObject body = new JSONObject()
                .put("model", "test-model")
                .put("stream", true);

        OpenAiCompatibleClient.applyGenerationParams(body, params);

        assertEquals("test-model", body.getString("model"));
        assertEquals(0.7, body.getDouble("temperature"), 1e-9);
        assertEquals(0.9, body.getDouble("top_p"), 1e-9);
        assertEquals(512, body.getInt("max_tokens"));
        assertEquals(0.3, body.getDouble("frequency_penalty"), 1e-9);
        assertEquals(-0.1, body.getDouble("presence_penalty"), 1e-9);
        // 关键：Double 序列化应输出干净的 "0.7"/"-0.1"，而非 float 精度污染后的 0.699999988
        assertEquals("0.7", body.get("temperature").toString());
        assertEquals("-0.1", body.get("presence_penalty").toString());
    }

    @Test
    public void emptyParams_areOmitted() throws Exception {
        GenerationParams params = GenerationParams.EMPTY;
        JSONObject body = new JSONObject()
                .put("model", "test-model")
                .put("stream", true);

        OpenAiCompatibleClient.applyGenerationParams(body, params);

        assertFalse(body.has("temperature"));
        assertFalse(body.has("top_p"));
        assertFalse(body.has("max_tokens"));
        assertFalse(body.has("frequency_penalty"));
        assertFalse(body.has("presence_penalty"));
    }

    @Test
    public void partialParams_onlySetFieldsSerialized() throws Exception {
        GenerationParams params = new GenerationParams(
                null, 0.95, null, null, null);
        JSONObject body = new JSONObject();

        OpenAiCompatibleClient.applyGenerationParams(body, params);

        assertFalse(body.has("temperature"));
        assertTrue(body.has("top_p"));
        assertEquals(0.95, body.getDouble("top_p"), 1e-9);
        assertFalse(body.has("max_tokens"));
    }

    @Test
    public void nullParams_areNoOp() throws Exception {
        JSONObject body = new JSONObject().put("model", "test-model");
        OpenAiCompatibleClient.applyGenerationParams(body, null);
        assertEquals("test-model", body.getString("model"));
        assertEquals(1, body.length());
    }

    /**
     * 路由分流：只有 Anthropic/Google 走非 OpenAI 请求格式的
     * {@code ChatCompletionProvider} 路径；OpenAI/DeepSeek/OpenRouter/自定义
     * 端点这几个本来就是 OpenAI 兼容格式，必须继续走现有 {@code /chat/completions}
     * 路径，不能被误判去调用不存在的 Anthropic/Gemini 端点。
     */
    @Test
    public void isNativeChatProvider_onlyTrueForAnthropicAndGoogle() {
        assertTrue(OpenAiCompatibleClient.isNativeChatProvider("anthropic"));
        assertTrue(OpenAiCompatibleClient.isNativeChatProvider("google"));
        assertFalse(OpenAiCompatibleClient.isNativeChatProvider("openai"));
        assertFalse(OpenAiCompatibleClient.isNativeChatProvider("deepseek"));
        assertFalse(OpenAiCompatibleClient.isNativeChatProvider("openrouter"));
        assertFalse(OpenAiCompatibleClient.isNativeChatProvider("custom"));
        assertFalse(OpenAiCompatibleClient.isNativeChatProvider(null));
    }
}
