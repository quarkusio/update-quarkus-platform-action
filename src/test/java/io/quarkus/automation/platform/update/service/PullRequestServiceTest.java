package io.quarkus.automation.platform.update.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import io.quarkiverse.githubaction.Commands;

class PullRequestServiceTest {

    private PullRequestService service;
    private GHRepository repo;
    private Commands commands;

    @BeforeEach
    void setUp() {
        service = new PullRequestService();
        repo = mock(GHRepository.class);
        commands = mock(Commands.class);
    }

    @Test
    void staleBranchWithNoOpenPRIsDeleted() throws Exception {
        Map<String, GHBranch> branches = new LinkedHashMap<>();
        branches.put("main", mock(GHBranch.class));
        branches.put("update-automation/main-camel-3.18.0", mock(GHBranch.class));
        when(repo.getBranches()).thenReturn(branches);

        mockNoOpenPR("update-automation/main-camel-3.18.0");

        GHRef ref = mock(GHRef.class);
        when(repo.getRef("heads/update-automation/main-camel-3.18.0")).thenReturn(ref);

        service.cleanupStaleBranches(repo, commands);

        verify(ref).delete();
        verify(commands).notice("Cleaned up 1 stale branch(es).");
    }

    @Test
    void branchWithOpenPRIsKept() throws Exception {
        Map<String, GHBranch> branches = new LinkedHashMap<>();
        branches.put("update-automation/main-camel-3.18.0", mock(GHBranch.class));
        when(repo.getBranches()).thenReturn(branches);

        mockOpenPRExists("update-automation/main-camel-3.18.0");

        service.cleanupStaleBranches(repo, commands);

        verify(repo, never()).getRef(anyString());
    }

    @Test
    void nonAutomationBranchesAreIgnored() throws Exception {
        Map<String, GHBranch> branches = new LinkedHashMap<>();
        branches.put("main", mock(GHBranch.class));
        branches.put("feature-branch", mock(GHBranch.class));
        when(repo.getBranches()).thenReturn(branches);

        service.cleanupStaleBranches(repo, commands);

        verify(repo, never()).getRef(anyString());
    }

    @Test
    void staleDependabotBranchIsDeleted() throws Exception {
        Map<String, GHBranch> branches = new LinkedHashMap<>();
        branches.put("dependabot/maven/io.quarkus-3.18.0", mock(GHBranch.class));
        when(repo.getBranches()).thenReturn(branches);

        mockNoOpenPR("dependabot/maven/io.quarkus-3.18.0");

        GHRef ref = mock(GHRef.class);
        when(repo.getRef("heads/dependabot/maven/io.quarkus-3.18.0")).thenReturn(ref);

        service.cleanupStaleBranches(repo, commands);

        verify(ref).delete();
    }

    @Test
    void dependabotBranchWithOpenPRIsKept() throws Exception {
        Map<String, GHBranch> branches = new LinkedHashMap<>();
        branches.put("dependabot/github_actions/actions/checkout-4", mock(GHBranch.class));
        when(repo.getBranches()).thenReturn(branches);

        mockOpenPRExists("dependabot/github_actions/actions/checkout-4");

        service.cleanupStaleBranches(repo, commands);

        verify(repo, never()).getRef(anyString());
    }

    @Test
    void errorOnOneBranchDoesNotStopOthers() throws Exception {
        Map<String, GHBranch> branches = new LinkedHashMap<>();
        branches.put("update-automation/main-camel-3.18.0", mock(GHBranch.class));
        branches.put("update-automation/main-cxf-2.5.0", mock(GHBranch.class));
        when(repo.getBranches()).thenReturn(branches);

        mockPRQuery(false, "update-automation/main-camel-3.18.0", "update-automation/main-cxf-2.5.0");

        GHRef failingRef = mock(GHRef.class);
        doThrow(new IOException("API error")).when(failingRef).delete();
        when(repo.getRef("heads/update-automation/main-camel-3.18.0")).thenReturn(failingRef);

        GHRef successRef = mock(GHRef.class);
        when(repo.getRef("heads/update-automation/main-cxf-2.5.0")).thenReturn(successRef);

        service.cleanupStaleBranches(repo, commands);

        verify(successRef).delete();
        verify(commands).warning(org.mockito.ArgumentMatchers.contains("update-automation/main-camel-3.18.0"));
    }

    @SuppressWarnings("unchecked")
    private void mockPRQuery(boolean hasOpenPR, String... branchNames) throws IOException {
        GHPullRequestQueryBuilder query = mock(GHPullRequestQueryBuilder.class);
        when(repo.queryPullRequests()).thenReturn(query);
        when(query.state(GHIssueState.OPEN)).thenReturn(query);
        for (String branchName : branchNames) {
            when(query.head(branchName)).thenReturn(query);
        }
        PagedIterable<?> iterable = mock(PagedIterable.class);
        when(query.list()).thenReturn((PagedIterable) iterable);
        PagedIterator<?> iterator = mock(PagedIterator.class);
        when(iterable.iterator()).thenReturn((PagedIterator) iterator);
        when(iterator.hasNext()).thenReturn(hasOpenPR);
    }

    private void mockNoOpenPR(String branchName) throws IOException {
        mockPRQuery(false, branchName);
    }

    private void mockOpenPRExists(String branchName) throws IOException {
        mockPRQuery(true, branchName);
    }
}
