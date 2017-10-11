package com.lapsa.hibernate.validation;

import javax.validation.MessageInterpolator;

import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.hibernate.validator.spi.resourceloading.ResourceBundleLocator;

public class AggregateResourceBundleMessageInterpolator extends ResourceBundleMessageInterpolator
	implements MessageInterpolator {

    private static final ResourceBundleLocator AGGREGATE_RESOURCE_BUNDLE_LOCATOR;

    static {
	AGGREGATE_RESOURCE_BUNDLE_LOCATOR = new AggregateResourceBundleLocator(
		ResourceBundleMessageInterpolator.USER_VALIDATION_MESSAGES);
    }

    public AggregateResourceBundleMessageInterpolator() {
	super(AGGREGATE_RESOURCE_BUNDLE_LOCATOR);
    }
}