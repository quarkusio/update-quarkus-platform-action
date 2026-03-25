package io.quarkus.automation.platform.update.model;

public class PlatformMember {

    private final String name;
    private final String groupId;
    private final String artifactId;
    private final String versionProperty;
    private final String currentVersion;

    public PlatformMember(String name, String groupId, String artifactId, String versionProperty, String currentVersion) {
        this.name = name;
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
    public String toString() {
        return name + " (" + groupId + ":" + artifactId + ":" + currentVersion + ")";
    }
}
