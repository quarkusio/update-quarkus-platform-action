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

    @Test
    void isFinalVersionDetectsNonFinal() {
        assertThat(VersionResolver.isFinalVersion("4.0.0.Beta2")).isFalse();
        assertThat(VersionResolver.isFinalVersion("4.0.0.CR1")).isFalse();
        assertThat(VersionResolver.isFinalVersion("4.0.0")).isTrue();
        assertThat(VersionResolver.isFinalVersion("4.0.0.Final")).isTrue();
    }

    @Test
    void isEligibleUpgradeFromNonFinalToNewerNonFinalSameBase() {
        // Beta2 -> Beta3: same base, newer
        assertThat(resolver.isNewer("4.0.0.Beta2", "4.0.0.Beta3")).isTrue();
        // Beta2 -> CR1: same base, newer qualifier family
        assertThat(resolver.isNewer("4.0.0.Beta2", "4.0.0.CR1")).isTrue();
        // Beta2 -> final: same base, final
        assertThat(resolver.isNewer("4.0.0.Beta2", "4.0.0")).isTrue();
        assertThat(resolver.isNewer("4.0.0.Beta2", "4.0.0.Final")).isTrue();
    }

    @Test
    void nonFinalCurrentShouldNotUpgradeToDifferentBaseNonFinal() {
        // 4.0.0.Beta2 -> 4.1.0.Beta1: different base, should not be eligible
        // (isNewer is true, but the filtering logic in findBestMatchingVersion rejects it)
        assertThat(VersionResolver.isFinalVersion("4.1.0.Beta1")).isFalse();
        assertThat(VersionResolver.extractNumericBase("4.0.0.Beta2"))
                .isNotEqualTo(VersionResolver.extractNumericBase("4.1.0.Beta1"));
    }

    @Test
    void finalCurrentShouldNotUpgradeToNonFinal() {
        // A member on 3.17.0 should not be offered 3.17.1-beta1
        assertThat(VersionResolver.isFinalVersion("3.17.0")).isTrue();
        assertThat(VersionResolver.isFinalVersion("3.17.1.Beta1")).isFalse();
    }
}
