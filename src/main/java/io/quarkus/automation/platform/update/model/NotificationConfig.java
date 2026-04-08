package io.quarkus.automation.platform.update.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationConfig {

    private String member;

    private List<String> notify;

    public String getMember() {
        return member;
    }

    public void setMember(String member) {
        this.member = member;
    }

    public List<String> getNotify() {
        return notify;
    }

    public void setNotify(List<String> notify) {
        this.notify = notify;
    }
}
