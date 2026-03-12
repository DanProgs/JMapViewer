// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.EventListenerList;

import org.openstreetmap.gui.jmapviewer.events.JMVCommandEvent;
import org.openstreetmap.gui.jmapviewer.events.JMVCommandEvent.COMMAND;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;
import org.openstreetmap.gui.jmapviewer.interfaces.JMapViewerEventListener;
import org.openstreetmap.gui.jmapviewer.interfaces.MapMarker;
import org.openstreetmap.gui.jmapviewer.interfaces.MapPolygon;
import org.openstreetmap.gui.jmapviewer.interfaces.MapRectangle;
import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.OsmTileSource;

/**
 * Provides a simple panel that displays pre-rendered map tiles loaded from the
 * OpenStreetMap project.
 *
 * @author Jan Peter Stotz
 * @author Jason Huntley
 */
public class JMapViewer extends JPanel implements TileLoaderListener {

	/**
	 * Apparence of zoom controls.
	 */
	public enum ZOOM_BUTTON_STYLE {
		/** Zoom buttons are displayed horizontally (default) */
		HORIZONTAL,
		/** Zoom buttons are displayed vertically */
		VERTICAL
	}

	/** whether debug mode is enabled or not */
	public static boolean debug;

	/** Maximum zoom level */
	public static final int MAX_ZOOM = 22;

	/** Minimum zoom level */
	public static final int MIN_ZOOM = 0;

	/**
	 * Vectors for clock-wise tile painting
	 */
	private static final Point[] move = { new Point(1, 0), new Point(0, 1), new Point(-1, 0), new Point(0, -1) };
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	/** option to reverse zoom direction with mouse wheel */
	public static boolean zoomReverseWheel;
	protected transient AttributionSupport attribution = new AttributionSupport();
	/**
	 * x- and y-position of the center of this map-panel on the world map denoted in
	 * screen pixel regarding the current zoom level.
	 */
	protected Point center;

	protected EventListenerList evtListenerList = new EventListenerList();
	protected transient List<MapMarker> mapMarkerList;
	protected boolean mapMarkersVisible;

	protected transient List<MapPolygon> mapPolygonList;
	protected boolean mapPolygonsVisible;

	protected transient List<MapRectangle> mapRectangleList;

	protected boolean mapRectanglesVisible;

	protected boolean scrollWrapEnabled;

	protected transient TileController tileController;
	protected boolean tileGridVisible;
	protected transient TileSource tileSource;

	/**
	 * Current zoom level
	 */
	protected int zoom;

	protected ZOOM_BUTTON_STYLE zoomButtonStyle;

	protected JButton zoomInButton;

	protected JButton zoomOutButton;

	protected JSlider zoomSlider;

	/**
	 * Creates a standard {@link JMapViewer} instance that can be controlled via
	 * mouse: hold right mouse button for moving, double click left mouse button or
	 * use mouse wheel for zooming. Loaded tiles are stored in a
	 * {@link MemoryTileCache} and the tile loader uses 4 parallel threads for
	 * retrieving the tiles.
	 */
	public JMapViewer() {
		this(new MemoryTileCache());
		new DefaultMapController(this);
	}

	/**
	 * Creates a new {@link JMapViewer} instance.
	 *
	 * @param tileCache The cache where to store tiles
	 *
	 */
	public JMapViewer(TileCache tileCache) {
		tileSource = new OsmTileSource.Mapnik();
		tileController = new TileController(tileSource, tileCache, this);
		mapMarkerList = Collections.synchronizedList(new LinkedList<MapMarker>());
		mapPolygonList = Collections.synchronizedList(new LinkedList<MapPolygon>());
		mapRectangleList = Collections.synchronizedList(new LinkedList<MapRectangle>());
		mapMarkersVisible = true;
		mapRectanglesVisible = true;
		mapPolygonsVisible = true;
		tileGridVisible = false;
		setLayout(null);
		initializeZoomSlider();
		setMinimumSize(new Dimension(tileSource.getTileSize(), tileSource.getTileSize()));
		setPreferredSize(new Dimension(400, 400));
		setDisplayPosition(new Coordinate(50, 9), 3);
	}

	/**
	 * Creates a new {@link JMapViewer} instance.
	 *
	 * @param tileCache           The cache where to store tiles
	 * @param downloadThreadCount not used anymore
	 * @deprecated use {@link #JMapViewer(TileCache)}
	 */
	@Deprecated
	public JMapViewer(TileCache tileCache, int downloadThreadCount) {
		this(tileCache);
	}

	/**
	 * @param listener listener to set
	 */
	public void addJMVListener(JMapViewerEventListener listener) {
		evtListenerList.add(JMapViewerEventListener.class, listener);
	}

	/**
	 * Add a {@link MapMarker}.
	 *
	 * @param marker map marker to add
	 */
	public void addMapMarker(MapMarker marker) {
		mapMarkerList.add(marker);
		repaint();
	}

	/**
	 * Add a {@link MapPolygon}.
	 *
	 * @param polygon map polygon to add
	 */
	public void addMapPolygon(MapPolygon polygon) {
		mapPolygonList.add(polygon);
		repaint();
	}

	/**
	 * Add a {@link MapRectangle}.
	 *
	 * @param rectangle map rectangle to add
	 */
	public void addMapRectangle(MapRectangle rectangle) {
		mapRectangleList.add(rectangle);
		repaint();
	}

