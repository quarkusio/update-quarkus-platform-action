package io.quarkus.automation.platform.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.ConfigFile;
import io.quarkiverse.githubaction.Context;
import io.quarkus.automation.platform.update.model.BranchConfig;
import io.quarkus.automation.platform.update.model.MemberConfig;
import io.quarkus.automation.platform.update.model.PlatformMember;
import io.quarkus.automation.platform.update.model.UpdatePlatformConfig;
import io.quarkus.automation.platform.update.model.UpdatePolicy;
import io.quarkus.automation.platform.update.service.GitService;
import io.quarkus.automation.platform.update.service.PlatformMemberService;
import io.quarkus.automation.platform.update.service.PullRequestService;
import io.quarkus.automation.platform.update.service.VersionResolver;
import io.quarkus.automation.platform.update.util.Processes;

public class UpdatePlatformMembersAction {

    private static final Logger LOG = Logger.getLogger(UpdatePlatformMembersAction.class);

    private static final String GIT_USER_NAME = "github-actions[bot]";
    private static final String GIT_USER_EMAIL = "github-actions[bot]@users.noreply.github.com";

    private static final String LATEST_PSEUDO_BRANCH = "latest";
    private static final Pattern VERSION_BRANCH_PATTERN = Pattern.compile("\\d+\\.\\d+");

    @Inject
    PlatformMemberService platformMemberService;

    @Inject
    VersionResolver versionResolver;

    @Inject
    GitService gitService;

    @Inject
    PullRequestService pullRequestService;

    @Inject
    Processes processes;

    @Action
    void updateMembers(Commands commands, Context context, GitHub gitHub,
            @ConfigFile("update-quarkus-platform.yml") UpdatePlatformConfig config) throws Exception {
        if (config == null) {
            commands.warning("No configuration found in .github/update-quarkus-platform.yml");
            return;
        }

        GHRepository repo = gitHub.getRepository(context.getGitHubRepository());
        Path repoDir = Path.of(System.getenv("GITHUB_WORKSPACE"));
        String defaultBranch = repo.getDefaultBranch();

        // Configure git user
        gitService.configureGitUser(repoDir, GIT_USER_NAME, GIT_USER_EMAIL);

        // Build the map of branches to process with their resolved configs
        Map<String, ResolvedConfig> branchesToProcess = resolveBranches(config, defaultBranch, repo);

        if (branchesToProcess.isEmpty()) {
            commands.warning("No branches with valid configuration found");
            return;
        }

        int totalUpdatesCreated = 0;

        for (Map.Entry<String, ResolvedConfig> entry : branchesToProcess.entrySet()) {
            String baseBranch = entry.getKey();
            ResolvedConfig resolvedConfig = entry.getValue();

            commands.notice("Processing branch " + baseBranch + " with " + resolvedConfig.members().size()
                    + " tracked member(s) (default policy: " + resolvedConfig.defaultUpdatePolicy() + ")");

            try {
                int updates = processBranch(commands, repo, repoDir, baseBranch, resolvedConfig);
                totalUpdatesCreated += updates;
            } catch (Exception e) {
                commands.warning("Failed to process branch " + baseBranch + ": " + e.getMessage());
                LOG.error("Failed to process branch " + baseBranch, e);
            }

            // Reset to default branch before processing next branch
            try {
                gitService.checkout(repoDir, defaultBranch);
                gitService.resetHard(repoDir, "origin/" + defaultBranch);
                gitService.clean(repoDir);
            } catch (Exception resetException) {
                LOG.error("Failed to reset to default branch", resetException);
            }
        }

        commands.notice("Platform member update check complete. Created " + totalUpdatesCreated + " PR(s).");
    }

