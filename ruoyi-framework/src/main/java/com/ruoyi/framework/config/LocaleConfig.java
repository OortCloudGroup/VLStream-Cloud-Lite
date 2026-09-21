package com.ruoyi.framework.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

/**
 * Resolves the UI locale from the standard Accept-Language request header.
 */
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

    public LocaleResolver localeResolver()
    {
        SessionLocaleResolver resolver = new SupportedLocaleResolver();
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        return resolver;
    }

    /** Accepts both the public short code (for example, es) and the selected regional code (es-MX). */
    private static class SupportedLocaleResolver extends SessionLocaleResolver
    {
        @Override
        public Locale resolveLocale(HttpServletRequest request)
        {
            String acceptLanguage = request.getHeader("Accept-Language");
            if (!StringUtils.hasText(acceptLanguage))
            {
                return super.resolveLocale(request);
            }

            List<WeightedLanguage> requestedLanguages = Arrays.stream(acceptLanguage.split(","))
                    .map(WeightedLanguage::parse)
                    .filter(item -> item != null)
                    .sorted(Comparator.comparingDouble(WeightedLanguage::getQuality).reversed())
                    .collect(Collectors.toList());
            for (WeightedLanguage item : requestedLanguages)
            {
                Locale requested = Locale.forLanguageTag(item.getLanguageTag().replace('_', '-'));
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

    private static class WeightedLanguage
    {
        private final String languageTag;
        private final double quality;

        private WeightedLanguage(String languageTag, double quality)
        {
            this.languageTag = languageTag;
            this.quality = quality;
        }

        private static WeightedLanguage parse(String value)
        {
            String[] parts = value.trim().split(";", 2);
            if (!StringUtils.hasText(parts[0]) || "*".equals(parts[0]))
            {
                return null;
            }
            double quality = 1.0D;
            if (parts.length == 2 && parts[1].trim().startsWith("q="))
            {
                try
                {
                    quality = Double.parseDouble(parts[1].trim().substring(2));
                }
                catch (NumberFormatException ignored)
                {
                    quality = 0.0D;
                }
            }
            return new WeightedLanguage(parts[0], quality);
        }

        private String getLanguageTag()
        {
            return languageTag;
        }

        private double getQuality()
        {
            return quality;
        }
    }
}
