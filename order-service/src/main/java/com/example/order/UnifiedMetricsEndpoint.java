package com.example.order;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.InvocationBuilder;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

@Component
@Endpoint(id = "unified-metrics")
public class UnifiedMetricsEndpoint {

    private static final Logger log = LoggerFactory.getLogger(UnifiedMetricsEndpoint.class);

    private final MeterRegistry meterRegistry;

    public UnifiedMetricsEndpoint(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @ReadOperation
    public Map<String, Object> getUnifiedMetrics() {
        Map<String, Object> unifiedStats = new HashMap<>();
        
        Map<String, Object> appMetrics = new HashMap<>();
        appMetrics.put("jvm.memory.used", getGaugeValue("jvm.memory.used"));
        appMetrics.put("jvm.memory.max", getGaugeValue("jvm.memory.max"));
        appMetrics.put("jvm.threads.live", getGaugeValue("jvm.threads.live"));
        unifiedStats.put("application", appMetrics);

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();
        
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();

        try (DockerClient dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build()) {
            
            String containerId = InetAddress.getLocalHost().getHostName();

            InvocationBuilder.AsyncResultCallback<Statistics> callback = new InvocationBuilder.AsyncResultCallback<>();
            dockerClient.statsCmd(containerId).withNoStream(true).exec(callback);
            Statistics dockerStats = callback.awaitResult();

            Map<String, Object> infraMetrics = new HashMap<>();
            infraMetrics.put("container.id", containerId);
            
            if (dockerStats.getCpuStats() != null && dockerStats.getCpuStats().getCpuUsage() != null) {
                infraMetrics.put("cpu.usage.total", dockerStats.getCpuStats().getCpuUsage().getTotalUsage());
            }
            if (dockerStats.getMemoryStats() != null) {
                infraMetrics.put("memory.usage", dockerStats.getMemoryStats().getUsage());
                infraMetrics.put("memory.limit", dockerStats.getMemoryStats().getLimit());
            }
            unifiedStats.put("infrastructure", infraMetrics);
            log.debug("Fetched Docker stats for container {}", containerId);
        } catch (Exception e) {
            log.warn("Failed to fetch Docker stats: {}", e.getMessage(), e);
            unifiedStats.put("infrastructure", "Error fetching Docker stats: " + e.getMessage());
        }

        return unifiedStats;
    }

    private Double getGaugeValue(String metricName) {
        var meter = meterRegistry.find(metricName).gauge();
        return meter != null ? meter.value() : -1.0;
    }
}