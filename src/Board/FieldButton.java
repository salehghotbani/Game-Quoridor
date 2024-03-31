package Board;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class FieldButton {
    private int position;

    static int foundLine(int i) {
        if (i - 127 < 10) {
            return 0;
        } else if (i - 127 <= 18) {
            return 1;
        } else if (i - 127 <= 27) {
            return 2;
        } else if (i - 127 <= 36) {
            return 3;
        } else if (i - 127 <= 45) {
            return 4;
        } else if (i - 127 <= 54) {
            return 5;
        } else if (i - 127 <= 63) {
            return 6;
        } else if (i - 127 <= 72) {
            return 7;
        } else if (i - 127 <= 81) {
            return 8;
        } else {
            return 0;
        }
    }

    FieldButton(ArrayList<JButton> jButtonArrayList, boolean isRoundPlayer1) {
        doFalse(jButtonArrayList);
        fieldButtonEnableFalse(jButtonArrayList, isRoundPlayer1);
    }

    private void doFalse(ArrayList<JButton> jButtonArrayList) {
        for (int i = 128; i < jButtonArrayList.size() - 1; i++) {
            jButtonArrayList.get(i).setEnabled(false);
        }
    }

    protected int setPositionButton() {
        return position;
    }
    //BARAY SOTON AVAL AZ SAMT RAST
    public void case1(ArrayList<JButton> jButtonArrayList, int i, Color opponent) {
        int line, f = 1, b = 1, l = 0;
        position = i;
        line = foundLine(i);
        //forward for Blue and bacward for green
        if (i - 127 != 1) {
            if (!(jButtonArrayList.get(i - 72 - line).getBackground() == Color.ORANGE)) {
                if (jButtonArrayList.get(i - 9).getBackground() == opponent) {
                    if (!(jButtonArrayList.get(i - 81 - foundLine(i - 9)).getBackground() == Color.ORANGE)) {
                        f = 2;
                    } else {
                        if (line != 0 && line != 8) {
                            if (!(jButtonArrayList.get(i - 137 - foundLine(i - 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 145 - foundLine(i - 9)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i - 8).setEnabled(true);
                            }
                        } else {
                            if (!(jButtonArrayList.get(i - 137 - foundLine(i - 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 145 - foundLine(i - 9)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i - 8).setEnabled(true);
                            }
                        }

                    }
                }
                jButtonArrayList.get(i - f * 9).setEnabled(true);
            }
        }
        //backward for blue and forward for green
        if (!(jButtonArrayList.get(i - 64 - line).getBackground() == Color.ORANGE)) {
            if (jButtonArrayList.get(i + 9).getBackground() == opponent) {
                if (!(jButtonArrayList.get(i - 55 - foundLine(i + 9)).getBackground() == Color.ORANGE)) {
                    b = 2;
                } else {
                    if (line != 0 && line != 8) {
                        if (!(jButtonArrayList.get(i - 119 - foundLine(i + 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 127 - foundLine(i + 9)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i + 10).setEnabled(true);
                        }
                    } else {
                        if (!(jButtonArrayList.get(i - 119 - foundLine(i + 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 127 - foundLine(i + 9)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i + 10).setEnabled(true);
                        }
                    }

                }
            }
            jButtonArrayList.get(i + b * 9).setEnabled(true);
        }
        //left for greeen and blue
        if (line != 0 && line != 8) {
            if (!(jButtonArrayList.get(i - 128 - line).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 136 - line).getBackground() == Color.ORANGE)) {
                if (jButtonArrayList.get(i + 1).getBackground() == opponent) {
                    if (!(jButtonArrayList.get(i - 127 - line).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 135 - line).getBackground() == Color.ORANGE)) {
                        l = 1;
                    } else {
                        if (!(jButtonArrayList.get(i - 71 - foundLine(i + 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 72 - foundLine(i + 1)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i - 8).setEnabled(true);
                        }
                        if (!(jButtonArrayList.get(i - 64 - foundLine(i + 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 63 - foundLine(i + 1)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i + 10).setEnabled(true);
                        }
                    }
                }
                jButtonArrayList.get(i + 1 + l).setEnabled(true);
            }
        } else {
            if (jButtonArrayList.get(i).getBackground() == Color.BLUE) {
                if (!(jButtonArrayList.get(i - 136 - line).getBackground() == Color.ORANGE)) {
                    if (jButtonArrayList.get(i + 1).getBackground() == opponent) {
                        if (!(jButtonArrayList.get(i - 127 - line).getBackground() == Color.ORANGE)) {

                            l = 1;
                        } else {
                            if (!(jButtonArrayList.get(i - 71 - foundLine(i + 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 72 - foundLine(i + 1)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i - 8).setEnabled(true);
                            }
                            if (!(jButtonArrayList.get(i - 64 - foundLine(i + 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 63 - foundLine(i + 1)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i + 10).setEnabled(true);
                            }
                        }
                    }
                    jButtonArrayList.get(i + 1 + l).setEnabled(true);
                }

            } else {
                if (!(jButtonArrayList.get(i - 128 - line).getBackground() == Color.ORANGE)) {
                    if (jButtonArrayList.get(i + 1).getBackground() == opponent) {
                        if (!(jButtonArrayList.get(i - 127 - line).getBackground() == Color.ORANGE)) {
                            l = 1;
                        } else {
                            if (!(jButtonArrayList.get(i - 64 - foundLine(i + 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 63 - foundLine(i + 1)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i + 10).setEnabled(true);
                            }
                        }
                    }
                    jButtonArrayList.get(i + 1 + l).setEnabled(true);
                }

            }

        }
        if (jButtonArrayList.get(i + 1).getBackground() == opponent) {
            jButtonArrayList.get(i + 1).setEnabled(false);
        }
        if (jButtonArrayList.get(i - 9).getBackground() == opponent) {
            jButtonArrayList.get(i - 9).setEnabled(false);
        }
        if (line != 8) {
            if (jButtonArrayList.get(i + 9).getBackground() == opponent) {
                jButtonArrayList.get(i + 9).setEnabled(false);
            }
        }
    }
    // BARAYE SOTONHAYE 2 TA 8 AZ SAMTE RAST
    private void case2_8(ArrayList<JButton> jButtonArrayList, int i, Color opponent) {
        int line, f = 1, b = 1, l = 0, r = 0;
        position = i;
        line = foundLine(i);
        //backward for blue and forward for green
        if (line != 8) {
            if (!(jButtonArrayList.get(i - 65 - line).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 64 - line).getBackground() == Color.ORANGE)) {
                if (jButtonArrayList.get(i + 9).getBackground() == opponent) {
                    if (!(jButtonArrayList.get(i - 55 - foundLine(i + 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 56 - foundLine(i + 9)).getBackground() == Color.ORANGE)) {
                        b = 2;
                    } else {
                        if (!(jButtonArrayList.get(i - 119 - foundLine(i + 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 127 - foundLine(i + 9)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i + 10).setEnabled(true);

                        }
                        if (!(jButtonArrayList.get(i - 120 - foundLine(i + 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 128 - foundLine(i + 9)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i + 8).setEnabled(true);

                        }
                    }
                }
                jButtonArrayList.get(i + 9 * b).setEnabled(true);
            }
        }
        //forward for blue and backward for green
        if (i - 127 >= 9) {
            if (!(jButtonArrayList.get(i - 72 - line).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 73 - line).getBackground() == Color.ORANGE)) {
                if (jButtonArrayList.get(i - 9).getBackground() == opponent) {
                    if (!(jButtonArrayList.get(i - 81 - foundLine(i - 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 82 - foundLine(i - 9)).getBackground() == Color.ORANGE)) {
                        f = 2;
                    } else {
                        if (!(jButtonArrayList.get(i - 137 - foundLine(i - 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 145 - foundLine(i - 9)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i - 8).setEnabled(true);

                        }
                        if (!(jButtonArrayList.get(i - 138 - foundLine(i - 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 146 - foundLine(i - 9)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i - 10).setEnabled(true);

                        }
                    }

                }
                jButtonArrayList.get(i - 9 * f).setEnabled(true);
            }
        }
        //Left for green and blue
        if (line != 0 && line != 8) {
            if (!(jButtonArrayList.get(i - 128 - line).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 136 - line).getBackground() == Color.ORANGE)) {
                if (jButtonArrayList.get(i + 1).getBackground() == opponent) {
                    if (!(jButtonArrayList.get(i - 127 - line).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 135 - line).getBackground() == Color.ORANGE)) {
                        if (Math.abs(i - 127) % 9 != 8) {
                            l = 1;
                        }
                    } else {
                        if (!(jButtonArrayList.get(i - 71 - foundLine(i + 1) + 1).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 72 - foundLine(i + 1)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i - 8).setEnabled(true);
                        }
                        if (!(jButtonArrayList.get(i - 64 - foundLine(i + 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 63 - foundLine(i + 1)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i + 10).setEnabled(true);
                        }
                    }
                }
                jButtonArrayList.get(i + 1 + l).setEnabled(true);
            }
        } else {
            if (jButtonArrayList.get(i).getBackground() == Color.BLUE) {
                if (!(jButtonArrayList.get(i - 136 - line).getBackground() == Color.ORANGE)) {
                    if (jButtonArrayList.get(i + 1).getBackground() == opponent) {
                        if (!(jButtonArrayList.get(i - 127 - line).getBackground() == Color.ORANGE)) {

                            l = 1;
                        } else {
                            if (!(jButtonArrayList.get(i - 71 - foundLine(i + 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 72 - foundLine(i + 1)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i - 8).setEnabled(true);
                            }
                            if (!(jButtonArrayList.get(i - 64 - foundLine(i + 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 63 - foundLine(i + 1)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i + 10).setEnabled(true);
                            }
                        }
                    }
                    jButtonArrayList.get(i + 1 + l).setEnabled(true);
                }
            } else {
                if (!(jButtonArrayList.get(i - 128 - line).getBackground() == Color.ORANGE)) {
                    if (jButtonArrayList.get(i + 1).getBackground() == opponent) {
                        if (!(jButtonArrayList.get(i - 127 - line).getBackground() == Color.ORANGE)) {
                            if (Math.abs(i - 127) % 9 != 8) {

                                l = 1;
                            }
                        } else {

                            if (!(jButtonArrayList.get(i - 64 - foundLine(i + 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 63 - foundLine(i + 1)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i + 10).setEnabled(true);
                            }
                        }
                    }
                    jButtonArrayList.get(i + 1 + l).setEnabled(true);
                }

            }
        }
        //Right for green and blue
        if (line != 0 && line != 8) {
            if (!(jButtonArrayList.get(i - 129 - line).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 137 - line).getBackground() == Color.ORANGE)) {
                if (jButtonArrayList.get(i - 1).getBackground() == opponent) {
                    if (!(jButtonArrayList.get(i - 130 - line).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 138 - line).getBackground() == Color.ORANGE)) {
                        if (Math.abs(i - 127) % 9 != 1) {

                            r = 1;
                        }
                    } else {
                        //up
                        if (!(jButtonArrayList.get(i - 73 - foundLine(i - 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 74 - foundLine(i - 1)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i - 10).setEnabled(true);
                        }
                        if (!(jButtonArrayList.get(i - 66 - foundLine(i - 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 65 - foundLine(i - 1)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i + 8).setEnabled(true);
                        }
                    }
                }

                jButtonArrayList.get(i - 1 - r).setEnabled(true);
            }
        } else {
            if (jButtonArrayList.get(i).getBackground() == Color.BLUE) {
                if (!(jButtonArrayList.get(i - 128 - line + 1 - 10).getBackground() == Color.ORANGE)) {
                    if (jButtonArrayList.get(i - 1).getBackground() == opponent) {
                        if (!(jButtonArrayList.get(i - 130 - line).getBackground() == Color.ORANGE)) {
                            r = 1;
                        } else {
                            //up
                            if (!(jButtonArrayList.get(i - 73 - foundLine(i - 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 74 - foundLine(i - 1)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i - 9 - 1).setEnabled(true);
                            }
                        }
                    }
                    jButtonArrayList.get(i - 1 - r).setEnabled(true);
                }
            } else {
                if (!(jButtonArrayList.get(i - 128 - line - 1).getBackground() == Color.ORANGE)) {
                    if (jButtonArrayList.get(i - 1).getBackground() == opponent) {
                        if (!(jButtonArrayList.get(i - 130 - line).getBackground() == Color.ORANGE)) {
                            if (Math.abs(i - 127) % 9 != 1) {

                                r = 1;
                            }
                        } else {

                            if (!(jButtonArrayList.get(i - 66 - foundLine(i - 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 65 - foundLine(i - 1)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i + 8).setEnabled(true);
                            }
                        }

                    }
                    jButtonArrayList.get(i - 1 - r).setEnabled(true);
                }
            }
        }

        if (jButtonArrayList.get(i + 1).getBackground() == opponent) {
            jButtonArrayList.get(i + 1).setEnabled(false);
        }

        if (jButtonArrayList.get(i - 1).getBackground() == opponent) {
            jButtonArrayList.get(i - 1).setEnabled(false);
        }

        if (jButtonArrayList.get(i - 9).getBackground() == opponent) {
            jButtonArrayList.get(i - 9).setEnabled(false);
        }

        if (line != 8) {
            if (jButtonArrayList.get(i + 9).getBackground() == opponent) {
                jButtonArrayList.get(i + 9).setEnabled(false);
            }
        }

    }
    // BARAYE SOTON AKHAR AZ SAMTE RAST
    private void case0(ArrayList<JButton> jButtonArrayList, int i, Color opponent) {
        int line, f = 1, b = 1, r = 0;
        position = i;
        line = foundLine(i);
        //forward for blue and backward for green
        if (!(jButtonArrayList.get(i - 65 - line).getBackground() == Color.ORANGE)) {
            if (jButtonArrayList.get(i + 9).getBackground() == opponent) {
                if (!(jButtonArrayList.get(i - 56 - foundLine(i + 9)).getBackground() == Color.ORANGE)) {
                    f = 2;
                } else {
                    if (!(jButtonArrayList.get(i - 120 - foundLine(i + 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 128 - foundLine(i + 9)).getBackground() == Color.ORANGE)) {
                        jButtonArrayList.get(i + 8).setEnabled(true);
                    }
                }
            }
            jButtonArrayList.get(i + 9 * f).setEnabled(true);
        }
        //backward for blue and forward for green
        if (i - 127 != 9) {
            if (!(jButtonArrayList.get(i - 73 - line).getBackground() == Color.ORANGE)) {
                if (jButtonArrayList.get(i - 9).getBackground() == opponent) {
                    if (!(jButtonArrayList.get(i - 82 - foundLine(i - 9)).getBackground() == Color.ORANGE)) {
                        b = 2;
                    } else {
                        if (line != 8) {
                            if (!(jButtonArrayList.get(i - 138 - foundLine(i - 9) - 1).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 146 - foundLine(i - 9)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i - 10).setEnabled(true);
                            }
                        } else {
                            if (!(jButtonArrayList.get(i - 138 - foundLine(i - 9)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 146 - foundLine(i - 9)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i - 10).setEnabled(true);
                            }
                        }

                    }
                }
                jButtonArrayList.get(i - 9 * b).setEnabled(true);
            }
        }
        //Right for green and blue
        if (line != 0 && line != 8) {
            if (!(jButtonArrayList.get(i - 129 - line).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 137 - line).getBackground() == Color.ORANGE)) {
                if (jButtonArrayList.get(i - 1).getBackground() == opponent) {
                    if (!(jButtonArrayList.get(i - 130 - line).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 138 - line).getBackground() == Color.ORANGE)) {
                        r = 1;
                    } else {
                        //up
                        if (!(jButtonArrayList.get(i - 73 - foundLine(i - 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 74 - foundLine(i - 1)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i - 10).setEnabled(true);
                        }
                        if (!(jButtonArrayList.get(i - 66 - foundLine(i - 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 65 - foundLine(i - 1)).getBackground() == Color.ORANGE)) {
                            jButtonArrayList.get(i + 8).setEnabled(true);
                        }

                    }
                }
                jButtonArrayList.get(i - 1 - r).setEnabled(true);
            }
        } else {
            if (jButtonArrayList.get(i).getBackground() == Color.BLUE) {
                if (!(jButtonArrayList.get(i - 137 - line).getBackground() == Color.ORANGE)) {
                    if (jButtonArrayList.get(i - 1).getBackground() == opponent) {
                        if (!(jButtonArrayList.get(i - 130 - line).getBackground() == Color.ORANGE)) {
                            r = 1;
                        } else {
                            //up
                            if (!(jButtonArrayList.get(i - 73 - foundLine(i - 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 74 - foundLine(i - 1)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i - 10).setEnabled(true);
                            }


                        }
                    }
                    jButtonArrayList.get(i - 1 - r).setEnabled(true);
                }
            } else {
                if (!(jButtonArrayList.get(i - 129 - line).getBackground() == Color.ORANGE)) {
                    if (jButtonArrayList.get(i - 1).getBackground() == opponent) {
                        if (!(jButtonArrayList.get(i - 130 - line).getBackground() == Color.ORANGE)) {
                            r = 1;
                        } else {
                            if (!(jButtonArrayList.get(i - 66 - foundLine(i - 1)).getBackground() == Color.ORANGE || jButtonArrayList.get(i - 65 - foundLine(i - 1)).getBackground() == Color.ORANGE)) {
                                jButtonArrayList.get(i + 8).setEnabled(true);
                            }
                        }
                    }
                    jButtonArrayList.get(i - 1 - r).setEnabled(true);
                }
            }
        }
        if (jButtonArrayList.get(i - 1).getBackground() == opponent) {
            jButtonArrayList.get(i - 1).setEnabled(false);
        }

        if (jButtonArrayList.get(i - 9).getBackground() == opponent) {
            jButtonArrayList.get(i - 9).setEnabled(false);
        }
        if (line != 8) {
            if (jButtonArrayList.get(i + 9).getBackground() == opponent) {
                jButtonArrayList.get(i + 9).setEnabled(false);
            }
        }
    }
    // IN METHOD BARAYE MOSHAKHAS KARDAN KHANEHAYE KE MITAVAN BE AN DASTRESI DASHT
    private void fieldButtonEnableFalse(ArrayList<JButton> jButtonArrayList, boolean isRoundPlayer1) {
        if (isRoundPlayer1) {
            for (int i = 128; i < jButtonArrayList.size() - 1; i++) {
                if (jButtonArrayList.get(i).getBackground() == Color.BLUE) {
                    Color opponent = Color.GREEN;
                    position = i;
                    switch (Math.abs(i - 127) % 9) {
//forward
                        case 1 -> case1(jButtonArrayList, i, opponent);
                        case 2, 3, 4, 5, 6, 7, 8 -> case2_8(jButtonArrayList, i, opponent);
                        case 0 -> case0(jButtonArrayList, i, opponent);
                    }
                    break;
                }
            }
        } else {
            for (int i = 128; i < jButtonArrayList.size() - 1; i++) {
                if (jButtonArrayList.get(i).getBackground() == Color.GREEN) {
                    Color opponent = Color.BLUE;
                    position = i;
                    switch (Math.abs(i - 127) % 9) {
                        case 1 -> case1(jButtonArrayList, i, opponent);
                        case 2, 3, 4, 5, 6, 7, 8 -> case2_8(jButtonArrayList, i, opponent);
                        case 0 -> case0(jButtonArrayList, i, opponent);
                    }
                    break;
                }
            }
        }
    }
}