	/**
	 * Send an update to all objects registered with viewer
	 *
	 * @param evt event to dispatch
	 */
	private void fireJMVEvent(JMVCommandEvent evt) {
		Object[] listeners = evtListenerList.getListenerList();
		for (int i = 0; i < listeners.length; i += 2) {
			if (listeners[i] == JMapViewerEventListener.class) {
				((JMapViewerEventListener) listeners[i + 1]).processCommand(evt);
			}
		}
	}

	/**
	 * Returns attribution.
	 *
	 * @return attribution
	 */
	public AttributionSupport getAttribution() {
		return attribution;
	}

	/**
	 * @return the center
	 */
	public Point getCenter() {
		return center;
	}

	@Override
	public int getHeight() {
		if (OsmMercator.isRetina()) {
			return super.getHeight() * 2;
		} else {
			return super.getHeight();
		}
	}

	/**
	 * Calculates the position on the map of a given coordinate
	 *
	 * @param lat          Latitude
	 * @param lon          longitude
	 * @param offset       Offset respect Latitude
	 * @param checkOutside check if the point is outside the displayed area
	 * @return Integer the radius in pixels
	 */
	public Integer getLatOffset(double lat, double lon, double offset, boolean checkOutside) {
		Point p = tileSource.latLonToXY(lat + offset, lon, zoom);
		int y = p.y - (center.y - getHeight() / 2);
		if (checkOutside && (y < 0 || y > getHeight())) {
			return null;
		}
		return y;
	}

	/**
	 * Returns the list of {@link MapMarker}s.
	 *
	 * @return list of {@link MapMarker}s
	 */
	public List<MapMarker> getMapMarkerList() {
		return mapMarkerList;
	}

	/**
	 * Determines whether {@link MapMarker}s are painted or not.
	 *
	 * @return {@code true} if {@link MapMarker}s are painted, {@code false}
	 *         otherwise
	 */
	public boolean getMapMarkersVisible() {
		return mapMarkersVisible;
	}

	/**
	 * Returns the list of {@link MapPolygon}s.
	 *
	 * @return list of {@link MapPolygon}s
	 */
	public List<MapPolygon> getMapPolygonList() {
		return mapPolygonList;
	}

	/**
	 * Calculates the position on the map of a given coordinate
	 *
	 * @param coord coordinate
	 * @return point on the map or <code>null</code> if the point is not visible
	 */
	public Point getMapPosition(Coordinate coord) {
		if (coord != null) {
			return getMapPosition(coord.getLat(), coord.getLon());
		} else {
			return null;
		}
	}

	/**
	 * Calculates the position on the map of a given coordinate
	 *
	 * @param lat latitude
	 * @param lon longitude
	 * @return point on the map or <code>null</code> if the point is not visible
	 */
	public Point getMapPosition(double lat, double lon) {
		return getMapPosition(lat, lon, true);
	}

	/**
	 * Calculates the position on the map of a given coordinate
	 *
	 * @param lat          latitude
	 * @param lon          longitude
	 * @param checkOutside check if the point is outside the displayed area
	 * @return point on the map or <code>null</code> if the point is not visible and
	 *         checkOutside set to <code>true</code>
	 */
	public Point getMapPosition(double lat, double lon, boolean checkOutside) {
		Point p = tileSource.latLonToXY(lat, lon, zoom);
		p.translate(-(center.x - getWidth() / 2), -(center.y - getHeight() / 2));

		if (checkOutside && (p.x < 0 || p.y < 0 || p.x > getWidth() || p.y > getHeight())) {
			return null;
		}
		return p;
	}

	/**
	 * Calculates the position on the map of a given coordinate
	 *
	 * @param coord        coordinate
	 * @param checkOutside check if the point is outside the displayed area
	 * @return point on the map or <code>null</code> if the point is not visible and
	 *         checkOutside set to <code>true</code>
	 */
	public Point getMapPosition(ICoordinate coord, boolean checkOutside) {
		if (coord != null) {
			return getMapPosition(coord.getLat(), coord.getLon(), checkOutside);
		} else {
			return null;
		}
	}

	/**
	 * Returns the list of {@link MapRectangle}s.
	 *
	 * @return list of {@link MapRectangle}s
	 */
	public List<MapRectangle> getMapRectangleList() {
		return mapRectangleList;
	}

	/**
	 * Gets the meter per pixel.
	 *
	 * @return the meter per pixel
	 */
	public double getMeterPerPixel() {
		Point origin = new Point(5, 5);
		Point center = new Point(getWidth() / 2, getHeight() / 2);

		double pDistance = center.distance(origin);

		ICoordinate originCoord = getPosition(origin);
		ICoordinate centerCoord = getPosition(center);

		double mDistance = tileSource.getDistance(originCoord.getLat(), originCoord.getLon(), centerCoord.getLat(),
				centerCoord.getLon());

		return mDistance / pDistance;
	}

	/**
	 * Calculates the latitude/longitude coordinate of the center of the currently
	 * displayed map area.
	 *
	 * @return latitude / longitude
	 */
	public ICoordinate getPosition() {
		return tileSource.xyToLatLon(center, zoom);
	}

