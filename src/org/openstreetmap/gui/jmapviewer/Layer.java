// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.gui.jmapviewer.interfaces.MapObject;

public class Layer extends AbstractLayer {
	
	private List<MapObject> elements = new ArrayList<>();

    public Layer(String name) {
        this(name, null, null);
    }

    public Layer(String name, String description) {
        this(name, description, null);
    }

    public Layer(String name, Style style) {
        this(name, null, style);
    }

    public Layer(String name, String description, Style style) {
        super(name, description, style);
    }

    public Layer(LayerGroup parent, String name) {
        this(parent, name, null, null);
    }

    public Layer(LayerGroup parent, String name, Style style) {
        this(parent, name, null, style);
    }

    public Layer(LayerGroup parent, String name, String description, Style style) {
        super(parent, name, description, style);
    }

    public List<MapObject> getElements() {
        return Collections.unmodifiableList(elements);
    }

    public void setElements(List<MapObject> elements) {
        this.elements = elements;
    }

    public Layer add(MapObject element) {
        element.setLayer(this);
        elements = add(elements, element);
        return this;
    }
}
