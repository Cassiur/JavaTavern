package com.zcz.javatavern.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class GenerationPresetTest {

    @Test
    public void builtInPresets_haveStableIdsAndSensibleParams() {
        List<GenerationPreset> presets = GenerationPreset.builtInPresets();
        assertEquals(4, presets.size());

        GenerationPreset defaultPreset = findById(presets, "builtin:default");
        assertNotNull(defaultPreset);
        assertTrue(defaultPreset.getParams().isEmpty());

        GenerationPreset creative = findById(presets, "builtin:creative");
        assertEquals(1.2, creative.getParams().getTemperature(), 1e-9);
        assertEquals(0.95, creative.getParams().getTopP(), 1e-9);
        assertNull(creative.getParams().getMaxTokens());

        GenerationPreset precise = findById(presets, "builtin:precise");
        assertEquals(0.3, precise.getParams().getTemperature(), 1e-9);

        GenerationPreset roleplay = findById(presets, "builtin:roleplay");
        assertEquals(0.9, roleplay.getParams().getTemperature(), 1e-9);
        assertEquals(0.3, roleplay.getParams().getFrequencyPenalty(), 1e-9);
        assertEquals(0.3, roleplay.getParams().getPresencePenalty(), 1e-9);
    }

    private static GenerationPreset findById(List<GenerationPreset> presets, String id) {
        for (GenerationPreset preset : presets) {
            if (preset.getId().equals(id)) {
                return preset;
            }
        }
        return null;
    }
}