	/**
	 * Converts the relative pixel coordinate (regarding the top left corner of the
	 * displayed map) into a latitude / longitude coordinate
	 *
	 * @param mapPointX X coordinate
	 * @param mapPointY Y coordinate
	 * @return latitude / longitude
	 */
	public ICoordinate getPosition(int mapPointX, int mapPointY) {
		int x = center.x + mapPointX - getWidth() / 2;
		int y = center.y + mapPointY - getHeight() / 2;
		return tileSource.xyToLatLon(x, y, zoom);
	}

	/**
	 * Converts the relative pixel coordinate (regarding the top left corner of the
	 * displayed map) into a latitude / longitude coordinate
	 *
	 * @param mapPoint relative pixel coordinate regarding the top left corner of
	 *                 the displayed map
	 * @return latitude / longitude
	 */
	public ICoordinate getPosition(Point mapPoint) {
		return getPosition(mapPoint.x, mapPoint.y);
	}

	/**
	 * Calculates the position on the map of a given coordinate
	 *
	 * @param marker MapMarker object that define the x,y coordinate
	 * @param p      coordinate
	 * @return Integer the radius in pixels
	 */
	public Integer getRadius(MapMarker marker, Point p) {
		if (marker.getMarkerStyle() == MapMarker.STYLE.FIXED) {
			return (int) marker.getRadius();
		} else if (p != null) {
			Integer radius = getLatOffset(marker.getLat(), marker.getLon(), marker.getRadius(), false);
			radius = radius == null ? null : p.y - radius.intValue();
			return radius;
		} else {
			return null;
		}
	}

	/**
	 * Return tile information caching class
	 *
	 * @return tile cache
	 * @see TileController#getTileCache()
	 */
	public TileCache getTileCache() {
		return tileController.getTileCache();
	}

	/**
	 * Returns the tile controller.
	 *
	 * @return the tile controller
	 */
	public TileController getTileController() {
		return tileController;
	}

	@Override
	public String getToolTipText(MouseEvent event) {
		return super.getToolTipText(event);
	}

	@Override
	public int getWidth() {
		if (OsmMercator.isRetina()) {
			return super.getWidth() * 2;
		} else {
			return super.getWidth();
		}
	}

	/**
	 * @return the current zoom level
	 */
	public int getZoom() {
		return zoom;
	}

	/**
	 * Returns the zoom controls apparence style (horizontal/vertical).
	 *
	 * @return {@link ZOOM_BUTTON_STYLE#VERTICAL} or
	 *         {@link ZOOM_BUTTON_STYLE#HORIZONTAL}
	 */
	public ZOOM_BUTTON_STYLE getZoomButtonStyle() {
		return zoomButtonStyle;
	}

	/**
	 * Determines whether zoom controls are displayed or not.
	 *
	 * @return {@code true} if zoom controls are displayed, {@code false} otherwise
	 */
	public boolean getZoomControlsVisible() {
		return zoomSlider.isVisible();
	}

	protected void initializeZoomSlider() {
		zoomSlider = new JSlider(MIN_ZOOM, tileController.getTileSource().getMaxZoom());
		zoomSlider.setOrientation(SwingConstants.VERTICAL);
		zoomSlider.setBounds(10, 10, 30, 150);
		zoomSlider.setOpaque(false);
		zoomSlider.addChangeListener(e -> setZoom(zoomSlider.getValue()));
		zoomSlider.setFocusable(false);
		add(zoomSlider);
		int size = 18;
		URL url = JMapViewer.class.getResource("images/plus.png");
		if (url != null) {
			ImageIcon icon = new ImageIcon(url);
			zoomInButton = new JButton(icon);
		} else {
			zoomInButton = new JButton("+");
			zoomInButton.setFont(new Font("sansserif", Font.BOLD, 9));
			zoomInButton.setMargin(new Insets(0, 0, 0, 0));
		}
		zoomInButton.setBounds(4, 155, size, size);
		zoomInButton.addActionListener(e -> zoomIn());
		zoomInButton.setFocusable(false);
		add(zoomInButton);
		url = JMapViewer.class.getResource("images/minus.png");
		if (url != null) {
			ImageIcon icon = new ImageIcon(url);
			zoomOutButton = new JButton(icon);
		} else {
			zoomOutButton = new JButton("-");
			zoomOutButton.setFont(new Font("sansserif", Font.BOLD, 9));
			zoomOutButton.setMargin(new Insets(0, 0, 0, 0));
		}
		zoomOutButton.setBounds(8 + size, 155, size, size);
		zoomOutButton.addActionListener(e -> zoomOut());
		zoomOutButton.setFocusable(false);
		add(zoomOutButton);
	}

	/**
	 * Determines whether the {@link MapPolygon}s are painted or not.
	 *
	 * @return {@code true} if the {@link MapPolygon}s are painted, {@code false}
	 *         otherwise
	 */
	public boolean isMapPolygonsVisible() {
		return mapPolygonsVisible;
	}

	/**
	 * Determines whether the {@link MapRectangle}s are painted or not.
	 *
	 * @return {@code true} if the {@link MapRectangle}s are painted, {@code false}
	 *         otherwise
	 */
	public boolean isMapRectanglesVisible() {
		return mapRectanglesVisible;
	}

	/**
	 * Determines whether scroll wrap is enabled or not.
	 *
	 * @return {@code true} if scroll wrap is enabled, {@code false} otherwise
	 */
	public boolean isScrollWrapEnabled() {
		return scrollWrapEnabled;
	}

