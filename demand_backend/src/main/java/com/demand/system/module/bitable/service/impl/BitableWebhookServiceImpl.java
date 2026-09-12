package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.entity.BitableWebhookSubscription;
import com.demand.system.module.bitable.mapper.BitableWebhookSubscriptionMapper;
import com.demand.system.module.bitable.service.BitableWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 多维表格-Webhook Service 实现（MVP）。
 * <ul>
 *   <li>推送异步执行（独立线程池），失败不影响业务主流程；</li>
 *   <li>payload 为 JSON，HMAC-SHA256 签名，请求头携带 X-Webhook-Timestamp 与 X-Webhook-Signature；</li>
 *   <li>非 2xx 或异常按指数退避重试（最多 3 次），结果写回订阅的 last_status/last_delivered_at；</li>
 *   <li>目标 URL 仅允许 http/https，拒绝内网/回环地址（SSRF 防护）；</li>
 *   <li>secret 仅创建时返回一次。</li>
 * </ul>
 */
@Service
public class BitableWebhookServiceImpl implements BitableWebhookService {

    private static final Logger log = LoggerFactory.getLogger(BitableWebhookServiceImpl.class);

    /** 订阅事件类型白名单 */
    private static final Set<String> VALID_EVENT_TYPES = Set.of(
            "record_created", "record_updated", "record_deleted", "form_submitted");

    private static final int MAX_RETRY = 3;
    private static final int MAX_DELIVERY_LOG = 500;

