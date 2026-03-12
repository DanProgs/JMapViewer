package org.openstreetmap.gui.jmapviewer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import org.openstreetmap.gui.jmapviewer.checkBoxTree.CheckBoxTree;

public class JMapViewerTree extends JPanel {
	private static final long serialVersionUID = 3050203054402323972L;

	private final JButton clearButton;
	private final JMapViewer map;
	private final DefaultTreeModel originalModel;
	private final DefaultMutableTreeNode originalRoot;
	private final JTextField searchField;
	private JSplitPane splitPane;
	private final CheckBoxTree tree;

	public JMapViewerTree(String name, boolean treeVisible) {
		super(new BorderLayout());
		this.map = new JMapViewer();
		this.tree = new CheckBoxTree(name);
		this.originalModel = (DefaultTreeModel) tree.getModel();
		this.originalRoot = (DefaultMutableTreeNode) originalModel.getRoot();

		this.searchField = new JTextField();
		this.clearButton = new JButton("×");
		this.splitPane = new JSplitPane();

		setupUI(treeVisible);
		initNodeListener();
		addMapRefreshListener();
	}

	public void addLayer(AbstractLayer layer) {
		// Nutze die interne Logik des CheckBoxTree, um die Checkbox-Struktur zu
		// erhalten
		// Diese Methode erstellt intern die korrekten UserObjects (CheckBoxNodeData)
		DefaultMutableTreeNode node = tree.addLayer(layer);

		// Falls es eine Gruppe ist, fügen wir die Kinder unter diesen neuen Knoten
		if (layer instanceof LayerGroup) {
			for (AbstractLayer child : ((LayerGroup) layer).getLayers()) {
				addLayerRecursive(node, child);
			}
		}

		// Wichtig: Listener für automatische Updates registrieren
		layer.addChangeListener(this::syncLayerWithVisibleTree);

		// Struktur im Original-Model aktualisieren
		originalModel.nodeStructureChanged(originalRoot);
	}

	/**
	 * Fügt einen neuen Layer zu einer bestehenden Gruppe im Baum hinzu.
	 *
	 * @param group Die übergeordnete LayerGroup
	 * @param name  Der Name des neuen Layers
	 * @return Der erstellte Layer
	 */
	public Layer addLayer(LayerGroup group, String name) {
		// 1. Layer-Objekt erstellen
		Layer layer = new Layer(group, name);

		// 2. Den Layer beim JMapViewerTree registrieren
		// (Dadurch wird die Checkbox im UI erstellt und verknüpft)
		tree.addLayer(layer);

		return layer;
	}

	private void addLayerRecursive(DefaultMutableTreeNode parentNode, AbstractLayer layer) {
		// Wir nutzen hier ebenfalls die CheckBoxTree-Logik, um die Checkbox zu erzeugen
		// Falls tree.addLayer nur für den Root funktioniert, nutzen wir eine
		// Hilfsmethode:
		DefaultMutableTreeNode node = createCheckBoxNode(layer);
		parentNode.add(node);

		if (layer instanceof LayerGroup) {
			for (AbstractLayer child : ((LayerGroup) layer).getLayers()) {
				addLayerRecursive(node, child);
			}
		}
	}

	private void addMapRefreshListener() {
		tree.getModel().addTreeModelListener(new TreeModelListener() {
			@Override
			public void treeNodesChanged(TreeModelEvent e) {
				map.repaint();
			}

			@Override
			public void treeNodesInserted(TreeModelEvent e) {
				map.repaint();
			}

			@Override
			public void treeNodesRemoved(TreeModelEvent e) {
				map.repaint();
			}

			@Override
			public void treeStructureChanged(TreeModelEvent e) {
				map.repaint();
			}
		});
	}

	/**
	 * Filters the tree based on the provided text.
	 *
	 * @param filter the search string
	 */
	public void applyFilter(String filter) {
		String query = (filter == null) ? "" : filter.trim().toLowerCase();
		if (query.isEmpty()) {
			tree.setModel(originalModel);
		} else {
			DefaultMutableTreeNode filteredRoot = filterAndCopy(originalRoot, query);
			tree.setModel(new DefaultTreeModel(
					filteredRoot != null ? filteredRoot : new DefaultMutableTreeNode(originalRoot.getUserObject())));
		}
		expandAll(tree);
	}

