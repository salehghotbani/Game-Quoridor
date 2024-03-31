package Board;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

import javax.swing.*;
import java.util.ArrayList;

public class MakeGraph {

    MakeGraph(ArrayList<JButton> jButtonArrayList, DefaultUndirectedGraph<JButton, DefaultEdge> graph) {
        CreateVertexGraph(jButtonArrayList, graph);
        CreateEdgeGraph(jButtonArrayList, graph);
    }

    private void CreateVertexGraph(ArrayList<JButton> jButtonArrayList, DefaultUndirectedGraph<JButton, DefaultEdge> graph) {
        for (int i = 128; i < jButtonArrayList.size() - 1; i++) {
            graph.addVertex(jButtonArrayList.get(i));
        }
    }

    private void CreateEdgeGraph(ArrayList<JButton> jButtonArrayList, DefaultUndirectedGraph<JButton, DefaultEdge> graph) {
        for (int i = 128; i < jButtonArrayList.size() - 1; i++) {
            switch ((i - 127) % 9) {
                case 1:
                    if (i - 127 != 73) {
                        graph.addEdge(jButtonArrayList.get(i), jButtonArrayList.get(i + 9));
                    }
                    graph.addEdge(jButtonArrayList.get(i), jButtonArrayList.get(i + 1));
                    break;
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                    if (i - 127 <= 73) {
                        graph.addEdge(jButtonArrayList.get(i), jButtonArrayList.get(i + 9));
                    }
                    graph.addEdge(jButtonArrayList.get(i), jButtonArrayList.get(i + 1));
                    break;
                case 0:
                    if (i - 127 != 81) {
                        graph.addEdge(jButtonArrayList.get(i), jButtonArrayList.get(i + 9));
                    }
                    break;
            }
        }
    }
}