    private final BitableWebhookSubscriptionMapper subscriptionMapper;
    private final ObjectMapper objectMapper;
    private final ExecutorService deliveryExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "bitable-webhook-delivery");
        t.setDaemon(true);
        return t;
    });
    private final RestTemplate restTemplate;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public BitableWebhookServiceImpl(BitableWebhookSubscriptionMapper subscriptionMapper,
                                     ObjectMapper objectMapper) {
        this.subscriptionMapper = subscriptionMapper;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public List<Map<String, Object>> listByBase(Long baseId) {
        List<BitableWebhookSubscription> subscriptions = subscriptionMapper.selectList(
                new LambdaQueryWrapper<BitableWebhookSubscription>()
                        .eq(BitableWebhookSubscription::getBaseId, baseId)
                        .orderByDesc(BitableWebhookSubscription::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (BitableWebhookSubscription sub : subscriptions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", sub.getId());
            item.put("baseId", sub.getBaseId());
            item.put("tableId", sub.getTableId());
            item.put("name", sub.getName());
            item.put("eventTypes", List.of(sub.getEventTypes().split(",")));
            item.put("url", sub.getUrl());
            item.put("status", sub.getStatus());
            item.put("lastStatus", sub.getLastStatus());
            item.put("lastDeliveredAt", sub.getLastDeliveredAt());
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> create(Long baseId, Long tableId, String name, List<String> eventTypes,
                                      String url, Long userId) {
        Set<String> types = new LinkedHashSet<>();
        if (eventTypes == null || eventTypes.isEmpty()) {
            types.addAll(VALID_EVENT_TYPES);
        } else {
            for (String type : eventTypes) {
                if (!VALID_EVENT_TYPES.contains(type)) {
                    throw new BusinessException("不支持的 Webhook 事件类型: " + type);
                }
                types.add(type);
            }
        }
        assertUrlSafe(url);

        byte[] secretBytes = new byte[24];
        SECURE_RANDOM.nextBytes(secretBytes);
        String secret = "wh_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        BitableWebhookSubscription sub = new BitableWebhookSubscription();
        sub.setBaseId(baseId);
        sub.setTableId(tableId);
        sub.setName(name != null && !name.isBlank() ? name : "Webhook 订阅");
        sub.setEventTypes(String.join(",", types));
        sub.setUrl(url.trim());
        sub.setSecret(secret);
        sub.setStatus("enabled");
        sub.setCreatedBy(userId);
        subscriptionMapper.insert(sub);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", sub.getId());
        result.put("secret", secret);
        result.put("eventTypes", types);
        return result;
    }

    @Override
    public void updateStatus(Long subscriptionId, boolean enabled) {
        BitableWebhookSubscription sub = subscriptionMapper.selectById(subscriptionId);
        if (sub == null) {
            throw new BusinessException("订阅不存在");
        }
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BitableWebhookSubscription> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("id", subscriptionId).set("status", enabled ? "enabled" : "disabled");
        subscriptionMapper.update(null, wrapper);
    }

    @Override
    public void delete(Long subscriptionId) {
        BitableWebhookSubscription sub = subscriptionMapper.selectById(subscriptionId);
        if (sub == null) {
            throw new BusinessException("订阅不存在");
        }
        subscriptionMapper.deleteById(subscriptionId);
    }

    @Override
    public void dispatch(Long baseId, Long tableId, String eventType, Map<String, Object> payload) {
        // 记录事件在事务提交前到达：注册 afterCommit 后再推送，确保接收方读到已提交数据
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            final Long fBaseId = baseId;
            final Long fTableId = tableId;
            final String fEventType = eventType;
            final Map<String, Object> fPayload = payload;
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            findAndEnqueue(fBaseId, fTableId, fEventType, fPayload);
                        }
                    });
        } else {
            findAndEnqueue(baseId, tableId, eventType, payload);
        }
    }

    private void findAndEnqueue(Long baseId, Long tableId, String eventType, Map<String, Object> payload) {
        try {
            List<BitableWebhookSubscription> subscriptions = subscriptionMapper.selectList(
                    new LambdaQueryWrapper<BitableWebhookSubscription>()
                            .eq(BitableWebhookSubscription::getBaseId, baseId)
                            .eq(BitableWebhookSubscription::getStatus, "enabled"));
            if (subscriptions.isEmpty()) {
                return;
            }
            for (BitableWebhookSubscription sub : subscriptions) {
                Set<String> types = new HashSet<>(Arrays.asList(sub.getEventTypes().split(",")));
                boolean tableMatched = sub.getTableId() == null || sub.getTableId().equals(tableId);
                if (types.contains(eventType) && tableMatched) {
                    deliverAsync(sub, eventType, payload);
                }
            }
        } catch (Exception e) {
            log.warn("Webhook 事件分发失败: baseId={}, eventType={}", baseId, eventType, e);
        }
    }

    private void deliverAsync(BitableWebhookSubscription sub, String eventType, Map<String, Object> payload) {
        deliveryExecutor.execute(() -> {
            long timestamp = System.currentTimeMillis() / 1000;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("eventId", UUID.randomUUID().toString());
            body.put("eventType", eventType);
            body.put("baseId", sub.getBaseId());
            body.put("tableId", sub.getTableId());
            body.put("timestamp", timestamp);
            body.put("data", payload != null ? payload : Map.of());

            boolean succeeded = false;
            String lastError = null;
            for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
                try {
                    String json = objectMapper.writeValueAsString(body);
                    Mac mac = Mac.getInstance("HmacSHA256");
                    mac.init(new SecretKeySpec(sub.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                    String signature = Base64.getEncoder().encodeToString(
                            mac.doFinal((timestamp + "." + json).getBytes(StandardCharsets.UTF_8)));

                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    headers.add("Content-Type", "application/json");
                    headers.add("X-Webhook-Event", eventType);
                    headers.add("X-Webhook-Timestamp", String.valueOf(timestamp));
                    headers.add("X-Webhook-Signature", signature);

                    org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(json, headers);
                    org.springframework.http.ResponseEntity<String> response =
                            restTemplate.postForEntity(sub.getUrl(), entity, String.class);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        succeeded = true;
                        break;
                    }
                    lastError = "HTTP " + response.getStatusCode().value();
                } catch (Exception e) {
                    lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                }
                if (attempt < MAX_RETRY) {
                    try {
                        // 指数退避：1s、2s
                        Thread.sleep(1_000L * (1L << (attempt - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            try {
                com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BitableWebhookSubscription> wrapper =
                        new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
                wrapper.eq("id", sub.getId())
                        .set("last_status", succeeded ? "succeeded" : "failed")
                        .set("last_delivered_at", LocalDateTime.now());
                subscriptionMapper.update(null, wrapper);
            } catch (Exception ignored) {
            }
            if (!succeeded) {
                log.warn("Webhook 投递最终失败: subscriptionId={}, url={}, error={}", sub.getId(), sub.getUrl(), lastError);
            }
        });
    }

    /**
     * SSRF 防护：仅允许 http/https，拒绝内网/回环/链路本地地址
     */
    private void assertUrlSafe(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException("Webhook URL 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new BusinessException("Webhook URL 格式无效");
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BusinessException("Webhook 仅支持 http/https 协议");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException("Webhook URL 缺少主机地址");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new BusinessException("无法解析 Webhook 目标主机");
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                    || address.isMulticastAddress() || isUniqueLocalIpv6(address)) {
                throw new BusinessException("不允许推送到内网或保留地址");
            }
        }
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
}
