package io.quarkus.automation.platform.update.service;

import java.io.IOException;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import io.quarkus.automation.platform.update.model.PlatformMember;

@Singleton
public class PullRequestService {

    private static final Logger LOG = Logger.getLogger(PullRequestService.class);

    public boolean hasOpenPR(GHRepository repo, String branchName) throws IOException {
        return repo.queryPullRequests()
                .state(GHIssueState.OPEN)
                .head(branchName)
                .list()
                .iterator()
                .hasNext();
    }

    public GHPullRequest createPullRequest(GHRepository repo, String branchName, String baseBranch,
            PlatformMember member, String newVersion) throws IOException {
        String title = "Update " + member.getName() + " to " + newVersion;
        String body = buildPRBody(member, newVersion);

        LOG.infof("Creating PR: %s", title);
        return repo.createPullRequest(title, branchName, baseBranch, body);
    }

    private String buildPRBody(PlatformMember member, String newVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append("Updates the Quarkus Platform member **").append(member.getName()).append("**");
        sb.append(" from `").append(member.getCurrentVersion()).append("`");
        sb.append(" to `").append(newVersion).append("`.\n\n");
        sb.append("### Changes\n");
        sb.append("- Updated Maven property `").append(member.getVersionProperty()).append("`");
        sb.append(" from `").append(member.getCurrentVersion()).append("`");
        sb.append(" to `").append(newVersion).append("`\n");
        sb.append("- Ran `./mvnw -Dsync` to synchronize the platform\n\n");
        sb.append("### Artifact\n");
        sb.append("- `").append(member.getGroupId()).append(":").append(member.getArtifactId()).append("`\n\n");
        sb.append("---\n");
        sb.append("*This PR was automatically created by the Update Quarkus Platform Action.*\n");
        return sb.toString();
    }
}
