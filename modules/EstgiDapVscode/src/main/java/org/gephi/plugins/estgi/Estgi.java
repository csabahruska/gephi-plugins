
package org.gephi.plugins.estgi;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import org.gephi.graph.api.Node;
import org.gephi.tools.spi.NodeClickEventListener;
import org.gephi.tools.spi.Tool;
import org.gephi.tools.spi.ToolEventListener;
import org.gephi.tools.spi.ToolSelectionType;
import org.gephi.tools.spi.ToolUI;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
//import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.gephi.io.importer.api.ImportController;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.io.processor.spi.Processor;
import org.gephi.io.importer.api.Container;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2Builder;
import org.gephi.layout.api.LayoutController;
import org.gephi.layout.api.LayoutModel;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.visualization.VizController;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Scanner;
import java.util.Arrays;

import com.google.gson.Gson;
// see: https://futurestud.io/tutorials/gson-getting-started-with-java-json-serialization-deserialization

@ServiceProvider(service = Tool.class)
public class Estgi implements Tool {

    private final ProjectController projectController = Lookup.getDefault().lookup(ProjectController.class);
    private final ImportController importController = Lookup.getDefault().lookup(ImportController.class);

    Gson gson;
    Protocol protocol;
    GraphCommandService graphCommandService;

    public Estgi() {
      gson = new Gson();
      protocol = new Protocol();
      System.out.println("estgi started");

      graphCommandService = new GraphCommandService();
      graphCommandService.start();
    }

    @Override
    public void select() {
    }

    @Override
    public void unselect() {
    }

    @Override
    public ToolEventListener[] getListeners() {
        return new ToolEventListener[] {new NodeClickEventListener() {

            @Override
            public void clickNodes(Node[] nodes) {
              try {
                // WORKAROUND for GEPHI imprecise node selection (which uses float instead of double)
                //  select the closest node to the current mouse position

                float[] mousePosition = VizController.getInstance().getGraphIO().getMousePosition3d();
                Node closestNode = null;
                double closestDistance = Double.MAX_VALUE;
                System.out.println("nodes clicked: " + nodes.length);

                for (Node n : nodes) {
                  System.out.println("  " + n.getId());
                  double xDist = Math.abs(n.x() - mousePosition[0]);
                  double yDist = Math.abs(n.y() - mousePosition[1]);
                  double distance = (double) Math.sqrt(xDist * xDist + yDist * yDist);
                  if (distance < closestDistance) {
                    closestDistance = distance;
                    closestNode = n;
                  }
                }
                System.out.println("selected closest node: " + closestNode.getId());

                Command.ShowValueEvent cmd = new Command.ShowValueEvent();
                cmd.event = "showValue";
                cmd.nodeId = (String)closestNode.getId();
                protocol.encode(graphCommandService.getOutput(), gson.toJson(cmd));
              } catch (Exception ex) {
                  System.out.println("error: " + ex.getMessage());
              }

            }
        }};
    }

    @Override
    public ToolUI getUI() {
        return new ToolUI() {

            @Override
            public JPanel getPropertiesBar(Tool tool) {
                return new JPanel();
            }

            @Override
            public Icon getIcon() {
                return ImageUtilities.loadImageIcon("haskell.png", false);
            }

            @Override
            public String getName() {
                return "Haskell";
            }

            @Override
            public String getDescription() {
                return "Show node details in VSCode";
            }

            @Override
            public int getPosition() {
                return 10;
            }

        };
    }

    @Override
    public ToolSelectionType getSelectionType() {
        return ToolSelectionType.SELECTION;
    }
}