    /**
     * Builds an ordered map of branch name to resolved config for all branches to process.
     * The default branch is always first (if it has members configured), followed by branches
     * from the branches map with the latest pseudo-branch resolved.
     */
    Map<String, ResolvedConfig> resolveBranches(UpdatePlatformConfig config, String defaultBranch,
            GHRepository repo) throws IOException {
        Map<String, ResolvedConfig> result = new LinkedHashMap<>();

        // Default branch uses root config
        if (config.getMembers() != null && !config.getMembers().isEmpty()) {
            result.put(defaultBranch, new ResolvedConfig(
                    config.getMembers(),
                    config.getDefaultUpdatePolicy() != null ? config.getDefaultUpdatePolicy() : UpdatePolicy.ANY));
        }

        Map<String, BranchConfig> branches = config.getBranches();
        if (branches == null || branches.isEmpty()) {
            return result;
        }

        // Resolve latest pseudo-branch name once
        String latestBranchName = null;
        if (branches.containsKey(LATEST_PSEUDO_BRANCH)) {
            latestBranchName = resolveLatestBranch(repo);
            if (latestBranchName == null) {
                LOG.warn("'latest' pseudo-branch configured but no X.Y branches found in repository");
            }
        }

        // Process exact branch matches first so they take priority over 'latest'
        for (Map.Entry<String, BranchConfig> entry : branches.entrySet()) {
            String key = entry.getKey();
            if (LATEST_PSEUDO_BRANCH.equals(key)) {
                continue;
            }

            BranchConfig branchConfig = entry.getValue();
            if (branchConfig.getMembers() == null || branchConfig.getMembers().isEmpty()) {
                LOG.warnf("No members configured for branch %s, skipping", key);
                continue;
            }

            result.put(key, new ResolvedConfig(
                    branchConfig.getMembers(),
                    branchConfig.getDefaultUpdatePolicy() != null
                            ? branchConfig.getDefaultUpdatePolicy()
                            : UpdatePolicy.ANY));
        }

        // Process 'latest' pseudo-branch last so exact matches take priority
        if (latestBranchName != null && !result.containsKey(latestBranchName)) {
            BranchConfig latestConfig = branches.get(LATEST_PSEUDO_BRANCH);
            if (latestConfig.getMembers() == null || latestConfig.getMembers().isEmpty()) {
                LOG.warnf("No members configured for branch %s (latest), skipping", latestBranchName);
            } else {
                result.put(latestBranchName, new ResolvedConfig(
                        latestConfig.getMembers(),
                        latestConfig.getDefaultUpdatePolicy() != null
                                ? latestConfig.getDefaultUpdatePolicy()
                                : UpdatePolicy.ANY));
            }
        }

        return result;
    }

    /**
     * Finds the latest X.Y-formatted branch in the repository.
     */
    String resolveLatestBranch(GHRepository repo) throws IOException {
        Map<String, GHBranch> allBranches = repo.getBranches();
        String latestBranch = null;
        ComparableVersion latestVersion = null;

        for (String name : allBranches.keySet()) {
            if (VERSION_BRANCH_PATTERN.matcher(name).matches()) {
                ComparableVersion version = new ComparableVersion(name);
                if (latestVersion == null || version.compareTo(latestVersion) > 0) {
                    latestBranch = name;
                    latestVersion = version;
                }
            }
        }

        return latestBranch;
    }

