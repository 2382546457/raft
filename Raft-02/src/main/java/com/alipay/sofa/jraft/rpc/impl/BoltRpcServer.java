package com.alipay.sofa.jraft.rpc.impl;

import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;
import com.alipay.sofa.jraft.rpc.Connection;
import com.alipay.remoting.ConnectionEventProcessor;
import com.alipay.remoting.ConnectionEventType;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.sofa.jraft.rpc.RpcContext;
import com.alipay.sofa.jraft.rpc.RpcProcessor;
import com.alipay.sofa.jraft.rpc.RpcServer;
import com.alipay.sofa.jraft.util.Requires;

import java.util.concurrent.Executor;

/**
 * @author : 小何
 * @Description : BoltRpc服务端
 * @date : 2024-04-05 00:24
 */
public class BoltRpcServer implements RpcServer {
    private final com.alipay.remoting.rpc.RpcServer rpcServer;

    public BoltRpcServer(final com.alipay.remoting.rpc.RpcServer rpcServer) {
        this.rpcServer = Requires.requireNonNull(rpcServer, "rpcServer");
    }

    /**
     * 初始化并启动RpcServer
     *
     * @param opts
     */
    @Override
    public boolean init(Void opts) {
        this.rpcServer.option(BoltClientOption.NETTY_FLUSH_CONSOLIDATION, true);
        this.rpcServer.initWriteBufferWaterMark(BoltRaftRpcFactory.CHANNEL_WRITE_BUF_LOW_WATER_MARK, BoltRaftRpcFactory.CHANNEL_WRITE_BUF_HIGH_WATER_MARK);
        this.rpcServer.startup();
        return this.rpcServer.isStarted();
    }

    @Override
    public void shutdown() {
        this.rpcServer.shutdown();
    }

    /**
     * 注册连接关闭监听器
     *
     * @param listener
     */
    @Override
    public void registerConnectionClosedEventListener(ConnectionClosedEventListener listener) {
        this.rpcServer.addConnectionEventProcessor(ConnectionEventType.CLOSE, new ConnectionEventProcessor() {
            @Override
            public void onEvent(String remoteAddress, com.alipay.remoting.Connection conn) {
                // 使用 Bolt 的 Connection 包装我们的 Connection
                Connection connection = conn == null ? null : new Connection() {
                    @Override
                    public Object getAttribute(final String key) {
                        return conn.getAttribute(key);
                    }

                    @Override
                    public Object setAttributeIfAbsent(final String key, final Object value) {
                        return conn.setAttributeIfAbsent(key, value);
                    }

                    @Override
                    public void setAttribute(final String key, final Object value) {
                        conn.setAttribute(key, value);
                    }

                    @Override
                    public void close() {
                        conn.close();
                    }
                };
                listener.onClosed(remoteAddress, connection);
            }
        });

    }
    @Override
    public void registerProcessor(final RpcProcessor processor) {
        // processor 会被继续包装成一个AsyncUserProcessor对象
        this.rpcServer.registerUserProcessor(new AsyncUserProcessor<Object>() {
            // 服务端handler会调用这个方法，然后一层层向下调用，最后调用到Jraft框架中定义的处理器中
            // asyncCtx这个对象是从bolt框架中传递过来的，里面定义着回复响应的方法
            @SuppressWarnings("unchecked")
            @Override
            public void handleRequest(final BizContext bizCtx, final AsyncContext asyncCtx, final Object request) {
                final RpcContext rpcCtx = new RpcContext() {
                    @Override
                    public void sendResponse(final Object responseObj) {
                        asyncCtx.sendResponse(responseObj);
                    }

                    @Override
                    public Connection getConnection() {
                        com.alipay.remoting.Connection conn = bizCtx.getConnection();
                        if (conn == null) {
                            return null;
                        }
                        return new BoltConnection(conn);
                    }

                    @Override
                    public String getRemoteAddress() {
                        return bizCtx.getRemoteAddress();
                    }
                };
                // 将 Bolt 的Context 包装为我们自己的 Context，放入自己的Processor中等待使用
                processor.handleRequest(rpcCtx, request);
            }

            @Override
            public String interest() {
                return processor.interest();
            }

            @Override
            public ExecutorSelector getExecutorSelector() {
                final RpcProcessor.ExecutorSelector realSelector = processor.executorSelector();
                if (realSelector == null) {
                    return null;
                }
                return realSelector::select;
            }

            @Override
            public Executor getExecutor() {
                return processor.executor();
            }
        });
    }
    /**
     * 返回此服务端绑定的端口
     * @return
     */
    @Override
    public int boundPort() {
        return this.rpcServer.port();
    }
    public com.alipay.remoting.rpc.RpcServer getServer() {
        return this.rpcServer;
    }

    private static class BoltConnection implements Connection {

        private final com.alipay.remoting.Connection conn;

        private BoltConnection(final com.alipay.remoting.Connection conn) {
            this.conn = Requires.requireNonNull(conn, "conn");
        }

        @Override
        public Object getAttribute(final String key) {
            return this.conn.getAttribute(key);
        }

        @Override
        public Object setAttributeIfAbsent(final String key, final Object value) {
            return this.conn.setAttributeIfAbsent(key, value);
        }

        @Override
        public void setAttribute(final String key, final Object value) {
            this.conn.setAttribute(key, value);
        }

        @Override
        public void close() {
            this.conn.close();
        }
    }
}
