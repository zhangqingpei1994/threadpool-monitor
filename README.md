# 动态线程池监控组件

## 项目简介

本组件是一个基于 Spring Boot 的动态线程池监控组件，提供线程池的创建、动态配置、状态监控和上下文传递等功能。

## 功能特性

### ✅ 线程池工厂模式
- 通过 `ThreadPoolFactory` 统一创建和管理线程池
- 支持多种创建方式：默认配置、自定义参数、配置对象

### ✅ 动态配置支持
- 通过 Diamond 配置中心动态修改线程池参数
- 支持动态修改：核心线程数、最大线程数、空闲线程存活时间
- 配置变更自动生效，无需重启应用

### ✅ 自动注册监控
- 自动扫描 Spring 容器中已有的 `ThreadPoolTaskExecutor`
- 自动注册到监控器进行统一管理

### ✅ 定时状态监控
- 每60秒自动打印线程池状态
- 监控指标包括：核心线程数、最大线程数、活跃线程数、空闲线程数、队列任务数、已完成任务数、拒绝任务数

### ✅ 上下文传递
- 支持 MDC 上下文传递
- 确保线程池执行任务时能够继承主线程的上下文信息（如 traceId、userId 等）

### ✅ 线程预热
- 线程池初始化时自动预热所有核心线程
- 避免首次请求的线程创建开销

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>org.example</groupId>
    <artifactId>threadpool-monitor</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置参数

在 `application.yml` 中添加以下配置：

```yaml
thread:
  pool:
    core-pool-size: 10
    max-pool-size: 20
    keep-alive-seconds: 60
    queue-capacity: 1000
    thread-name-prefix: dynamic-pool-
    allow-core-thread-time-out: false

threadpool:
  monitor:
    open-monitor: true
    dynamic-modifier: true
    initial-delay: 60
    period: 60

spring:
  application:
    name: example-app
```

### 3. 使用方式

#### 方式一：使用线程池工厂创建（推荐）

```java
@Autowired
private ThreadPoolFactory threadPoolFactory;

// 使用默认配置创建
ThreadPoolTaskExecutor executor1 = threadPoolFactory.createThreadPool("orderExecutor");

// 使用自定义参数创建
ThreadPoolTaskExecutor executor2 = threadPoolFactory.createThreadPool(
    "payExecutor",    // 线程池名称
    5,                // 核心线程数
    10,               // 最大线程数
    60,               // 空闲存活时间
    500,              // 队列容量
    false             // 是否允许核心线程超时
);

// 使用配置对象创建
ThreadPoolFactory.PoolConfig config = new ThreadPoolFactory.PoolConfig();
config.setPoolName("asyncExecutor");
config.setCorePoolSize(8);
config.setMaxPoolSize(16);
ThreadPoolTaskExecutor executor3 = threadPoolFactory.createThreadPool(config);

// 使用线程池执行任务
executor1.execute(() -> {
    // 业务逻辑
});
```

#### 方式二：自定义线程池（自动注册）

```java
@Bean
public ThreadPoolTaskExecutor customExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setThreadNamePrefix("custom-pool-");
    executor.initialize();
    return executor;
}
```

#### 方式三：动态修改配置

通过 Diamond 配置中心修改线程池参数：

**配置格式（dataId: `threadpool.{poolName}.config`）：**
```properties
corePoolSize=15
maxPoolSize=30
keepAliveSeconds=120
queueCapacity=2000
```

## 监控输出示例

```
========== 线程池状态监控 ==========
线程池[orderExecutor] 状态: 核心线程=10, 最大线程=20, 活跃线程=3, 空闲线程=7, 队列任务数=5, 已完成任务=100, 拒绝任务数=0, allowCoreThreadTimeOut=false
线程池[customExecutor] 状态: 核心线程=5, 最大线程=10, 活跃线程=2, 空闲线程=3, 队列任务数=0, 已完成任务=50, 拒绝任务数=0, allowCoreThreadTimeOut=false
===================================
```

## 核心组件

### 1. ThreadPoolFactory
- 线程池工厂类，负责创建和管理多个线程池实例
- 提供多种创建方式，支持自定义配置

### 2. ThreadPoolHolder
- 线程池持有器，全局存储所有线程池实例
- 支持按名称获取线程池

### 3. DynamicThreadPoolAutoConfig
- 自动配置类，负责初始化监控器和工厂
- 自动扫描并注册 Spring 容器中的线程池

### 4. DynamicThreadPoolMonitor
- 线程池监控器，负责定时打印线程池状态

### 5. DiamondConfig
- Diamond 配置读取类，支持配置变更监听

### 6. ContextCopyingDecorator
- 上下文传递装饰器，负责 MDC 上下文的捕获和传递

### 7. MonitorRejectedHandler
- 可监控的拒绝策略，统计被拒绝的任务数量

## 配置参数说明

### 线程池配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| core-pool-size | int | 10 | 核心线程数 |
| max-pool-size | int | 20 | 最大线程数 |
| keep-alive-seconds | int | 60 | 空闲线程存活时间（秒） |
| queue-capacity | int | 1000 | 队列容量 |
| thread-name-prefix | String | dynamic-pool- | 线程名前缀 |
| allow-core-thread-time-out | boolean | false | 是否允许核心线程超时 |

### 监控配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| open-monitor | boolean | true | 是否开启监控 |
| dynamic-modifier | boolean | true | 是否允许动态修改配置 |
| initial-delay | int | 60 | 首次执行延迟（秒） |
| period | int | 60 | 执行周期（秒） |

## 项目结构

```
src/main/java/com/my/threadpool/
├── autoconfig/                    # 自动配置
│   ├── DiamondConfig.java
│   ├── DynamicThreadPoolAutoConfig.java
│   ├── ThreadPoolMonitorProperties.java
│   └── ThreadPoolProperties.java
├── decorator/                     # 装饰器
│   ├── AlRunnable.java
│   └── ContextCopyingDecorator.java
├── handler/                       # 处理器
│   └── MonitorRejectedHandler.java
├── monitor/                       # 监控器
│   └── DynamicThreadPoolMonitor.java
├── util/                          # 工具类
│   └── IpUtil.java
├── ThreadPoolFactory.java         # 线程池工厂
└── ThreadPoolHolder.java          # 线程池持有器
```

## 注意事项

1. 若需要使用 Diamond 动态配置功能，请确保已正确配置 Diamond 环境
2. 自定义线程池需要调用 `initialize()` 方法后才能被自动注册
3. 监控日志输出间隔可通过 `threadpool.monitor.period` 配置调整
4. 通过工厂创建的线程池会自动注册到监控器，无需手动注册

## License

MIT License