package com.hymnal.socket

import org.apache.mina.core.filterchain.IoFilter
import org.apache.mina.core.future.ConnectFuture
import org.apache.mina.core.future.IoFuture
import org.apache.mina.core.service.IoConnector
import org.apache.mina.core.session.IdleStatus
import org.apache.mina.core.session.IoSession
import org.apache.mina.filter.codec.ProtocolCodecFactory
import org.apache.mina.filter.codec.ProtocolCodecFilter
import org.apache.mina.filter.codec.textline.LineDelimiter
import org.apache.mina.filter.codec.textline.TextLineCodecFactory
import org.apache.mina.filter.keepalive.KeepAliveFilter
import org.apache.mina.filter.keepalive.KeepAliveRequestTimeoutHandler
import org.apache.mina.filter.logging.LoggingFilter
import java.nio.charset.Charset

interface SocketClient {

    fun conntection()

    fun init()

    fun createConnector(): IoConnector

    fun initFuture(connector: IoConnector): ConnectFuture

    fun initSession(future: IoFuture?): IoSession?

    fun stopConnector()

    fun connectorState(): Boolean

    fun addListener(connector: IoConnector)

    fun send(msg: Any?)

    fun getIsAliveThread(): Boolean

    fun startThread()

    fun close()

    enum class Type {
        TCP,
        UDP
    }

    class Builder {

        private var type: Type = Type.TCP

        private var HEART_TIME = 5
        private var TIME_OUT = 3
        private var IDLE = 10

        private var tag: String = "SocketClient"
        private lateinit var ip: String
        private var port: Int = 0

        private var long: Boolean = true

        private var charsetName = "UTF-8"

        private lateinit var loggerIoFilterAdapter: IoFilter

        private var hearBeat: IoFilter? = null

        private lateinit var protocolCodecIoFilterAdapter: IoFilter

        private var socketCallback: SocketCallback? = null

        private var message: Any? = null


        fun setType(type: Type, long: Boolean = true): Builder {
            this.type = type
            this.long = long
            return this
        }

        fun setTag(tag: String): Builder {
            this.tag = tag
            return this
        }

        fun setIp(ip: String, port: Int): Builder {
            this.ip = ip
            this.port = port
            return this
        }

        fun send(message: Any?): Builder {
            this.message = message
            return this
        }

        fun setTime(timeout: Int, idle: Int = 10): Builder {
            TIME_OUT = timeout
            IDLE = idle
            return this
        }

        fun setLogFilter(logFilter: IoFilter): Builder {
            this.loggerIoFilterAdapter = logFilter
            return this
        }

        fun setCodecFactory(factory: ProtocolCodecFactory): Builder {
            val protocolCodecFilter = ProtocolCodecFilter(factory)
            this.protocolCodecIoFilterAdapter = protocolCodecFilter
            return this
        }

        fun setSocketCallBack(socketCallback: SocketCallback): Builder {
            this.socketCallback = socketCallback
            return this
        }

        fun builder(): SocketClient {
            if (!::ip.isInitialized) {
                throw Exception("IP和端口号为必填项")
            }

            if (!::loggerIoFilterAdapter.isInitialized) {
                this.loggerIoFilterAdapter = createLogger()
            }

            if (long) hearBeat = createHearBeat()

            if (!::protocolCodecIoFilterAdapter.isInitialized) {
                this.protocolCodecIoFilterAdapter = createCodec()
            }

            val configuration = SocketConfiguration(
                ip, port,
                loggerIoFilterAdapter,
                hearBeat,
                protocolCodecIoFilterAdapter,
                socketCallback,
                long
            ).apply {
                TIMEOUT = if (long)TIME_OUT else 2000
                BOTH_IDLE = IDLE
            }

            return when (type) {
                Type.TCP -> SocketTcpClient(tag, configuration).apply {
                    startThread()
                    if (!long) this.send(message)
                }
                Type.UDP -> SocketUdpClient(tag, configuration).apply {
                    startThread()
                    if (!long) this.send(message)
                }
            }
        }


        private fun createLogger(): IoFilter {
            return LoggingFilter()
        }

        private fun createCodec(): IoFilter {
            val factory = TextLineCodecFactory(
                Charset.forName(charsetName),
                LineDelimiter.WINDOWS.value,
                LineDelimiter.WINDOWS.value
            )
            factory.decoderMaxLineLength = 1024 * 1024
            factory.encoderMaxLineLength = 1024 * 1024
            //Mina2中已经提供了TextLine解码的方式----根据文本的换行符进行解码 \r\n
            //即看到 \r\n 就结束接受数据，把\r\n前的数据解码为字符串！
            return ProtocolCodecFilter(factory)
        }

        private fun createHearBeat(): IoFilter {
            //设置心跳工程
            val heartBeatFactory = KeepAliveMessageFactoryImpl()
            //当读操作空闲时发送心跳
            val heartBeat = KeepAliveFilter(heartBeatFactory, IdleStatus.BOTH_IDLE)
            //设置心跳包请求后超时无反馈情况下的处理机制，默认为关闭连接,在此处设置为输出日志提醒
            heartBeat.requestTimeoutHandler = KeepAliveRequestTimeoutHandler.CLOSE
            //是否回发
            heartBeat.isForwardEvent = true
            //发送频率
            heartBeat.requestInterval = HEART_TIME
            //设置心跳包请求后 等待反馈超时时间。 超过该时间后则调用KeepAliveRequestTimeoutHandler.CLOSE */
            heartBeat.requestTimeout = TIME_OUT
            return heartBeat
        }
    }
}