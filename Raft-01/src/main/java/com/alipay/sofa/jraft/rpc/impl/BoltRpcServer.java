package com.alipay.sofa.jraft.rpc.impl;


import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.ConnectionEventType;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;
import com.alipay.sofa.jraft.rpc.Connection;
import com.alipay.sofa.jraft.rpc.RpcContext;
import com.alipay.sofa.jraft.rpc.RpcProcessor;
import com.alipay.sofa.jraft.rpc.RpcServer;
import com.alipay.sofa.jraft.util.Requires;

import java.util.concurrent.Executor;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-03-31 21:39
 */
public class BoltRpcServer implements RpcServer {
    private final com.alipay.remoting.rpc.RpcServer rpcServer;

    public BoltRpcServer(com.alipay.remoting.rpc.RpcServer rpcServer) {
        Requires.requireNonNull(rpcServer, "rpcServer");
        this.rpcServer = rpcServer;
    }

    @Override
    public boolean init(Void opts) {
        this.rpcServer.option(BoltClientOption.NETTY_FLUSH_CONSOLIDATION, true);
        this.rpcServer.initWriteBufferWaterMark(BoltRaftRpcFactory.CHANNEL_WRITE_BUF_LOW_WATER_MARK,
                BoltRaftRpcFactory.CHANNEL_WRITE_BUF_HIGH_WATER_MARK);
        this.rpcServer.startup();
        return this.rpcServer.isStarted();
    }

    @Override
    public void shutdown() {
        this.rpcServer.shutdown();
    }

    @Override
    public void registerConnectionClosedEventListener(ConnectionClosedEventListener listener) {
        this.rpcServer.addConnectionEventProcessor(ConnectionEventType.CLOSE, (remoteAddress, conn) -> {
            final Connection proxyConn = conn == null ? null : new Connection() {

                @Override
                public Object getAttribute(String key) {
                    return conn.getAttribute(key);
                }

                @Override
                public void setAttribute(String key, Object value) {
                    conn.setAttribute(key, value);
                }

                @Override
                public Object setAttributeIfAbsent(String key, Object value) {
                    return conn.setAttributeIfAbsent(key, value);
                }

                @Override
                public void close() {
                    conn.close();
                }
            };
            listener.onClosed(remoteAddress, proxyConn);
        });
    }

    /**
     * 将processor绑定到 rpcServer 中
     * @param processor
     */
    @Override
    public void registerProcessor(final RpcProcessor processor) {
        this.rpcServer.registerUserProcessor(new AsyncUserProcessor<Object>() {
            @Override
            public void handleRequest(BizContext bizContext, AsyncContext asyncContext, Object object) {
                final RpcContext rpcCtx = new RpcContext() {
                    @Override
                    public void sendResponse(Object responseObj) {
                        asyncContext.sendResponse(responseObj);
                    }

                    @Override
                    public Connection getConnection() {
                        com.alipay.remoting.Connection conn = bizContext.getConnection();
                        if (conn == null) {
                            return null;
                        }
                        return new BoltConnection(conn);
                    }

                    @Override
                    public String getRemoteAddress() {
                        return bizContext.getRemoteAddress();
                    }
                };
                processor.handleRequest(rpcCtx, object);
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
     * 查看当前服务端绑定的端口
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
