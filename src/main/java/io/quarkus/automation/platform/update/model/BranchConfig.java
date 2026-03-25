package io.quarkus.automation.platform.update.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BranchConfig {

    @JsonProperty("default-update-policy")
    private UpdatePolicy defaultUpdatePolicy;

    private List<MemberConfig> members;

    public UpdatePolicy getDefaultUpdatePolicy() {
        return defaultUpdatePolicy;
    }

    public void setDefaultUpdatePolicy(UpdatePolicy defaultUpdatePolicy) {
        this.defaultUpdatePolicy = defaultUpdatePolicy;
    }

    public List<MemberConfig> getMembers() {
        return members;
    }

    public void setMembers(List<MemberConfig> members) {
        this.members = members;
    }
}
