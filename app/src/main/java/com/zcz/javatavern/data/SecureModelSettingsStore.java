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
                decrypt(preferences.getString(KEY_API_KEY, ""))
        );
    }

    public void save(ModelSettings settings) {
        preferences.edit()
                .putString(KEY_PROVIDER_ID, settings.getProviderId())
                .putString(KEY_BASE_URL, settings.getBaseUrl())
                .putString(KEY_MODEL, settings.getModel())
                .putString(KEY_API_KEY, encrypt(settings.getApiKey()))
                .apply();
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
