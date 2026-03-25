package io.quarkus.automation.platform.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;

import io.quarkus.automation.platform.update.model.BranchConfig;
import io.quarkus.automation.platform.update.model.MemberConfig;
import io.quarkus.automation.platform.update.model.UpdatePlatformConfig;
import io.quarkus.automation.platform.update.model.UpdatePolicy;

class UpdatePlatformMembersActionTest {

    private UpdatePlatformMembersAction action;
    private GHRepository repo;

    @BeforeEach
    void setUp() throws IOException {
        action = new UpdatePlatformMembersAction();
        repo = mock(GHRepository.class);
    }

    @Test
    void defaultBranchUsesRootConfig() throws IOException {
        UpdatePlatformConfig config = new UpdatePlatformConfig();
        config.setMembers(List.of(memberConfig("Camel")));

        Map<String, UpdatePlatformMembersAction.ResolvedConfig> result =
                action.resolveBranches(config, "main", repo);

        assertThat(result).containsKey("main");
        assertThat(result.get("main").members()).hasSize(1);
        assertThat(result.get("main").members().get(0).getName()).isEqualTo("Camel");
        assertThat(result.get("main").defaultUpdatePolicy()).isEqualTo(UpdatePolicy.ANY);
    }

    @Test
    void defaultBranchUsesRootDefaultPolicy() throws IOException {
        UpdatePlatformConfig config = new UpdatePlatformConfig();
        config.setMembers(List.of(memberConfig("Camel")));
        config.setDefaultUpdatePolicy(UpdatePolicy.MINOR);

        Map<String, UpdatePlatformMembersAction.ResolvedConfig> result =
                action.resolveBranches(config, "main", repo);

        assertThat(result.get("main").defaultUpdatePolicy()).isEqualTo(UpdatePolicy.MINOR);
    }

    @Test
    void exactBranchMatch() throws IOException {
        UpdatePlatformConfig config = new UpdatePlatformConfig();
        config.setMembers(List.of(memberConfig("Camel")));

        BranchConfig branchConfig = new BranchConfig();
        branchConfig.setMembers(List.of(memberConfig("CXF")));
        branchConfig.setDefaultUpdatePolicy(UpdatePolicy.MICRO);
        config.setBranches(Map.of("3.33", branchConfig));

        Map<String, UpdatePlatformMembersAction.ResolvedConfig> result =
                action.resolveBranches(config, "main", repo);

        assertThat(result).containsKeys("main", "3.33");
        assertThat(result.get("3.33").members().get(0).getName()).isEqualTo("CXF");
        assertThat(result.get("3.33").defaultUpdatePolicy()).isEqualTo(UpdatePolicy.MICRO);
    }

    @Test
    void latestResolvesToNewestVersionBranch() throws IOException {
        UpdatePlatformConfig config = new UpdatePlatformConfig();
        config.setMembers(List.of(memberConfig("Camel")));

        BranchConfig latestConfig = new BranchConfig();
        latestConfig.setMembers(List.of(memberConfig("CXF")));
        latestConfig.setDefaultUpdatePolicy(UpdatePolicy.MICRO);
        config.setBranches(Map.of("latest", latestConfig));

        Map<String, GHBranch> branches = new LinkedHashMap<>();
        branches.put("main", mock(GHBranch.class));
        branches.put("3.31", mock(GHBranch.class));
        branches.put("3.32", mock(GHBranch.class));
        branches.put("3.33", mock(GHBranch.class));
        when(repo.getBranches()).thenReturn(branches);

        Map<String, UpdatePlatformMembersAction.ResolvedConfig> result =
                action.resolveBranches(config, "main", repo);

        // latest resolves to 3.33
        assertThat(result).containsKeys("main", "3.33");
        assertThat(result.get("3.33").members().get(0).getName()).isEqualTo("CXF");
        assertThat(result.get("3.33").defaultUpdatePolicy()).isEqualTo(UpdatePolicy.MICRO);
    }

    @Test
    void exactMatchTakesPriorityOverLatest() throws IOException {
        UpdatePlatformConfig config = new UpdatePlatformConfig();
        config.setMembers(List.of(memberConfig("Camel")));

        BranchConfig exactConfig = new BranchConfig();
        exactConfig.setMembers(List.of(memberConfig("Debezium")));
        exactConfig.setDefaultUpdatePolicy(UpdatePolicy.MINOR);

        BranchConfig latestConfig = new BranchConfig();
        latestConfig.setMembers(List.of(memberConfig("CXF")));
        latestConfig.setDefaultUpdatePolicy(UpdatePolicy.MICRO);

        // Put 'latest' first to verify exact match takes priority regardless of map order
        Map<String, BranchConfig> branchesMap = new LinkedHashMap<>();
        branchesMap.put("latest", latestConfig);
        branchesMap.put("3.33", exactConfig);
        config.setBranches(branchesMap);

        Map<String, GHBranch> branches = new LinkedHashMap<>();
        branches.put("main", mock(GHBranch.class));
        branches.put("3.33", mock(GHBranch.class));
        when(repo.getBranches()).thenReturn(branches);

        Map<String, UpdatePlatformMembersAction.ResolvedConfig> result =
                action.resolveBranches(config, "main", repo);

        // 3.33 has exact match, latest also resolves to 3.33 but is skipped
        assertThat(result).containsKeys("main", "3.33");
        assertThat(result.get("3.33").members().get(0).getName()).isEqualTo("Debezium");
        assertThat(result.get("3.33").defaultUpdatePolicy()).isEqualTo(UpdatePolicy.MINOR);
    }

