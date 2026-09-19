package com.sentinel.aml.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "sentinel.rules")
public class DetectionRuleThresholdProperties {

    @NestedConfigurationProperty
    private LargeTransaction largeTransaction = new LargeTransaction();

    @NestedConfigurationProperty
    private Structuring structuring = new Structuring();

    @Getter
    @Setter
    public static class LargeTransaction {
        private BigDecimal threshold;
    }

    @Getter
    @Setter
    public static class Structuring {
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private int minCount;
        private int windowHours;
    }
}
