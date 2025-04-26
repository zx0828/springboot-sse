package cn.zuster.sse.session;

import cn.zuster.sse.exception.SseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * SSE Session
 *
 * @author zuster
 * @date 2021/1/5
 */
public class SseSession {
    private static final Logger logger = LoggerFactory.getLogger(SseSession.class);

    /**
     * Session维护Map
     */
    private static Map<String, SseEmitter> SESSION = new ConcurrentHashMap<>();
    
    /**
     * 存储每个emitter对应的clientId，用于安全地处理onCompletion回调
     */
    private static Map<Integer, String> EMITTER_IDS = new ConcurrentHashMap<>();

    /**
     * 判断Session是否存在
     *
     * @param id 客户端ID
     * @return 存在返回false，不存在返回true
     */
    public static boolean exist(String id) {
        return SESSION.get(id) != null;
    }

    /**
     * 增加Session
     *
     * @param id      客户端ID
     * @param emitter SseEmitter
     */
    public static void add(String id, SseEmitter emitter) {
        logger.info("MSG: Adding SSE Session | ID: {} | EmitterHash: {} | Date: {}", id, emitter.hashCode(), new Date());
        final SseEmitter oldEmitter = SESSION.get(id);
        
        if (oldEmitter != null) {
            logger.info("MSG: Found existing emitter for ID: {} | OldEmitterHash: {} | Will replace with new emitter", id, oldEmitter.hashCode());
            
            // 先从EMITTER_IDS中移除旧emitter的记录，防止其onCompletion移除新emitter
            EMITTER_IDS.remove(oldEmitter.hashCode());
            
            try {
                // 安全关闭旧的emitter
                oldEmitter.complete();
            } catch (Exception e) {
                logger.warn("MSG: Error completing old emitter | ID: {} | Error: {}", id, e.getMessage());
            }
        }
        
        // 确保新的emitter被放入映射
        SESSION.put(id, emitter);
        
        // 记录emitter和clientId的对应关系
        EMITTER_IDS.put(emitter.hashCode(), id);
        
        logger.info("MSG: SSE Session added | ID: {} | EmitterHash: {} | CurrentSessionSize: {}", 
                id, emitter.hashCode(), SESSION.size());
    }


    /**
     * 删除Session
     *
     * @param id 客户端ID
     * @return
     */
    public static boolean del(String id) {
        final SseEmitter emitter = SESSION.remove(id);
        if (emitter != null) {
            try {
                // 从EMITTER_IDS中移除
                EMITTER_IDS.remove(emitter.hashCode());
                
                emitter.complete();
                logger.info("MSG: SSE Session removed | ID: {} | EmitterHash: {} | CurrentSessionSize: {}", 
                        id, emitter.hashCode(), SESSION.size());
                return true;
            } catch (Exception e) {
                logger.warn("MSG: Error completing emitter during removal | ID: {} | Error: {}", id, e.getMessage());
            }
        }
        return false;
    }

    /**
     * 发送消息
     *
     * @param id  客户端ID
     * @param msg 发送的消息
     * @return
     */
    public static boolean send(String id, Object msg) {
        final SseEmitter emitter = SESSION.get(id);
        if (emitter != null) {
            try {
                emitter.send(msg);
                return true;
            } catch (IOException e) {
                logger.error("MSG: SendMessageError-IOException | ID: {} | EmitterHash: {} | Date: {} | Error: {}", 
                        id, emitter.hashCode(), new Date(), e.getMessage());
                // 如果发送出错，可能连接已断开，从SESSION中删除
                SESSION.remove(id);
                EMITTER_IDS.remove(emitter.hashCode());
                return false;
            }
        } else {
            logger.warn("MSG: Emitter not found for ID: {} when sending message", id);
        }
        return false;
    }

    /**
     * SseEmitter onCompletion 后执行的逻辑
     * 
     * 这个方法由emitter的onCompletion回调触发，需要安全地处理并发情况
     *
     * @param id     客户端ID
     * @param future 定时任务Future，可以为null
     */
    public static void onCompletion(String id, ScheduledFuture<?> future) {
        // *** 重要修改: 不再尝试从SESSION中移除任何emitter ***
        // 因为我们无法确定触发回调的是哪个emitter
        
        logger.info("MSG: SSE Session completion event received | ID: {} | Event ignored | CurrentSessionSize: {}", 
                id, SESSION.size());
        
        // 只处理future，不修改任何session状态
        if (future != null) {
            // SseEmitter断开后需要中断心跳发送
            future.cancel(true);
        }
    }

    /**
     * SseEmitter onTimeout 或 onError 后执行的逻辑
     *
     * @param id
     * @param e
     */
    public static void onError(String id, SseException e) {
        final SseEmitter emitter = SESSION.get(id);
        if (emitter != null) {
            try {
                int emitterHash = emitter.hashCode();
                
                // 从SESSION和EMITTER_IDS中移除
                SESSION.remove(id);
                EMITTER_IDS.remove(emitterHash);
                
                emitter.completeWithError(e);
                logger.info("MSG: SSE Session error | ID: {} | EmitterHash: {} | Error: {}", 
                        id, emitterHash, e.getMessage());
            } catch (Exception ex) {
                logger.warn("MSG: Error completing emitter with error | ID: {} | Error: {}", id, ex.getMessage());
            }
        }
    }
    
    /**
     * 获取当前活跃的SSE会话数
     * @return 活跃会话数
     */
    public static int getActiveSessionCount() {
        return SESSION.size();
    }
}
