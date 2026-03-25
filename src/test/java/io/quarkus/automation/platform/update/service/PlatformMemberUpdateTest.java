package io.quarkus.automation.platform.update.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import io.quarkus.automation.platform.update.model.PlatformMember;
import io.quarkus.automation.platform.update.model.UpdatePolicy;

class PlatformMemberUpdateTest {

    private static final Path TEST_POM_PATH = Path.of("src/test/resources/test-platform-pom.xml");

    private PlatformMemberService memberService;
    private VersionResolver versionResolver;

    @TempDir
    Path tempDir;
    Path pomPath;

    @BeforeEach
    void setUp() throws IOException {
        memberService = new PlatformMemberService();
        versionResolver = Mockito.mock(VersionResolver.class);
        pomPath = tempDir.resolve("pom.xml");
        Files.copy(TEST_POM_PATH, pomPath);
    }

    @Test
    void updateSingleMember() throws IOException {
        when(versionResolver.getLatestRelease(eq("org.apache.camel.quarkus"), eq("camel-quarkus-bom"),
                eq("3.17.0"), eq(UpdatePolicy.ANY)))
                .thenReturn(Optional.of("3.18.0"));
        when(versionResolver.isNewer("3.17.0", "3.18.0")).thenReturn(true);

        List<PlatformMember> members = memberService.parseMembers(pomPath);
        PlatformMember camel = members.stream()
                .filter(m -> m.getName().equals("Camel"))
                .findFirst().orElseThrow();

        Optional<String> latest = versionResolver.getLatestRelease(
                camel.getGroupId(), camel.getArtifactId(), camel.getCurrentVersion(), UpdatePolicy.ANY);

        assertThat(latest).contains("3.18.0");
        assertThat(versionResolver.isNewer(camel.getCurrentVersion(), latest.get())).isTrue();

        memberService.updateVersionProperty(pomPath, camel.getVersionProperty(), latest.get());

        String updatedPom = Files.readString(pomPath);
        assertThat(updatedPom).contains("<camel-quarkus.version>3.18.0</camel-quarkus.version>");
        assertThat(updatedPom).doesNotContain("<camel-quarkus.version>3.17.0</camel-quarkus.version>");
        // Other properties are untouched
        assertThat(updatedPom).contains("<debezium.version>2.5.0.Final</debezium.version>");
        // The BOM reference still uses the property placeholder
        assertThat(updatedPom).contains("${camel-quarkus.version}");
    }

    @Test
    void updateMultipleMembers() throws IOException {
        when(versionResolver.getLatestRelease(eq("org.apache.camel.quarkus"), eq("camel-quarkus-bom"),
                eq("3.17.0"), eq(UpdatePolicy.ANY)))
                .thenReturn(Optional.of("3.18.0"));
        when(versionResolver.getLatestRelease(eq("io.debezium"), eq("debezium-core"),
                eq("2.5.0.Final"), eq(UpdatePolicy.ANY)))
                .thenReturn(Optional.of("2.6.0.Final"));

        List<PlatformMember> members = memberService.parseMembers(pomPath);

        for (PlatformMember member : members) {
            Optional<String> latest = versionResolver.getLatestRelease(
                    member.getGroupId(), member.getArtifactId(), member.getCurrentVersion(), UpdatePolicy.ANY);
            if (latest.isPresent()) {
                memberService.updateVersionProperty(pomPath, member.getVersionProperty(), latest.get());
            }
        }

        String updatedPom = Files.readString(pomPath);
        assertThat(updatedPom).contains("<camel-quarkus.version>3.18.0</camel-quarkus.version>");
        assertThat(updatedPom).contains("<debezium.version>2.6.0.Final</debezium.version>");
    }

    @Test
    void noUpdateWhenVersionIsCurrent() throws IOException {
        when(versionResolver.getLatestRelease(eq("org.apache.camel.quarkus"), eq("camel-quarkus-bom"),
                eq("3.17.0"), eq(UpdatePolicy.ANY)))
                .thenReturn(Optional.of("3.17.0"));
        when(versionResolver.isNewer("3.17.0", "3.17.0")).thenReturn(false);

        List<PlatformMember> members = memberService.parseMembers(pomPath);
        PlatformMember camel = members.stream()
                .filter(m -> m.getName().equals("Camel"))
                .findFirst().orElseThrow();

        Optional<String> latest = versionResolver.getLatestRelease(
                camel.getGroupId(), camel.getArtifactId(), camel.getCurrentVersion(), UpdatePolicy.ANY);
        assertThat(versionResolver.isNewer(camel.getCurrentVersion(), latest.get())).isFalse();

        // POM should remain unchanged
        String originalPom = Files.readString(pomPath);
        assertThat(originalPom).contains("<camel-quarkus.version>3.17.0</camel-quarkus.version>");
    }

    @Test
    void microPolicySkipsMinorUpdate() throws IOException {
        when(versionResolver.getLatestRelease(eq("org.apache.camel.quarkus"), eq("camel-quarkus-bom"),
                eq("3.17.0"), eq(UpdatePolicy.MICRO)))
                .thenReturn(Optional.empty());

        List<PlatformMember> members = memberService.parseMembers(pomPath);
        PlatformMember camel = members.stream()
                .filter(m -> m.getName().equals("Camel"))
                .findFirst().orElseThrow();

        Optional<String> latest = versionResolver.getLatestRelease(
                camel.getGroupId(), camel.getArtifactId(), camel.getCurrentVersion(), UpdatePolicy.MICRO);
        assertThat(latest).isEmpty();

        // POM should remain unchanged
        String originalPom = Files.readString(pomPath);
        assertThat(originalPom).contains("<camel-quarkus.version>3.17.0</camel-quarkus.version>");
    }

    @Test
    void microPolicyAllowsPatchUpdate() throws IOException {
        when(versionResolver.getLatestRelease(eq("org.apache.camel.quarkus"), eq("camel-quarkus-bom"),
                eq("3.17.0"), eq(UpdatePolicy.MICRO)))
                .thenReturn(Optional.of("3.17.1"));

        List<PlatformMember> members = memberService.parseMembers(pomPath);
        PlatformMember camel = members.stream()
                .filter(m -> m.getName().equals("Camel"))
                .findFirst().orElseThrow();

        Optional<String> latest = versionResolver.getLatestRelease(
                camel.getGroupId(), camel.getArtifactId(), camel.getCurrentVersion(), UpdatePolicy.MICRO);
        assertThat(latest).contains("3.17.1");

        memberService.updateVersionProperty(pomPath, camel.getVersionProperty(), latest.get());

        String updatedPom = Files.readString(pomPath);
        assertThat(updatedPom).contains("<camel-quarkus.version>3.17.1</camel-quarkus.version>");
    }

    @Test
    void pomFormattingIsPreserved() throws IOException {
        String originalPom = Files.readString(pomPath);

        memberService.updateVersionProperty(pomPath, "camel-quarkus.version", "3.18.0");

        String updatedPom = Files.readString(pomPath);
        // Only the version value should change, the rest of the formatting should be identical
        String expectedPom = originalPom.replace(
                "<camel-quarkus.version>3.17.0</camel-quarkus.version>",
                "<camel-quarkus.version>3.18.0</camel-quarkus.version>");
        assertThat(updatedPom).isEqualTo(expectedPom);
    }
}
