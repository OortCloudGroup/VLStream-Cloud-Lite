package com.ruoyi.framework.config;

import static org.junit.Assert.assertEquals;
import java.util.Locale;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;

public class LocaleConfigTest
{
    private final LocaleResolver resolver = new LocaleConfig().localeResolver();

    @Test
    public void resolvesSupportedRegionalLocale()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8");
        assertEquals(new Locale("pt", "BR"), resolver.resolveLocale(request));
    }

    @Test
    public void mapsPublicShortCodeToSelectedRegionalLocale()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "es");
        assertEquals(new Locale("es", "MX"), resolver.resolveLocale(request));
    }

    @Test
    public void acceptsUnderscoreLocaleCode()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "zh_CN");
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(request));
    }

    @Test
    public void honorsQualityValues()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "de-DE;q=0.4,fr-FR;q=0.9,en-US;q=0.7");
        assertEquals(Locale.FRANCE, resolver.resolveLocale(request));
    }

    @Test
    public void fallsBackToSimplifiedChineseForUnsupportedLocale()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "it-IT");
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(request));
    }

    @Test
    public void defaultsToSimplifiedChineseWithoutHeader()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(request));
    }
}
