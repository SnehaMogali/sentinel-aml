package com.sentinel.aml.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "sentinel")
public class HighRiskCountryProperties {

    private List<String> highRiskCountries = new ArrayList<>();
}
