package io.quarkus.automation.platform.update.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.automation.platform.update.model.UpdatePolicy;

class VersionResolverTest {

    private final VersionResolver resolver = new VersionResolver();

    @ParameterizedTest
    @ValueSource(strings = {
            "3.17.0",
            "3.17.1",
            "1.0",
            "2.3.4.5",
            "3.17.0.SP1",
            "3.17.0.Final",
            "1.0.0.AM1",
    })
    void finalVersions(String version) {
        assertThat(VersionResolver.isFinalVersion(version))
                .as(version + " should be final")
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3.17.0-alpha1",
            "3.17.0-beta1",
            "3.17.0-milestone1",
            "3.17.0-rc1",
            "3.17.0-CR1",
            "3.17.0-SNAPSHOT",
            "3.17.0.Alpha1",
            "3.17.0.Beta1",
            "3.17.0.CR1",
            "2.0.0.M1",
            "4.0.0-M2",
    })
    void nonFinalVersions(String version) {
        assertThat(VersionResolver.isFinalVersion(version))
                .as(version + " should not be final")
                .isFalse();
    }

    @Test
    void isNewer() {
        assertThat(resolver.isNewer("3.17.0", "3.17.1")).isTrue();
        assertThat(resolver.isNewer("3.17.0", "3.18.0")).isTrue();
        assertThat(resolver.isNewer("3.17.0", "4.0.0")).isTrue();
    }

    @Test
    void isNotNewer() {
        assertThat(resolver.isNewer("3.17.0", "3.17.0")).isFalse();
        assertThat(resolver.isNewer("3.17.1", "3.17.0")).isFalse();
        assertThat(resolver.isNewer("4.0.0", "3.99.99")).isFalse();
    }

    @Test
    void anyPolicyAllowsEverything() {
        assertThat(resolver.matchesPolicy("3.17.0", "4.0.0", UpdatePolicy.ANY)).isTrue();
        assertThat(resolver.matchesPolicy("3.17.0", "3.18.0", UpdatePolicy.ANY)).isTrue();
        assertThat(resolver.matchesPolicy("3.17.0", "3.17.1", UpdatePolicy.ANY)).isTrue();
    }

    @Test
    void minorPolicyAllowsSameMajor() {
        assertThat(resolver.matchesPolicy("3.17.0", "3.17.1", UpdatePolicy.MINOR)).isTrue();
        assertThat(resolver.matchesPolicy("3.17.0", "3.18.0", UpdatePolicy.MINOR)).isTrue();
        assertThat(resolver.matchesPolicy("3.17.0", "3.99.0", UpdatePolicy.MINOR)).isTrue();
    }

    @Test
    void minorPolicyRejectsDifferentMajor() {
        assertThat(resolver.matchesPolicy("3.17.0", "4.0.0", UpdatePolicy.MINOR)).isFalse();
        assertThat(resolver.matchesPolicy("3.17.0", "2.0.0", UpdatePolicy.MINOR)).isFalse();
    }

    @Test
    void microPolicyAllowsSameMajorMinor() {
        assertThat(resolver.matchesPolicy("3.17.0", "3.17.1", UpdatePolicy.MICRO)).isTrue();
        assertThat(resolver.matchesPolicy("3.17.0", "3.17.99", UpdatePolicy.MICRO)).isTrue();
    }

    @Test
    void microPolicyRejectsDifferentMinor() {
        assertThat(resolver.matchesPolicy("3.17.0", "3.18.0", UpdatePolicy.MICRO)).isFalse();
    }

    @Test
    void microPolicyRejectsDifferentMajor() {
        assertThat(resolver.matchesPolicy("3.17.0", "4.17.0", UpdatePolicy.MICRO)).isFalse();
    }
}
