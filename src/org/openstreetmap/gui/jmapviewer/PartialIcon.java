package org.openstreetmap.gui.jmapviewer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * System-adaptive icon for indeterminate checkbox states.
 */
public class PartialIcon implements Icon {
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fg = UIManager.getColor("CheckBox.foreground");
        g2.setColor(fg != null ? fg : Color.BLACK);
        g2.drawRect(x + 2, y + 2, 12, 12);
        g2.fillRect(x + 5, y + 5, 7, 7);
        g2.dispose();
    }
    public int getIconWidth() { return 16; }
    public int getIconHeight() { return 16; }
}
