package com.zcz.javatavern.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

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
    private static final String KEY_CONTEXT_TOKENS = "context_tokens";

    // 备份里用的键名（与内部存储键解耦，避免以后改存储键破坏旧备份的兼容性）。
    private static final String KEY_EXPORT_PROVIDER_ID = "providerId";
    private static final String KEY_EXPORT_BASE_URL = "baseUrl";
    private static final String KEY_EXPORT_MODEL = "model";
    private static final String KEY_EXPORT_CONTEXT_TOKENS = "contextTokens";
    private static final String KEY_EXPORT_TEMPERATURE = "temperature";
    private static final String KEY_EXPORT_TOP_P = "topP";
    private static final String KEY_EXPORT_MAX_TOKENS = "maxTokens";
    private static final String KEY_EXPORT_FREQUENCY_PENALTY = "frequencyPenalty";
    private static final String KEY_EXPORT_PRESENCE_PENALTY = "presencePenalty";

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
                loadGenerationParams(),
                preferences.getInt(KEY_CONTEXT_TOKENS, ModelSettings.DEFAULT_CONTEXT_TOKENS)
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
        editor.putInt(KEY_CONTEXT_TOKENS, settings.getContextTokens());
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

    /**
     * 导出连接与采样配置。
     *
     * <p><b>刻意不含 API Key</b>：密钥由 Android Keystore 加密且与本机绑定，
     * 放进可以随手分享的备份文件里，既在别的设备解不开，也没有任何加密收益。
     */
    public JSONObject exportConnectionSettings() {
        JSONObject result = new JSONObject();
        try {
            result.put(KEY_EXPORT_PROVIDER_ID,
                    preferences.getString(KEY_PROVIDER_ID, ProviderCatalog.CUSTOM_ID));
            result.put(KEY_EXPORT_BASE_URL, preferences.getString(KEY_BASE_URL, ""));
            result.put(KEY_EXPORT_MODEL, preferences.getString(KEY_MODEL, ""));
            result.put(KEY_EXPORT_CONTEXT_TOKENS, preferences.getInt(
                    KEY_CONTEXT_TOKENS, ModelSettings.DEFAULT_CONTEXT_TOKENS));
            putNullable(result, KEY_EXPORT_TEMPERATURE, readDouble(KEY_TEMPERATURE));
            putNullable(result, KEY_EXPORT_TOP_P, readDouble(KEY_TOP_P));
            putNullable(result, KEY_EXPORT_MAX_TOKENS, readInt(KEY_MAX_TOKENS));
            putNullable(result, KEY_EXPORT_FREQUENCY_PENALTY, readDouble(KEY_FREQUENCY_PENALTY));
            putNullable(result, KEY_EXPORT_PRESENCE_PENALTY, readDouble(KEY_PRESENCE_PENALTY));
        } catch (JSONException exception) {
            throw new IllegalStateException("无法导出模型配置", exception);
        }
        return result;
    }

    /**
     * 恢复连接与采样配置，但**原样保留已保存的 API Key**：备份里没有密钥，
     * 也不应该因为一次数据恢复把用户已经配好的密钥抹掉。
     *
     * <p>这里直接写字段而不是复用 {@link #save}，就是为了避免「解密失败拿到空串
     * 再加密写回」把密钥覆盖掉。
     */
    public void restoreConnectionSettings(JSONObject settings) {
        if (settings == null || settings.length() == 0) {
            return;
        }
        String baseUrl = settings.optString(KEY_EXPORT_BASE_URL, "");
        String model = settings.optString(KEY_EXPORT_MODEL, "");
        if (baseUrl.isEmpty() && model.isEmpty()) {
            return; // 备份里没带连接配置，保持本机现状
        }
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_PROVIDER_ID, settings.optString(
                        KEY_EXPORT_PROVIDER_ID, ProviderCatalog.CUSTOM_ID))
                .putString(KEY_BASE_URL, baseUrl)
                .putString(KEY_MODEL, model)
                .putString(KEY_API_KEY, preferences.getString(KEY_API_KEY, ""));
        writeNullableDouble(editor, KEY_TEMPERATURE,
                optDouble(settings, KEY_EXPORT_TEMPERATURE));
        writeNullableDouble(editor, KEY_TOP_P, optDouble(settings, KEY_EXPORT_TOP_P));
        writeNullableInt(editor, KEY_MAX_TOKENS, optInt(settings, KEY_EXPORT_MAX_TOKENS));
        writeNullableDouble(editor, KEY_FREQUENCY_PENALTY,
                optDouble(settings, KEY_EXPORT_FREQUENCY_PENALTY));
        writeNullableDouble(editor, KEY_PRESENCE_PENALTY,
                optDouble(settings, KEY_EXPORT_PRESENCE_PENALTY));
        editor.putInt(KEY_CONTEXT_TOKENS, settings.optInt(
                KEY_EXPORT_CONTEXT_TOKENS, ModelSettings.DEFAULT_CONTEXT_TOKENS));
        editor.apply();
    }

    private static void putNullable(JSONObject target, String key, Object value)
            throws JSONException {
        target.put(key, value == null ? JSONObject.NULL : value);
    }

    private static Double optDouble(JSONObject source, String key) {
        Object value = source.opt(key);
        if (value == null || JSONObject.NULL.equals(value)) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer optInt(JSONObject source, String key) {
        Object value = source.opt(key);
        if (value == null || JSONObject.NULL.equals(value)) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
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
