package org.infinispan.server.test.core.persistence;

import java.net.InetAddress;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.infinispan.commons.logging.Log;
import org.infinispan.commons.logging.LogFactory;
import org.infinispan.commons.util.StringPropertyReplacer;
import org.infinispan.commons.util.Util;
import org.infinispan.server.test.core.proxy.SocketProxy;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * @author Tristan Tarrant &lt;tristan@infinispan.org&gt;
 * @since 10.0
 **/
public class ContainerDatabase extends Database {
   private final static Log log = LogFactory.getLog(ContainerDatabase.class);
   private final static String ENV_PREFIX = "database.container.env.";
   private final GenericContainer container;
   private final int port;
   private SocketProxy socketProxy;


   ContainerDatabase(String type, Properties properties) {
      super(type, properties);
      Map<String, String> env = properties.entrySet().stream().filter(e -> e.getKey().toString().startsWith(ENV_PREFIX))
            .collect(Collectors.toMap(e -> e.getKey().toString().substring(ENV_PREFIX.length()), e -> e.getValue().toString()));
      port = Integer.parseInt(properties.getProperty("database.container.port"));
      ImageFromDockerfile image = new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder -> {
               builder
                     .from(properties.getProperty("database.container.name") + ":" + properties.getProperty("database.container.tag"))
                     .expose(port)
                     .env(env)
                     .build();
            });
      container = new GenericContainer(image).withExposedPorts(port).waitingFor(Wait.forListeningPort());
   }

   @Override
   public void start() {
      log.infof("Starting database %s", getType());
      container.start();
      String containerIpAddress = container.getContainerIpAddress();
      try {
         InetAddress localAddress = InetAddress.getByName("0.0.0.0");
         InetAddress remoteAddress = InetAddress.getByName(containerIpAddress);
         socketProxy = new SocketProxy(localAddress, 20000, remoteAddress, container.getMappedPort(port));
         log.infof("Started socket proxy %s", socketProxy);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void stop() {
      log.info("Stopping socket proxy");
      Util.close(socketProxy);
      log.infof("Stopping database %s", getType());
      container.stop();
      log.infof("Stopped database %s", getType());
   }

   public int getPort() {
      return container.getMappedPort(port);
   }

   @Override
   public String jdbcUrl() {
      String address = container.getContainerInfo().getNetworkSettings().getNetworks().values().iterator().next().getIpAddress();
      Properties props = new Properties();
      props.setProperty("container.address", address);
      return StringPropertyReplacer.replaceProperties(super.jdbcUrl(), props);
   }
}
