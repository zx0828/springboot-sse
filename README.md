# SpringBoot SSE 示例项目

这是一个基于Spring Boot实现的Server-Sent Events (SSE) 示例项目，演示了如何使用SSE技术实现服务器向客户端的实时推送功能。

## 项目概述

Server-Sent Events (SSE) 是一种服务器推送技术，允许服务器通过HTTP连接向客户端发送更新。与WebSocket不同，SSE是单向的（只能从服务器到客户端），并且基于HTTP协议，更加轻量级和易于实现。

本项目展示了如何在Spring Boot应用程序中实现SSE，包括：
- 建立SSE连接
- 发送实时数据
- 处理连接断开和重连
- 异步数据处理
- 心跳机制保持连接活跃
- 解决中文编码问题

## 功能特点

1. **SSE连接管理**：
   - 创建和维护SSE连接
   - 支持客户端断开重连
   - 会话状态管理

2. **数据推送**：
   - 异步数据处理和推送
   - 支持数据接收后自动断开连接
   - 数据结果缓存，支持断开重连后获取结果

3. **心跳机制**：
   - 定时发送心跳消息保持连接活跃
   - 防止连接超时断开

4. **中文支持**：
   - 自定义SseEmitter解决中文乱码问题
   - 使用UTF-8编码确保中文正确显示

5. **前端演示**：
   - 提供简洁的Web界面测试SSE功能
   - 实时显示接收到的事件

## 项目结构

```
src/main/java/cn/zuster/sse/
├── SseApplication.java              # 应用程序入口
├── controller/
│   └── SseTestController.java       # SSE控制器
├── exception/
│   └── SseException.java            # 自定义异常
├── service/
│   ├── SseService.java              # SSE服务接口
│   └── impl/
│       └── SseServiceImpl.java      # SSE服务实现
├── session/
│   └── SseSession.java              # SSE会话管理
├── task/
│   ├── AsyncDataTask.java           # 异步数据处理任务
│   └── HeartBeatTask.java           # 心跳任务
└── util/
    └── SseEmitterUTF8.java          # 自定义UTF-8编码的SseEmitter
```

## 技术栈

- Spring Boot 2.4.1
- Spring Web
- Java 8
- HTML/CSS/JavaScript (前端测试页面)

## 如何运行

### 前提条件

- JDK 1.8+
- Maven 3.0+

### 启动步骤

1. 克隆项目到本地
   ```bash
   git clone <repository-url>
   cd springboot-sse-demo
   ```

2. 使用Maven构建项目
   ```bash
   mvn clean package
   ```

3. 运行应用程序
   ```bash
   mvn spring-boot:run
   ```
   或者
   ```bash
   java -jar target/my-demo-springboot-sse-0.0.1-SNAPSHOT.jar
   ```

4. 访问测试页面
   打开浏览器，访问 http://localhost:8080

### 使用说明

1. 在测试页面输入一个用户ID（任意字符串）
2. 选择是否在接收数据后自动断开连接
3. 点击"连接SSE"按钮建立连接
4. 观察接收到的事件
5. 可以通过"断开连接"按钮手动断开连接

## API接口

### 1. 建立SSE连接

```
GET /sse/start?clientId={clientId}&autoCloseAfterData={autoCloseAfterData}
```

- `clientId`: 客户端唯一标识
- `autoCloseAfterData`: 是否在接收数据后自动断开连接（可选，默认为false）

### 2. 关闭SSE连接

```
GET /sse/end?clientId={clientId}
```

- `clientId`: 客户端唯一标识

## 注意事项

1. 本项目主要用于演示SSE技术，生产环境使用时需要考虑更多的安全性和稳定性问题。
2. SSE连接数量会占用服务器资源，生产环境中应考虑连接数限制和资源管理。
3. 某些代理服务器可能不支持SSE，需要进行适当的配置。

## 许可证

[MIT License](LICENSE)