	/**
	 * Determines whether the tile grid is visible or not.
	 *
	 * @return {@code true} if the tile grid is visible, {@code false} otherwise
	 */
	public boolean isTileGridVisible() {
		return tileGridVisible;
	}

	/**
	 * Moves the visible map pane.
	 *
	 * @param x horizontal movement in pixel.
	 * @param y vertical movement in pixel
	 */
	public void moveMap(int x, int y) {
		tileController.cancelOutstandingJobs(); // Clear outstanding load
		center.x += x;
		center.y += y;
		repaint();
		this.fireJMVEvent(new JMVCommandEvent(COMMAND.MOVE, this));
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;

		AffineTransform defaultTransform = g2.getTransform();
		AffineTransform retina = new AffineTransform();
		if (OsmMercator.isRetina()) {
			retina.setToScale(0.5, 0.5);
		}
		g2.transform(retina);

		int iMove = 0;

		int tilesize = tileSource.getTileSize();
		int tilex = center.x / tilesize;
		int tiley = center.y / tilesize;
		int offsx = center.x % tilesize;
		int offsy = center.y % tilesize;

		int w2 = getWidth() / 2;
		int h2 = getHeight() / 2;
		int posx = w2 - offsx;
		int posy = h2 - offsy;

		int diffLeft = offsx;
		int diffRight = tilesize - offsx;
		int diffTop = offsy;
		int diffBottom = tilesize - offsy;

		boolean startLeft = diffLeft < diffRight;
		boolean startTop = diffTop < diffBottom;

		if (startTop) {
			if (startLeft) {
				iMove = 2;
			} else {
				iMove = 3;
			}
		} else {
			if (startLeft) {
				iMove = 1;
			} else {
				iMove = 0;
			}
			
		} // calculate the visibility borders
		int xMin = -tilesize;
		int yMin = -tilesize;
		int xMax = getWidth();
		int yMax = getHeight();

		// calculate the length of the grid (number of squares per edge)
		int gridLength = 1 << zoom;

		renderTilesSpiraly(g2, iMove, tilesize, tilex, tiley, posx, posy, xMin, yMin, xMax, yMax, gridLength);
		
		renderOuterMapBorder(g2, tilesize, w2, h2);

		renderCoordinateGrid(g2);

		renderMapMarkers(g2);

		renderLabels(g2);
		
		// reset graphics context
		g2.setTransform(defaultTransform);
	}

	private void renderLabels(Graphics2D g2) {
		attribution.paintAttribution(g2, getWidth(), getHeight(), getPosition(0, 0),
				getPosition(getWidth(), getHeight()), zoom, this);
	}

	private void renderMapMarkers(Graphics2D g2) {
		if (mapMarkersVisible && mapMarkerList != null) {
			synchronized (this) {
				for (MapMarker marker : mapMarkerList) {
					if (marker.isVisible()) {
						paintMarker(g2, marker);
					}
				}
			}
		}
	}

	private void renderCoordinateGrid(Graphics2D g2) {
		if (mapRectanglesVisible && mapRectangleList != null) {
			synchronized (this) {
				for (MapRectangle rectangle : mapRectangleList) {
					if (rectangle.isVisible()) {
						paintRectangle(g2, rectangle);
					}
				}
			}
		}
	}

	private void renderOuterMapBorder(Graphics2D g2, int tilesize, int w2, int h2) {
		// outer border of the map
		int mapSize = tilesize << zoom;
		if (scrollWrapEnabled) {
			g2.drawLine(0, h2 - center.y, getWidth(), h2 - center.y);
			g2.drawLine(0, h2 - center.y + mapSize, getWidth(), h2 - center.y + mapSize);
		} else {
			g2.drawRect(w2 - center.x, h2 - center.y, mapSize, mapSize);
		}

		// g2.drawString("Tiles in cache: " + tileCache.getTileCount(), 50, 20);

		// keep x-coordinates from growing without bound if scroll-wrap is enabled
		if (scrollWrapEnabled) {
			center.x = center.x % mapSize;
		}

		if (mapPolygonsVisible && mapPolygonList != null) {
			synchronized (this) {
				for (MapPolygon polygon : mapPolygonList) {
					if (polygon.isVisible()) {
						paintPolygon(g2, polygon);
					}
				}
			}
		}
	}

	/**
	 * Paint a single marker.
	 *
	 * @param g      Graphics used for painting
	 * @param marker marker to paint
	 */
	protected void paintMarker(Graphics g, MapMarker marker) {
		Point p = getMapPosition(marker.getLat(), marker.getLon(), marker.getMarkerStyle() == MapMarker.STYLE.FIXED);
		Integer radius = getRadius(marker, p);
		if (scrollWrapEnabled) {
			int tilesize = tileSource.getTileSize();
			int mapSize = tilesize << zoom;
			if (p == null) {
				p = getMapPosition(marker.getLat(), marker.getLon(), false);
				radius = getRadius(marker, p);
			}
			marker.paint(g, p, radius);
			int xSave = p.x;
			int xWrap = xSave;
			// overscan of 15 allows up to 30-pixel markers to gracefully scroll off the
			// edge of the panel
			while ((xWrap -= mapSize) >= -15) {
				p.x = xWrap;
				marker.paint(g, p, radius);
			}
			xWrap = xSave;
			while ((xWrap += mapSize) <= getWidth() + 15) {
				p.x = xWrap;
				marker.paint(g, p, radius);
			}
		} else {
			if (p != null) {
				marker.paint(g, p, radius);
			}
		}
	}