	/**
	 * Erzeugt einen Knoten, den der CheckBoxTree als Checkbox erkennt.
	 */
	private DefaultMutableTreeNode createCheckBoxNode(AbstractLayer layer) {
		// CheckBoxTree erwartet oft eine spezielle Datenstruktur
		// Wir spiegeln hier die Logik von tree.addLayer()
		try {
			// Falls die Klasse CheckBoxNodeData zugänglich ist:
			// return new DefaultMutableTreeNode(new
			// org.openstreetmap.gui.jmapviewer.checkBoxTree.CheckBoxNodeData(layer, true));

			// Da wir generisch bleiben wollen, rufen wir tree.addLayer auf einem temporären
			// Root auf
			// oder nutzen direkt die interne API, falls vorhanden.
			// Der einfachste Weg im JMapViewer:
			return tree.addLayer(layer);
		} catch (Exception e) {
			return new DefaultMutableTreeNode(layer);
		}
	}

	private void expandAll(JTree tree) {
		for (int i = 0; i < tree.getRowCount(); i++) {
			tree.expandRow(i);
		}
	}

	private DefaultMutableTreeNode filterAndCopy(DefaultMutableTreeNode node, String filter) {
		AbstractLayer layer = getLayerFromObject(node.getUserObject());
		String text = (layer != null) ? layer.getName() : Objects.toString(node.getUserObject(), "");
		boolean matches = text.toLowerCase().contains(filter);

		DefaultMutableTreeNode copy = new DefaultMutableTreeNode(node.getUserObject());
		boolean hasChildMatch = false;

		for (int i = 0; i < node.getChildCount(); i++) {
			DefaultMutableTreeNode childCopy = filterAndCopy((DefaultMutableTreeNode) node.getChildAt(i), filter);
			if (childCopy != null) {
				copy.add(childCopy);
				hasChildMatch = true;
			}
		}
		return (matches || hasChildMatch) ? copy : null;
	}

	private AbstractLayer getLayerFromObject(Object obj) {
		if (obj instanceof AbstractLayer) {
			return (AbstractLayer) obj;
		}
		try {
			return (AbstractLayer) obj.getClass().getMethod("getAbstractLayer").invoke(obj);
		} catch (Exception e) {
			return null;
		}
	}

	protected CheckBoxTree getTree() {
		return tree;
	}

	public JMapViewer getViewer() {
		return map;
	}

