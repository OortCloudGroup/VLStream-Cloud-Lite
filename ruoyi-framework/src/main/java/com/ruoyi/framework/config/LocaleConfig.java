package com.ruoyi.framework.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Resolves the UI locale from the standard Accept-Language request header.
 */
@Configuration
public class LocaleConfig
{
    private static final List<Locale> SUPPORTED_LOCALES = Collections.unmodifiableList(Arrays.asList(
            Locale.SIMPLIFIED_CHINESE,
            Locale.US,
            new Locale("es", "MX"),
            new Locale("ar", "SA"),
            Locale.GERMANY,
            Locale.FRANCE,
            Locale.JAPAN,
            new Locale("pt", "BR"),
            new Locale("ru", "RU"),
            Locale.KOREA,
            new Locale("id", "ID"),
            new Locale("tr", "TR")));

    @Bean
    public LocaleResolver localeResolver()
    {
        AcceptHeaderLocaleResolver resolver = new SupportedLocaleResolver();
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        return resolver;
    }

    /** Accepts both the public short code (for example, es) and the selected regional code (es-MX). */
    private static class SupportedLocaleResolver extends AcceptHeaderLocaleResolver
    {
        @Override
        public Locale resolveLocale(HttpServletRequest request)
        {
            if (!StringUtils.hasText(request.getHeader("Accept-Language")))
            {
                return Locale.SIMPLIFIED_CHINESE;
            }
            Enumeration<Locale> requestedLocales = request.getLocales();
            while (requestedLocales.hasMoreElements())
            {
                Locale requested = requestedLocales.nextElement();
                for (Locale supported : SUPPORTED_LOCALES)
                {
                    if (supported.equals(requested)
                            || supported.getLanguage().equalsIgnoreCase(requested.getLanguage()))
                    {
                        return supported;
                    }
                }
            }
            return Locale.SIMPLIFIED_CHINESE;
        }
    }
}