	/**
	 * Paint a single polygon.
	 *
	 * @param g       Graphics used for painting
	 * @param polygon polygon to paint
	 */
	protected void paintPolygon(Graphics g, MapPolygon polygon) {
		List<? extends ICoordinate> coords = polygon.getPoints();
		if (coords != null && coords.size() >= 3) {
			List<Point> points = new LinkedList<>();
			for (ICoordinate c : coords) {
				Point p = getMapPosition(c, false);
				if (p == null) {
					return;
				}
				points.add(p);
			}
			polygon.paint(g, points);
			if (scrollWrapEnabled) {
				int tilesize = tileSource.getTileSize();
				int mapSize = tilesize << zoom;
				List<Point> pointsWrapped = new LinkedList<>(points);
				boolean keepWrapping = true;
				while (keepWrapping) {
					for (Point p : pointsWrapped) {
						p.x -= mapSize;
						if (p.x < 0) {
							keepWrapping = false;
						}
					}
					polygon.paint(g, pointsWrapped);
				}
				pointsWrapped = new LinkedList<>(points);
				keepWrapping = true;
				while (keepWrapping) {
					for (Point p : pointsWrapped) {
						p.x += mapSize;
						if (p.x > getWidth()) {
							keepWrapping = false;
						}
					}
					polygon.paint(g, pointsWrapped);
				}
			}
		}
	}

	/**
	 * Paint a single rectangle.
	 *
	 * @param g         Graphics used for painting
	 * @param rectangle rectangle to paint
	 */
	protected void paintRectangle(Graphics g, MapRectangle rectangle) {
		Coordinate topLeft = rectangle.getTopLeft();
		Coordinate bottomRight = rectangle.getBottomRight();
		if (topLeft != null && bottomRight != null) {
			Point pTopLeft = getMapPosition(topLeft, false);
			Point pBottomRight = getMapPosition(bottomRight, false);
			if (pTopLeft != null && pBottomRight != null) {
				rectangle.paint(g, pTopLeft, pBottomRight);
				if (scrollWrapEnabled) {
					int tilesize = tileSource.getTileSize();
					int mapSize = tilesize << zoom;
					int xTopLeftSave = pTopLeft.x;
					int xTopLeftWrap = xTopLeftSave;
					int xBottomRightSave = pBottomRight.x;
					int xBottomRightWrap = xBottomRightSave;
					while ((xBottomRightWrap -= mapSize) >= 0) {
						xTopLeftWrap -= mapSize;
						pTopLeft.x = xTopLeftWrap;
						pBottomRight.x = xBottomRightWrap;
						rectangle.paint(g, pTopLeft, pBottomRight);
					}
					xTopLeftWrap = xTopLeftSave;
					xBottomRightWrap = xBottomRightSave;
					while ((xTopLeftWrap += mapSize) <= getWidth()) {
						xBottomRightWrap += mapSize;
						pTopLeft.x = xTopLeftWrap;
						pBottomRight.x = xBottomRightWrap;
						rectangle.paint(g, pTopLeft, pBottomRight);
					}
				}
			}
		}
	}

	/**
	 * Remove all {@link MapMarker}s.
	 */
	public void removeAllMapMarkers() {
		mapMarkerList.clear();
		repaint();
	}

	/**
	 * Remove all {@link MapPolygon}s.
	 */
	public void removeAllMapPolygons() {
		mapPolygonList.clear();
		repaint();
	}

	/**
	 * Remove all {@link MapRectangle}s.
	 */
	public void removeAllMapRectangles() {
		mapRectangleList.clear();
		repaint();
	}

	/**
	 * @param listener listener to remove
	 */
	public void removeJMVListener(JMapViewerEventListener listener) {
		evtListenerList.remove(JMapViewerEventListener.class, listener);
	}

	/**
	 * Remove a {@link MapMarker}.
	 *
	 * @param marker map marker to remove
	 */
	public void removeMapMarker(MapMarker marker) {
		mapMarkerList.remove(marker);
		repaint();
	}

	/**
	 * Remove a {@link MapPolygon}.
	 *
	 * @param polygon map polygon to remove
	 */
	public void removeMapPolygon(MapPolygon polygon) {
		mapPolygonList.remove(polygon);
		repaint();
	}

	/**
	 * Remove a {@link MapRectangle}.
	 *
	 * @param rectangle map rectangle to remove
	 */
	public void removeMapRectangle(MapRectangle rectangle) {
		mapRectangleList.remove(rectangle);
		repaint();
	}

