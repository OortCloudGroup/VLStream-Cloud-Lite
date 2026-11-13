package com.ruoyi.isup.ehome;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.isup.service.cmsService.CMS;
import com.ruoyi.isup.service.cmsService.HCISUPCMS;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EhomePreviewTest {
    @Test public void onlyEhome4SendsSecondPushRequest() {
        HCISUPCMS previous = CMS.hCEhomeCMS;
        HCISUPCMS nativeSdk = mock(HCISUPCMS.class);
        CMS.hCEhomeCMS = nativeSdk;
        try {
            EhomeSdk sdk = new EhomeSdk(new EhomeConfig(), mock(EhomeRuntime.class), new com.ruoyi.isup.config.IsupConfig());
            sdk.push(9, 42, "2.6"); sdk.push(9, 42, "3.0");
            verifyNoInteractions(nativeSdk);
            when(nativeSdk.NET_ECMS_StartPushRealStream(eq(9), any(), any())).thenReturn(true);
            sdk.push(9, 42, "4.0");
            verify(nativeSdk).NET_ECMS_StartPushRealStream(eq(9), any(), any());
        } finally { CMS.hCEhomeCMS = previous; }
    }
    @Test public void lastViewerOwnsCleanupAndOtherUsersCannotStopIt() throws Exception {
        EhomeSdk sdk = mock(EhomeSdk.class);
        when(sdk.channels(9)).thenReturn(Collections.singletonList(new EhomeSdk.Channel(33,"33")));
        when(sdk.open(9,33,0,null)).thenReturn(42);
        EhomeMediaPipe pipe = mock(EhomeMediaPipe.class);
        when(pipe.running()).thenReturn(true);
        EhomePreviews previews = new EhomePreviews(sdk) {
            @Override EhomeMediaPipe createPipe(String url, CompletableFuture<String> ready) { ready.complete("true"); return pipe; }
        };
        set(previews,"pushSign","test-sign");
        EhomeDevice device = device();
        String first = (String)previews.start(device,33,0,1L).get("sessionId");
        String second = (String)previews.start(device,33,0,2L).get("sessionId");
        verify(sdk,times(1)).open(9,33,0,null);
        try { previews.stop(second,1L); fail(); } catch (ServiceException expected) { }
        previews.stop(first,1L);
        verify(sdk,never()).close(9,42);
        previews.stop(second,2L);
        verify(sdk).close(9,42);
        verify(pipe).close();
        previews.stop(second,2L); // idempotent stop
        verify(sdk,times(1)).close(9,42);
    }
    @Test public void pushFailureReleasesNativeSessionAndPipe() throws Exception {
        EhomeSdk sdk = mock(EhomeSdk.class);
        when(sdk.channels(9)).thenReturn(Collections.singletonList(new EhomeSdk.Channel(33,"33")));
        when(sdk.open(9,33,0,null)).thenReturn(42);
        doThrow(new ServiceException("push failed")).when(sdk).push(9,42,"4.0");
        EhomeMediaPipe pipe = mock(EhomeMediaPipe.class);
        EhomePreviews previews = new EhomePreviews(sdk) {
            @Override EhomeMediaPipe createPipe(String url, CompletableFuture<String> ready) { return pipe; }
        };
        set(previews,"pushSign","test-sign");
        try { previews.start(device(),33,0,1L); fail(); } catch (ServiceException expected) { }
        verify(sdk).close(9,42); verify(pipe).close();
        previews.destroy(); verify(sdk,times(1)).close(9,42);
    }
    @Test public void offlineAndInvalidChannelCannotStartNativeStream() {
        EhomeSdk sdk = mock(EhomeSdk.class);
        EhomePreviews previews = new EhomePreviews(sdk);
        EhomeDevice device = device(); device.setStatus("OFFLINE");
        try { previews.start(device,33,0,1L); fail(); } catch (ServiceException expected) { }
        verifyNoInteractions(sdk);
    }
    @Test public void configuredMediaBasePreservesGatewayPathAndRemovesTrailingSlashes() throws Exception {
        String url = previewUrl("https://gateway.example.invalid:2443/bus/zlm-server///");
        assertTrue(url.startsWith("https://gateway.example.invalid:2443/bus/zlm-server/haikang/"));
        assertTrue(url.endsWith(".live.flv"));
    }
    @Test public void absentMediaBaseKeepsLegacySchemeHostAndPort() throws Exception {
        String url = previewUrl("");
        assertTrue(url.startsWith("http://127.0.0.1:8090/haikang/"));
        assertTrue(url.endsWith(".live.flv"));
    }
    private static String previewUrl(String mediaBaseUrl) throws Exception {
        EhomeSdk sdk = mock(EhomeSdk.class);
        when(sdk.channels(9)).thenReturn(Collections.singletonList(new EhomeSdk.Channel(33, "33")));
        when(sdk.open(9, 33, 0, null)).thenReturn(42);
        EhomeMediaPipe pipe = mock(EhomeMediaPipe.class);
        when(pipe.running()).thenReturn(true);
        EhomePreviews previews = new EhomePreviews(sdk) {
            @Override EhomeMediaPipe createPipe(String url, CompletableFuture<String> ready) {
                ready.complete("true");
                return pipe;
            }
        };
        set(previews, "pushSign", "test-sign");
        set(previews, "mediaBaseUrl", mediaBaseUrl);
        set(previews, "mediaPublicHost", "127.0.0.1");
        set(previews, "httpScheme", "http");
        set(previews, "httpPort", 8090);
        try {
            return (String) previews.start(device(), 33, 0, 1L).get("url");
        } finally {
            previews.destroy();
        }
    }
    private static EhomeDevice device() {
        EhomeDevice device = new EhomeDevice(); device.setId(1L); device.setDeviceId("test");
        device.setLuserId(9); device.setDevProtocolVersion("4.0"); device.setStatus("ON"); return device;
    }
    private static void set(Object target,String name,Object value) throws Exception {
        Field field=EhomePreviews.class.getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
}
