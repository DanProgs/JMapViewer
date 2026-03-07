// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

import org.openstreetmap.gui.jmapviewer.checkBoxTree.CheckBoxNodePanel;
import org.openstreetmap.gui.jmapviewer.checkBoxTree.CheckBoxTree;
import org.openstreetmap.gui.jmapviewer.interfaces.MapObject;

/**
 * Tree of layers for JMapViewer component
 *
 * @author galo
 */
public class JMapViewerTree extends JPanel {
	/** Serial Version UID */
	private static final long serialVersionUID = 3050203054402323972L;

	private static void setVisibleTexts(AbstractLayer layer, boolean visible) {
		layer.setVisibleTexts(visible);
		if (layer instanceof LayerGroup) {
			List<AbstractLayer> children = ((LayerGroup) layer).getLayers();
			if (children != null) {
				for (AbstractLayer child : children) {
					setVisibleTexts(child, visible);
				}
			}
		}
	}

	public static int size(List<?> list) {
		return list == null ? 0 : list.size();
	}

	private JMapViewer map;
	private JSplitPane splitPane;

	private CheckBoxTree tree;

	private JPanel treePanel;

	public JMapViewerTree(String name) {
		this(name, false);
	}

	public JMapViewerTree(String name, boolean treeVisible) {
		super(new BorderLayout());

		map = new JMapViewer();
		tree = new CheckBoxTree(name);

		treePanel = new JPanel(new BorderLayout());
		treePanel.add(tree, BorderLayout.CENTER);

		JLabel hintLabel = new JLabel("<html><center>Use right mouse button to<br />show/hide texts</center></html>",
				SwingConstants.CENTER);
		treePanel.add(hintLabel, BorderLayout.SOUTH);

		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.setOneTouchExpandable(true);
		splitPane.setDividerLocation(150);

		map.setMinimumSize(new Dimension(100, 50));

		initNodeListener();
		createRefresh();
		setTreeVisible(treeVisible);
	}

	public JMapViewerTree addLayer(Layer layer) {
		if (layer != null) {
			tree.addLayer(layer);
		}
		return this;
	}

	public JMapViewerTree addLayer(MapObject element) {
		if (element != null && element.getLayer() != null) {
			addLayer(element.getLayer());
		}
		return this;
	}

	public Layer addLayer(String name) {
		Layer layer = new Layer(name);
		this.addLayer(layer);
		return layer;
	}

	public void addMapObject(MapObject o) {

	}

	private JPopupMenu createPopupMenu(final AbstractLayer layer) {
		JPopupMenu popup = new JPopupMenu();
		Boolean visible = layer.isVisibleTexts();

		if (visible == null || !visible) {
			JMenuItem showItem = new JMenuItem("show texts");
			showItem.addActionListener(e -> updateLayerVisibility(layer, true));
			popup.add(showItem);
		}

		if (visible == null || visible) {
			JMenuItem hideItem = new JMenuItem("hide texts");
			hideItem.addActionListener(e -> updateLayerVisibility(layer, false));
			popup.add(hideItem);
		}
		return popup;
	}

	private void createRefresh() {
		tree.getModel().addTreeModelListener(new TreeModelListener() {
			@Override
			public void treeNodesChanged(TreeModelEvent e) {
				map.repaint();
			}

			@Override
			public void treeNodesInserted(TreeModelEvent e) {
			}

			@Override
			public void treeNodesRemoved(TreeModelEvent e) {
			}

			@Override
			public void treeStructureChanged(TreeModelEvent e) {
			}
		});
	}

	public CheckBoxTree getTree() {
		return tree;
	}

	public JMapViewer getViewer() {
		return map;
	}

	private void initNodeListener() {
		tree.addNodeListener(new MouseAdapter() {
			private void maybeShowPopup(MouseEvent e) {
				if (e.isPopupTrigger() && e.getComponent() instanceof CheckBoxNodePanel) {
					AbstractLayer layer = ((CheckBoxNodePanel) e.getComponent()).getData().getAbstractLayer();
					if (layer != null) {
						createPopupMenu(layer).show(e.getComponent(), e.getX(), e.getY());
					}
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				maybeShowPopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				maybeShowPopup(e);
			}
		});
	}

	public Layer removeFromLayer(MapObject element) {
		element.getLayer().getElements().remove(element);
		return element.getLayer();
	}

	public void setTreeVisible(boolean visible) {
		removeAll();
		revalidate();
		if (visible) {
			splitPane.setLeftComponent(treePanel);
			splitPane.setRightComponent(map);
			add(splitPane, BorderLayout.CENTER);
		} else {
			add(map, BorderLayout.CENTER);
		}
		repaint();
	}

	private void updateLayerVisibility(AbstractLayer layer, boolean visible) {
		setVisibleTexts(layer, visible);
		if (layer.getParent() != null) {
			layer.getParent().calculateVisibleTexts();
		}
		map.repaint();
	}
}
