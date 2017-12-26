package tech.lapsa.payara.hibernate.interpolator;

import java.io.IOException;
import java.io.InputStream;
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

import org.hibernate.validator.internal.util.privilegedactions.GetClassLoader;
import org.hibernate.validator.spi.resourceloading.ResourceBundleLocator;

import tech.lapsa.java.commons.logging.MyLogger;

public class AggregateResourceBundleLocator implements ResourceBundleLocator {

    private static final MyLogger logger = MyLogger.newBuilder() //
	    .withNameOf(AggregateResourceBundleLocator.class) //
	    .build();

    private static final boolean RESOURCE_BUNDLE_CONTROL_INSTANTIABLE = determineAvailabilityOfResourceBundleControl();

    private final String bundleName;
    private final boolean aggregate;

    public AggregateResourceBundleLocator(final String bundleName) {
	this.bundleName = bundleName;
	aggregate = RESOURCE_BUNDLE_CONTROL_INSTANTIABLE;
    }

    @Override
    public ResourceBundle getResourceBundle(final Locale locale) {
	ResourceBundle rb = null;

	if (rb == null) {
	    final ClassLoader classLoader = run(GetClassLoader.fromContext());
	    if (classLoader != null)
		rb = loadBundle(classLoader, locale, bundleName + " not found by thread context classloader");
	}

	if (rb == null) {
	    final ClassLoader classLoader = run(GetClassLoader.fromClass(AggregateResourceBundleLocator.class));
	    rb = loadBundle(classLoader, locale, bundleName + " not found by validator classloader");
	}

	if (rb != null)
	    logger.FINE.log("%s found.", bundleName);
	else
	    logger.FINE.log("%s not found.", bundleName);
	return rb;
    }

    private ResourceBundle loadBundle(final ClassLoader classLoader, final Locale locale, final String message) {
	ResourceBundle rb = null;
	try {
	    if (aggregate)
		rb = ResourceBundle.getBundle(
			bundleName,
			locale,
			classLoader,
			AggregateResourceBundle.CONTROL);
	    else
		rb = ResourceBundle.getBundle(
			bundleName,
			locale,
			classLoader);
	} catch (final MissingResourceException e) {
	    logger.FINE.log(e, message);
	}
	return rb;
    }

    private static <T> T run(final PrivilegedAction<T> action) {
	return System.getSecurityManager() != null ? AccessController.doPrivileged(action) : action.run();
    }

    private static boolean determineAvailabilityOfResourceBundleControl() {
	try {
	    @SuppressWarnings("unused")
	    final ResourceBundle.Control dummyControl = AggregateResourceBundle.CONTROL;
	    return true;
	} catch (final NoClassDefFoundError e) {
	    logger.INFO.log("unable to use resource bundle aggregation");
	    return false;
	}
    }

    private static class AggregateResourceBundle extends ResourceBundle {

	protected static final Control CONTROL = new AggregateResourceBundleControl();
	private final Properties properties;

	protected AggregateResourceBundle(final Properties properties) {
	    this.properties = properties;
	}

	@Override
	protected Object handleGetObject(final String key) {
	    return properties.get(key);
	}

	@Override
	public Enumeration<String> getKeys() {
	    final Set<String> keySet = new HashSet<>();
	    keySet.addAll(properties.stringPropertyNames());
	    if (parent != null)
		keySet.addAll(Collections.list(parent.getKeys()));
	    return Collections.enumeration(keySet);
	}
    }

    private static class AggregateResourceBundleControl extends ResourceBundle.Control {
	@Override
	public ResourceBundle newBundle(
		final String baseName,
		final Locale locale,
		final String format,
		final ClassLoader loader,
		final boolean reload)
		throws IllegalAccessException, InstantiationException, IOException {
	    // only *.properties files can be aggregated. Other formats are
	    // delegated to the default implementation
	    if (!"java.properties".equals(format))
		return super.newBundle(baseName, locale, format, loader, reload);

	    final String resourceName = toBundleName(baseName, locale) + ".properties";
	    final Properties properties = load(resourceName, loader);
	    return properties.size() == 0 ? null : new AggregateResourceBundle(properties);
	}

	private Properties load(final String resourceName, final ClassLoader loader) throws IOException {
	    final Properties aggregatedProperties = new Properties();
	    final Enumeration<URL> urls = run(GetResources.action(loader, resourceName));
	    while (urls.hasMoreElements()) {
		final URL url = urls.nextElement();
		final Properties properties = new Properties();
		try (final InputStream is = url.openStream()) {
		    properties.load(is);
		}
		aggregatedProperties.putAll(properties);
	    }
	    return aggregatedProperties;
	}
    }
}
