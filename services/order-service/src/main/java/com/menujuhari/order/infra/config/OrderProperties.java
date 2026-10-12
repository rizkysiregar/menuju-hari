package com.menujuhari.order.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(int orderTtlHours, int orderNumberMaxAttempts) {

	public OrderProperties {
		if (orderTtlHours <= 0) {
			orderTtlHours = 24;
		}
		if (orderNumberMaxAttempts <= 0) {
			orderNumberMaxAttempts = 5;
		}
	}
}
