package io.github.atomoty.faultpilot.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.adapters.codex.CodexCliDiagnosisModel;
import io.github.atomoty.faultpilot.adapters.mock.MockDiagnosisModel;
import io.github.atomoty.faultpilot.adapters.openai.OpenAiApiDiagnosisModel;
import io.github.atomoty.faultpilot.core.adapter.DiagnosisModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeanConfigModelSelectionTest {

    private final BeanConfig beanConfig = new BeanConfig();
    private final ObjectMapper mapper = new ObjectMapper();

    private FaultPilotProperties props(String provider) {
        FaultPilotProperties p = new FaultPilotProperties();
        p.getAi().setProvider(provider);
        return p;
    }

    @Test
    void mockByDefault() {
        DiagnosisModel model = beanConfig.diagnosisModel(props("mock"), mapper);
        assertThat(model).isInstanceOf(MockDiagnosisModel.class);
    }

    @Test
    void openAiApiRequiresApiKeyAndModel() {
        // provider=openai-api but no api-key/model configured -> fail fast
        assertThatThrownBy(() -> beanConfig.diagnosisModel(props("openai-api"), mapper))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openAiApiBuiltWhenConfigured() {
        FaultPilotProperties p = props("openai-api");
        p.getAi().setApiKey("sk-test");
        p.getAi().setModel("gpt-test");
        assertThat(beanConfig.diagnosisModel(p, mapper)).isInstanceOf(OpenAiApiDiagnosisModel.class);
    }

    @Test
    void codexCliBuilt() {
        assertThat(beanConfig.diagnosisModel(props("codex-cli"), mapper))
                .isInstanceOf(CodexCliDiagnosisModel.class);
    }

    @Test
    void unknownProviderFailsFast() {
        assertThatThrownBy(() -> beanConfig.diagnosisModel(props("gemini"), mapper))
                .isInstanceOf(IllegalStateException.class);
    }
}
