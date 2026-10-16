package com.ruoyi.web.service;

import com.ruoyi.common.deviceavailability.AvailabilitySnapshot;
import com.ruoyi.common.deviceavailability.ProtocolAvailabilityService;
import com.ruoyi.common.deviceavailability.RtspDescribeProbe;
import com.ruoyi.onvif.domain.bo.FetchMainAndSubStreamUrisBo;
import com.ruoyi.onvif.utils.OnvifDeviceProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Infrastructure-only scanner of saved sources. User HTTP reads never trigger network probes. */
@Component
public class ProtocolDeviceAvailabilityMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(ProtocolDeviceAvailabilityMonitor.class);
    private final DataSource database;
    private final ProtocolAvailabilityService availability;
    private final boolean enabled;
    private final long interval;
    private final long maxBackoff;
    private final int timeout;
    private final Map<String, Long> due = new ConcurrentHashMap<>();
    private final Map<String, String> fingerprints = new ConcurrentHashMap<>();
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), task -> {
                Thread thread = new Thread(task, "protocol-availability");
                thread.setDaemon(true);
                return thread;
            });

    public ProtocolDeviceAvailabilityMonitor(DataSource database, ProtocolAvailabilityService availability,
            @Value("${video.availability.enabled:true}") boolean enabled,
            @Value("${video.availability.interval-seconds:30}") long intervalSeconds,
            @Value("${video.availability.max-backoff-seconds:300}") long backoffSeconds,
            @Value("${video.availability.timeout-millis:3000}") int timeout) {
        this.database = database;
        this.availability = availability;
        this.enabled = enabled;
        this.interval = Math.max(30, intervalSeconds) * 1000;
        this.maxBackoff = Math.max(interval, backoffSeconds * 1000);
        this.timeout = Math.min(5000, Math.max(500, timeout));
    }

    @Scheduled(fixedDelay = 5000)
    public void tick() {
        if (!enabled) return;
        try {
            List<Target> targets = targets();
            long now = System.currentTimeMillis();
            Set<String> present = ConcurrentHashMap.newKeySet();
            for (Target target : targets) {
                present.add(target.key());
                String hash = fingerprint(target);
                String old = fingerprints.put(target.key(), hash);
                if (old != null && !hash.equals(old)) {
                    availability.invalidate(target.protocol, target.id);
                    due.put(target.key(), now);
                }
                due.putIfAbsent(target.key(), now + Math.floorMod(target.key().hashCode(), interval));
            }
            due.keySet().retainAll(present);
            fingerprints.keySet().retainAll(present);
            targets.sort(Comparator.comparingLong(target -> due.getOrDefault(target.key(), now)));
            for (Target target : targets) {
                if (due.get(target.key()) > now || !running.add(target.key())) continue;
                try {
                    workers.execute(() -> probe(target));
                } catch (RejectedExecutionException busy) {
                    running.remove(target.key());
                    break;
                }
            }
        } catch (Exception unavailable) {
            // Never log JDBC/RTSP/SOAP exception messages: they can carry connection material.
            LOG.warn("Protocol availability monitor could not read/update evidence ({})", unavailable.getClass().getSimpleName());
        }
    }

    private void probe(Target target) {
        long delay = interval;
        try {
            if (!availability.claim(target.protocol, target.id)) return;
            String result;
            URI uri;
            try {
                uri = new URI(target.url);
                String configuredHost = new URI("http://" + target.ip).getHost();
                if (configuredHost == null || uri.getHost() == null || !configuredHost.equalsIgnoreCase(uri.getHost()))
                    throw new IllegalArgumentException();
            } catch (Exception invalid) {
                availability.record(target.protocol, target.id, "CONFIGURATION_ERROR");
                return;
            }
            result = new RtspDescribeProbe().check(target.url, timeout);
            if ("ONVIF".equals(target.protocol) && "AVAILABLE".equals(result)) {
                FetchMainAndSubStreamUrisBo query = new FetchMainAndSubStreamUrisBo();
                query.setIp(target.ip);
                query.setUsername(target.username);
                query.setPassword(target.password);
                result = new OnvifDeviceProbe(timeout).checkManagement(query);
            }
            // Ignore evidence from a configuration replaced while the request was in flight.
            if (!fingerprint(target).equals(fingerprints.get(target.key()))) return;
            AvailabilitySnapshot snapshot = availability.record(target.protocol, target.id, result);
            if (snapshot.getFailureCount() >= 3)
                delay = Math.min(maxBackoff, interval * (1L << Math.min(4, snapshot.getFailureCount() - 2)));
        } catch (RuntimeException unavailable) {
            LOG.warn("Protocol availability evidence unavailable for {} device {} ({})",
                target.protocol, target.id, unavailable.getClass().getSimpleName());
        } finally {
            due.put(target.key(), System.currentTimeMillis() + delay);
            running.remove(target.key());
        }
    }

    private List<Target> targets() throws Exception {
        String sql = "select 'RTSP' protocol,id,ip,url,user_name,password from rtsp_device where play_type='1' "
            + "union all select 'ONVIF' protocol,id,ip,url,user_name,password from onvif_device where play_type='1'";
        List<Target> rows = new ArrayList<>();
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            try (ResultSet records = statement.executeQuery()) {
                while (records.next()) rows.add(new Target(records.getString("protocol"), records.getString("id"),
                    records.getString("ip"), records.getString("url"), records.getString("user_name"), records.getString("password")));
            }
        }
        return rows;
    }

    private static String fingerprint(Target target) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((target.ip + "\n" + target.url + "\n"
                + target.username + "\n" + target.password).getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception impossible) { throw new IllegalStateException(); }
    }

    @PreDestroy public void close() { workers.shutdownNow(); }

    private static class Target {
        final String protocol, id, ip, url, username, password;
        Target(String protocol, String id, String ip, String url, String username, String password) {
            this.protocol = protocol; this.id = id; this.ip = ip; this.url = url; this.username = username; this.password = password;
        }
        String key() { return protocol + ":" + id; }
    }
}
