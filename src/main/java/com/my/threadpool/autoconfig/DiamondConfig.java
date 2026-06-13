package com.my.threadpool.autoconfig;

import com.alibaba.edas.acm.ConfigService;
import com.alibaba.edas.acm.listener.ConfigChangeListener;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ACM配置读取类
 * 用于动态读取阿里云ACM配置中心的线程池参数
 * dataId: thread.pool.monitor (固定)
 * group: 应用名称 (spring.application.name)
 * @author zhangqingpei
 */
@Configuration
@EnableConfigurationProperties(ThreadPoolProperties.class)
public class DiamondConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiamondConfig.class);
    
    /**
     * 固定的 dataId
     */
    private static final String DATA_ID = "thread.pool.monitor";

    /**
     * 存储各线程池的动态配置 key: 线程池名称, value: 配置
     */
    private final Map<String, DynamicThreadPoolConfig> dynamicConfigs = new ConcurrentHashMap<>();

    @Autowired
    private ThreadPoolProperties defaultProperties;
    
    @Autowired(required = false)
    private String applicationName = "DEFAULT_GROUP";

    /**
     * 配置变更监听器回调
     */
    private ConfigChangeListenerWrapper configChangeListener;

    @PostConstruct
    public void init() {
        // 初始化默认线程池配置
        DynamicThreadPoolConfig defaultConfig = new DynamicThreadPoolConfig();
        defaultConfig.setCorePoolSize(defaultProperties.getCorePoolSize());
        defaultConfig.setMaxPoolSize(defaultProperties.getMaxPoolSize());
        defaultConfig.setKeepAliveSeconds(defaultProperties.getKeepAliveSeconds());
        defaultConfig.setQueueCapacity(defaultProperties.getQueueCapacity());
        dynamicConfigs.put("default", defaultConfig);
        LOGGER.info("ACM配置初始化完成，默认配置: corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}",
                defaultConfig.getCorePoolSize(), defaultConfig.getMaxPoolSize(), defaultConfig.getKeepAliveSeconds());
        
        // 初始化ACM配置（使用环境变量或配置文件）
        initAcmConfig();
    }

    /**
     * 初始化ACM配置
     */
    private void initAcmConfig() {
        try {
            Properties properties = new Properties();
            // 从环境变量或系统属性中获取配置
            String endpoint = System.getProperty("acm.endpoint", System.getenv("ACM_ENDPOINT"));
            String namespace = System.getProperty("acm.namespace", System.getenv("ACM_NAMESPACE"));
            String accessKey = System.getProperty("acm.accessKey", System.getenv("ACM_ACCESS_KEY"));
            String secretKey = System.getProperty("acm.secretKey", System.getenv("ACM_SECRET_KEY"));
            
            if (endpoint != null && namespace != null) {
                properties.put("endpoint", endpoint);
                properties.put("namespace", namespace);
                if (accessKey != null) {
                    properties.put("accessKey", accessKey);
                }
                if (secretKey != null) {
                    properties.put("secretKey", secretKey);
                }
                ConfigService.init(properties);
                LOGGER.info("ACM服务初始化成功, dataId={}, group={}", DATA_ID, applicationName);
            } else {
                LOGGER.warn("未配置ACM环境变量(ACM_ENDPOINT, ACM_NAMESPACE), 将使用本地配置");
            }
        } catch (Exception e) {
            LOGGER.warn("ACM服务初始化失败, 将使用本地配置: {}", e.getMessage());
        }
    }

    /**
     * 获取指定名称的线程池配置
     */
    public DynamicThreadPoolConfig getConfig(String poolName) {
        return dynamicConfigs.get(poolName);
    }
    
    /**
     * 获取所有已注册的线程池名称
     */
    public Set<String> getAllPoolNames() {
        return dynamicConfigs.keySet();
    }

    /**
     * 注册或更新线程池配置
     */
    public void registerConfig(String poolName, DynamicThreadPoolConfig config) {
        dynamicConfigs.put(poolName, config);
        LOGGER.info("线程池[{}]配置已注册: corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}",
                poolName, config.getCorePoolSize(), config.getMaxPoolSize(), config.getKeepAliveSeconds());
    }

    /**
     * 订阅ACM配置（只订阅一次，所有线程池配置在同一个 dataId 中）
     */
    public void subscribeConfig() {
        try {
            // 添加配置变更监听器
            ConfigService.addListener(DATA_ID, applicationName, new ConfigChangeListener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    LOGGER.info("ACM配置发生变更: {}", configInfo);
                    parseAndNotifyAll(configInfo);
                }
            });
            
            // 立即获取一次配置
            String currentConfig = ConfigService.getConfig(DATA_ID, applicationName, 3000);
            if (currentConfig != null && !currentConfig.isEmpty()) {
                parseAndNotifyAll(currentConfig);
            }
            
            LOGGER.info("已订阅ACM配置, dataId={}, group={}", DATA_ID, applicationName);
        } catch (Exception e) {
            LOGGER.warn("订阅ACM配置失败, 可能未配置ACM环境: {}", e.getMessage());
        }
    }

    /**
     * 解析配置并通知所有线程池变更
     * 配置格式：
     * orderPool.corePoolSize=10
     * orderPool.maxPoolSize=20
     * payPool.corePoolSize=5
     * payPool.maxPoolSize=15
     */
    private void parseAndNotifyAll(String configInfo) {
        if (configInfo == null || configInfo.isEmpty()) {
            return;
        }
        
        try {
            Map<String, DynamicThreadPoolConfig> parsedConfigs = new ConcurrentHashMap<>();
            
            String[] lines = configInfo.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // 格式: poolName.property=value
                String[] parts = line.split("=");
                if (parts.length != 2) {
                    continue;
                }
                
                String key = parts[0].trim();
                String value = parts[1].trim();
                
                // 解析 poolName.property
                int dotIndex = key.lastIndexOf('.');
                if (dotIndex <= 0 || dotIndex >= key.length() - 1) {
                    continue;
                }
                
                String poolName = key.substring(0, dotIndex);
                String property = key.substring(dotIndex + 1);
                
                // 获取或创建该线程池的配置
                DynamicThreadPoolConfig config = parsedConfigs.computeIfAbsent(poolName, k -> {
                    DynamicThreadPoolConfig existing = dynamicConfigs.get(k);
                    return existing != null ? existing : new DynamicThreadPoolConfig();
                });
                
                // 设置属性值
                setConfigProperty(config, property, value);
            }
            
            // 更新动态配置并通知变更
            for (Map.Entry<String, DynamicThreadPoolConfig> entry : parsedConfigs.entrySet()) {
                String poolName = entry.getKey();
                DynamicThreadPoolConfig newConfig = entry.getValue();
                
                // 保存配置
                dynamicConfigs.put(poolName, newConfig);
                
                LOGGER.info("线程池[{}]配置更新: corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}",
                        poolName, newConfig.getCorePoolSize(), newConfig.getMaxPoolSize(), newConfig.getKeepAliveSeconds());
                
                // 通知监听器
                if (configChangeListener != null) {
                    configChangeListener.onConfigChange(poolName, newConfig);
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("ACM配置解析失败: {}", e.getMessage());
        }
    }
    
    /**
     * 设置配置属性值
     */
    private void setConfigProperty(DynamicThreadPoolConfig config, String property, String value) {
        try {
            switch (property) {
                case "corePoolSize":
                    config.setCorePoolSize(Integer.parseInt(value));
                    break;
                case "maxPoolSize":
                    config.setMaxPoolSize(Integer.parseInt(value));
                    break;
                case "keepAliveSeconds":
                    config.setKeepAliveSeconds(Integer.parseInt(value));
                    break;
                case "queueCapacity":
                    config.setQueueCapacity(Integer.parseInt(value));
                    break;
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("配置值格式错误: {}={}", property, value);
        }
    }

    /**
     * 设置配置变更监听器
     */
    public void setConfigChangeListener(ConfigChangeListenerWrapper listener) {
        this.configChangeListener = listener;
    }

    /**
     * 配置变更监听器接口
     */
    public interface ConfigChangeListenerWrapper {
        void onConfigChange(String poolName, DynamicThreadPoolConfig config);
    }

    /**
     * 动态线程池配置
     */
    @Data
    public static class DynamicThreadPoolConfig {
        private int corePoolSize;
        private int maxPoolSize;
        private int keepAliveSeconds;
        private int queueCapacity;
    }
}
