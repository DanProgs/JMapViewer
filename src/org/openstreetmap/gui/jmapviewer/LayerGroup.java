// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a group of layers in the JMapViewer.
 * This class allows hierarchical organization of layers and manages 
 * recursive visibility states (Selected, Deselected, Partial).
 */
public class LayerGroup extends AbstractLayer {

    /**
     * Represents the selection state of the group based on its children.
     */
    public enum State {
        /** All child layers are hidden. */
        DESELECTED, 
        /** Some child layers are visible, others are hidden. */
        PARTIAL, 
        /** All child layers are visible. */
        SELECTED
    }

    private final List<AbstractLayer> layers = new CopyOnWriteArrayList<>();

    public LayerGroup(String name) {
        super(name);
    }

    public LayerGroup(LayerGroup parent, String name) {
        super(parent, name);
    }

    public LayerGroup(String name, Style style) {
        super(name, style);
    }

    /**
     * Adds a child layer to this group and establishes the parent-child relationship.
     * Attaches a ChangeListener to the child to ensure the group's state is 
     * recalculated whenever a child's visibility changes.
     * 
     * @param child The {@link AbstractLayer} to be added.
     * @return The added layer for method chaining.
     */
    public AbstractLayer addLayer(AbstractLayer child) {
        if (child != null && !layers.contains(child)) {
            child.setParent(this);
            layers.add(child);

            // Notify group when child visibility changes to update UI/Tristate
            child.addChangeListener(l -> fireChangeEvent());
            fireChangeEvent();
        }
        return child;
    }

    /**
     * Convenience method to create and add a new {@link Layer} by name.
     * 
     * @param name The display name of the new layer.
     * @return The newly created Layer instance.
     */
    public Layer addLayer(String name) {
        Layer layer = new Layer(this, name);
        addLayer(layer);
        return layer;
    }

    /**
     * Calculates the current {@link State} of the group by checking 
     * the visibility of all nested layers and sub-groups.
     * 
     * @return The calculated selection state (SELECTED, DESELECTED, or PARTIAL).
     */
    public State calculateState() {
        if (layers.isEmpty()) {
            return isVisible() ? State.SELECTED : State.DESELECTED;
        }

        int visibleCount = 0;
        for (AbstractLayer l : layers) {
            if (l instanceof LayerGroup) {
                State s = ((LayerGroup) l).calculateState();
                if (s == State.PARTIAL) return State.PARTIAL;
                if (s == State.SELECTED) visibleCount++;
            } else if (l.isVisible()) {
                visibleCount++;
            }
        }

        if (visibleCount == 0) return State.DESELECTED;
        if (visibleCount == layers.size()) return State.SELECTED;
        return State.PARTIAL;
    }

    /**
     * Sets the visibility for this group and recursively for all its children.
     * 
     * @param visible {@code true} to show all children, {@code false} to hide them.
     */
    @Override
    public void setVisible(boolean visible) {
        if (isVisible() == visible) return;

        super.setVisible(visible);
        for (AbstractLayer child : layers) {
            child.setVisible(visible);
        }
    }

    /**
     * Returns the list of all direct child layers in this group.
     * 
     * @return A thread-safe list of {@link AbstractLayer} objects.
     */
    public List<AbstractLayer> getLayers() {
        return layers;
    }

    /**
     * Updates the visibility of labels/texts for this group based on 
     * the settings of its children.
     */
    public void calculateVisibleTexts() {
        if (layers.isEmpty()) return;

        boolean first = layers.get(0).isVisibleTexts();
        boolean allSame = true;
        for (AbstractLayer l : layers) {
            if (l.isVisibleTexts() != first) {
                allSame = false;
                break;
            }
        }
        setVisibleTexts(allSame ? first : false);
        if (getParent() != null) {
            getParent().calculateVisibleTexts();
        }
    }
}
