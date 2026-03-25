package io.quarkus.automation.platform.update.service;

import java.nio.file.Path;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkus.automation.platform.update.util.Processes;

@Singleton
public class GitService {

    private static final Logger LOG = Logger.getLogger(GitService.class);

    @Inject
    Processes processes;

    public void configureGitUser(Path repoDir, String name, String email) throws Exception {
        executeGit(repoDir, "config", "user.name", name);
        executeGit(repoDir, "config", "user.email", email);
    }

    public void createBranch(Path repoDir, String branchName) throws Exception {
        executeGit(repoDir, "checkout", "-b", branchName);
    }

    public void checkout(Path repoDir, String branchName) throws Exception {
        executeGit(repoDir, "checkout", branchName);
    }

    public void addAll(Path repoDir) throws Exception {
        executeGit(repoDir, "add", ".");
    }

    public void commit(Path repoDir, String message) throws Exception {
        executeGit(repoDir, "commit", "-m", message);
    }

    public void push(Path repoDir, String branchName) throws Exception {
        executeGit(repoDir, "push", "origin", branchName);
    }

    public void resetHard(Path repoDir, String ref) throws Exception {
        executeGit(repoDir, "reset", "--hard", ref);
    }

    public void clean(Path repoDir) throws Exception {
        executeGit(repoDir, "clean", "-fd");
    }

    private void executeGit(Path repoDir, String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));

        LOG.infof("Executing: %s", String.join(" ", command));
        int exitCode = processes.execute(command, repoDir);
        if (exitCode != 0) {
            throw new RuntimeException("Git command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }
    }
}
