package io.quarkus.automation.platform.update.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import io.quarkiverse.githubaction.Commands;
import io.quarkus.automation.platform.update.model.PlatformMember;

@Singleton
public class PullRequestService {

    private static final Logger LOG = Logger.getLogger(PullRequestService.class);

    private static final String UPDATE_AUTOMATION_PREFIX = "update-automation/";
    private static final String DEPENDABOT_PREFIX = "dependabot/";

    public void cleanupStaleBranches(GHRepository repo, Commands commands) throws IOException {
        Map<String, GHBranch> allBranches = repo.getBranches();

        int deleted = 0;
        for (String branchName : allBranches.keySet()) {
            if (!branchName.startsWith(UPDATE_AUTOMATION_PREFIX) && !branchName.startsWith(DEPENDABOT_PREFIX)) {
                continue;
            }

            try {
                if (!hasOpenPR(repo, branchName)) {
                    LOG.infof("Deleting stale branch: %s", branchName);
                    repo.getRef("heads/" + branchName).delete();
                    deleted++;
                }
            } catch (Exception e) {
                commands.warning("Failed to clean up branch " + branchName + ": " + e.getMessage());
                LOG.errorf(e, "Failed to clean up branch %s", branchName);
            }
        }

        if (deleted > 0) {
            commands.notice("Cleaned up " + deleted + " stale branch(es).");
        }
    }

    public boolean hasOpenPR(GHRepository repo, String branchName) throws IOException {
        return repo.queryPullRequests()
                .state(GHIssueState.OPEN)
                .head(branchName)
                .list()
                .iterator()
                .hasNext();
    }

    public GHPullRequest createPullRequest(GHRepository repo, String branchName, String baseBranch,
            PlatformMember member, String newVersion, List<String> notify) throws IOException {
        String title = "Update " + member.getName() + " to " + newVersion;
        String body = buildPRBody(member, newVersion, notify);

        LOG.infof("Creating PR: %s", title);
        return repo.createPullRequest(title, branchName, baseBranch, body);
    }

    private String buildPRBody(PlatformMember member, String newVersion, List<String> notify) {
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

        if (notify != null && !notify.isEmpty()) {
            sb.append("\n/cc");
            for (String handle : notify) {
                sb.append(" @").append(handle);
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
