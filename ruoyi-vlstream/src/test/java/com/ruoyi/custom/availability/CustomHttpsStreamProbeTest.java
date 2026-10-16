package com.ruoyi.custom.availability;

import org.junit.*;
import javax.net.ssl.*;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.nio.charset.StandardCharsets;
import sun.security.x509.*;
import static org.junit.Assert.*;
import static com.ruoyi.custom.availability.ProtocolFixture.*;

/** Ephemeral fixture CA/key only; production still uses the JVM's default trust and HTTPS host check. */
public class CustomHttpsStreamProbeTest {
    static SSLContext fixtureTls;CustomStreamAddressPolicy policy;
    @BeforeClass public static void tls()throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair key=generator.generateKeyPair();
        X509CertInfo info=new X509CertInfo();X500Name name=new X500Name("CN=fixture.invalid");
        info.set(X509CertInfo.VERSION,new CertificateVersion(CertificateVersion.V3));info.set(X509CertInfo.SERIAL_NUMBER,new CertificateSerialNumber(1));
        info.set(X509CertInfo.SUBJECT,name);info.set(X509CertInfo.ISSUER,name);info.set(X509CertInfo.VALIDITY,new CertificateValidity(new Date(System.currentTimeMillis()-60000),new Date(System.currentTimeMillis()+3600000)));
        info.set(X509CertInfo.KEY,new CertificateX509Key(key.getPublic()));info.set(X509CertInfo.ALGORITHM_ID,new CertificateAlgorithmId(AlgorithmId.get("SHA256withRSA")));
        X509CertImpl cert=new X509CertImpl(info);cert.sign(key.getPrivate(),"SHA256withRSA");
        KeyStore store=KeyStore.getInstance("JKS");store.load(null,null);store.setKeyEntry("fixture",key.getPrivate(),"fixture".toCharArray(),new X509Certificate[]{cert});
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(store,"fixture".toCharArray());TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(store);
        fixtureTls=SSLContext.getInstance("TLS");fixtureTls.init(km.getKeyManagers(),trust.getTrustManagers(),new SecureRandom());
    }
    @Before public void setup(){policy=loopbackPolicy();}
    @After public void close(){policy.close();}
    ProtocolFixture server(String reply)throws Exception{return new ProtocolFixture(fixtureTls.getServerSocketFactory().createServerSocket(0,32,InetAddress.getLoopbackAddress()),socket->{header(socket.getInputStream());socket.getOutputStream().write(reply.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();});}
    CustomHttpStreamProbe trusted(){return new CustomHttpStreamProbe(policy){@Override protected SSLSocketFactory tlsFactory(){return fixtureTls.getSocketFactory();}};}
    @Test public void trustedHttpsContentIsCheckedAfterHostValidation()throws Exception{byte[] b=flv();try(ProtocolFixture s=new ProtocolFixture(fixtureTls.getServerSocketFactory().createServerSocket(0,32,InetAddress.getLoopbackAddress()),socket->{header(socket.getInputStream());socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: "+b.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(b);})){assertEquals("AVAILABLE",trusted().check(s.uri("https","/v"),deadline(2000)));}}
    @Test public void untrustedCertificateCannotBecomeNetworkOfflineOrAvailable()throws Exception{try(ProtocolFixture s=server("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")){assertEquals("TLS_VALIDATION_FAILED",assertThrows(CustomStreamSpec.ProbeFailure.class,()->new CustomHttpStreamProbe(policy).check(s.uri("https","/v"),deadline(2000))).reason);}}
    @Test public void trustedCertificateStillRejectsWrongHost()throws Exception{try(ProtocolFixture s=server("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")){URI wrong=URI.create("https://wrong.invalid:"+s.server.getLocalPort()+"/v");assertEquals("TLS_VALIDATION_FAILED",assertThrows(CustomStreamSpec.ProbeFailure.class,()->trusted().check(wrong,deadline(2000))).reason);}}
    @Test public void httpsRedirectNeverDowngradesToPlaintext()throws Exception{try(ProtocolFixture s=server("HTTP/1.1 302 Found\r\nLocation: http://fixture.invalid/video\r\nContent-Length: 0\r\n\r\n")){assertEquals("ADDRESS_BLOCKED",assertThrows(CustomStreamSpec.ProbeFailure.class,()->trusted().check(s.uri("https","/v"),deadline(2000))).reason);}}
    @Test public void stalledTlsHandshakeIsClosedAtTheAbsoluteDeadline()throws Exception{
        try(ProtocolFixture s=new ProtocolFixture(socket->{bytes(socket.getInputStream(),5);while(socket.getInputStream().read()>=0){} })){long now=System.nanoTime();
            assertEquals("UNREACHABLE",assertThrows(CustomStreamSpec.ProbeFailure.class,()->new CustomHttpStreamProbe(policy).check(s.uri("https","/v"),deadline(200))).reason);
            assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-now)<700);assertTrue(s.disconnected.await(1,java.util.concurrent.TimeUnit.SECONDS));
        }
    }
}
