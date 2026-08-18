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
}
