package com.github.ambry.network;

import com.codahale.metrics.MetricRegistry;
import com.github.ambry.config.NetworkConfig;
import com.github.ambry.config.VerifiableProperties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;


public class SocketServerTest {

  private SocketServer server = null;

  public SocketServerTest()
      throws InterruptedException, IOException {
    Properties props = new Properties();
    VerifiableProperties propverify = new VerifiableProperties(props);
    NetworkConfig config = new NetworkConfig(propverify);
    ArrayList<Port> ports = new ArrayList<Port>();
    ports.add(new Port(config.port, PortType.PLAINTEXT));
    ports.add(new Port(config.port + 1000, PortType.SSL));
    server = new SocketServer(config, new MetricRegistry(), ports);
    server.start();
  }

  @After
  public void cleanup() {
    server.shutdown();
  }

  @Test
  public void simpleRequest()
      throws IOException, InterruptedException {
    simpleRequest(new Port(server.getPort(), PortType.PLAINTEXT));
  }

  @Test
  public void simpleSSLRequest()
      throws IOException, InterruptedException {
    simpleRequest(new Port(server.getSSLPort(), PortType.SSL));
  }

  private void simpleRequest(Port targetPort)
      throws IOException, InterruptedException {
    byte[] bytesToSend = new byte[1028];
    new Random().nextBytes(bytesToSend);
    ByteBuffer byteBufferToSend = ByteBuffer.wrap(bytesToSend);
    byteBufferToSend.putLong(0, 1028);
    BoundedByteBufferSend bufferToSend = new BoundedByteBufferSend(byteBufferToSend);
    BlockingChannel channel = null;
    if (targetPort.getPortType() == PortType.SSL) {
      channel = new SSLBlockingChannel("localhost", targetPort.getPortNo(), 10000, 10000, 1000, 2000);
    } else {
      channel = new BlockingChannel("localhost", targetPort.getPortNo(), 10000, 10000, 1000, 2000);
    }
    channel.connect();
    channel.send(bufferToSend);
    RequestResponseChannel requestResponseChannel = server.getRequestResponseChannel();
    Request request = requestResponseChannel.receiveRequest();
    DataInputStream requestStream = new DataInputStream(request.getInputStream());
    byte[] outputBytes = new byte[1020];
    requestStream.readFully(outputBytes);
    for (int i = 0; i < 1020; i++) {
      Assert.assertEquals(bytesToSend[8 + i], outputBytes[i]);
    }

    // send response back and ensure response is received
    byte[] responseBytes = new byte[2048];
    new Random().nextBytes(responseBytes);
    ByteBuffer byteBufferToSendResponse = ByteBuffer.wrap(responseBytes);
    byteBufferToSendResponse.putLong(0, 2048);
    BoundedByteBufferSend responseToSend = new BoundedByteBufferSend(byteBufferToSendResponse);
    requestResponseChannel.sendResponse(responseToSend, request, null);
    InputStream streamResponse = channel.receive().getInputStream();
    byte[] responseBytesReceived = new byte[2040];
    streamResponse.read(responseBytesReceived);
    for (int i = 0; i < 2040; i++) {
      Assert.assertEquals(responseBytes[8 + i], responseBytesReceived[i]);
    }
    channel.disconnect();
  }

  /**
   * Choose a number of random available ports
   */
  ArrayList<Integer> choosePorts(int count)
      throws IOException {
    ArrayList<Integer> sockets = new ArrayList<Integer>();
    for (int i = 0; i < count; i++) {
      ServerSocket socket = new ServerSocket(0);
      sockets.add(socket.getLocalPort());
      socket.close();
    }
    return sockets;
  }

  /**
   * Choose an available port
   */
  public int choosePort()
      throws IOException {
    return choosePorts(1).get(0);
  }
}