	private void renderTilesSpiraly(Graphics2D g2, int iMove, int tilesize, int tilex, int tiley, int posx, int posy,
			int xMin, int yMin, int xMax, int yMax, int gridLength) {
		int gridLen = gridLength;
		boolean painted = true;
		int x = 0;
		while (painted) {
			painted = false;
			for (int i = 0; i < 4; i++) {
				if (i % 2 == 0) {
					x++;
				}

				for (int j = 0; j < x; j++) {
					if (posx >= xMin && posx <= xMax && posy >= yMin && posy <= yMax) {

						int lookupX = tilex;
						if (scrollWrapEnabled) {
							lookupX = (tilex % gridLen + gridLen) % gridLen;
						}

						painted = renderTiles(g2, tilesize, tiley, posx, posy, painted, lookupX);
					}

					// State-Update
					Point p = move[iMove];
					posx += p.x * tilesize;
					posy += p.y * tilesize;
					tilex += p.x;
					tiley += p.y;
				}
				iMove = (iMove + 1) % 4; // move.length ist fix 4 bei Spiralen
			}
		}
	}

	private boolean renderTiles(Graphics2D g2, int tilesize, int tiley, int posx, int posy, boolean painted,
			int lookupX) {
		Tile tile = tileController.getTile(lookupX, tiley, zoom);
		if (tile != null) {
			tile.paint(g2, posx, posy, tilesize, tilesize);
			if (tileGridVisible) {
				g2.drawRect(posx, posy, tilesize, tilesize);
			}
			painted = true;
		}
		return painted;
	}

	/**
	 * @param center the center to set
	 */
	public void setCenter(Point center) {
		this.center = center;
	}

	/**
	 * Changes the map pane so that it is centered on the specified coordinate at
	 * the given zoom level.
	 *
	 * @param to   specified coordinate
	 * @param zoom {@link #MIN_ZOOM} &lt;= zoom level &lt;= {@link #MAX_ZOOM}
	 */
	public void setDisplayPosition(ICoordinate to, int zoom) {
		setDisplayPosition(new Point(getWidth() / 2, getHeight() / 2), to, zoom);
	}

	/**
	 * Sets the display position.
	 *
	 * @param x    X coordinate
	 * @param y    Y coordinate
	 * @param zoom zoom level, between {@link #MIN_ZOOM} and {@link #MAX_ZOOM}
	 */
	public void setDisplayPosition(int x, int y, int zoom) {
		setDisplayPosition(new Point(getWidth() / 2, getHeight() / 2), x, y, zoom);
	}

	/**
	 * Changes the map pane so that the specified coordinate at the given zoom level
	 * is displayed on the map at the screen coordinate <code>mapPoint</code>.
	 *
	 * @param mapPoint point on the map denoted in pixels where the coordinate
	 *                 should be set
	 * @param to       specified coordinate
	 * @param zoom     {@link #MIN_ZOOM} &lt;= zoom level &lt;=
	 *                 {@link TileSource#getMaxZoom()}
	 */
	public void setDisplayPosition(Point mapPoint, ICoordinate to, int zoom) {
		Point p = tileSource.latLonToXY(to, zoom);
		setDisplayPosition(mapPoint, p.x, p.y, zoom);
	}

	/**
	 * Sets the display position.
	 *
	 * @param mapPoint map point
	 * @param x        X coordinate
	 * @param y        Y coordinate
	 * @param zoom     zoom level, between {@link #MIN_ZOOM} and {@link #MAX_ZOOM}
	 */
	public void setDisplayPosition(Point mapPoint, int x, int y, int zoom) {
		if (zoom > tileController.getTileSource().getMaxZoom() || zoom < MIN_ZOOM) {
			return;
		}

		// Get the plain tile number
		Point p = new Point();
		p.x = x - mapPoint.x + getWidth() / 2;
		p.y = y - mapPoint.y + getHeight() / 2;
		center = p;
		setIgnoreRepaint(true);
		try {
			int oldZoom = this.zoom;
			this.zoom = zoom;
			if (oldZoom != zoom) {
				zoomChanged(oldZoom);
			}
			if (zoomSlider.getValue() != zoom) {
				zoomSlider.setValue(zoom);
			}
		} finally {
			setIgnoreRepaint(false);
			repaint();
		}
	}

