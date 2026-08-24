package com.zcz.javatavern.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the single decision the offline demo banner depends on:
 * {@link ModelSettings#isRemoteConfigured()}. A remote model counts as
 * configured only when both the base URL and the model name are non-blank.
 */
public final class ModelSettingsTest {

    private static ModelSettings settings(String baseUrl, String model) {
        return new ModelSettings(baseUrl, model, "key");
    }

    @Test
    public void emptyBaseUrlAndModelIsNotConfigured() {
        assertFalse(settings("", "").isRemoteConfigured());
    }

    @Test
    public void emptyModelIsNotConfigured() {
        assertFalse(settings("https://api.openai.com/v1", "").isRemoteConfigured());
    }

    @Test
    public void emptyBaseUrlIsNotConfigured() {
        assertFalse(settings("", "gpt-4o-mini").isRemoteConfigured());
    }

    @Test
    public void whitespaceOnlyFieldsAreNotConfigured() {
        assertFalse(settings("   ", "   ").isRemoteConfigured());
        assertFalse(settings("   ", "gpt-4o-mini").isRemoteConfigured());
    }

    @Test
    public void bothFieldsFilledIsConfigured() {
        assertTrue(settings("https://api.deepseek.com/v1", "deepseek-chat").isRemoteConfigured());
    }
}
