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