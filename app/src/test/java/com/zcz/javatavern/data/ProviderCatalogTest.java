package com.zcz.javatavern.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ProviderCatalogTest {
    @Test
    public void matchesKnownBaseUrlIgnoringTrailingSlash() {
        assertEquals(
                "openrouter",
                ProviderCatalog.matchBaseUrl("https://openrouter.ai/api/v1/").getId()
        );
    }

    @Test
    public void unknownEndpointUsesCustomPreset() {
        assertEquals(
                ProviderCatalog.CUSTOM_ID,
                ProviderCatalog.matchBaseUrl("https://models.example.com/v1").getId()
        );
    }

    @Test
    public void matchesAnthropicBaseUrl() {
        assertEquals(
                ProviderCatalog.ANTHROPIC_ID,
                ProviderCatalog.matchBaseUrl("https://api.anthropic.com/v1").getId()
        );
    }

    @Test
    public void matchesGoogleBaseUrl() {
        assertEquals(
                ProviderCatalog.GOOGLE_ID,
                ProviderCatalog.matchBaseUrl("https://generativelanguage.googleapis.com/v1beta/").getId()
        );
    }

    @Test
    public void anthropicAndGoogleArePresentInPresetList() {
        assertEquals(ProviderCatalog.ANTHROPIC_ID, ProviderCatalog.findById(ProviderCatalog.ANTHROPIC_ID).getId());
        assertEquals(ProviderCatalog.GOOGLE_ID, ProviderCatalog.findById(ProviderCatalog.GOOGLE_ID).getId());
    }
}
