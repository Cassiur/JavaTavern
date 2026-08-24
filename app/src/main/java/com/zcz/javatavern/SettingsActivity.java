package com.zcz.javatavern;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.zcz.javatavern.data.GenerationParams;
import com.zcz.javatavern.data.GenerationPreset;
import com.zcz.javatavern.data.ModelSettings;
import com.zcz.javatavern.data.PresetRepository;
import com.zcz.javatavern.data.ProviderCatalog;
import com.zcz.javatavern.data.ProviderPreset;
import com.zcz.javatavern.data.SecureModelSettingsStore;
import com.zcz.javatavern.network.ConnectionTestResult;
import com.zcz.javatavern.network.ModelConnectionTester;
import com.zcz.javatavern.util.AppExecutors;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class SettingsActivity extends AppCompatActivity {
    private final ModelConnectionTester connectionTester = new ModelConnectionTester();
    private boolean paramsValid = true;
    private Spinner providerSpinner;
    private EditText baseUrlInput;
    private EditText modelInput;
    private EditText apiKeyInput;
    private TextInputLayout baseUrlLayout;
    private TextInputLayout modelLayout;
    private TextInputLayout temperatureLayout;
    private TextInputLayout topPLayout;
    private TextInputLayout maxTokensLayout;
    private TextInputLayout frequencyPenaltyLayout;
    private TextInputLayout presencePenaltyLayout;
    private EditText temperatureInput;
    private EditText topPInput;
    private EditText maxTokensInput;
    private EditText frequencyPenaltyInput;
    private EditText presencePenaltyInput;
    private TextView connectionStatus;
    private MaterialButton testButton;
    private MaterialButton saveButton;
    private SecureModelSettingsStore settingsStore;
    private Spinner presetSpinner;
    private PresetRepository presetRepository;
    private List<GenerationPreset> generationPresets = List.of();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_settings);

        providerSpinner = findViewById(R.id.providerSpinner);
        baseUrlInput = findViewById(R.id.baseUrlInput);
        modelInput = findViewById(R.id.modelInput);
        apiKeyInput = findViewById(R.id.apiKeyInput);
        baseUrlLayout = findViewById(R.id.baseUrlLayout);
        modelLayout = findViewById(R.id.modelLayout);
        temperatureLayout = findViewById(R.id.temperatureLayout);
        topPLayout = findViewById(R.id.topPLayout);
        maxTokensLayout = findViewById(R.id.maxTokensLayout);
        frequencyPenaltyLayout = findViewById(R.id.frequencyPenaltyLayout);
        presencePenaltyLayout = findViewById(R.id.presencePenaltyLayout);
        temperatureInput = findViewById(R.id.temperatureInput);
        topPInput = findViewById(R.id.topPInput);
        maxTokensInput = findViewById(R.id.maxTokensInput);
        frequencyPenaltyInput = findViewById(R.id.frequencyPenaltyInput);
        presencePenaltyInput = findViewById(R.id.presencePenaltyInput);
        connectionStatus = findViewById(R.id.connectionStatus);
        testButton = findViewById(R.id.testConnectionButton);
        saveButton = findViewById(R.id.saveSettingsButton);
        presetSpinner = findViewById(R.id.presetSpinner);
        settingsStore = new SecureModelSettingsStore(this);
        presetRepository = new PresetRepository(this);

        List<ProviderPreset> presets = ProviderCatalog.getPresets();
        ArrayAdapter<ProviderPreset> presetAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                presets
        );
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(presetAdapter);

        ModelSettings settings = settingsStore.load();
        ProviderPreset selectedPreset = ProviderCatalog.findById(settings.getProviderId());
        if (ProviderCatalog.CUSTOM_ID.equals(selectedPreset.getId())) {
            selectedPreset = ProviderCatalog.matchBaseUrl(settings.getBaseUrl());
        }
        providerSpinner.setSelection(ProviderCatalog.indexOf(selectedPreset.getId()));
        baseUrlInput.setText(settings.getBaseUrl());
        modelInput.setText(settings.getModel());
        modelInput.setHint(selectedPreset.getModelHint());
        apiKeyInput.setText(settings.getApiKey());
        GenerationParams params = settings.getGenerationParams();
        bindParam(temperatureInput, params.getTemperature());
        bindParam(topPInput, params.getTopP());
        bindParam(maxTokensInput, params.getMaxTokens());
        bindParam(frequencyPenaltyInput, params.getFrequencyPenalty());
        bindParam(presencePenaltyInput, params.getPresencePenalty());

        providerSpinner.post(() -> providerSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener(position -> applyPreset(presets.get(position)))
        ));
        findViewById(R.id.settingsBackButton).setOnClickListener(view -> finish());
        testButton.setOnClickListener(view -> testConnection());
        saveButton.setOnClickListener(view -> saveSettings());
        findViewById(R.id.applyPresetButton).setOnClickListener(view -> applyGenerationPreset());
        findViewById(R.id.savePresetButton).setOnClickListener(view -> saveAsPreset());
        findViewById(R.id.deletePresetButton).setOnClickListener(view -> deleteSelectedPreset());
        refreshPresetSpinner();
    }

    private void applyPreset(ProviderPreset preset) {
        connectionStatus.setVisibility(View.GONE);
        modelInput.setHint(preset.getModelHint());
        if (ProviderCatalog.CUSTOM_ID.equals(preset.getId())) {
            baseUrlInput.setText("");
            modelInput.setText("");
            baseUrlInput.requestFocus();
            return;
        }
        baseUrlInput.setText(preset.getBaseUrl());
        modelInput.setText(preset.getDefaultModel());
    }

    private void saveSettings() {
        ModelSettings settings = validatedSettings();
        if (settings == null) {
            return;
        }
        settingsStore.save(settings);
        Toast.makeText(this, R.string.connection_saved, Toast.LENGTH_SHORT).show();
    }

    private void refreshPresetSpinner() {
        generationPresets = presetRepository.listPresets();
        List<String> names = new ArrayList<>();
        for (GenerationPreset preset : generationPresets) {
            names.add(preset.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                names
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);
        if (!names.isEmpty()) {
            presetSpinner.setSelection(0);
        }
    }

    private void applyGenerationPreset() {
        int index = presetSpinner.getSelectedItemPosition();
        if (index < 0 || index >= generationPresets.size()) {
            return;
        }
        GenerationPreset preset = generationPresets.get(index);
        GenerationParams params = preset.getParams();
        bindParam(temperatureInput, params.getTemperature());
        bindParam(topPInput, params.getTopP());
        bindParam(maxTokensInput, params.getMaxTokens());
        bindParam(frequencyPenaltyInput, params.getFrequencyPenalty());
        bindParam(presencePenaltyInput, params.getPresencePenalty());
        clearParamErrors();
        Toast.makeText(
                this,
                getString(R.string.preset_applied, preset.getName()),
                Toast.LENGTH_SHORT
        ).show();
    }

    private void saveAsPreset() {
        GenerationParams params = parseGenerationParams();
        if (params == null) {
            Toast.makeText(this, R.string.preset_params_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.preset_name_hint);
        nameInput.setSingleLine(true);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.save_as_preset)
                .setView(nameInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        nameInput.setError(getString(R.string.preset_name_required));
                        return;
                    }
                    presetRepository.savePreset(name, params);
                    dialog.dismiss();
                    refreshPresetSpinner();
                    Toast.makeText(this, R.string.preset_saved, Toast.LENGTH_SHORT).show();
                }));
        dialog.show();
    }

    private void deleteSelectedPreset() {
        int index = presetSpinner.getSelectedItemPosition();
        if (index < 0 || index >= generationPresets.size()) {
            return;
        }
        GenerationPreset preset = generationPresets.get(index);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_preset)
                .setMessage(getString(R.string.preset_delete_confirm, preset.getName()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_preset, (dialog, which) -> {
                    presetRepository.deletePreset(preset.getId());
                    refreshPresetSpinner();
                    Toast.makeText(this, R.string.preset_deleted, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void testConnection() {
        ModelSettings settings = validatedSettings();
        if (settings == null) {
            return;
        }
        setTesting(true);
        AppExecutors.get().network().execute(() -> {
            ConnectionTestResult result = connectionTester.test(settings);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setTesting(false);
                connectionStatus.setVisibility(View.VISIBLE);
                connectionStatus.setText(getString(
                        R.string.connection_test_result,
                        result.getMessage(),
                        result.getLatencyMillis()
                ));
                connectionStatus.setTextColor(ContextCompat.getColor(
                        this,
                        result.isSuccessful() ? R.color.success : R.color.warning
                ));
            });
        });
    }

    private ModelSettings validatedSettings() {
        baseUrlLayout.setError(null);
        modelLayout.setError(null);
        clearParamErrors();
        String baseUrl = baseUrlInput.getText().toString().trim();
        String model = modelInput.getText().toString().trim();
        if (TextUtils.isEmpty(baseUrl)) {
            baseUrlLayout.setError(getString(R.string.base_url_required));
            return null;
        }
        if (!isSecureBaseUrl(baseUrl)) {
            baseUrlLayout.setError(getString(R.string.https_url_required));
            return null;
        }
        if (TextUtils.isEmpty(model)) {
            modelLayout.setError(getString(R.string.model_required));
            return null;
        }
        GenerationParams params = parseGenerationParams();
        if (params == null) {
            return null;
        }
        ProviderPreset preset = (ProviderPreset) providerSpinner.getSelectedItem();
        return new ModelSettings(
                preset.getId(),
                baseUrl,
                model,
                apiKeyInput.getText().toString().trim(),
                params
        );
    }

    private void clearParamErrors() {
        temperatureLayout.setError(null);
        topPLayout.setError(null);
        maxTokensLayout.setError(null);
        frequencyPenaltyLayout.setError(null);
        presencePenaltyLayout.setError(null);
    }

    private void bindParam(EditText input, Number value) {
        input.setText(value == null ? "" : formatParam(value));
    }

    private String formatParam(Number value) {
        if (value instanceof Double) {
            return String.valueOf(value);
        }
        return String.valueOf(value.intValue());
    }

    private GenerationParams parseGenerationParams() {
        paramsValid = true;
        Double temperature = parseFloatParam(
                temperatureInput, temperatureLayout, R.string.param_temperature, 0d, 2d);
        Double topP = parseFloatParam(
                topPInput, topPLayout, R.string.param_top_p, 0d, 1d);
        Double frequencyPenalty = parseFloatParam(
                frequencyPenaltyInput, frequencyPenaltyLayout,
                R.string.param_frequency_penalty, -2d, 2d);
        Double presencePenalty = parseFloatParam(
                presencePenaltyInput, presencePenaltyLayout,
                R.string.param_presence_penalty, -2d, 2d);
        Integer maxTokens = parseMaxTokens();
        if (!paramsValid) {
            return null;
        }
        return new GenerationParams(
                temperature, topP, maxTokens, frequencyPenalty, presencePenalty);
    }

    /**
     * 解析浮点参数：空输入返回 null 表示"未设置，走服务端默认"；
     * 非法输入设置错误并返回 null。
     */
    private Double parseFloatParam(
            EditText input,
            TextInputLayout layout,
            int labelRes,
            double min,
            double max
    ) {
        String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw);
            if (value < min || value > max) {
                layout.setError(getString(
                        R.string.param_out_of_range,
                        getString(labelRes),
                        trimNumber(min),
                        trimNumber(max)));
                paramsValid = false;
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            layout.setError(getString(R.string.param_invalid_number, getString(labelRes)));
            paramsValid = false;
            return null;
        }
    }

    private Integer parseMaxTokens() {
        String raw = maxTokensInput.getText().toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        maxTokensLayout.setError(getString(R.string.param_max_tokens_invalid));
        paramsValid = false;
        return null;
    }

    private String trimNumber(double value) {
        return value == (long) value ? String.valueOf((long) value) : String.valueOf(value);
    }

    private boolean isSecureBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void setTesting(boolean testing) {
        testButton.setEnabled(!testing);
        saveButton.setEnabled(!testing);
        providerSpinner.setEnabled(!testing);
        connectionStatus.setVisibility(View.VISIBLE);
        connectionStatus.setText(testing ? getString(R.string.testing_connection) : "");
        connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
    }
}
