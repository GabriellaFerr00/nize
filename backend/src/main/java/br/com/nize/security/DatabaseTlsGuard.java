package br.com.nize.security;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In production the app refuses to start unless the MySQL URL demands TLS (NFR-009, D-14).
 * Runs as a bean factory post-processor, i.e. before the data source opens any connection.
 */
@Component
@Profile("prod")
class DatabaseTlsGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Pattern SSL_MODE = Pattern.compile("[?&]sslMode=([A-Za-z_]+)", Pattern.CASE_INSENSITIVE);

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        requireTls(environment.getProperty("spring.datasource.url"));
    }

    static void requireTls(String url) {
        Matcher m = SSL_MODE.matcher(url == null ? "" : url);
        String mode = m.find() ? m.group(1).toUpperCase(Locale.ROOT) : "";
        if (!(mode.equals("REQUIRED") || mode.equals("VERIFY_CA") || mode.equals("VERIFY_IDENTITY"))) {
            // The URL itself may carry credentials, so it is not echoed.
            throw new IllegalStateException(
                    "NIZE_DB_URL must set sslMode=REQUIRED, VERIFY_CA or VERIFY_IDENTITY in the prod profile");
        }
    }
}
