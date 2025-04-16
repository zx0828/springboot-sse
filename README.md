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