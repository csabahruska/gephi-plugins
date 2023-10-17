package org.gephi.plugins.estgi;

import com.google.gson.Gson;
//import org.gephi.plugins.estgi.Protocol;

import java.util.Collection;
import java.awt.Color;
import org.gephi.graph.api.*;
import org.openide.util.Lookup;
import org.gephi.io.importer.api.EdgeMergeStrategy;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.api.ImportUtils;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.io.processor.spi.Processor;
import org.gephi.io.importer.api.Container;
import org.gephi.layout.plugin.AutoLayout;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2Builder;
import org.gephi.layout.plugin.force.yifanHu.YifanHu;
import org.gephi.layout.plugin.labelAdjust.LabelAdjustBuilder;
import org.gephi.layout.api.LayoutController;
import org.gephi.layout.api.LayoutModel;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.visualization.VizController;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.TimeUnit;
import org.gephi.appearance.api.*;
import org.gephi.appearance.spi.*;
import org.gephi.appearance.plugin.*;
import org.gephi.appearance.plugin.palette.*;
import org.gephi.ui.appearance.plugin.*;
import org.gephi.tools.api.*;

// dap connection
import java.net.*;
import java.io.*;

public class GraphCommandService extends Thread {

  Workspace workspace;

  Socket socket;
  InputStream input;
  OutputStream output;
  String hostname = "0.0.0.0";
  int port = 4721;

  GraphCommandService() {
    this.workspace = null;
  }

  public synchronized OutputStream getOutput() {
    return output;
  }

  public synchronized void connectGraphServer() throws Exception {
    socket = new Socket(hostname, port);
    input = socket.getInputStream();
    output = socket.getOutputStream();
  }

  public synchronized void disconnectGraphServer() throws Exception {
    input = null;
    output = null;
    socket.close();
    socket = null;
  }

  public void run() {
    while (true) {
      try {
        // HINT: auto connect loop

        Thread.sleep(500); // milliseconds
        connectGraphServer();
        System.out.println("GraphCommandServiceThread connected to " + hostname + " port: " + port);
        serve();

      } catch (Exception ex) {
        System.out.println("GraphCommandServiceThread error: " + ex.getMessage());
      }
    }
  }

  void serve() throws Exception {
    Protocol protocol = new Protocol();
    Gson gson = new Gson();
    while (true) {
      // HINT: request serve loop, in case of disconnect exception the auto connect loop will reconnect
      String cmdStr = protocol.decode(input);
      System.out.println("request: " + cmdStr);
      Command.GenericRequest cmd = gson.fromJson(cmdStr, Command.GenericRequest.class);
      switch (cmd.request) {
        case "loadGraph":
          evalLoadGraph(gson.fromJson(cmdStr, Command.LoadGraphRequest.class));
          break;

        default:
          System.out.println("unknown request: " + cmd.request);
      }
    }
  }

  void evalLoadGraph(Command.LoadGraphRequest cmd) throws Exception {
    final ProjectController projectController = Lookup.getDefault().lookup(ProjectController.class);
    final ImportController importController = Lookup.getDefault().lookup(ImportController.class);

    // create new workspace + import csv
    Workspace lastWorkspace = workspace;
    workspace = projectController.openNewWorkspace();
    projectController.renameWorkspace(workspace, cmd.title);

    // close previous graph
    if (lastWorkspace != null) {
      projectController.deleteWorkspace(lastWorkspace);
    }

    // select the estgi tool
    ToolController tc = Lookup.getDefault().lookup(ToolController.class);
    Estgi estgi = Lookup.getDefault().lookup(Estgi.class);
    tc.select(estgi);

    // load graph data

    // load nodes
    if (cmd.nodesFilepath != null) {
      File file = new File(cmd.nodesFilepath);
      Container container = importController.importFile(file);
      Processor processor = Lookup.getDefault().lookup(Processor.class);
      container.getLoader().setEdgesMergeStrategy(EdgeMergeStrategy.NO_MERGE);
      importController.process(container, processor, workspace);
    }

    // load nodes
    File file = new File(cmd.edgesFilepath);
    Container container = importController.importFile(file);
    Processor processor = Lookup.getDefault().lookup(Processor.class);
    container.getLoader().setEdgesMergeStrategy(EdgeMergeStrategy.NO_MERGE);
    importController.process(container, processor, workspace);

    VizController.getInstance().refreshWorkspace();

    /*
    for (Node n : graph.getNodes()) {
      n.setColor(ImportUtils.parseColor((String)n.getAttribute("color")));
    }
    for (Edge e : graph.getEdges()) {
      e.setColor(ImportUtils.parseColor((String)e.getAttribute("color")));
    }
    */
    // show labels
    VizController.getInstance().getVizModel().getTextModel().setShowEdgeLabels(true);
    VizController.getInstance().getVizModel().getTextModel().setShowNodeLabels(true);
    VizController.getInstance().getVizModel().getTextModel().setNodeSizeFactor(0.1f);
    VizController.getInstance().getVizModel().setEdgeScale(6.0f);

    // apply layout
    AutoLayout autoLayout = new AutoLayout(2, TimeUnit.SECONDS);
    autoLayout.setGraphModel(Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace));
    autoLayout.addLayout(Lookup.getDefault().lookup(ForceAtlas2Builder.class).buildLayout(), 0.2f);
    autoLayout.addLayout(Lookup.getDefault().lookup(YifanHu.class).buildLayout(), 0.6f);
    autoLayout.addLayout(Lookup.getDefault().lookup(LabelAdjustBuilder.class).buildLayout(), 0.2f);
    autoLayout.execute();

    // zoom out to the full graph
    VizController.getInstance().getGraphIO().centerOnGraph();

    // set layout UI default value
    LayoutController lc = Lookup.getDefault().lookup(LayoutController.class);
    lc.setLayout(Lookup.getDefault().lookup(YifanHu.class).buildLayout());

    // apply colors
    AppearanceController ac = Lookup.getDefault().lookup(AppearanceController.class);

    AppearanceModel am = ac.getModel(workspace);

    System.out.println("functions:");
    for (Function f : am.getNodeFunctions()) {
      System.out.println(f.getId());
      if ( f.getId().equals("node_PartitionElementColorTransformer_column_partition2")) {
        PartitionFunction pf = (PartitionFunction)f;
        Collection values = pf.getPartition().getSortedValues(f.getGraph());
        System.out.println("partition getElementCount: " + pf.getPartition().getElementCount(f.getGraph()));
        System.out.println("partition size: " + pf.getPartition().size(f.getGraph()));
        System.out.println("collection size: " + values.size());

        Palette palette = PaletteManager.getInstance().generatePalette(pf.getPartition().size(f.getGraph()));
        pf.getPartition().setColors(f.getGraph(), palette.getColors());
        for (Object o : values) {
          System.out.println(o);
        }

        ac.transform(f);
        System.out.println("transform partition");

      }
    }

    for (Function f : am.getEdgeFunctions()) {
      System.out.println(f.getId());
      if ( f.getId().equals("edge_PartitionElementColorTransformer_column_partition2") ||
           f.getId().equals("edge_PartitionElementColorTransformer_column_call-site-type")
         ) {
        PartitionFunction pf = (PartitionFunction)f;
        Palette palette = PaletteManager.getInstance().generatePalette(pf.getPartition().size(f.getGraph()));
        pf.getPartition().setColors(f.getGraph(), palette.getColors());
        ac.transform(f);
        System.out.println("transform edge partition");
      }
    }

  }
}
