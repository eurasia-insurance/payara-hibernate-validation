package com.lapsa.hibernate.validation;

import java.io.IOException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hibernate.validator.internal.util.privilegedactions.GetClassLoader;
import org.hibernate.validator.spi.resourceloading.ResourceBundleLocator;

public class AggregateResourceBundleLocator implements ResourceBundleLocator {

    private static final Logger logger = Logger.getLogger(AggregateResourceBundleLocator.class.getName());
    private static final boolean RESOURCE_BUNDLE_CONTROL_INSTANTIABLE = determineAvailabilityOfResourceBundleControl();

    private final String bundleName;
    private final boolean aggregate;

    public AggregateResourceBundleLocator(String bundleName) {
	this.bundleName = bundleName;
	this.aggregate = RESOURCE_BUNDLE_CONTROL_INSTANTIABLE;
    }

    @Override
    public ResourceBundle getResourceBundle(Locale locale) {
	ResourceBundle rb = null;

	if (rb == null) {
	    ClassLoader classLoader = run(GetClassLoader.fromContext());
	    if (classLoader != null)
		rb = loadBundle(classLoader, locale, bundleName + " not found by thread context classloader");
	}

	if (rb == null) {
	    ClassLoader classLoader = run(GetClassLoader.fromClass(AggregateResourceBundleLocator.class));
	    rb = loadBundle(classLoader, locale, bundleName + " not found by validator classloader");
	}

	if (rb != null)
	    logger.fine(String.format("%s found.", bundleName));
	else
	    logger.fine(String.format("%s not found.", bundleName));
	return rb;
    }

    private ResourceBundle loadBundle(ClassLoader classLoader, Locale locale, String message) {
	ResourceBundle rb = null;
	try {
	    if (aggregate) {
		rb = ResourceBundle.getBundle(
			bundleName,
			locale,
			classLoader,
			AggregateResourceBundle.CONTROL);
	    } else {
		rb = ResourceBundle.getBundle(
			bundleName,
			locale,
			classLoader);
	    }
	} catch (MissingResourceException e) {
	    logger.log(Level.FINE, message, e);
	}
	return rb;
    }

    private static <T> T run(PrivilegedAction<T> action) {
	return System.getSecurityManager() != null ? AccessController.doPrivileged(action) : action.run();
    }

    private static boolean determineAvailabilityOfResourceBundleControl() {
	try {
	    @SuppressWarnings("unused")
	    ResourceBundle.Control dummyControl = AggregateResourceBundle.CONTROL;
	    return true;
	} catch (NoClassDefFoundError e) {
	    logger.info("unable to use resource bundle aggregation");
	    return false;
	}
    }

    private static class AggregateResourceBundle extends ResourceBundle {

	protected static final Control CONTROL = new AggregateResourceBundleControl();
	private final Properties properties;

	protected AggregateResourceBundle(Properties properties) {
	    this.properties = properties;
	}

	@Override
	protected Object handleGetObject(String key) {
	    return properties.get(key);
	}

	@Override
	public Enumeration<String> getKeys() {
	    Set<String> keySet = new HashSet<>();
	    keySet.addAll(properties.stringPropertyNames());
	    if (parent != null) {
		keySet.addAll(Collections.list(parent.getKeys()));
	    }
	    return Collections.enumeration(keySet);
	}
    }

    private static class AggregateResourceBundleControl extends ResourceBundle.Control {
	@Override
	public ResourceBundle newBundle(
		String baseName,
		Locale locale,
		String format,
		ClassLoader loader,
		boolean reload)
		throws IllegalAccessException, InstantiationException, IOException {
	    // only *.properties files can be aggregated. Other formats are
	    // delegated to the default implementation
	    if (!"java.properties".equals(format)) {
		return super.newBundle(baseName, locale, format, loader, reload);
	    }

	    String resourceName = toBundleName(baseName, locale) + ".properties";
	    Properties properties = load(resourceName, loader);
	    return properties.size() == 0 ? null : new AggregateResourceBundle(properties);
	}

	private Properties load(String resourceName, ClassLoader loader) throws IOException {
	    Properties aggregatedProperties = new Properties();
	    Enumeration<URL> urls = run(GetResources.action(loader, resourceName));
	    while (urls.hasMoreElements()) {
		URL url = urls.nextElement();
		Properties properties = new Properties();
		properties.load(url.openStream());
		aggregatedProperties.putAll(properties);
	    }
	    return aggregatedProperties;
	}
    }
}
