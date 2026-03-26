package io.quarkus.automation.platform.update.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import eu.maveniverse.domtrip.Document;
import eu.maveniverse.domtrip.Element;
import eu.maveniverse.domtrip.maven.PomEditor;
import io.quarkus.automation.platform.update.model.PlatformMember;

@Singleton
public class PlatformMemberService {

    private static final Logger LOG = Logger.getLogger(PlatformMemberService.class);

    private static final Pattern PROPERTY_REFERENCE_PATTERN = Pattern.compile("\\$\\{(.+?)}");

    public List<PlatformMember> parseMembers(Path pomPath) throws IOException {
        String pomContent = Files.readString(pomPath);
        Document doc = Document.of(pomContent);
        PomEditor editor = new PomEditor(doc);
        Element root = editor.root();

        Element properties = root.childElement("properties").orElseThrow(
                () -> new IllegalStateException("No <properties> element found in pom.xml"));

        Optional<Element> platformConfigOpt = findPlatformConfig(root);
        if (platformConfigOpt.isEmpty()) {
            LOG.warn("No <platformConfig> element found in pom.xml");
            return List.of();
        }

        Optional<Element> membersOpt = platformConfigOpt.get().childElement("members");
        if (membersOpt.isEmpty()) {
            LOG.warn("No <members> element found in <platformConfig>");
            return List.of();
        }

        List<PlatformMember> members = new ArrayList<>();

        // Parse core dependencies (grouped by version property)
        parseCoreMembers(platformConfigOpt.get(), properties, members);

        membersOpt.get().childElements("member").forEach(memberElement -> {
            String name = memberElement.childTextTrimmed("name");
            if (name == null || name.isEmpty()) {
                LOG.warn("Skipping member with no <name>");
                return;
            }

            String groupId = null;
            String artifactId = null;
            String versionPropertyName = null;

            // Try to extract from <bom> element first
            Optional<Element> bomOpt = memberElement.childElement("bom");
            if (bomOpt.isPresent()) {
                String bomGav = bomOpt.get().textContentTrimmed();
                String[] parts = bomGav.split(":");
                if (parts.length >= 3) {
                    groupId = parts[0];
                    artifactId = parts[1];
                    versionPropertyName = extractPropertyName(parts[2]);
                }
            }

            // Fall back to first dependency in <dependencyManagement>
            if (versionPropertyName == null) {
                Optional<Element> depMgmtOpt = memberElement.childElement("dependencyManagement");
                if (depMgmtOpt.isPresent()) {
                    Optional<Element> firstDep = depMgmtOpt.get().childElements("dependency").findFirst();
                    if (firstDep.isPresent()) {
                        String depGav = firstDep.get().textContentTrimmed();
                        String[] parts = depGav.split(":");
                        if (parts.length >= 3) {
                            groupId = parts[0];
                            artifactId = parts[1];
                            versionPropertyName = extractPropertyName(parts[2]);
                        }
                    }
                }
            }

            if (versionPropertyName == null) {
                LOG.warnf("Could not determine version property for member %s", name);
                return;
            }

            // Resolve property value
            String currentVersion = null;
            Optional<Element> propElement = properties.childElement(versionPropertyName);
            if (propElement.isPresent()) {
                currentVersion = propElement.get().textContentTrimmed();
            }

            if (currentVersion == null || currentVersion.isEmpty()) {
                LOG.warnf("Could not resolve version property %s for member %s", versionPropertyName, name);
                return;
            }

            members.add(new PlatformMember(name, groupId, artifactId, versionPropertyName, currentVersion));
        });

        return members;
    }

    public void updateVersionProperty(Path pomPath, String propertyName, String newVersion) throws IOException {
        String pomContent = Files.readString(pomPath);
        Document doc = Document.of(pomContent);
        PomEditor editor = new PomEditor(doc);
        Element root = editor.root();

        Element properties = root.childElement("properties").orElseThrow(
                () -> new IllegalStateException("No <properties> element found in pom.xml"));

        Element versionProp = properties.childElement(propertyName).orElseThrow(
                () -> new IllegalStateException("Property " + propertyName + " not found in pom.xml"));

        editor.setTextContent(versionProp, newVersion);
        Files.writeString(pomPath, editor.toXml());
    }

    /**
     * Parses dependencies from the core section's dependencyManagement, grouped by version property.
     * For each unique version property, creates one PlatformMember using the first non-deployment artifact.
     * The groupId is used as the member name.
     */
    private void parseCoreMembers(Element platformConfig, Element properties, List<PlatformMember> members) {
        Optional<Element> coreOpt = platformConfig.childElement("core");
        if (coreOpt.isEmpty()) {
            return;
        }

        Optional<Element> depMgmtOpt = coreOpt.get().childElement("dependencyManagement");
        if (depMgmtOpt.isEmpty()) {
            return;
        }

        // Group dependencies by version property, keeping insertion order
        Map<String, String[]> byVersionProperty = new LinkedHashMap<>();

        depMgmtOpt.get().childElements("dependency").forEach(dep -> {
            String gav = dep.textContentTrimmed();
            String[] parts = gav.split(":");
            if (parts.length < 3) {
                return;
            }

            String groupId = parts[0];
            String artifactId = parts[1];
            String versionPropertyName = extractPropertyName(parts[2]);
            if (versionPropertyName == null) {
                return;
            }

            byVersionProperty.putIfAbsent(versionPropertyName, new String[] { groupId, artifactId });
        });

        for (Map.Entry<String, String[]> entry : byVersionProperty.entrySet()) {
            String versionPropertyName = entry.getKey();
            String[] ga = entry.getValue();

            Optional<Element> propElement = properties.childElement(versionPropertyName);
            if (propElement.isEmpty()) {
                LOG.warnf("Could not resolve version property %s for core dependency", versionPropertyName);
                continue;
            }

            String currentVersion = propElement.get().textContentTrimmed();
            if (currentVersion.isEmpty()) {
                continue;
            }

            members.add(new PlatformMember(ga[0], ga[0], ga[1], versionPropertyName, currentVersion));
        }
    }

    /**
     * Finds the platformConfig element inside
     * build > pluginManagement > plugins > plugin(quarkus-platform-bom-maven-plugin) > configuration.
     */
    private static Optional<Element> findPlatformConfig(Element root) {
        Optional<Element> build = root.childElement("build");
        if (build.isEmpty()) {
            return Optional.empty();
        }

        Optional<Element> pluginManagement = build.get().childElement("pluginManagement");
        if (pluginManagement.isEmpty()) {
            return Optional.empty();
        }

        Optional<Element> plugins = pluginManagement.get().childElement("plugins");
        if (plugins.isEmpty()) {
            return Optional.empty();
        }

        Optional<Element> bomPlugin = plugins.get().childElements("plugin")
                .filter(p -> {
                    Optional<Element> artifactId = p.childElement("artifactId");
                    return artifactId.isPresent()
                            && "quarkus-platform-bom-maven-plugin".equals(artifactId.get().textContentTrimmed());
                })
                .findFirst();
        if (bomPlugin.isEmpty()) {
            return Optional.empty();
        }

        Optional<Element> configuration = bomPlugin.get().childElement("configuration");
        if (configuration.isEmpty()) {
            return Optional.empty();
        }

        return configuration.get().childElement("platformConfig");
    }

    private static String extractPropertyName(String versionRef) {
        Matcher matcher = PROPERTY_REFERENCE_PATTERN.matcher(versionRef);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
}