	/**
	 * Sets the displayed map pane and zoom level so that all chosen map elements
	 * are visible.
	 *
	 * @param markers    whether to consider markers
	 * @param rectangles whether to consider rectangles
	 * @param polygons   whether to consider polygons
	 */
	public void setDisplayToFitMapElements(boolean markers, boolean rectangles, boolean polygons) {
		int nbElemToCheck = 0;
		if (markers && mapMarkerList != null) {
			nbElemToCheck += mapMarkerList.size();
		}
		if (rectangles && mapRectangleList != null) {
			nbElemToCheck += mapRectangleList.size();
		}
		if (polygons && mapPolygonList != null) {
			nbElemToCheck += mapPolygonList.size();
		}
		if (nbElemToCheck == 0) {
			return;
		}

		int xMin = Integer.MAX_VALUE;
		int yMin = Integer.MAX_VALUE;
		int xMax = Integer.MIN_VALUE;
		int yMax = Integer.MIN_VALUE;
		int mapZoomMax = tileController.getTileSource().getMaxZoom();

		if (markers && mapMarkerList != null) {
			synchronized (this) {
				for (MapMarker marker : mapMarkerList) {
					if (marker.isVisible()) {
						Point p = tileSource.latLonToXY(marker.getCoordinate(), mapZoomMax);
						xMax = Math.max(xMax, p.x);
						yMax = Math.max(yMax, p.y);
						xMin = Math.min(xMin, p.x);
						yMin = Math.min(yMin, p.y);
					}
				}
			}
		}

		if (rectangles && mapRectangleList != null) {
			synchronized (this) {
				for (MapRectangle rectangle : mapRectangleList) {
					if (rectangle.isVisible()) {
						Point bottomRight = tileSource.latLonToXY(rectangle.getBottomRight(), mapZoomMax);
						Point topLeft = tileSource.latLonToXY(rectangle.getTopLeft(), mapZoomMax);
						xMax = Math.max(xMax, bottomRight.x);
						yMax = Math.max(yMax, topLeft.y);
						xMin = Math.min(xMin, topLeft.x);
						yMin = Math.min(yMin, bottomRight.y);
					}
				}
			}
		}

		if (polygons && mapPolygonList != null) {
			synchronized (this) {
				for (MapPolygon polygon : mapPolygonList) {
					if (polygon.isVisible()) {
						for (ICoordinate c : polygon.getPoints()) {
							Point p = tileSource.latLonToXY(c, mapZoomMax);
							xMax = Math.max(xMax, p.x);
							yMax = Math.max(yMax, p.y);
							xMin = Math.min(xMin, p.x);
							yMin = Math.min(yMin, p.y);
						}
					}
				}
			}
		}

		int height = Math.max(0, getHeight());
		int width = Math.max(0, getWidth());
		int newZoom = mapZoomMax;
		int x = xMax - xMin;
		int y = yMax - yMin;
		while (x > width || y > height) {
			newZoom--;
			x >>= 1;
					y >>= 1;
		}
		x = xMin + (xMax - xMin) / 2;
		y = yMin + (yMax - yMin) / 2;
		int z = 1 << (mapZoomMax - newZoom);
		x /= z;
		y /= z;
		setDisplayPosition(x, y, newZoom);
	}

	/**
	 * Sets the displayed map pane and zoom level so that all map markers are
	 * visible.
	 */
	public void setDisplayToFitMapMarkers() {
		setDisplayToFitMapElements(true, false, false);
	}

	/**
	 * Sets the displayed map pane and zoom level so that all map polygons are
	 * visible.
	 */
	public void setDisplayToFitMapPolygons() {
		setDisplayToFitMapElements(false, false, true);
	}

	/**
	 * Sets the displayed map pane and zoom level so that all map rectangles are
	 * visible.
	 */
	public void setDisplayToFitMapRectangles() {
		setDisplayToFitMapElements(false, true, false);
	}

	/**
	 * Sets the list of {@link MapMarker}s.
	 *
	 * @param mapMarkerList list of {@link MapMarker}s
	 */
	public void setMapMarkerList(List<MapMarker> mapMarkerList) {
		this.mapMarkerList = mapMarkerList;
		repaint();
	}

	/**
	 * Enables or disables painting of the {@link MapMarker}
	 *
	 * @param mapMarkersVisible {@code true} to enable painting of markers
	 * @see #addMapMarker(MapMarker)
	 * @see #getMapMarkerList()
	 */
	public void setMapMarkerVisible(boolean mapMarkersVisible) {
		this.mapMarkersVisible = mapMarkersVisible;
		repaint();
	}

	/**
	 * Sets the list of {@link MapPolygon}s.
	 *
	 * @param mapPolygonList list of {@link MapPolygon}s
	 */
	public void setMapPolygonList(List<MapPolygon> mapPolygonList) {
		this.mapPolygonList = mapPolygonList;
		repaint();
	}

	/**
	 * Enables or disables painting of the {@link MapPolygon}s.
	 *
	 * @param mapPolygonsVisible {@code true} to enable painting of polygons
	 * @see #addMapPolygon(MapPolygon)
	 * @see #getMapPolygonList()
	 */
	public void setMapPolygonsVisible(boolean mapPolygonsVisible) {
		this.mapPolygonsVisible = mapPolygonsVisible;
		repaint();
	}

	/**
	 * Sets the list of {@link MapRectangle}s.
	 *
	 * @param mapRectangleList list of {@link MapRectangle}s
	 */
	public void setMapRectangleList(List<MapRectangle> mapRectangleList) {
		this.mapRectangleList = mapRectangleList;
		repaint();
	}

	/**
	 * Enables or disables painting of the {@link MapRectangle}s.
	 *
	 * @param mapRectanglesVisible {@code true} to enable painting of rectangles
	 * @see #addMapRectangle(MapRectangle)
	 * @see #getMapRectangleList()
	 */
	public void setMapRectanglesVisible(boolean mapRectanglesVisible) {
		this.mapRectanglesVisible = mapRectanglesVisible;
		repaint();
	}

	/**
	 * Sets whether scroll wrap is enabled or not.
	 *
	 * @param scrollWrapEnabled {@code true} if scroll wrap is enabled,
	 *                          {@code false} otherwise
	 */
	public void setScrollWrapEnabled(boolean scrollWrapEnabled) {
		this.scrollWrapEnabled = scrollWrapEnabled;
		repaint();
	}

	/**
	 * Sets whether the tile grid is visible or not.
	 *
	 * @param tileGridVisible {@code true} if the tile grid is visible,
	 *                        {@code false} otherwise
	 */
	public void setTileGridVisible(boolean tileGridVisible) {
		this.tileGridVisible = tileGridVisible;
		repaint();
	}

	/**
	 * Sets the tile loader.
	 *
	 * @param loader tile loader
	 */
	public void setTileLoader(TileLoader loader) {
		tileController.setTileLoader(loader);
	}

