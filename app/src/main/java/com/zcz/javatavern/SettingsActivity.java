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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.zcz.javatavern.data.ModelSettings;
import com.zcz.javatavern.data.ProviderCatalog;
import com.zcz.javatavern.data.ProviderPreset;
import com.zcz.javatavern.data.SecureModelSettingsStore;
import com.zcz.javatavern.network.ConnectionTestResult;
import com.zcz.javatavern.network.ModelConnectionTester;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsActivity extends AppCompatActivity {
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final ModelConnectionTester connectionTester = new ModelConnectionTester();
    private Spinner providerSpinner;
    private EditText baseUrlInput;
    private EditText modelInput;
    private EditText apiKeyInput;
    private TextInputLayout baseUrlLayout;
    private TextInputLayout modelLayout;
    private TextView connectionStatus;
    private MaterialButton testButton;
    private MaterialButton saveButton;
    private SecureModelSettingsStore settingsStore;

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
        connectionStatus = findViewById(R.id.connectionStatus);
        testButton = findViewById(R.id.testConnectionButton);
        saveButton = findViewById(R.id.saveSettingsButton);
        settingsStore = new SecureModelSettingsStore(this);

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

        providerSpinner.post(() -> providerSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener(position -> applyPreset(presets.get(position)))
        ));
        findViewById(R.id.settingsBackButton).setOnClickListener(view -> finish());
        testButton.setOnClickListener(view -> testConnection());
        saveButton.setOnClickListener(view -> saveSettings());
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

    private void testConnection() {
        ModelSettings settings = validatedSettings();
        if (settings == null) {
            return;
        }
        setTesting(true);
        networkExecutor.execute(() -> {
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
        ProviderPreset preset = (ProviderPreset) providerSpinner.getSelectedItem();
        return new ModelSettings(
                preset.getId(),
                baseUrl,
                model,
                apiKeyInput.getText().toString().trim()
        );
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

    @Override
    protected void onDestroy() {
        networkExecutor.shutdownNow();
        super.onDestroy();
    }
}
