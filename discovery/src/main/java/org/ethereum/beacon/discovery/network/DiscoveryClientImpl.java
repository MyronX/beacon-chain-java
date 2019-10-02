package org.ethereum.beacon.discovery.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.enr.EnrScheme;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import org.ethereum.beacon.schedulers.Scheduler;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import tech.pegasys.artemis.util.bytes.Bytes4;
import tech.pegasys.artemis.util.bytes.BytesValue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** Discovery UDP client */
public class DiscoveryClientImpl implements DiscoveryClient {
  private static final int RECREATION_TIMEOUT = 5000;
  private static final int STOPPING_TIMEOUT = 10000;
  private static final Logger logger = LogManager.getLogger(DiscoveryClientImpl.class);
  private AtomicBoolean listen = new AtomicBoolean(true);
  private CountDownLatch starting = new CountDownLatch(1);
  private Channel channel;

  /**
   * Constructs UDP client using
   *
   * @param outgoingStream Stream of outgoing packets, client will forward them to the channel
   * @param scheduler Scheduler to run client loop on
   */
  public DiscoveryClientImpl(Publisher<NetworkParcel> outgoingStream, Scheduler scheduler) {
    Flux.from(outgoingStream)
        .subscribe(
            networkPacket ->
                send(networkPacket.getPacket().getBytes(), networkPacket.getNodeRecord()));
    logger.info("Starting UDP discovery client");
    scheduler.execute(this::clientLoop);
    try {
      starting.await();
      logger.info("UDP discovery client started");
    } catch (InterruptedException e) {
      throw new RuntimeException("Initialization of discovery client broke by interruption", e);
    }
  }

  private void clientLoop() {
    NioEventLoopGroup group = new NioEventLoopGroup(1);
    try {
      while (listen.get()) {
        Bootstrap b = new Bootstrap();
        b.group(group)
            .channel(NioDatagramChannel.class)
            .handler(
                new ChannelInitializer<NioDatagramChannel>() {
                  @Override
                  protected void initChannel(NioDatagramChannel ch) throws Exception {
                    starting.countDown();
                  }
                });

        channel = b.bind(0).sync().channel();
        channel.closeFuture().sync();

        if (!listen.get()) {
          logger.info("Shutting down discovery client");
          break;
        }
        logger.error("Discovery client closed. Trying to restore after %s seconds delay");
        Thread.sleep(RECREATION_TIMEOUT);
      }
    } catch (Exception e) {
      logger.error("Can't start discovery client", e);
    } finally {
      try {
        group.shutdownGracefully().sync();
      } catch (Exception ex) {
        logger.error("Failed to shutdown discovery client thread group", ex);
      }
    }
  }

  @Override
  public void stop() {
    if (listen.get()) {
      logger.info("Stopping discovery client");
      listen.set(false);
      if (channel != null) {
        try {
          channel.close().await(STOPPING_TIMEOUT);
        } catch (InterruptedException ex) {
          logger.error("Failed to stop discovery client", ex);
        }
      }
    } else {
      logger.warn("An attempt to stop already stopping/stopped discovery client");
    }
  }

  @Override
  public void send(BytesValue data, NodeRecord recipient) {
    if (!(recipient.getIdentityScheme().equals(EnrScheme.V4))) {
      String error =
          String.format(
              "Accepts only V4 version of recipient's node records. Got %s instead", recipient);
      logger.error(error);
      throw new RuntimeException(error);
    }
    InetSocketAddress address;
    try {
      address =
          new InetSocketAddress(
              InetAddress.getByAddress(
                  ((Bytes4) recipient.get(NodeRecord.FIELD_IP_V4)).extractArray()),
              (int) recipient.get(NodeRecord.FIELD_UDP_V4));
    } catch (UnknownHostException e) {
      String error = String.format("Failed to resolve host for node record: %s", recipient);
      logger.error(error);
      throw new RuntimeException(error);
    }
    DatagramPacket packet = new DatagramPacket(Unpooled.copiedBuffer(data.extractArray()), address);
    logger.trace(() -> String.format("Sending packet %s", packet));
    channel.write(packet);
    channel.flush();
  }
}
