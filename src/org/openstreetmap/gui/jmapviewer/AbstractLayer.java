// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AbstractLayer {

	/**
	 * Interface definition for receiving layer state change events.
	 */
	public interface LayerChangeListener {
		/**
		 * Invoked when the target layer has changed its properties.
		 *
		 * @param layer the layer that was modified
		 */
		void layerChanged(AbstractLayer layer);
	}

	public static <E> List<E> add(List<E> list, E element) {
		if (element != null) {
			if (list == null) {
				list = new ArrayList<>();
			}
			if (!list.contains(element)) {
				list.add(element);
			}
		}
		return list;
	}

	private String description;
	/**
	 * Thread-safe list of listeners to avoid ConcurrentModificationException during
	 * event broadcasting.
	 */
	private final java.util.List<LayerChangeListener> listeners = new CopyOnWriteArrayList<>();
	private String name;
	private LayerGroup parent;
	private Style style;

	private Boolean visible = Boolean.TRUE;

	private Boolean visibleTexts = Boolean.TRUE;

	public AbstractLayer(LayerGroup parent, String name) {
		this(parent, name, MapMarkerCircle.getDefaultStyle());
	}

	public AbstractLayer(LayerGroup parent, String name, String description, Style style) {
		setParent(parent);
		setName(name);
		setDescription(description);
		setStyle(style);
		setVisible(Boolean.TRUE);

		if (parent != null) {
			parent.addLayer(this);
		}
	}

	public AbstractLayer(LayerGroup parent, String name, Style style) {
		this(parent, name, null, style);
	}

	public AbstractLayer(String name) {
		this.name = name;
	}

	public AbstractLayer(String name, String description) {
		this(name, description, MapMarkerCircle.getDefaultStyle());
	}

	public AbstractLayer(String name, String description, Style style) {
		this(null, name, description, style);
	}

	public AbstractLayer(String name, Style style) {
		this(name, null, style);
	}

	/**
	 * Adds a listener that will be notified whenever the layer's state changes
	 * (e.g., visibility, name, or description).
	 *
	 * @param listener the listener to add
	 */
	public void addChangeListener(LayerChangeListener listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	/**
	 * Notifies all registered listeners about a state change in this layer. This
	 * triggers UI updates in the tree and a repaint on the map.
	 */
	protected void fireChangeEvent() {
		for (LayerChangeListener l : listeners) {
			l.layerChanged(this);
		}
	}

	public String getDescription() {
		return description;
	}

	public String getName() {
		return name;
	}

	public LayerGroup getParent() {
		return parent;
	}

	public Style getStyle() {
		return style;
	}

	public Boolean isVisible() {
		return visible;
	}

	public Boolean isVisibleTexts() {
		return visibleTexts;
	}

	/**
	 * Removes a previously registered change listener.
	 *
	 * @param listener the listener to remove
	 */
	public void removeChangeListener(LayerChangeListener listener) {
		listeners.remove(listener);
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setParent(LayerGroup parent) {
		this.parent = parent;
	}

	public void setStyle(Style style) {
		this.style = style;
	}

	public void setVisibleTexts(Boolean visibleTexts) {
		this.visibleTexts = visibleTexts;
		fireChangeEvent();
	}

	@Override
	public String toString() {
		return name;
	}

	public void setVisible(boolean visible) {
		if (Boolean.TRUE.equals(this.visible) != visible) {
			this.visible = visible;
			fireChangeEvent();
		}
		
	}
}
