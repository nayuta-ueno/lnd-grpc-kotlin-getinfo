import io.grpc.CallCredentials
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import io.netty.handler.ssl.SslContext
import lnrpc.LightningGrpc
import lnrpc.LightningGrpc.LightningBlockingStub
import org.apache.commons.codec.binary.Hex
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executor

internal class MacaroonCallCredential(private val macaroon: String) : CallCredentials() {
    override fun applyRequestMetadata(requestInfo: RequestInfo, executor: Executor, metadataApplier: MetadataApplier) {
        executor.execute {
            try {
                val headers = Metadata()
                val macaroonKey: Metadata.Key<String> =
                    Metadata.Key.of("macaroon", Metadata.ASCII_STRING_MARSHALLER)
                headers.put(macaroonKey, macaroon)
                metadataApplier.apply(headers)
            } catch (e: Throwable) {
                metadataApplier.fail(Status.UNAUTHENTICATED.withCause(e))
            }
        }
    }

    override fun thisUsesUnstableApi() {}
}

private const val CERT_PATH = "/home/ueno/Lightning/s1/lnddata/tls.cert"
private const val MACAROON_PATH = "/home/ueno/Lightning/s1/lnddata/data/chain/bitcoin/signet/admin.macaroon"
private const val HOST = "localhost"
private const val PORT = 10009

fun main() {
    val sslContext = GrpcSslContexts.forClient().trustManager(File(CERT_PATH)).build()
    val channelBuilder = NettyChannelBuilder.forAddress(HOST, PORT)
    val channel = channelBuilder.sslContext(sslContext).build()
    val macaroon = Hex.encodeHexString(Files.readAllBytes(Paths.get(MACAROON_PATH)))
    val stub = LightningGrpc
        .newBlockingStub(channel)
        .withCallCredentials(MacaroonCallCredential(macaroon))

    val response: lnrpc.LightningOuterClass.GetInfoResponse = stub.getInfo(lnrpc.LightningOuterClass.GetInfoRequest.getDefaultInstance())
    println("node_id: ${response.getIdentityPubkey()}")
}