	/**
	 * Sets the tile source.
	 *
	 * @param tileSource tile source
	 */
	public void setTileSource(TileSource tileSource) {
		if (tileSource.getMaxZoom() > MAX_ZOOM) {
			throw new RuntimeException("Maximum zoom level too high");
		}
		if (tileSource.getMinZoom() < MIN_ZOOM) {
			throw new RuntimeException("Minimum zoom level too low");
		}
		ICoordinate position = getPosition();
		this.tileSource = tileSource;
		tileController.setTileSource(tileSource);
		zoomSlider.setMinimum(tileSource.getMinZoom());
		zoomSlider.setMaximum(tileSource.getMaxZoom());
		tileController.cancelOutstandingJobs();
		if (zoom > tileSource.getMaxZoom()) {
			setZoom(tileSource.getMaxZoom());
		}
		attribution.initialize(tileSource);
		setDisplayPosition(position, zoom);
		repaint();
	}

	/**
	 * Set the zoom level
	 *
	 * @param zoom new zoom level
	 */
	public void setZoom(int zoom) {
		Point mapPoint = new Point(getWidth() / 2, getHeight() / 2);
		if (OsmMercator.isRetina()) {
			// retina 2x scale
			mapPoint.setLocation((mapPoint.x / 2), mapPoint.y / 2);
		}
		setZoom(zoom, mapPoint);
	}

	/**
	 * Set the zoom level and center point for display
	 *
	 * @param zoom     new zoom level
	 * @param mapPoint point to choose as center for new zoom level
	 */
	public void setZoom(int zoom, Point mapPoint) {
		if (zoom > tileController.getTileSource().getMaxZoom() || zoom < tileController.getTileSource().getMinZoom()
				|| zoom == this.zoom) {
			return;
		}
		if (OsmMercator.isRetina()) {
			// retina 2x scale
			mapPoint.setLocation(2 * mapPoint.x, 2 * mapPoint.y);
		}
		ICoordinate zoomPos = getPosition(mapPoint);
		tileController.cancelOutstandingJobs(); // Clearing outstanding load
		// requests
		setDisplayPosition(mapPoint, zoomPos, zoom);

		this.fireJMVEvent(new JMVCommandEvent(COMMAND.ZOOM, this));
	}

	/**
	 * Sets the zoom controls apparence style (horizontal/vertical).
	 *
	 * @param style {@link ZOOM_BUTTON_STYLE#VERTICAL} or
	 *              {@link ZOOM_BUTTON_STYLE#HORIZONTAL}
	 */
	public void setZoomButtonStyle(ZOOM_BUTTON_STYLE style) {
		zoomButtonStyle = style;
		if (zoomSlider == null || zoomInButton == null || zoomOutButton == null) {
			return;
		}
		switch (style) {
		case VERTICAL:
			zoomSlider.setBounds(10, 27, 30, 150);
			zoomInButton.setBounds(14, 8, 20, 20);
			zoomOutButton.setBounds(14, 176, 20, 20);
			break;
		case HORIZONTAL:
		default:
			zoomSlider.setBounds(10, 10, 30, 150);
			zoomInButton.setBounds(4, 155, 18, 18);
			zoomOutButton.setBounds(26, 155, 18, 18);
			break;
		}
		repaint();
	}

	/**
	 * Sets whether zoom controls are displayed or not.
	 *
	 * @param visible {@code true} if zoom controls are displayed, {@code false}
	 *                otherwise
	 */
	public void setZoomContolsVisible(boolean visible) {
		zoomSlider.setVisible(visible);
		zoomInButton.setVisible(visible);
		zoomOutButton.setVisible(visible);
	}

	@Override
	public void tileLoadingFinished(Tile tile, boolean success) {
		tile.setLoaded(success);
		repaint();
	}

	/**
	 * Every time the zoom level changes this method is called. Override it in
	 * derived implementations for adapting zoom dependent values. The new zoom
	 * level can be obtained via {@link #getZoom()}.
	 *
	 * @param oldZoom the previous zoom level
	 */
	protected void zoomChanged(int oldZoom) {
		zoomSlider.setToolTipText("Zoom level " + zoom);
		zoomInButton.setToolTipText("Zoom to level " + (zoom + 1));
		zoomOutButton.setToolTipText("Zoom to level " + (zoom - 1));
		zoomOutButton.setEnabled(zoom > tileController.getTileSource().getMinZoom());
		zoomInButton.setEnabled(zoom < tileController.getTileSource().getMaxZoom());
	}

	/**
	 * Increases the current zoom level by one
	 */
	public void zoomIn() {
		setZoom(zoom + 1);
	}

	/**
	 * Increases the current zoom level by one
	 *
	 * @param mapPoint point to choose as center for new zoom level
	 */
	public void zoomIn(Point mapPoint) {
		setZoom(zoom + 1, mapPoint);
	}

	/**
	 * Decreases the current zoom level by one
	 */
	public void zoomOut() {
		setZoom(zoom - 1);
	}

	/**
	 * Decreases the current zoom level by one
	 *
	 * @param mapPoint point to choose as center for new zoom level
	 */
	public void zoomOut(Point mapPoint) {
		setZoom(zoom - 1, mapPoint);
	}
}
