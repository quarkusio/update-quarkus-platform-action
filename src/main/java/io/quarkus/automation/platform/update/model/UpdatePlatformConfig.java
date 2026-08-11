package io.quarkus.automation.platform.update.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdatePlatformConfig {

    @JsonProperty("default-update-policy")
    private UpdatePolicy defaultUpdatePolicy;

    private Integer delay;

    private List<MemberConfig> members;

    private Map<String, BranchConfig> branches;

    private List<NotificationConfig> notifications;

    public UpdatePolicy getDefaultUpdatePolicy() {
        return defaultUpdatePolicy;
    }

    public Integer getDelay() {
        return delay;
    }

    public void setDelay(Integer delay) {
        this.delay = delay;
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

    public Map<String, BranchConfig> getBranches() {
        return branches;
    }

    public void setBranches(Map<String, BranchConfig> branches) {
        this.branches = branches;
    }

    public List<NotificationConfig> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<NotificationConfig> notifications) {
        this.notifications = notifications;
    }
}