	private void initNodeListener() {
		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				// 1. Pfad an der Klick-Position bestimmen
				TreePath path = tree.getPathForLocation(e.getX(), e.getY());
				if (path == null) {
					return;
				}

				DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
				AbstractLayer layer = getLayerFromObject(node.getUserObject());

				if (layer != null && SwingUtilities.isLeftMouseButton(e)) {
					Rectangle rect = tree.getPathBounds(path);
					if (rect != null) {
						// 2. Prüfen, ob der Klick im Bereich der Checkbox war (ca. erste 25 Pixel)
						// Das funktioniert auch im gefilterten Zustand!
						if (e.getX() < rect.x + 25) {

							// 3. Status direkt am Layer ändern
							boolean newVisible = !layer.isVisible();
							layer.setVisible(newVisible);

							// 4. Falls es eine Gruppe ist, Kinder rekursiv umschalten
							if (layer instanceof LayerGroup) {
								setRecursiveVisible((LayerGroup) layer, newVisible);
							}

							// 5. UI-Update erzwingen (triggert syncLayerWithVisibleTree)
							syncLayerWithVisibleTree(layer);

							// Event konsumieren, damit die (im Filter defekte)
							// Standard-Logik des CheckBoxTree nicht stört
							e.consume();
						}
					}
				}
			}
		});
	}

	// Hilfsmethode für rekursive Sichtbarkeit
	private void setRecursiveVisible(LayerGroup group, boolean visible) {
		for (AbstractLayer child : group.getLayers()) {
			child.setVisible(visible);
			if (child instanceof LayerGroup) {
				setRecursiveVisible((LayerGroup) child, visible);
			}
		}
	}

	public void setTreeVisible(boolean visible) {
		splitPane.getLeftComponent().setVisible(visible);
		splitPane.setDividerSize(visible ? 5 : 0);
		revalidate();
	}

	private void setupUI(boolean treeVisible) {
		// Search Bar Setup
		this.clearButton.setToolTipText("Clear filter");
		this.clearButton.setFocusable(false);
		this.clearButton.setEnabled(false);
		this.clearButton.addActionListener(e -> searchField.setText(""));

		this.searchField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e) {
				update();
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				update();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				update();
			}

			private void update() {
				String text = searchField.getText();
				clearButton.setEnabled(!text.isEmpty());
				applyFilter(text);
			}
		});

		// WRAPPER-RENDERER: Enhances the CheckBoxTree renderer with labels, icons, and
		// highlighting
		final TreeCellRenderer baseRenderer = tree.getCellRenderer();
		tree.setCellRenderer((t, v, s, e, l, r, h) -> {
			Component c = baseRenderer.getTreeCellRendererComponent(t, v, s, e, l, r, h);
			if (v instanceof DefaultMutableTreeNode && c instanceof JPanel) {
				JPanel panel = (JPanel) c;
				AbstractLayer layer = getLayerFromObject(((DefaultMutableTreeNode) v).getUserObject());
				if (layer != null) {
					String filter = searchField.getText().trim();
					for (Component child : panel.getComponents()) {
						if (child instanceof JLabel) {
							JLabel label = (JLabel) child;
							String name = layer.getName();

							// --- HIGHLIGHTING LOGIC ---
							if (!filter.isEmpty() && name.toLowerCase().contains(filter.toLowerCase())) {
								// Case-insensitive replace with bold tags using regex
								String highlighted = name
										.replaceAll("(?i)(" + java.util.regex.Pattern.quote(filter) + ")", "<b>$1</b>");
								label.setText("<html>" + highlighted + "</html>");
							} else {
								label.setText(name);
							}

							// --- ICON LOGIC ---
							if (layer instanceof LayerGroup
									&& ((LayerGroup) layer).calculateState() == LayerGroup.State.PARTIAL) {
								label.setIcon(new PartialIcon());
							} else {
								label.setIcon(null);
							}
						}
						if (child instanceof JCheckBox) {
							((JCheckBox) child).setSelected(layer.isVisible());
						}
					}
				}
			}
			return c;
		});

		// Layout Construction
		JPanel searchBar = new JPanel(new BorderLayout(5, 0));
		searchBar.setBorder(new EmptyBorder(4, 4, 4, 4));
		searchBar.add(new JLabel(" Filter: "), BorderLayout.WEST);
		searchBar.add(searchField, BorderLayout.CENTER);
		searchBar.add(clearButton, BorderLayout.EAST);

		JPanel treePanel = new JPanel(new BorderLayout());
		treePanel.add(searchBar, BorderLayout.NORTH);
		treePanel.add(new JScrollPane(tree), BorderLayout.CENTER);

		JLabel hintLabel = new JLabel("<html><small>Right-click for options</small></html>", SwingConstants.CENTER);
		hintLabel.setBorder(new EmptyBorder(2, 0, 4, 0));
		treePanel.add(hintLabel, BorderLayout.SOUTH);

		this.splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, map);
		this.splitPane.setDividerLocation(220);
		this.add(splitPane, BorderLayout.CENTER);

		setTreeVisible(treeVisible);
	}

	private void syncLayerWithVisibleTree(AbstractLayer layer) {
		map.repaint();
		DefaultTreeModel currentModel = (DefaultTreeModel) tree.getModel();
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) currentModel.getRoot();

		updateNodesRecursively(currentModel, root, layer);
	}

	private void updateNodesRecursively(DefaultTreeModel model, DefaultMutableTreeNode node, AbstractLayer layer) {
		if (getLayerFromObject(node.getUserObject()) == layer) {
			model.nodeChanged(node);
			// Wenn es eine Gruppe ist, müssen auch die Kinder im UI-Update markiert werden
			for (int i = 0; i < node.getChildCount(); i++) {
				model.nodeChanged(node.getChildAt(i));
			}
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			updateNodesRecursively(model, (DefaultMutableTreeNode) node.getChildAt(i), layer);
		}
	}
}
