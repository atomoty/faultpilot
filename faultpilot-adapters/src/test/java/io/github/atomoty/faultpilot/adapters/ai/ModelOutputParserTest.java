package io.github.atomoty.faultpilot.adapters.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.atomoty.faultpilot.core.model.ModelOutput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelOutputParserTest {

    private final ModelOutputParser parser = new ModelOutputParser(new ObjectMapper());

    @Test
    void parsesValidOutput() {
        ModelOutput out = parser.parse("""
                {"summary":"s","rootCauseCandidates":[{"label":"l","title":"t","explanation":"e","evidenceIds":["log-1"]}],
                 "recommendedActions":["a"]}
                """);
        assertThat(out.summary()).isEqualTo("s");
        assertThat(out.rootCauseCandidates()).hasSize(1);
        assertThat(out.recommendedActions()).containsExactly("a");
    }

    @Test
    void emptyOrBlankThrows() {
        assertThatThrownBy(() -> parser.parse("")).isInstanceOf(ModelUnavailableException.class);
        assertThatThrownBy(() -> parser.parse("   ")).isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void malformedJsonThrows() {
        assertThatThrownBy(() -> parser.parse("{ broken")).isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void structurallyWrongButValidJsonThrows() {
        // valid JSON, wrong shape — must be treated as unusable, not silently accepted
        assertThatThrownBy(() -> parser.parse("\"just a string\"")).isInstanceOf(ModelUnavailableException.class);
        assertThatThrownBy(() -> parser.parse("[1,2,3]")).isInstanceOf(ModelUnavailableException.class);
        assertThatThrownBy(() -> parser.parse("{\"unrelated\":true}")).isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void dropsCandidatesWithBlankLabel() {
        ModelOutput out = parser.parse("""
                {"summary":"s","rootCauseCandidates":[{"label":"","title":"t","explanation":"e","evidenceIds":["log-1"]}],
                 "recommendedActions":[]}
                """);
        assertThat(out.rootCauseCandidates()).isEmpty();
    }
}
