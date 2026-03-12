package org.openstreetmap.gui.jmapviewer;

import java.util.Properties;

/**
 * Handles saving and loading of layer visibility states.
 */
public class LayerPersistence {
    public static void saveStates(AbstractLayer root, Properties props) {
        props.setProperty(root.getName() + ".visible", String.valueOf(root.isVisible()));
        if (root instanceof LayerGroup) {
            for (AbstractLayer child : ((LayerGroup) root).getLayers()) saveStates(child, props);
        }
    }

    public static void loadStates(AbstractLayer root, Properties props) {
        String val = props.getProperty(root.getName() + ".visible");
        if (val != null) root.setVisible(Boolean.parseBoolean(val));
        if (root instanceof LayerGroup) {
            for (AbstractLayer child : ((LayerGroup) root).getLayers()) loadStates(child, props);
        }
    }
}
