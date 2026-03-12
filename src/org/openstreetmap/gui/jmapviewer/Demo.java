package org.openstreetmap.gui.jmapviewer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import org.openstreetmap.gui.jmapviewer.events.JMVCommandEvent;
import org.openstreetmap.gui.jmapviewer.interfaces.JMapViewerEventListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.BingAerialTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.BingTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.OsmTileSource;

public class Demo extends JFrame implements JMapViewerEventListener {
    private static final long serialVersionUID = 1L;
    final JMapViewerTree treeMap;
    private final JLabel mperpLabelValue;
    private final JLabel zoomValue;

    public static void main(String[] args) {
        new Demo().setVisible(true);
    }

    public Demo() {
        super("JMapViewer Demo - Optimized Layers");
        setSize(1200, 800);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setExtendedState(Frame.MAXIMIZED_BOTH);

        treeMap = new JMapViewerTree("Map Layers", true);
        map().addJMVListener(this);

        setLayout(new BorderLayout());

        // Top Info Panel
        JPanel panelTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        mperpLabelValue = new JLabel();
        zoomValue = new JLabel();
        updateInfo();

        JComboBox<TileSource> tileSelector = new JComboBox<>(new TileSource[] { 
            new OsmTileSource.Mapnik(), new BingTileSource(), new BingAerialTileSource() 
        });
        tileSelector.addItemListener(e -> map().setTileSource((TileSource) e.getItem()));

        panelTop.add(new JLabel("Source: ")); panelTop.add(tileSelector);
        panelTop.add(new JLabel("  Zoom: ")); panelTop.add(zoomValue);
        panelTop.add(new JLabel("  M/Px: ")); panelTop.add(mperpLabelValue);

        // Bottom Controls
        JPanel panelBottom = new JPanel();
        JCheckBox showTree = new JCheckBox("Show Tree", true);
        showTree.addActionListener(e -> treeMap.setTreeVisible(showTree.isSelected()));
        
        JButton fitMarkers = new JButton("Fit Markers");
        fitMarkers.addActionListener(e -> map().setDisplayToFitMapMarkers());

        panelBottom.add(showTree);
        panelBottom.add(fitMarkers);

        add(panelTop, BorderLayout.NORTH);
        add(treeMap, BorderLayout.CENTER);
        add(panelBottom, BorderLayout.SOUTH);

        initLayersAndMarkers();
        map().setDisplayPosition(new Coordinate(51.16, 10.45), 6);
    }

    private void initLayersAndMarkers() {
        // Germany Group
        LayerGroup germany = new LayerGroup("Germany");
        Layer berlin = treeMap.addLayer(germany, "Berlin");
        Layer munich = treeMap.addLayer(germany, "Munich");
        map().addMapMarker(new MapMarkerDot(berlin, "Berlin", 52.52, 13.40));
        map().addMapMarker(new MapMarkerDot(munich, "Munich", 48.13, 11.57));

        // Standalone Layer
        Layer france = new Layer("France");
        treeMap.addLayer(france);
        map().addMapMarker(new MapMarkerDot(france, "Paris", 48.85, 2.35));

        // Hidden Layer Example
        Layer spain = new Layer("Spain");
        treeMap.addLayer(spain);
        spain.setVisible(false);
        map().addMapMarker(new MapMarkerDot(spain, "Madrid", 40.41, -3.70));
        
        for (int i = 0; i < treeMap.getTree().getRowCount(); i++) {
            treeMap.getTree().expandRow(i);
        }
    }

    private void updateInfo() {
        zoomValue.setText(String.valueOf(map().getZoom()));
        mperpLabelValue.setText(String.format("%.2f", map().getMeterPerPixel()));
    }

    private JMapViewer map() { return treeMap.getViewer(); }

    @Override
    public void processCommand(JMVCommandEvent command) {
        if (command.getCommand() == JMVCommandEvent.COMMAND.ZOOM || 
            command.getCommand() == JMVCommandEvent.COMMAND.MOVE) {
            updateInfo();
        }
    }
}
