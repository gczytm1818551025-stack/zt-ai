package org.dialectics.ai.server.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aliyun")
@Data
public class AliyunProperties {
    private String accessKeyId;
    private String accessKeySecret;
    private Sms sms;

    @Data
    public static class Sms {
        private String signName;
        private String templateCode;
        private String templateParam;
        private Integer ttl; // min
    }
}
