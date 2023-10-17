package org.gephi.plugins.estgi;

import java.io.*;
import java.util.*;

public class Protocol {

  void matchToken(DataInputStream dis, String token) throws Exception {
    final byte[] tokenBytes = token.getBytes("UTF-8");
    byte[] inputBytes = new byte[tokenBytes.length];
    dis.readFully(inputBytes, 0, tokenBytes.length);
    if (!Arrays.equals(inputBytes, tokenBytes)) throw new Exception("Token mismatch, expected: " + token + ", got: " + inputBytes);
  }

  String parseString(DataInputStream dis, int byteSize, String encoding) throws Exception {
    byte[] data = new byte[byteSize];
    dis.readFully(data, 0, byteSize);
    return new String(data, "UTF-8");
  }

  String parseStringUntil(DataInputStream dis, String encoding, byte endMarker) throws Exception {
    ByteArrayOutputStream bOutput = new ByteArrayOutputStream(12);
    byte b = dis.readByte();
    // TODO: acquire lock
    while (b != endMarker) {
      bOutput.write(b);
      b = dis.readByte();
    }
    return new String(bOutput.toByteArray(), encoding);
  }

  public String decode(InputStream input) throws Exception {
    DataInputStream dis = new DataInputStream(input);
    String headerStr = parseStringUntil(dis, "UTF-8", (byte)'\r');
    matchToken(dis, "\n\r\n");
    // process header
    Scanner scanner = new Scanner(headerStr);
    scanner.next("Content-Length:");
    int contentLength = scanner.nextInt();
    // read content
    return parseString(dis, contentLength, "UTF-8");
  }

  public void encode(OutputStream output, String message) throws Exception {
     final byte[] cmdJson = message.getBytes("UTF-8");
     final byte[] header = String.format("Content-Length: %d\r\n\r\n", cmdJson.length).getBytes("UTF-8");
     output.write(header);
     output.write(cmdJson);
  }

}

/*
  TODO
    - implement ack messages ; that would require to add locking on socket
*/
