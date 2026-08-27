package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

/**
 * Netty bootstrap lifecycle. A {@link SmartLifecycle} rather than an {@code @EventListener} on
 * {@code ApplicationReadyEvent} so it participates properly in ordered, graceful shutdown — the
 * SMTP port stops accepting new connections before the rest of the application context tears
 * down, rather than mid-transaction connections being severed abruptly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpServer implements SmartLifecycle {

    private final MailIntakeProperties properties;
    private final SmtpChannelInitializer channelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running = false;

    @Override
    public synchronized void start() {
        MailIntakeProperties.Listener cfg = properties.getListener();

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, cfg.getBacklog())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(channelInitializer);

            serverChannel = bootstrap.bind(new InetSocketAddress(cfg.getBindAddress(), cfg.getPort()))
                    .sync()
                    .channel();

            running = true;
            log.info("cms-mail-intake SMTP listener bound on {}:{}", cfg.getBindAddress(), cfg.getPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while binding SMTP listener", e);
        } catch (Exception e) {
            shutdownEventLoopGroups();
            throw new IllegalStateException(
                    "Failed to bind SMTP listener on " + cfg.getBindAddress() + ":" + cfg.getPort()
                    + " — see RUNBOOK.md for :25 binding options if this is a permission error", e);
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        shutdownEventLoopGroups();
        log.info("cms-mail-intake SMTP listener stopped");
    }

    private void shutdownEventLoopGroups() {
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup != null) bossGroup.shutdownGracefully();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** The actual bound port — useful in tests that configure cms.mail.intake.listener.port=0
     *  (ephemeral, OS-assigned) to avoid clashing with anything else on the test host. */
    public int getBoundPort() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    /** Start after the JPA/datasource infrastructure is up — the listener durably persists to the
     *  database on every accepted message, so accepting connections before that's ready would
     *  just produce 451s. A high phase value starts this late in the SmartLifecycle ordering. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }
}
