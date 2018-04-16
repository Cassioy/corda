package net.corda.behave.service.proxy

import net.corda.behave.logging.getLogger
import net.corda.behave.service.Service
import net.corda.behave.service.ServiceSettings
import net.corda.behave.service.proxy.RPCProxyServer.Companion.initialiseSerialization
import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.KRYO_RPC_CLIENT_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPClientSerializationScheme
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import java.net.InetSocketAddress

class RPCProxyServer(hostAndPort: NetworkHostAndPort,
                     settings: ServiceSettings = ServiceSettings(),
                     val webService: RPCProxyWebService) : Service(hostAndPort.host, hostAndPort.port, settings) {

    override fun toString() = "RPC Proxy Service(name = $name, port = $port)"

    override fun checkPrerequisites() {
        buildServletContextHandler()
    }

    override fun startService(): Boolean {
        log.info("Starting RPC Proxy web services...")
        try {
            server.start()
        }
        catch(e: Exception) {
            log.info("Failed to start RPC Proxy server: ${e.message}")
            stopService()
            return false
        }
        log.info("RPC Proxy web services started on $hostAndPort with ${webService.javaClass.simpleName}}")

        return true
    }

    override fun stopService(): Boolean {
        log.info("Shutting down RPC Proxy web services...")
        server.stop()
        server.join()

        return true
    }

    override fun verify() = true

    override fun waitUntilStarted() = true

    private val server: Server = Server(InetSocketAddress(hostAndPort.host, hostAndPort.port)).apply {
        handler = HandlerCollection().apply {
            addHandler(buildServletContextHandler())
        }
    }

    private val hostAndPort: NetworkHostAndPort
        get() = server.connectors.mapNotNull { it as? ServerConnector }
                .map { NetworkHostAndPort(it.host, it.localPort) }
                .first()

    private fun buildServletContextHandler(): ServletContextHandler {
        return ServletContextHandler().apply {
            contextPath = "/"
            val resourceConfig = ResourceConfig().apply {
                // Add your API provider classes (annotated for JAX-RS) here
                register(webService)
            }
            val jerseyServlet = ServletHolder(ServletContainer(resourceConfig)).apply { initOrder = 0 }// Initialise at server start
            addServlet(jerseyServlet, "/*")
        }
    }

    companion object {
        fun initialiseSerialization() {
            try {
                nodeSerializationEnv =
                        SerializationEnvironmentImpl(
                                SerializationFactoryImpl().apply {
                                    registerScheme(KryoClientSerializationScheme())
                                    registerScheme(AMQPClientSerializationScheme(emptyList()))
                                },
                                AMQP_P2P_CONTEXT,
                                rpcClientContext = KRYO_RPC_CLIENT_CONTEXT)
            }
            catch(e: Exception) {  getLogger<RPCProxyServer>().warn("Skipping initialiseSerialization: ${e.message}") }
        }
    }
}

fun main(args: Array<String>) {
    initialiseSerialization()
    val portNo = args.singleOrNull() ?: throw IllegalArgumentException("Please specify a port number")
    val hostAndPort = NetworkHostAndPort("localhost", portNo.toIntOrNull() ?: 13000)
    println("Starting RPC Proxy Server on [$hostAndPort] ...")
    RPCProxyServer(hostAndPort, webService = RPCProxyWebService(hostAndPort)).start()
}