package io.quarkus.automation.platform.update.model;

import java.util.Objects;

public class PlatformMember {

    private final String name;
    private final String groupId;
    private final String artifactId;
    private final String versionProperty;
    private final String currentVersion;

    public PlatformMember(String name, String groupId, String artifactId, String versionProperty, String currentVersion) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.versionProperty = versionProperty;
        this.currentVersion = currentVersion;
    }

    public String getName() {
        return name;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersionProperty() {
        return versionProperty;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PlatformMember that = (PlatformMember) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + groupId + ":" + artifactId + ":" + currentVersion + ")";
    }
}