    @Test
    void latestWithNoVersionBranchesIsSkipped() throws IOException {
        UpdatePlatformConfig config = new UpdatePlatformConfig();
        config.setMembers(List.of(memberConfig("Camel")));

        BranchConfig latestConfig = new BranchConfig();
        latestConfig.setMembers(List.of(memberConfig("CXF")));
        config.setBranches(Map.of("latest", latestConfig));

        Map<String, GHBranch> branches = new LinkedHashMap<>();
        branches.put("main", mock(GHBranch.class));
        branches.put("feature-branch", mock(GHBranch.class));
        when(repo.getBranches()).thenReturn(branches);

        Map<String, UpdatePlatformMembersAction.ResolvedConfig> result =
                action.resolveBranches(config, "main", repo);

        // Only main, no X.Y branches exist
        assertThat(result).containsOnlyKeys("main");
    }

    @Test
    void noBranchesConfigOnlyProcessesDefaultBranch() throws IOException {
        UpdatePlatformConfig config = new UpdatePlatformConfig();
        config.setMembers(List.of(memberConfig("Camel")));

        Map<String, UpdatePlatformMembersAction.ResolvedConfig> result =
                action.resolveBranches(config, "main", repo);

        assertThat(result).containsOnlyKeys("main");
    }

    @Test
    void branchConfigDefaultPolicyDefaultsToAny() throws IOException {
        UpdatePlatformConfig config = new UpdatePlatformConfig();
        config.setMembers(List.of(memberConfig("Camel")));

        BranchConfig branchConfig = new BranchConfig();
        branchConfig.setMembers(List.of(memberConfig("CXF")));
        // no defaultUpdatePolicy set
        config.setBranches(Map.of("3.33", branchConfig));

        Map<String, UpdatePlatformMembersAction.ResolvedConfig> result =
                action.resolveBranches(config, "main", repo);

        assertThat(result.get("3.33").defaultUpdatePolicy()).isEqualTo(UpdatePolicy.ANY);
    }

    @Test
    void noRootMembersStillProcessesBranches() throws IOException {
        UpdatePlatformConfig config = new UpdatePlatformConfig();
        // No root members

        BranchConfig branchConfig = new BranchConfig();
        branchConfig.setMembers(List.of(memberConfig("CXF")));
        branchConfig.setDefaultUpdatePolicy(UpdatePolicy.MICRO);
        config.setBranches(Map.of("3.33", branchConfig));

        Map<String, UpdatePlatformMembersAction.ResolvedConfig> result =
                action.resolveBranches(config, "main", repo);

        assertThat(result).containsOnlyKeys("3.33");
        assertThat(result).doesNotContainKey("main");
    }

    @Test
    void branchWithNoMembersIsSkipped() throws IOException {
        UpdatePlatformConfig config = new UpdatePlatformConfig();
        config.setMembers(List.of(memberConfig("Camel")));

        BranchConfig branchConfig = new BranchConfig();
        // no members set
        config.setBranches(Map.of("3.33", branchConfig));

        Map<String, UpdatePlatformMembersAction.ResolvedConfig> result =
                action.resolveBranches(config, "main", repo);

        assertThat(result).containsOnlyKeys("main");
    }

    @Test
    void resolveLatestBranchFindsNewest() throws IOException {
        Map<String, GHBranch> branches = new LinkedHashMap<>();
        branches.put("main", mock(GHBranch.class));
        branches.put("3.31", mock(GHBranch.class));
        branches.put("3.32", mock(GHBranch.class));
        branches.put("3.33", mock(GHBranch.class));
        branches.put("feature-branch", mock(GHBranch.class));
        when(repo.getBranches()).thenReturn(branches);

        assertThat(action.resolveLatestBranch(repo)).isEqualTo("3.33");
    }

    @Test
    void resolveLatestBranchReturnsNullWhenNoVersionBranches() throws IOException {
        Map<String, GHBranch> branches = new LinkedHashMap<>();
        branches.put("main", mock(GHBranch.class));
        branches.put("feature-branch", mock(GHBranch.class));
        when(repo.getBranches()).thenReturn(branches);

        assertThat(action.resolveLatestBranch(repo)).isNull();
    }

    private static MemberConfig memberConfig(String name) {
        MemberConfig config = new MemberConfig();
        config.setName(name);
        return config;
    }
}
