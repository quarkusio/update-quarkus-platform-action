package io.quarkus.automation.platform.update.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.automation.platform.update.model.PlatformMember;

class PlatformMemberServiceTest {

    private static final Path TEST_POM_PATH = Path.of("src/test/resources/test-platform-pom.xml");

    private final PlatformMemberService service = new PlatformMemberService();

    @Test
    void parseMembersFromBom() throws IOException {
        List<PlatformMember> members = service.parseMembers(TEST_POM_PATH);

        PlatformMember camel = members.stream()
                .filter(m -> m.getName().equals("Camel"))
                .findFirst().orElse(null);

        assertThat(camel).isNotNull();
        assertThat(camel.getGroupId()).isEqualTo("org.apache.camel.quarkus");
        assertThat(camel.getArtifactId()).isEqualTo("camel-quarkus-bom");
        assertThat(camel.getVersionProperty()).isEqualTo("camel-quarkus.version");
        assertThat(camel.getCurrentVersion()).isEqualTo("3.17.0");
    }

    @Test
    void parseMembersFromDependencyManagement() throws IOException {
        List<PlatformMember> members = service.parseMembers(TEST_POM_PATH);

        PlatformMember debezium = members.stream()
                .filter(m -> m.getName().equals("Debezium"))
                .findFirst().orElse(null);

        assertThat(debezium).isNotNull();
        assertThat(debezium.getGroupId()).isEqualTo("io.debezium");
        assertThat(debezium.getArtifactId()).isEqualTo("debezium-core");
        assertThat(debezium.getVersionProperty()).isEqualTo("debezium.version");
        assertThat(debezium.getCurrentVersion()).isEqualTo("2.5.0.Final");
    }

    @Test
    void memberWithHardcodedVersionIsSkipped() throws IOException {
        List<PlatformMember> members = service.parseMembers(TEST_POM_PATH);

        assertThat(members.stream().filter(m -> m.getName().equals("NoVersion")).findFirst())
                .isEmpty();
    }

    @Test
    void parseCoreMembersGroupedByVersionProperty() throws IOException {
        List<PlatformMember> members = service.parseMembers(TEST_POM_PATH);

        // Vault has 3 deps (vault, vault-deployment, vault-model) but should produce one member
        PlatformMember vault = members.stream()
                .filter(m -> m.getName().equals("io.quarkiverse.vault"))
                .findFirst().orElse(null);

        assertThat(vault).isNotNull();
        assertThat(vault.getGroupId()).isEqualTo("io.quarkiverse.vault");
        assertThat(vault.getArtifactId()).isEqualTo("quarkus-vault");
        assertThat(vault.getVersionProperty()).isEqualTo("quarkus-vault.version");
        assertThat(vault.getCurrentVersion()).isEqualTo("4.7.0");
    }

    @Test
    void coreNonDeploymentArtifactPreferred() throws IOException {
        List<PlatformMember> members = service.parseMembers(TEST_POM_PATH);

        // Qute Web has runtime + deployment, runtime should be picked
        PlatformMember quteWeb = members.stream()
                .filter(m -> m.getName().equals("io.quarkiverse.qute.web"))
                .findFirst().orElse(null);

        assertThat(quteWeb).isNotNull();
        assertThat(quteWeb.getGroupId()).isEqualTo("io.quarkiverse.qute.web");
        assertThat(quteWeb.getArtifactId()).isEqualTo("quarkus-qute-web");
        assertThat(quteWeb.getCurrentVersion()).isEqualTo("3.4.4");
    }

    @Test
    void updateVersionProperty(@TempDir Path tempDir) throws IOException {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.copy(TEST_POM_PATH, pomPath);

        service.updateVersionProperty(pomPath, "camel-quarkus.version", "3.18.0");

        String updatedContent = Files.readString(pomPath);
        assertThat(updatedContent).contains("<camel-quarkus.version>3.18.0</camel-quarkus.version>");
        assertThat(updatedContent).doesNotContain("<camel-quarkus.version>3.17.0</camel-quarkus.version>");
        assertThat(updatedContent).contains("<debezium.version>2.5.0.Final</debezium.version>");
    }
}
