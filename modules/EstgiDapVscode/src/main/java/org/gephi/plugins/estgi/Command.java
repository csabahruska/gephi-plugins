package org.gephi.plugins.estgi;

public class Command {

  public static class GenericRequest {
    String request;
  }

  public static class LoadGraphRequest {
    String request;
    String edgesFilepath;
    String nodesFilepath;
    String title;
  }

  public static class SelectNodeRequest {
    String request;
    String nodeId;
  }

 public static class ShowValueEvent {
    String event;
    String nodeId;
  }

}