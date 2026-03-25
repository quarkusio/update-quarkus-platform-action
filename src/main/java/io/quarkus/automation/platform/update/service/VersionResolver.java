package io.quarkus.automation.platform.update.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Singleton;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jboss.logging.Logger;

import eu.maveniverse.domtrip.Document;
import eu.maveniverse.domtrip.Editor;
import eu.maveniverse.domtrip.Element;
import io.quarkus.automation.platform.update.model.UpdatePolicy;

@Singleton
public class VersionResolver {

    private static final Logger LOG = Logger.getLogger(VersionResolver.class);

    private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2/";

    private final HttpClient httpClient;

    public VersionResolver() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Find the latest final version that is newer than currentVersion and matches the given policy.
     * Only considers final versions (no alphas, betas, milestones, RCs, or snapshots).
     */
    public Optional<String> getLatestRelease(String groupId, String artifactId, String currentVersion,
            UpdatePolicy policy) {
        String url = MAVEN_CENTRAL_BASE
                + groupId.replace('.', '/')
                + "/" + artifactId
                + "/maven-metadata.xml";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Quarkus Platform Update GitHub Action")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warnf("Failed to fetch maven-metadata.xml for %s:%s (HTTP %d)", groupId, artifactId,
                        response.statusCode());
                return Optional.empty();
            }

            Document doc = Document.of(response.body());
            Editor editor = new Editor(doc);
            Element root = editor.root();

            Optional<Element> versioning = root.childElement("versioning");
            if (versioning.isEmpty()) {
                LOG.warnf("No <versioning> element in maven-metadata.xml for %s:%s", groupId, artifactId);
                return Optional.empty();
            }

            // For ANY policy, try the <release> shortcut first (it's usually a final version)
            if (policy == UpdatePolicy.ANY) {
                Optional<String> releaseVersion = extractReleaseVersion(versioning.get(), groupId, artifactId);
                if (releaseVersion.isPresent() && isFinalVersion(releaseVersion.get())
                        && isNewer(currentVersion, releaseVersion.get())) {
                    return releaseVersion;
                }
                // If <release> is not final or not newer, fall through to scanning all versions
            }

            // Scan all versions and find the newest final one matching the policy
            return findBestMatchingVersion(versioning.get(), currentVersion, policy, groupId, artifactId);

        } catch (Exception e) {
            LOG.warnf("Error fetching latest version for %s:%s: %s", groupId, artifactId, e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isNewer(String currentVersion, String candidateVersion) {
        ComparableVersion current = new ComparableVersion(currentVersion);
        ComparableVersion candidate = new ComparableVersion(candidateVersion);
        return candidate.compareTo(current) > 0;
    }

    boolean matchesPolicy(String currentVersion, String candidateVersion, UpdatePolicy policy) {
        if (policy == UpdatePolicy.ANY) {
            return true;
        }

        int[] currentParts = parseVersionParts(currentVersion);
        int[] candidateParts = parseVersionParts(candidateVersion);

        // Major must always match for MINOR and MICRO
        if (currentParts[0] != candidateParts[0]) {
            return false;
        }

        // For MICRO, minor must also match
        if (policy == UpdatePolicy.MICRO && currentParts[1] != candidateParts[1]) {
            return false;
        }

        return true;
    }

    private Optional<String> extractReleaseVersion(Element versioning, String groupId, String artifactId) {
        Optional<Element> release = versioning.childElement("release");
        if (release.isPresent()) {
            String version = release.get().textContentTrimmed();
            if (!version.isEmpty()) {
                return Optional.of(version);
            }
        }

        Optional<Element> latest = versioning.childElement("latest");
        if (latest.isPresent()) {
            String version = latest.get().textContentTrimmed();
            if (!version.isEmpty()) {
                return Optional.of(version);
            }
        }

        LOG.warnf("No release version found in maven-metadata.xml for %s:%s", groupId, artifactId);
        return Optional.empty();
    }

    private Optional<String> findBestMatchingVersion(Element versioning, String currentVersion,
            UpdatePolicy policy, String groupId, String artifactId) {
        Optional<Element> versionsOpt = versioning.childElement("versions");
        if (versionsOpt.isEmpty()) {
            LOG.warnf("No <versions> element in maven-metadata.xml for %s:%s", groupId, artifactId);
            return Optional.empty();
        }

        List<String> allVersions = new ArrayList<>();
        versionsOpt.get().childElements("version").forEach(v -> {
            String ver = v.textContentTrimmed();
            if (!ver.isEmpty()) {
                allVersions.add(ver);
            }
        });

        ComparableVersion current = new ComparableVersion(currentVersion);
        String bestVersion = null;
        ComparableVersion bestComparable = null;

        for (String candidate : allVersions) {
            if (!isFinalVersion(candidate)) {
                continue;
            }

            if (!matchesPolicy(currentVersion, candidate, policy)) {
                continue;
            }

            ComparableVersion candidateComparable = new ComparableVersion(candidate);
            if (candidateComparable.compareTo(current) <= 0) {
                continue;
            }

            if (bestComparable == null || candidateComparable.compareTo(bestComparable) > 0) {
                bestVersion = candidate;
                bestComparable = candidateComparable;
            }
        }

        return Optional.ofNullable(bestVersion);
    }

    /**
     * A version is "final" if it has no pre-release qualifiers.
     * Uses Maven's own ComparableVersion ordering: pre-release qualifiers
     * (alpha, beta, milestone, rc/cr, snapshot) sort before the numeric base,
     * while final versions (release, sp, .Final) sort at or after it.
     */
    static boolean isFinalVersion(String version) {
        String numericBase = extractNumericBase(version);
        if (numericBase.isEmpty()) {
            return false;
        }
        return new ComparableVersion(version).compareTo(new ComparableVersion(numericBase)) >= 0;
    }

    private static String extractNumericBase(String version) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                sb.append(c);
            } else {
                break;
            }
        }
        String result = sb.toString();
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static int[] parseVersionParts(String version) {
        // Strip qualifiers (e.g. "3.17.0.SP1" -> "3.17.0", "3.17.0-alpha1" -> "3.17.0")
        String numericPart = version.split("[^0-9.]", 2)[0];
        String[] parts = numericPart.split("\\.");
        int major = parts.length > 0 ? parseIntSafe(parts[0]) : 0;
        int minor = parts.length > 1 ? parseIntSafe(parts[1]) : 0;
        return new int[] { major, minor };
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
