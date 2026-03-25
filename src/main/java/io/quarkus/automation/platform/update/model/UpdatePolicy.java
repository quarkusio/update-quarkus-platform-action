package io.quarkus.automation.platform.update.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum UpdatePolicy {

    @JsonProperty("any")
    ANY,

    @JsonProperty("minor")
    MINOR,

    @JsonProperty("micro")
    MICRO;
}
