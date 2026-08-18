package com.zcz.javatavern.network;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ModelConnectionTesterTest {
    private final ModelConnectionTester tester = new ModelConnectionTester();

    @Test
    public void appendsModelsToBaseUrl() {
        assertEquals(
                "https://api.example.com/v1/models",
                tester.buildModelsEndpoint("https://api.example.com/v1/")
        );
    }

    @Test
    public void replacesChatCompletionsSuffix() {
        assertEquals(
                "https://api.example.com/v1/models",
                tester.buildModelsEndpoint("https://api.example.com/v1/chat/completions")
        );
    }
}
