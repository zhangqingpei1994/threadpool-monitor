package com.my.threadpool.autoconfig;

import com.alibaba.edas.acm.ConfigService;
import com.alibaba.edas.acm.listener.ConfigChangeListener;
import com.my.threadpool.ThreadPoolHolder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    private ThreadPoolProperties threadPoolProperties;

    @Value("${spring.application.name:DEFAULT_GROUP}")
    private String applicationName;

    /**
     * 配置变更监听器回调
     */
    private ConfigChangeListenerWrapper configChangeListener;

    /**
     * 初始化：合并之前两个 @PostConstruct 的逻辑
     */
    @PostConstruct
    public void init() {
        // 1. 初始化默认线程池配置
        initDefaultConfig();
        
        // 2. 初始化 ACM 服务（使用环境变量或配置文件）
        initAcmConfig();
        
        // 3. 设置配置变更回调
        setupConfigChangeListener();
        
        // 4. 订阅 ACM 配置变更
        subscribeConfig();
    }

    /**
     * 初始化默认线程池配置
     */
    private void initDefaultConfig() {
        DynamicThreadPoolConfig defaultConfig = new DynamicThreadPoolConfig();
        defaultConfig.setCorePoolSize(threadPoolProperties.getCorePoolSize());
        defaultConfig.setMaxPoolSize(threadPoolProperties.getMaxPoolSize());
        defaultConfig.setKeepAliveSeconds(threadPoolProperties.getKeepAliveSeconds());
        defaultConfig.setQueueCapacity(threadPoolProperties.getQueueCapacity());
        dynamicConfigs.put("default", defaultConfig);
        
        LOGGER.info("[{}] ACM配置初始化完成，默认配置: corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}",
                applicationName, defaultConfig.getCorePoolSize(), 
                defaultConfig.getMaxPoolSize(), defaultConfig.getKeepAliveSeconds());
    }

    /**
     * 初始化ACM服务配置
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
                LOGGER.info("[{}] ACM服务初始化成功, dataId={}", applicationName, DATA_ID);
            } else {
                LOGGER.warn("[{}] 未配置ACM环境变量(ACM_ENDPOINT, ACM_NAMESPACE), 将使用本地配置", applicationName);
            }
        } catch (Exception e) {
            LOGGER.warn("[{}] ACM服务初始化失败, 将使用本地配置: {}", applicationName, e.getMessage());
        }
    }

    /**
     * 设置配置变更回调
     */
    private void setupConfigChangeListener() {
        setConfigChangeListener((poolName, config) -> {
            ThreadPoolExecutor executor = ThreadPoolHolder.get(poolName);
            if (executor != null) {
                LOGGER.info("[{}] 线程池[{}]动态配置变更, 正在更新参数: corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}",
                        applicationName, poolName, 
                        config.getCorePoolSize(), config.getMaxPoolSize(), config.getKeepAliveSeconds());

                executor.setCorePoolSize(config.getCorePoolSize());
                executor.setMaximumPoolSize(config.getMaxPoolSize());
                executor.setKeepAliveTime(config.getKeepAliveSeconds(), TimeUnit.SECONDS);
            }
        });
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
        LOGGER.info("[{}] 线程池[{}]配置已注册: corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}",
                applicationName, poolName, 
                config.getCorePoolSize(), config.getMaxPoolSize(), config.getKeepAliveSeconds());
    }

    /**
     * 订阅ACM配置（只订阅一次，所有线程池配置在同一个 dataId 中）
     */
    private void subscribeConfig() {
        try {
            // 添加配置变更监听器
            ConfigService.addListener(DATA_ID, applicationName, new ConfigChangeListener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    LOGGER.info("[{}] ACM配置发生变更: {}", applicationName, configInfo);
                    parseAndNotifyAll(configInfo);
                }
            });
            
            // 立即获取一次配置
            String currentConfig = ConfigService.getConfig(DATA_ID, applicationName, 3000);
            if (currentConfig != null && !currentConfig.isEmpty()) {
                parseAndNotifyAll(currentConfig);
            }
            
            LOGGER.info("[{}] 已订阅ACM配置, dataId={}", applicationName, DATA_ID);
        } catch (Exception e) {
            LOGGER.warn("[{}] 订阅ACM配置失败, 可能未配置ACM环境: {}", applicationName, e.getMessage());
        }
    }

    /**
     * 解析配置并通知所有线程池变更
     * 配置格式：JSON数组
     * [
     *   {"poolName": "orderPool", "corePoolSize": 10, "maxPoolSize": 20, "keepAliveSeconds": 60, "queueCapacity": 1000},
     *   {"poolName": "payPool", "corePoolSize": 5, "maxPoolSize": 15, "keepAliveSeconds": 30, "queueCapacity": 500}
     * ]
     */
    private void parseAndNotifyAll(String configInfo) {
        if (configInfo == null || configInfo.isEmpty()) {
            return;
        }
        
        try {
            // 使用 Jackson 解析 JSON 数组
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<DynamicThreadPoolConfig> configs = mapper.readValue(configInfo, 
                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<DynamicThreadPoolConfig>>() {});
            
            for (DynamicThreadPoolConfig config : configs) {
                if (config.getPoolName() == null || config.getPoolName().isEmpty()) {
                    continue;
                }
                
                String poolName = config.getPoolName();
                
                // 保存配置
                dynamicConfigs.put(poolName, config);
                
                LOGGER.info("[{}] 线程池[{}]配置更新: corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}",
                        applicationName, poolName, 
                        config.getCorePoolSize(), config.getMaxPoolSize(), config.getKeepAliveSeconds());
                
                // 通知监听器
                if (configChangeListener != null) {
                    configChangeListener.onConfigChange(poolName, config);
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("[{}] ACM配置解析失败: {}", applicationName, e.getMessage());
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
        private String poolName;
        private int corePoolSize;
        private int maxPoolSize;
        private int keepAliveSeconds;
        private int queueCapacity;
    }
}
