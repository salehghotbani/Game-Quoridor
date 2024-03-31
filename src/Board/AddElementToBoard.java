package Board;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class AddElementToBoard {
    private final int i = 10, j = 20;
    private JButton Wall1, Wall2, Wall3, Wall4, Wall6, Wall7, Wall8, Wall9, Wall10, Wall11, Wall12, Wall13, Wall14, Wall15, Wall16,
            Wall17, Wall18, Wall19, Wall20, Wall21, Wall22, Wall23, Wall24, Wall25, Wall26, Wall27, Wall28, Wall29, Wall30, Wall31,
            Wall32, Wall33, Wall34, Wall35, Wall36, Wall37, Wall38, Wall39, Wall40, Wall41, Wall42, Wall43, Wall44, Wall45, Wall46,
            Wall47, Wall48, Wall49, Wall50, Wall51, Wall52, Wall53, Wall54, Wall55, Wall56, Wall57, Wall58, Wall59, Wall60, Wall61,
            Wall62, Wall63, Wall64, Wall65, WallHorizontal1, WallHorizontal2, WallHorizontal3, WallHorizontal4, WallHorizontal5, WallHorizontal6,
            WallHorizontal7, WallHorizontal8, WallHorizontal9, WallHorizontal10, WallHorizontal11, WallHorizontal12, WallHorizontal13, WallHorizontal14,
            WallHorizontal15, WallHorizontal16, WallHorizontal17, WallHorizontal18, WallHorizontal19, WallHorizontal20, WallHorizontal21, WallHorizontal22,
            WallHorizontal23, WallHorizontal24, WallHorizontal25, WallHorizontal26, WallHorizontal27, WallHorizontal28, WallHorizontal29, WallHorizontal30,
            WallHorizontal31, WallHorizontal32, WallHorizontal33, WallHorizontal34, WallHorizontal35, WallHorizontal36, WallHorizontal37, WallHorizontal38,
            WallHorizontal39, WallHorizontal40, WallHorizontal41, WallHorizontal42, WallHorizontal43, WallHorizontal44, WallHorizontal45, WallHorizontal46,
            WallHorizontal47, WallHorizontal48, WallHorizontal49, WallHorizontal50, WallHorizontal51, WallHorizontal52, WallHorizontal53, WallHorizontal54,
            WallHorizontal55, WallHorizontal56, WallHorizontal57, WallHorizontal58, WallHorizontal59, WallHorizontal60, WallHorizontal61, WallHorizontal62,
            WallHorizontal63, WallHorizontal64, Field1, Field2, Field3, Field4, Field5, Field6, Field7, Field8, Field9, Field10, Field11, Field12, Field13,
            Field14, Field15, Field16, Field17, Field18, Field19, Field20, Field21, Field22, Field23, Field24, Field25, Field26, Field27, Field28, Field29,
            Field30, Field31, Field32, Field33, Field34, Field35, Field36, Field37, Field38, Field39, Field40, Field41, Field42, Field43, Field44, Field45,
            Field46, Field47, Field48, Field49, Field50, Field51, Field52, Field53, Field54, Field55, Field56, Field57, Field58, Field59, Field60, Field61,
            Field62, Field63, Field64, Field65, Field66, Field67, Field68, Field69, Field70, Field71, Field72, Field73, Field74, Field75, Field76, Field77,
            Field78, Field79, Field80, Field81, SaveButton;
    protected JButton PlayerColorRound1, PlayerColorRound2;
    protected JLabel Player1Name, Player2Name, Player1WoodCount, Player2WoodCount;

    AddElementToBoard(JPanel panel, ArrayList<JButton> jButtonArrayList, int WallOfPlayer1, int WallOfPlayer2) {
        AddJLabel(WallOfPlayer1, WallOfPlayer2); //Add JLabel and PlayerColorRound Button to jButtonArrayList
        AddVerticalWall(jButtonArrayList);       //Creat and Add Vertical Wall to jButtonArrayList
        AddHorizontalWall(jButtonArrayList);     //Creat and Add Horizontal Wall to jButtonArrayList
        AddField(jButtonArrayList);              //Creat and Add Field Button to jButtonArrayList
        AddSaveButton(jButtonArrayList);         //Creat and Add Save Button to jButtonArrayList
        SetBoundedOfWalls();                     //Set Bounded Of Walls
        SetBoundedOfField();                     //Set Bounded Of Field
        AddToPanel(panel);                       //Add elements to Panel
    }

    private void AddJLabel(int WallOfPlayer1, int WallOfPlayer2) {
        Player1Name = new JLabel();
        Player2Name = new JLabel();
        Player1WoodCount = new JLabel();
        Player2WoodCount = new JLabel();

        Player1WoodCount.setText("Count Wood : " + WallOfPlayer1);
        Player2WoodCount.setText("Count Wood : " + WallOfPlayer2);

        Player1Name.setBounds(390, 80, 200, 30);
        Player2Name.setBounds(390, 110, 200, 30);
        Player1WoodCount.setBounds(375, 150, 200, 30);
        Player2WoodCount.setBounds(375, 180, 200, 30);

        Player1Name.setForeground(Color.white);
        Player2Name.setForeground(Color.white);
        Player1WoodCount.setForeground(Color.BLUE);
        Player2WoodCount.setForeground(Color.GREEN);

        PlayerColorRound1 = new JButton();
        PlayerColorRound2 = new JButton();
        PlayerColorRound1.setBounds(373, 90, 12, 12);
        PlayerColorRound2.setBounds(373, 120, 12, 12);
        PlayerColorRound1.setBackground(Color.BLUE);
        PlayerColorRound2.setBackground(Color.GREEN);
        PlayerColorRound1.setEnabled(false);
        PlayerColorRound2.setEnabled(false);
        PlayerColorRound2.setVisible(false);
    }

    private void AddSaveButton(ArrayList<JButton> jButtonArrayList) {
        jButtonArrayList.add(SaveButton = new JButton());
        SaveButton.setBounds(400, 25, 70, 25);
        SaveButton.setText("Save");
    }

    private void AddField(ArrayList<JButton> jButtonArrayList) {
        jButtonArrayList.add(Field1 = new JButton());
        jButtonArrayList.add(Field2 = new JButton());
        jButtonArrayList.add(Field3 = new JButton());
        jButtonArrayList.add(Field4 = new JButton());
        jButtonArrayList.add(Field5 = new JButton());
        Field5.setBackground(Color.GREEN);
        jButtonArrayList.add(Field6 = new JButton());
        jButtonArrayList.add(Field7 = new JButton());
        jButtonArrayList.add(Field8 = new JButton());
        jButtonArrayList.add(Field9 = new JButton());
        jButtonArrayList.add(Field10 = new JButton());
        jButtonArrayList.add(Field11 = new JButton());
        jButtonArrayList.add(Field12 = new JButton());
        jButtonArrayList.add(Field13 = new JButton());
        jButtonArrayList.add(Field14 = new JButton());
        jButtonArrayList.add(Field15 = new JButton());
        jButtonArrayList.add(Field16 = new JButton());
        jButtonArrayList.add(Field17 = new JButton());
        jButtonArrayList.add(Field18 = new JButton());
        jButtonArrayList.add(Field19 = new JButton());
        jButtonArrayList.add(Field20 = new JButton());
        jButtonArrayList.add(Field21 = new JButton());
        jButtonArrayList.add(Field22 = new JButton());
        jButtonArrayList.add(Field23 = new JButton());
        jButtonArrayList.add(Field24 = new JButton());
        jButtonArrayList.add(Field25 = new JButton());
        jButtonArrayList.add(Field26 = new JButton());
        jButtonArrayList.add(Field27 = new JButton());
        jButtonArrayList.add(Field28 = new JButton());
        jButtonArrayList.add(Field29 = new JButton());
        jButtonArrayList.add(Field30 = new JButton());
        jButtonArrayList.add(Field31 = new JButton());
        jButtonArrayList.add(Field32 = new JButton());
        jButtonArrayList.add(Field33 = new JButton());
        jButtonArrayList.add(Field34 = new JButton());
        jButtonArrayList.add(Field35 = new JButton());
        jButtonArrayList.add(Field36 = new JButton());
        jButtonArrayList.add(Field37 = new JButton());
        jButtonArrayList.add(Field38 = new JButton());
        jButtonArrayList.add(Field39 = new JButton());
        jButtonArrayList.add(Field40 = new JButton());
        jButtonArrayList.add(Field41 = new JButton());
        jButtonArrayList.add(Field42 = new JButton());
        jButtonArrayList.add(Field43 = new JButton());
        jButtonArrayList.add(Field44 = new JButton());
        jButtonArrayList.add(Field45 = new JButton());
        jButtonArrayList.add(Field46 = new JButton());
        jButtonArrayList.add(Field47 = new JButton());
        jButtonArrayList.add(Field48 = new JButton());
        jButtonArrayList.add(Field49 = new JButton());
        jButtonArrayList.add(Field50 = new JButton());
        jButtonArrayList.add(Field51 = new JButton());
        jButtonArrayList.add(Field52 = new JButton());
        jButtonArrayList.add(Field53 = new JButton());
        jButtonArrayList.add(Field54 = new JButton());
        jButtonArrayList.add(Field55 = new JButton());
        jButtonArrayList.add(Field56 = new JButton());
        jButtonArrayList.add(Field57 = new JButton());
        jButtonArrayList.add(Field58 = new JButton());
        jButtonArrayList.add(Field59 = new JButton());
        jButtonArrayList.add(Field60 = new JButton());
        jButtonArrayList.add(Field61 = new JButton());
        jButtonArrayList.add(Field62 = new JButton());
        jButtonArrayList.add(Field63 = new JButton());
        jButtonArrayList.add(Field64 = new JButton());
        jButtonArrayList.add(Field65 = new JButton());
        jButtonArrayList.add(Field66 = new JButton());
        jButtonArrayList.add(Field67 = new JButton());
        jButtonArrayList.add(Field68 = new JButton());
        jButtonArrayList.add(Field69 = new JButton());
        jButtonArrayList.add(Field70 = new JButton());
        jButtonArrayList.add(Field71 = new JButton());
        jButtonArrayList.add(Field72 = new JButton());
        jButtonArrayList.add(Field73 = new JButton());
        jButtonArrayList.add(Field74 = new JButton());
        jButtonArrayList.add(Field75 = new JButton());
        jButtonArrayList.add(Field76 = new JButton());
        jButtonArrayList.add(Field77 = new JButton());
        Field77.setBackground(Color.BLUE);
        jButtonArrayList.add(Field78 = new JButton());
        jButtonArrayList.add(Field79 = new JButton());
        jButtonArrayList.add(Field80 = new JButton());
        jButtonArrayList.add(Field81 = new JButton());
    }

    private void AddVerticalWall(ArrayList<JButton> jButtonArrayList) {
        jButtonArrayList.add(Wall1 = new JButton());
        jButtonArrayList.add(Wall2 = new JButton());
        jButtonArrayList.add(Wall3 = new JButton());
        jButtonArrayList.add(Wall4 = new JButton());
        jButtonArrayList.add(Wall6 = new JButton());
        jButtonArrayList.add(Wall7 = new JButton());
        jButtonArrayList.add(Wall8 = new JButton());
        jButtonArrayList.add(Wall9 = new JButton());
        jButtonArrayList.add(Wall10 = new JButton());
        jButtonArrayList.add(Wall11 = new JButton());
        jButtonArrayList.add(Wall12 = new JButton());
        jButtonArrayList.add(Wall13 = new JButton());
        jButtonArrayList.add(Wall14 = new JButton());
        jButtonArrayList.add(Wall15 = new JButton());
        jButtonArrayList.add(Wall16 = new JButton());
        jButtonArrayList.add(Wall17 = new JButton());
        jButtonArrayList.add(Wall18 = new JButton());
        jButtonArrayList.add(Wall19 = new JButton());
        jButtonArrayList.add(Wall20 = new JButton());
        jButtonArrayList.add(Wall21 = new JButton());
        jButtonArrayList.add(Wall22 = new JButton());
        jButtonArrayList.add(Wall23 = new JButton());
        jButtonArrayList.add(Wall24 = new JButton());
        jButtonArrayList.add(Wall25 = new JButton());
        jButtonArrayList.add(Wall26 = new JButton());
        jButtonArrayList.add(Wall27 = new JButton());
        jButtonArrayList.add(Wall28 = new JButton());
        jButtonArrayList.add(Wall29 = new JButton());
        jButtonArrayList.add(Wall30 = new JButton());
        jButtonArrayList.add(Wall31 = new JButton());
        jButtonArrayList.add(Wall32 = new JButton());
        jButtonArrayList.add(Wall33 = new JButton());
        jButtonArrayList.add(Wall34 = new JButton());
        jButtonArrayList.add(Wall35 = new JButton());
        jButtonArrayList.add(Wall36 = new JButton());
        jButtonArrayList.add(Wall37 = new JButton());
        jButtonArrayList.add(Wall38 = new JButton());
        jButtonArrayList.add(Wall39 = new JButton());
        jButtonArrayList.add(Wall40 = new JButton());
        jButtonArrayList.add(Wall41 = new JButton());
        jButtonArrayList.add(Wall42 = new JButton());
        jButtonArrayList.add(Wall43 = new JButton());
        jButtonArrayList.add(Wall44 = new JButton());
        jButtonArrayList.add(Wall45 = new JButton());
        jButtonArrayList.add(Wall46 = new JButton());
        jButtonArrayList.add(Wall47 = new JButton());
        jButtonArrayList.add(Wall48 = new JButton());
        jButtonArrayList.add(Wall49 = new JButton());
        jButtonArrayList.add(Wall50 = new JButton());
        jButtonArrayList.add(Wall51 = new JButton());
        jButtonArrayList.add(Wall52 = new JButton());
        jButtonArrayList.add(Wall53 = new JButton());
        jButtonArrayList.add(Wall54 = new JButton());
        jButtonArrayList.add(Wall55 = new JButton());
        jButtonArrayList.add(Wall56 = new JButton());
        jButtonArrayList.add(Wall57 = new JButton());
        jButtonArrayList.add(Wall58 = new JButton());
        jButtonArrayList.add(Wall59 = new JButton());
        jButtonArrayList.add(Wall60 = new JButton());
        jButtonArrayList.add(Wall61 = new JButton());
        jButtonArrayList.add(Wall62 = new JButton());
        jButtonArrayList.add(Wall63 = new JButton());
        jButtonArrayList.add(Wall64 = new JButton());
        jButtonArrayList.add(Wall65 = new JButton());
    }

    private void AddHorizontalWall(ArrayList<JButton> jButtonArrayList) {
        jButtonArrayList.add(WallHorizontal1 = new JButton());
        jButtonArrayList.add(WallHorizontal2 = new JButton());
        jButtonArrayList.add(WallHorizontal3 = new JButton());
        jButtonArrayList.add(WallHorizontal4 = new JButton());
        jButtonArrayList.add(WallHorizontal5 = new JButton());
        jButtonArrayList.add(WallHorizontal6 = new JButton());
        jButtonArrayList.add(WallHorizontal7 = new JButton());
        jButtonArrayList.add(WallHorizontal8 = new JButton());
        jButtonArrayList.add(WallHorizontal9 = new JButton());
        jButtonArrayList.add(WallHorizontal10 = new JButton());
        jButtonArrayList.add(WallHorizontal11 = new JButton());
        jButtonArrayList.add(WallHorizontal12 = new JButton());
        jButtonArrayList.add(WallHorizontal13 = new JButton());
        jButtonArrayList.add(WallHorizontal14 = new JButton());
        jButtonArrayList.add(WallHorizontal15 = new JButton());
        jButtonArrayList.add(WallHorizontal16 = new JButton());
        jButtonArrayList.add(WallHorizontal17 = new JButton());
        jButtonArrayList.add(WallHorizontal18 = new JButton());
        jButtonArrayList.add(WallHorizontal19 = new JButton());
        jButtonArrayList.add(WallHorizontal20 = new JButton());
        jButtonArrayList.add(WallHorizontal21 = new JButton());
        jButtonArrayList.add(WallHorizontal22 = new JButton());
        jButtonArrayList.add(WallHorizontal23 = new JButton());
        jButtonArrayList.add(WallHorizontal24 = new JButton());
        jButtonArrayList.add(WallHorizontal25 = new JButton());
        jButtonArrayList.add(WallHorizontal26 = new JButton());
        jButtonArrayList.add(WallHorizontal27 = new JButton());
        jButtonArrayList.add(WallHorizontal28 = new JButton());
        jButtonArrayList.add(WallHorizontal29 = new JButton());
        jButtonArrayList.add(WallHorizontal30 = new JButton());
        jButtonArrayList.add(WallHorizontal31 = new JButton());
        jButtonArrayList.add(WallHorizontal32 = new JButton());
        jButtonArrayList.add(WallHorizontal33 = new JButton());
        jButtonArrayList.add(WallHorizontal34 = new JButton());
        jButtonArrayList.add(WallHorizontal35 = new JButton());
        jButtonArrayList.add(WallHorizontal36 = new JButton());
        jButtonArrayList.add(WallHorizontal37 = new JButton());
        jButtonArrayList.add(WallHorizontal38 = new JButton());
        jButtonArrayList.add(WallHorizontal39 = new JButton());
        jButtonArrayList.add(WallHorizontal40 = new JButton());
        jButtonArrayList.add(WallHorizontal41 = new JButton());
        jButtonArrayList.add(WallHorizontal42 = new JButton());
        jButtonArrayList.add(WallHorizontal43 = new JButton());
        jButtonArrayList.add(WallHorizontal44 = new JButton());
        jButtonArrayList.add(WallHorizontal45 = new JButton());
        jButtonArrayList.add(WallHorizontal46 = new JButton());
        jButtonArrayList.add(WallHorizontal47 = new JButton());
        jButtonArrayList.add(WallHorizontal48 = new JButton());
        jButtonArrayList.add(WallHorizontal49 = new JButton());
        jButtonArrayList.add(WallHorizontal50 = new JButton());
        jButtonArrayList.add(WallHorizontal51 = new JButton());
        jButtonArrayList.add(WallHorizontal52 = new JButton());
        jButtonArrayList.add(WallHorizontal53 = new JButton());
        jButtonArrayList.add(WallHorizontal54 = new JButton());
        jButtonArrayList.add(WallHorizontal55 = new JButton());
        jButtonArrayList.add(WallHorizontal56 = new JButton());
        jButtonArrayList.add(WallHorizontal57 = new JButton());
        jButtonArrayList.add(WallHorizontal58 = new JButton());
        jButtonArrayList.add(WallHorizontal59 = new JButton());
        jButtonArrayList.add(WallHorizontal60 = new JButton());
        jButtonArrayList.add(WallHorizontal61 = new JButton());
        jButtonArrayList.add(WallHorizontal62 = new JButton());
        jButtonArrayList.add(WallHorizontal63 = new JButton());
        jButtonArrayList.add(WallHorizontal64 = new JButton());
    }

    private void SetBoundedOfWalls() {
        //Vertical:
        Wall1.setBounds(315 + i, 5 + j, 10, 70);
        Wall2.setBounds(275 + i, 5 + j, 10, 70);
        Wall3.setBounds(235 + i, 5 + j, 10, 70);
        Wall4.setBounds(195 + i, 5 + j, 10, 70);
        Wall6.setBounds(155 + i, 5 + j, 10, 70);
        Wall7.setBounds(115 + i, 5 + j, 10, 70);
        Wall8.setBounds(75 + i, 5 + j, 10, 70);
        Wall9.setBounds(35 + i, 5 + j, 10, 70);
        Wall10.setBounds(315 + i, 45 + j, 10, 70);
        Wall11.setBounds(275 + i, 45 + j, 10, 70);
        Wall12.setBounds(235 + i, 45 + j, 10, 70);
        Wall13.setBounds(195 + i, 45 + j, 10, 70);
        Wall14.setBounds(155 + i, 45 + j, 10, 70);
        Wall15.setBounds(115 + i, 45 + j, 10, 70);
        Wall16.setBounds(75 + i, 45 + j, 10, 70);
        Wall17.setBounds(35 + i, 45 + j, 10, 70);
        Wall18.setBounds(315 + i, 85 + j, 10, 70);
        Wall19.setBounds(275 + i, 85 + j, 10, 70);
        Wall20.setBounds(235 + i, 85 + j, 10, 70);
        Wall21.setBounds(195 + i, 85 + j, 10, 70);
        Wall22.setBounds(155 + i, 85 + j, 10, 70);
        Wall23.setBounds(115 + i, 85 + j, 10, 70);
        Wall24.setBounds(75 + i, 85 + j, 10, 70);
        Wall25.setBounds(35 + i, 85 + j, 10, 70);
        Wall26.setBounds(315 + i, 125 + j, 10, 70);
        Wall27.setBounds(275 + i, 120 + j, 10, 75);
        Wall28.setBounds(235 + i, 125 + j, 10, 70);
        Wall29.setBounds(195 + i, 125 + j, 10, 70);
        Wall30.setBounds(155 + i, 120 + j, 10, 75);
        Wall31.setBounds(115 + i, 125 + j, 10, 70);
        Wall32.setBounds(75 + i, 125 + j, 10, 70);
        Wall33.setBounds(35 + i, 120 + j, 10, 75);
        Wall34.setBounds(315 + i, 165 + j, 10, 70);
        Wall35.setBounds(275 + i, 165 + j, 10, 70);
        Wall36.setBounds(235 + i, 165 + j, 10, 70);
        Wall37.setBounds(195 + i, 165 + j, 10, 70);
        Wall38.setBounds(155 + i, 165 + j, 10, 70);
        Wall39.setBounds(115 + i, 165 + j, 10, 70);
        Wall40.setBounds(75 + i, 165 + j, 10, 70);
        Wall41.setBounds(35 + i, 165 + j, 10, 70);
        Wall42.setBounds(315 + i, 205 + j, 10, 70);
        Wall43.setBounds(275 + i, 205 + j, 10, 70);
        Wall44.setBounds(235 + i, 205 + j, 10, 70);
        Wall45.setBounds(195 + i, 205 + j, 10, 70);
        Wall46.setBounds(155 + i, 205 + j, 10, 70);
        Wall47.setBounds(115 + i, 205 + j, 10, 70);
        Wall48.setBounds(75 + i, 205 + j, 10, 70);
        Wall49.setBounds(35 + i, 205 + j, 10, 70);
        Wall50.setBounds(315 + i, 245 + j, 10, 75);
        Wall51.setBounds(275 + i, 245 + j, 10, 75);
        Wall52.setBounds(235 + i, 245 + j, 10, 70);
        Wall53.setBounds(195 + i, 245 + j, 10, 75);
        Wall54.setBounds(155 + i, 245 + j, 10, 75);
        Wall55.setBounds(115 + i, 245 + j, 10, 70);
        Wall56.setBounds(75 + i, 245 + j, 10, 75);
        Wall57.setBounds(35 + i, 245 + j, 10, 75);
        Wall58.setBounds(315 + i, 285 + j, 10, 70);
        Wall59.setBounds(275 + i, 285 + j, 10, 70);
        Wall60.setBounds(235 + i, 285 + j, 10, 70);
        Wall61.setBounds(195 + i, 285 + j, 10, 70);
        Wall62.setBounds(155 + i, 285 + j, 10, 70);
        Wall63.setBounds(115 + i, 285 + j, 10, 70);
        Wall64.setBounds(75 + i, 285 + j, 10, 70);
        Wall65.setBounds(35 + i, 285 + j, 10, 70);
        //Horizontal1:
        WallHorizontal1.setBounds(285 + i, 35 + j, 70, 10);
        WallHorizontal2.setBounds(245 + i, 35 + j, 70, 10);
        WallHorizontal3.setBounds(205 + i, 35 + j, 70, 10);
        WallHorizontal4.setBounds(165 + i, 35 + j, 70, 10);
        WallHorizontal5.setBounds(125 + i, 35 + j, 70, 10);
        WallHorizontal6.setBounds(85 + i, 35 + j, 70, 10);
        WallHorizontal7.setBounds(45 + i, 35 + j, 70, 10);
        WallHorizontal8.setBounds(5 + i, 35 + j, 70, 10);
        WallHorizontal9.setBounds(285 + i, 75 + j, 70, 10);
        WallHorizontal10.setBounds(245 + i, 75 + j, 70, 10);
        WallHorizontal11.setBounds(205 + i, 75 + j, 70, 10);
        WallHorizontal12.setBounds(165 + i, 75 + j, 70, 10);
        WallHorizontal13.setBounds(125 + i, 75 + j, 70, 10);
        WallHorizontal14.setBounds(85 + i, 75 + j, 70, 10);
        WallHorizontal15.setBounds(45 + i, 75 + j, 70, 10);
        WallHorizontal16.setBounds(5 + i, 75 + j, 70, 10);
        WallHorizontal17.setBounds(285 + i, 115 + j, 70, 10);
        WallHorizontal18.setBounds(245 + i, 115 + j, 70, 10);
        WallHorizontal19.setBounds(205 + i, 115 + j, 70, 10);
        WallHorizontal20.setBounds(165 + i, 115 + j, 70, 10);
        WallHorizontal21.setBounds(125 + i, 115 + j, 70, 10);
        WallHorizontal22.setBounds(85 + i, 115 + j, 70, 10);
        WallHorizontal23.setBounds(45 + i, 115 + j, 70, 10);
        WallHorizontal24.setBounds(5 + i, 115 + j, 70, 10);
        WallHorizontal25.setBounds(285 + i, 155 + j, 70, 10);
        WallHorizontal26.setBounds(245 + i, 155 + j, 70, 10);
        WallHorizontal27.setBounds(205 + i, 155 + j, 70, 10);
        WallHorizontal28.setBounds(165 + i, 155 + j, 70, 10);
        WallHorizontal29.setBounds(125 + i, 155 + j, 70, 10);
        WallHorizontal30.setBounds(85 + i, 155 + j, 70, 10);
        WallHorizontal31.setBounds(45 + i, 155 + j, 70, 10);
        WallHorizontal32.setBounds(5 + i, 155 + j, 70, 10);
        WallHorizontal33.setBounds(285 + i, 195 + j, 70, 10);
        WallHorizontal34.setBounds(245 + i, 195 + j, 70, 10);
        WallHorizontal35.setBounds(205 + i, 195 + j, 70, 10);
        WallHorizontal36.setBounds(165 + i, 195 + j, 70, 10);
        WallHorizontal37.setBounds(125 + i, 195 + j, 70, 10);
        WallHorizontal38.setBounds(85 + i, 195 + j, 70, 10);
        WallHorizontal39.setBounds(45 + i, 195 + j, 70, 10);
        WallHorizontal40.setBounds(5 + i, 195 + j, 70, 10);
        WallHorizontal41.setBounds(285 + i, 235 + j, 70, 10);
        WallHorizontal42.setBounds(245 + i, 235 + j, 70, 10);
        WallHorizontal43.setBounds(205 + i, 235 + j, 70, 10);
        WallHorizontal44.setBounds(165 + i, 235 + j, 70, 10);
        WallHorizontal45.setBounds(125 + i, 235 + j, 70, 10);
        WallHorizontal46.setBounds(85 + i, 235 + j, 70, 10);
        WallHorizontal47.setBounds(45 + i, 235 + j, 70, 10);
        WallHorizontal48.setBounds(5 + i, 235 + j, 70, 10);
        WallHorizontal49.setBounds(285 + i, 275 + j, 70, 10);
        WallHorizontal50.setBounds(245 + i, 275 + j, 70, 10);
        WallHorizontal51.setBounds(205 + i, 275 + j, 70, 10);
        WallHorizontal52.setBounds(165 + i, 275 + j, 70, 10);
        WallHorizontal53.setBounds(125 + i, 275 + j, 70, 10);
        WallHorizontal54.setBounds(85 + i, 275 + j, 70, 10);
        WallHorizontal55.setBounds(45 + i, 275 + j, 70, 10);
        WallHorizontal56.setBounds(5 + i, 275 + j, 70, 10);
        WallHorizontal57.setBounds(285 + i, 315 + j, 70, 10);
        WallHorizontal58.setBounds(245 + i, 315 + j, 70, 10);
        WallHorizontal59.setBounds(205 + i, 315 + j, 70, 10);
        WallHorizontal60.setBounds(165 + i, 315 + j, 70, 10);
        WallHorizontal61.setBounds(125 + i, 315 + j, 70, 10);
        WallHorizontal62.setBounds(85 + i, 315 + j, 70, 10);
        WallHorizontal63.setBounds(45 + i, 315 + j, 70, 10);
        WallHorizontal64.setBounds(5 + i, 315 + j, 70, 10);
    }

    private void SetBoundedOfField() {
        Field1.setBounds(325 + i, 5 + j, 30, 30);
        Field2.setBounds(285 + i, 5 + j, 30, 30);
        Field3.setBounds(245 + i, 5 + j, 30, 30);
        Field4.setBounds(205 + i, 5 + j, 30, 30);
        Field5.setBounds(165 + i, 5 + j, 30, 30);
        Field6.setBounds(125 + i, 5 + j, 30, 30);
        Field7.setBounds(85 + i, 5 + j, 30, 30);
        Field8.setBounds(45 + i, 5 + j, 30, 30);
        Field9.setBounds(5 + i, 5 + j, 30, 30);
        Field10.setBounds(325 + i, 45 + j, 30, 30);
        Field11.setBounds(285 + i, 45 + j, 30, 30);
        Field12.setBounds(245 + i, 45 + j, 30, 30);
        Field13.setBounds(205 + i, 45 + j, 30, 30);
        Field14.setBounds(165 + i, 45 + j, 30, 30);
        Field15.setBounds(125 + i, 45 + j, 30, 30);
        Field16.setBounds(85 + i, 45 + j, 30, 30);
        Field17.setBounds(45 + i, 45 + j, 30, 30);
        Field18.setBounds(5 + i, 45 + j, 30, 30);
        Field19.setBounds(325 + i, 85 + j, 30, 30);
        Field20.setBounds(285 + i, 85 + j, 30, 30);
        Field21.setBounds(245 + i, 85 + j, 30, 30);
        Field22.setBounds(205 + i, 85 + j, 30, 30);
        Field23.setBounds(165 + i, 85 + j, 30, 30);
        Field24.setBounds(125 + i, 85 + j, 30, 30);
        Field25.setBounds(85 + i, 85 + j, 30, 30);
        Field26.setBounds(45 + i, 85 + j, 30, 30);
        Field27.setBounds(5 + i, 85 + j, 30, 30);
        Field28.setBounds(325 + i, 125 + j, 30, 30);
        Field29.setBounds(285 + i, 125 + j, 30, 30);
        Field30.setBounds(245 + i, 125 + j, 30, 30);
        Field31.setBounds(205 + i, 125 + j, 30, 30);
        Field32.setBounds(165 + i, 125 + j, 30, 30);
        Field33.setBounds(125 + i, 125 + j, 30, 30);
        Field34.setBounds(85 + i, 125 + j, 30, 30);
        Field35.setBounds(45 + i, 125 + j, 30, 30);
        Field36.setBounds(5 + i, 125 + j, 30, 30);
        Field37.setBounds(325 + i, 165 + j, 30, 30);
        Field38.setBounds(285 + i, 165 + j, 30, 30);
        Field39.setBounds(245 + i, 165 + j, 30, 30);
        Field40.setBounds(205 + i, 165 + j, 30, 30);
        Field41.setBounds(165 + i, 165 + j, 30, 30);
        Field42.setBounds(125 + i, 165 + j, 30, 30);
        Field43.setBounds(85 + i, 165 + j, 30, 30);
        Field44.setBounds(45 + i, 165 + j, 30, 30);
        Field45.setBounds(5 + i, 165 + j, 30, 30);
        Field46.setBounds(325 + i, 205 + j, 30, 30);
        Field47.setBounds(285 + i, 205 + j, 30, 30);
        Field48.setBounds(245 + i, 205 + j, 30, 30);
        Field49.setBounds(205 + i, 205 + j, 30, 30);
        Field50.setBounds(165 + i, 205 + j, 30, 30);
        Field51.setBounds(125 + i, 205 + j, 30, 30);
        Field52.setBounds(85 + i, 205 + j, 30, 30);
        Field53.setBounds(45 + i, 205 + j, 30, 30);
        Field54.setBounds(5 + i, 205 + j, 30, 30);
        Field55.setBounds(325 + i, 245 + j, 30, 30);
        Field56.setBounds(285 + i, 245 + j, 30, 30);
        Field57.setBounds(245 + i, 245 + j, 30, 30);
        Field58.setBounds(205 + i, 245 + j, 30, 30);
        Field59.setBounds(165 + i, 245 + j, 30, 30);
        Field60.setBounds(125 + i, 245 + j, 30, 30);
        Field61.setBounds(85 + i, 245 + j, 30, 30);
        Field62.setBounds(45 + i, 245 + j, 30, 30);
        Field63.setBounds(5 + i, 245 + j, 30, 30);
        Field64.setBounds(325 + i, 285 + j, 30, 30);
        Field65.setBounds(285 + i, 285 + j, 30, 30);
        Field66.setBounds(245 + i, 285 + j, 30, 30);
        Field67.setBounds(205 + i, 285 + j, 30, 30);
        Field68.setBounds(165 + i, 285 + j, 30, 30);
        Field69.setBounds(125 + i, 285 + j, 30, 30);
        Field70.setBounds(85 + i, 285 + j, 30, 30);
        Field71.setBounds(45 + i, 285 + j, 30, 30);
        Field72.setBounds(5 + i, 285 + j, 30, 30);
        Field73.setBounds(325 + i, 325 + j, 30, 30);
        Field74.setBounds(285 + i, 325 + j, 30, 30);
        Field75.setBounds(245 + i, 325 + j, 30, 30);
        Field76.setBounds(205 + i, 325 + j, 30, 30);
        Field77.setBounds(165 + i, 325 + j, 30, 30);
        Field78.setBounds(125 + i, 325 + j, 30, 30);
        Field79.setBounds(85 + i, 325 + j, 30, 30);
        Field80.setBounds(45 + i, 325 + j, 30, 30);
        Field81.setBounds(5 + i, 325 + j, 30, 30);
    }

    private void AddToPanel(JPanel panel) {
        panel.add(Field1);
        panel.add(Field2);
        panel.add(Field3);
        panel.add(Field4);
        panel.add(Field5);
        panel.add(Field6);
        panel.add(Field7);
        panel.add(Field8);
        panel.add(Field9);
        panel.add(Field10);
        panel.add(Field11);
        panel.add(Field12);
        panel.add(Field13);
        panel.add(Field14);
        panel.add(Field15);
        panel.add(Field16);
        panel.add(Field17);
        panel.add(Field18);
        panel.add(Field19);
        panel.add(Field20);
        panel.add(Field21);
        panel.add(Field22);
        panel.add(Field23);
        panel.add(Field24);
        panel.add(Field25);
        panel.add(Field26);
        panel.add(Field27);
        panel.add(Field28);
        panel.add(Field29);
        panel.add(Field30);
        panel.add(Field31);
        panel.add(Field32);
        panel.add(Field33);
        panel.add(Field34);
        panel.add(Field35);
        panel.add(Field36);
        panel.add(Field37);
        panel.add(Field38);
        panel.add(Field39);
        panel.add(Field40);
        panel.add(Field41);
        panel.add(Field42);
        panel.add(Field43);
        panel.add(Field44);
        panel.add(Field45);
        panel.add(Field46);
        panel.add(Field47);
        panel.add(Field48);
        panel.add(Field49);
        panel.add(Field50);
        panel.add(Field51);
        panel.add(Field52);
        panel.add(Field53);
        panel.add(Field54);
        panel.add(Field55);
        panel.add(Field56);
        panel.add(Field57);
        panel.add(Field58);
        panel.add(Field59);
        panel.add(Field60);
        panel.add(Field61);
        panel.add(Field62);
        panel.add(Field63);
        panel.add(Field64);
        panel.add(Field65);
        panel.add(Field66);
        panel.add(Field67);
        panel.add(Field68);
        panel.add(Field69);
        panel.add(Field70);
        panel.add(Field71);
        panel.add(Field72);
        panel.add(Field73);
        panel.add(Field74);
        panel.add(Field75);
        panel.add(Field76);
        panel.add(Field77);
        panel.add(Field78);
        panel.add(Field79);
        panel.add(Field80);
        panel.add(Field81);
        panel.add(SaveButton);
        panel.add(Player1Name);
        panel.add(Player2Name);
        panel.add(PlayerColorRound1);
        panel.add(PlayerColorRound2);
        panel.add(Player1WoodCount);
        panel.add(Player2WoodCount);
    }
}