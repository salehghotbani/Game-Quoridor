package Board;

import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class WallButton {
    int BlueLocation, GreenLocation;
    List<JButton> shortestPath;

    //short path is exist?! if dose'nt create an Exception and return false
    public boolean shortPath(ArrayList<JButton> jButtonArrayList, DefaultUndirectedGraph<JButton, DefaultEdge> graph, int j, int countVertical, int countHorizontal) {
        CoordinatePlayerField coordinatePlayerField = new CoordinatePlayerField(jButtonArrayList);
        BlueLocation = coordinatePlayerField.getBlueLocation();
        GreenLocation = coordinatePlayerField.getGreenLocation();

        DijkstraShortestPath<JButton, DefaultEdge> dijkstraShortestPath = new DijkstraShortestPath<>(graph);
        removeEdge(jButtonArrayList, j, graph);

        for (int i = 128; i <= 136; i++) {
            try {
                shortestPath = dijkstraShortestPath.getPath(jButtonArrayList.get(i), jButtonArrayList.get(BlueLocation)).getVertexList();
                if (shortestPath.size() > 0)
                    break;
            } catch (NullPointerException e) { //IllegalArgumentException
                countVertical++;
                if (countVertical >= 8) {
                    addEdge(jButtonArrayList, j, graph);
                    return false;
                }
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (shortestPath.size() < 1) {
            return false;
        }
        shortestPath.clear();
        for (int i = 201; i <= 209; i++) {
            try {
                shortestPath = dijkstraShortestPath.getPath(jButtonArrayList.get(GreenLocation), jButtonArrayList.get(i)).getVertexList();
                if (shortestPath.size() > 0)
                    break;
            } catch (NullPointerException e) {
                countHorizontal++;
                if (countHorizontal >= 8) {
                    addEdge(jButtonArrayList, j, graph);
                    return false;
                }
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return shortestPath.size() >= 1;
    }

    protected void removeEdge(ArrayList<JButton> jButtonArrayList, int i, DefaultUndirectedGraph<JButton, DefaultEdge> graph) {
        if ((i + 1) <= 64) {
            int line = theLine(i + 1, true);
            graph.removeAllEdges(jButtonArrayList.get(i + 128 + line), jButtonArrayList.get(i + 129 + line));
            graph.removeAllEdges(jButtonArrayList.get(i + 137 + line), jButtonArrayList.get(i + 138 + line));
        } else if ((i + 1) <= 128) {
            int line = theLine(i + 1, false);
            graph.removeAllEdges(jButtonArrayList.get(i + 64 + line), jButtonArrayList.get(i + 73 + line));
            graph.removeAllEdges(jButtonArrayList.get(i + 65 + line), jButtonArrayList.get(i + 74 + line));
        }
    }

    // the wallButton is on which line?!
    private int theLine(int i, boolean isVertical) {
        if (isVertical) {
            if (i <= 8) {
                return 0;
            } else if (i <= 16) {
                return 1;
            } else if (i <= 24) {
                return 2;
            } else if (i <= 32) {
                return 3;
            } else if (i <= 40) {
                return 4;
            } else if (i <= 48) {
                return 5;
            } else if (i <= 56) {
                return 6;
            } else if (i <= 64) {
                return 7;
            }
        } else {
            if (i - 64 <= 8) {
                return 0;
            } else if (i - 64 <= 16) {
                return 1;
            } else if (i - 64 <= 24) {
                return 2;
            } else if (i - 64 <= 32) {
                return 3;
            } else if (i - 64 <= 40) {
                return 4;
            } else if (i - 64 <= 48) {
                return 5;
            } else if (i - 64 <= 56) {
                return 6;
            } else if (i - 64 <= 64) {
                return 7;
            }
        }
        return 0;
    }

    protected void addEdge(ArrayList<JButton> jButtonArrayList, int i, DefaultUndirectedGraph<JButton, DefaultEdge> graph) {
        try {
            if ((i + 1) <= 64) {
                int line = theLine(i + 1, true);
                graph.addEdge(jButtonArrayList.get(i + 128 + line), jButtonArrayList.get(i + 129 + line));
                graph.addEdge(jButtonArrayList.get(i + 137 + line), jButtonArrayList.get(i + 138 + line));
            } else if ((i + 1) <= 128) {
                int line = theLine(i + 1, false);
                graph.addEdge(jButtonArrayList.get(i + 64 + line), jButtonArrayList.get(i + 73 + line));
                graph.addEdge(jButtonArrayList.get(i + 65 + line), jButtonArrayList.get(i + 74 + line));
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    //Make false visible of back and forward of the wall button that clicked.
    protected void MakeFalse(ArrayList<JButton> jButtonArrayList, int i) {
        if ((i + 1) <= 64) { //Vertical
            jButtonArrayList.get(i + 64).setVisible(false);
            if ((i + 1) <= 56) {
                jButtonArrayList.get(i + 8).setVisible(false);
            }
            if ((i + 1) >= 9) {
                jButtonArrayList.get(i - 8).setVisible(false);
            }
        } else if ((i + 1) <= 128) { //Horizontal
            jButtonArrayList.get(i - 64).setVisible(false);
            if (((i + 1) % 8) != 1) {
                jButtonArrayList.get(i - 1).setVisible(false);
            }
            if (((i + 1) % 8) != 0) {
                jButtonArrayList.get(i + 1).setVisible(false);
            }
        }
    }
}