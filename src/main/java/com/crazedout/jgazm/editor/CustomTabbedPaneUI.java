package com.crazedout.jgazm.editor;

import com.sun.java.swing.plaf.windows.WindowsTabbedPaneUI;

import javax.swing.plaf.metal.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.*;
/**
 * Created by Fredrik on 2016-04-25.
 */
class CustomTabbedPaneUI extends WindowsTabbedPaneUI
{
    Rectangle xRect;
    Editor editor;

    public CustomTabbedPaneUI(Editor edit){
        this.editor = edit;
    }

    protected void installListeners() {
        super.installListeners();
        tabPane.addMouseListener(new MyMouseHandler());
    }

    protected void paintTab(Graphics g, int tabPlacement,
                            Rectangle[] rects, int tabIndex,
                            Rectangle iconRect, Rectangle textRect) {

        super.paintTab(g, tabPlacement, rects, tabIndex, iconRect, textRect);

        Font f = g.getFont();
        g.setFont(new Font("Courier", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics(g.getFont());
        int charWidth = fm.charWidth('x')+4;
        int maxAscent = fm.getMaxAscent()+4;
        g.drawString("x", textRect.x + textRect.width - 8, textRect.y + textRect.height - 2);
        /*g.drawRect(textRect.x+textRect.width-5,
                textRect.y+textRect.height-maxAscent, charWidth+2, maxAscent-1);
                */
        xRect = new Rectangle(textRect.x+textRect.width-8,
                textRect.y+textRect.height-maxAscent, charWidth+2, maxAscent-2);

        g.setFont(f);

    }

    public class MyMouseHandler extends MouseAdapter {
        public void mousePressed(MouseEvent e) {
            if (xRect.contains(e.getPoint())) {
                        editor.closeFile();

            }
        }
    }
}