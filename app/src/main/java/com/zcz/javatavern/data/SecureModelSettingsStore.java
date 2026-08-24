package com.zcz.javatavern.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureModelSettingsStore {
    private static final String PREFERENCES_NAME = "secure_model_settings";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_MODEL = "model";
    private static final String KEY_API_KEY = "encrypted_api_key";
    private static final String KEY_PROVIDER_ID = "provider_id";
    private static final String KEY_TEMPERATURE = "gen_temperature";
    private static final String KEY_TOP_P = "gen_top_p";
    private static final String KEY_MAX_TOKENS = "gen_max_tokens";
    private static final String KEY_FREQUENCY_PENALTY = "gen_frequency_penalty";
    private static final String KEY_PRESENCE_PENALTY = "gen_presence_penalty";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "java_tavern_model_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SharedPreferences preferences;

    public SecureModelSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public ModelSettings load() {
        return new ModelSettings(
                preferences.getString(KEY_PROVIDER_ID, ProviderCatalog.CUSTOM_ID),
                preferences.getString(KEY_BASE_URL, "https://api.openai.com/v1"),
                preferences.getString(KEY_MODEL, ""),
                decrypt(preferences.getString(KEY_API_KEY, "")),
                loadGenerationParams()
        );
    }

    private GenerationParams loadGenerationParams() {
        return new GenerationParams(
                readDouble(KEY_TEMPERATURE),
                readDouble(KEY_TOP_P),
                readInt(KEY_MAX_TOKENS),
                readDouble(KEY_FREQUENCY_PENALTY),
                readDouble(KEY_PRESENCE_PENALTY)
        );
    }

    /**
     * 采样参数用字符串存储（SharedPreferences 无 double 类型），
     * 避免 float 存储 0.7 时退化成 0.699999988079071 的精度污染。
     */
    private Double readDouble(String key) {
        if (!preferences.contains(key)) {
            return null;
        }
        try {
            return Double.parseDouble(preferences.getString(key, ""));
        } catch (NumberFormatException | ClassCastException exception) {
            // NumberFormatException：存储值不是合法数字；ClassCastException：旧版本按 float 存储。
            return null;
        }
    }

    private Integer readInt(String key) {
        if (!preferences.contains(key)) {
            return null;
        }
        try {
            return preferences.getInt(key, 0);
        } catch (ClassCastException exception) {
            return null;
        }
    }

    public void save(ModelSettings settings) {
        GenerationParams params = settings.getGenerationParams();
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_PROVIDER_ID, settings.getProviderId())
                .putString(KEY_BASE_URL, settings.getBaseUrl())
                .putString(KEY_MODEL, settings.getModel())
                .putString(KEY_API_KEY, encrypt(settings.getApiKey()));
        writeNullableDouble(editor, KEY_TEMPERATURE, params.getTemperature());
        writeNullableDouble(editor, KEY_TOP_P, params.getTopP());
        writeNullableInt(editor, KEY_MAX_TOKENS, params.getMaxTokens());
        writeNullableDouble(editor, KEY_FREQUENCY_PENALTY, params.getFrequencyPenalty());
        writeNullableDouble(editor, KEY_PRESENCE_PENALTY, params.getPresencePenalty());
        editor.apply();
    }

    private void writeNullableDouble(SharedPreferences.Editor editor, String key, Double value) {
        if (value == null) {
            editor.remove(key);
        } else {
            editor.putString(key, Double.toString(value));
        }
    }

    private void writeNullableInt(SharedPreferences.Editor editor, String key, Integer value) {
        if (value == null) {
            editor.remove(key);
        } else {
            editor.putInt(key, value);
        }
    }

    private String encrypt(String value) {
        if (value.isEmpty()) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
                    Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception exception) {
            throw new IllegalStateException("API Key 加密失败", exception);
        }
    }

    private String decrypt(String value) {
        if (value.isEmpty()) {
            return "";
        }
        try {
            String[] parts = value.split("\\.", 2);
            if (parts.length != 2) {
                return "";
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateSecretKey(),
                    new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
            );
            byte[] decrypted = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return "";
        }
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
        );
        keyGenerator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return keyGenerator.generateKey();
    }
}