    private int processBranch(Commands commands, GHRepository repo, Path repoDir,
            String baseBranch, ResolvedConfig resolvedConfig) throws Exception {

        // Checkout the target branch
        gitService.checkout(repoDir, baseBranch);
        gitService.resetHard(repoDir, "origin/" + baseBranch);
        gitService.clean(repoDir);

        Map<String, MemberConfig> memberConfigs = resolvedConfig.members().stream()
                .collect(Collectors.toMap(MemberConfig::getName, Function.identity()));

        Set<String> trackedNames = memberConfigs.keySet();

        Path pomPath = repoDir.resolve("pom.xml");
        List<PlatformMember> allMembers = platformMemberService.parseMembers(pomPath);

        List<PlatformMember> trackedMembers = allMembers.stream()
                .filter(m -> trackedNames.contains(m.getName()))
                .toList();

        if (trackedMembers.isEmpty()) {
            commands.warning("No tracked members found in pom.xml for branch " + baseBranch);
            return 0;
        }

        LOG.infof("Checking %d tracked members for updates on branch %s", trackedMembers.size(), baseBranch);

        int updatesCreated = 0;
        UpdatePolicy defaultPolicy = resolvedConfig.defaultUpdatePolicy();

        for (PlatformMember member : trackedMembers) {
            try {
                MemberConfig memberConfig = memberConfigs.get(member.getName());
                UpdatePolicy effectivePolicy = memberConfig.getUpdatePolicy() != null
                        ? memberConfig.getUpdatePolicy()
                        : defaultPolicy;
                boolean created = processMember(commands, repo, repoDir, pomPath, baseBranch, member,
                        effectivePolicy);
                if (created) {
                    updatesCreated++;
                }
            } catch (Exception e) {
                commands.warning("Failed to process member " + member.getName()
                        + " on branch " + baseBranch + ": " + e.getMessage());
                LOG.error("Failed to process member " + member.getName() + " on branch " + baseBranch, e);
                // Reset to clean state and continue with next member
                try {
                    gitService.checkout(repoDir, baseBranch);
                    gitService.resetHard(repoDir, "origin/" + baseBranch);
                    gitService.clean(repoDir);
                } catch (Exception resetException) {
                    LOG.error("Failed to reset after error", resetException);
                }
            }
        }

        return updatesCreated;
    }

    private boolean processMember(Commands commands, GHRepository repo, Path repoDir, Path pomPath,
            String baseBranch, PlatformMember member, UpdatePolicy policy) throws Exception {

        commands.notice("Checking " + member.getName() + " (" + member.getGroupId() + ":" + member.getArtifactId()
                + ") on branch " + baseBranch + " - current version: " + member.getCurrentVersion()
                + ", policy: " + policy);

        // Check Maven Central for latest version matching the policy
        Optional<String> latestOpt = versionResolver.getLatestRelease(member.getGroupId(), member.getArtifactId(),
                member.getCurrentVersion(), policy);
        if (latestOpt.isEmpty()) {
            commands.notice(member.getName() + " is up to date at " + member.getCurrentVersion());
            return false;
        }
        String latestVersion = latestOpt.get();

        commands.notice(member.getName() + " has update available: " + member.getCurrentVersion() + " -> " + latestVersion);

        // Check for existing open PR
        String branchName = buildBranchName(baseBranch, member, latestVersion);
        if (pullRequestService.hasOpenPR(repo, branchName)) {
            commands.notice("PR already open for " + member.getName() + " " + latestVersion + ", skipping");
            return false;
        }

        // Ensure we are on the base branch, clean
        gitService.checkout(repoDir, baseBranch);
        gitService.resetHard(repoDir, "origin/" + baseBranch);
        gitService.clean(repoDir);

        // Create branch
        gitService.createBranch(repoDir, branchName);

        // Update version property in pom.xml
        platformMemberService.updateVersionProperty(pomPath, member.getVersionProperty(), latestVersion);

        // Run ./mvnw -Dsync
        int syncResult = processes.execute(List.of("./mvnw", "-Dsync"), repoDir);
        if (syncResult != 0) {
            throw new RuntimeException("./mvnw -Dsync failed for " + member.getName() + " with exit code " + syncResult);
        }

        // Stage, commit, push
        gitService.addAll(repoDir);
        String commitMsg = "Update " + member.getName() + " to " + latestVersion;
        gitService.commit(repoDir, commitMsg);
        gitService.push(repoDir, branchName);

        // Create PR
        GHPullRequest pr = pullRequestService.createPullRequest(repo, branchName, baseBranch, member, latestVersion);
        commands.notice("Created PR #" + pr.getNumber() + " for " + member.getName() + " " + latestVersion);

        return true;
    }

    static String buildBranchName(String baseBranch, PlatformMember member, String newVersion) {
        return "update-platform/"
                + baseBranch + "-"
                + member.getName().toLowerCase(Locale.ROOT).replace(" ", "-")
                + "-" + newVersion;
    }

    record ResolvedConfig(List<MemberConfig> members, UpdatePolicy defaultUpdatePolicy) {
    }
}
