package com.learningLadder.learningLadder.config.backgroundJob.userSeederAsync;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.bootstrap")
public class BootstrapUserProperties {

    private boolean enabled = true;

    private Map<String, Credentials> users = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Credentials {
        private String email;
        private String password;
    }
}