// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;

/**
 * Holds one map tile. Additionally the code for loading the tile image and
 * painting it is also included in this class.
 *
 * @author Jan Peter Stotz
 */
public class Tile {

	private static class CachedCallable<V> implements Callable<V> {
		private Callable<V> callable;
		private volatile V result;

		CachedCallable(Callable<V> callable) {
			this.callable = Objects.requireNonNull(callable);
		}

		@Override
		public V call() {
			V v = result;
			if (v == null) {
				synchronized (this) {
					v = result;
					if (v == null) {
						try {
							v = callable.call();
							result = Objects.requireNonNull(v, "Callable returned null");

							this.callable = null;
						} catch (Exception e) {
							throw new RuntimeException("Error executing cached callable", e);
						}
					}
				}
			}
			return v;
		}
	}

	public static final BufferedImage ERROR_IMAGE;
	public static final BufferedImage LOADING_IMAGE;

	static {
		LOADING_IMAGE = loadImage("images/hourglass.png");
		ERROR_IMAGE = loadImage("images/error.png");
	}

	public static String getTileKey(TileSource source, int xtile, int ytile, int zoom) {
		return zoom + "/" + xtile + "/" + ytile + "@" + source.getName();
	}

	private static BufferedImage loadImage(String path) {
		try (InputStream in = JMapViewer.class.getResourceAsStream(path)) {
			if (in == null) {
				System.err.println("Resource not found: " + path);
				return null;
			}
			return readInImage(in);
		} catch (IOException e) {
			e.printStackTrace(); // Optional: Logging statt stillem Verschlucken
			return null;
		}
	}

	static BufferedImage readInImage(InputStream in) throws IOException {
		BufferedImage image = ImageIO.read(in);
		if (image == null) {
			return null;
		}

		if (OsmMercator.isRetina()) {
			int doubleSize = image.getWidth() * 2;
			GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
					.getDefaultConfiguration();

			BufferedImage image2x = gc.createCompatibleImage(doubleSize, doubleSize, Transparency.TRANSLUCENT);
			Graphics2D g = image2x.createGraphics();

			try {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				g.drawImage(image, 0, 0, doubleSize, doubleSize, null);
			} finally {
				g.dispose();
			}
			return image2x;
		}
		return image;
	}

	protected volatile boolean error;
	protected String errorMessage;
	protected BufferedImage image;
	protected String key;
	protected volatile boolean loaded;
	protected volatile boolean loading;

	/** TileLoader-specific tile metadata */
	protected Map<String, String> metadata;

	protected TileSource source;

	protected int xtile;

	protected int ytile;

	protected int zoom;

	/**
	 * Creates a tile with empty image.
	 *
	 * @param source Tile source
	 * @param xtile  X coordinate
	 * @param ytile  Y coordinate
	 * @param zoom   Zoom level
	 */
	public Tile(TileSource source, int xtile, int ytile, int zoom) {
		this(source, xtile, ytile, zoom, LOADING_IMAGE);
	}

	/**
	 * Creates a tile with specified image.
	 *
	 * @param source Tile source
	 * @param xtile  X coordinate
	 * @param ytile  Y coordinate
	 * @param zoom   Zoom level
	 * @param image  Image content
	 */
	public Tile(TileSource source, int xtile, int ytile, int zoom, BufferedImage image) {
		this.source = source;
		this.xtile = xtile;
		this.ytile = ytile;
		this.zoom = zoom;
		this.image = image;
		this.key = getTileKey(source, xtile, ytile, zoom);
	}

	/**
	 * Compares this object with <code>obj</code> based on the fields
	 * {@link #xtile}, {@link #ytile} and {@link #zoom}. The {@link #source} field
	 * is ignored.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Tile)) {
			return false;
		}

		Tile other = (Tile) obj;
		return xtile == other.xtile && ytile == other.ytile && zoom == other.zoom
				&& Objects.equals(getTileSource(), other.getTileSource());
	}

	/**
	 * indicate that loading process for this tile has ended
	 */
	public void finishLoading() {
		loading = false;
		loaded = true;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	private Graphics2D getGraphics(CachedCallable<BufferedImage> img, double sx, double sy, int tx, int ty) {
		Graphics2D g = img.call().createGraphics();
		g.setTransform(new AffineTransform(sx, 0, 0, sy, tx, ty));
		return g;
	}

	public BufferedImage getImage() {
		return image;
	}

	/**
	 * @return key that identifies a tile
	 */
	public String getKey() {
		return key;
	}

	/**
	 *
	 * @return metadata of the tile
	 */
	public Map<String, String> getMetadata() {
		if (metadata == null) {
			metadata = new HashMap<>();
		}
		return metadata;
	}

	public TileSource getSource() {
		return source;
	}

	public String getStatus() {
		if (this.error) {
			return "error";
		}
		if (this.loaded) {
			return "loaded";
		}
		if (this.loading) {
			return "loading";
		}
		return "new";
	}

	/**
	 *
	 * @return TileSource from which this tile comes
	 */
	public TileSource getTileSource() {
		return source;
	}

	/**
	 * @return tile indexes of the top left corner as TileXY object
	 */
	public TileXY getTileXY() {
		return new TileXY(xtile, ytile);
	}

	public String getUrl() throws IOException {
		return source.getTileUrl(zoom, xtile, ytile);
	}

	/**
	 * returns the metadata of the Tile
	 *
	 * @param key metadata key that should be returned
	 * @return null if no such metadata exists, or the value of the metadata
	 */
	public String getValue(String key) {
		if (metadata == null) {
			return null;
		}
		return metadata.get(key);
	}

	/**
	 * Returns the X coordinate.
	 *
	 * @return tile number on the x axis of this tile
	 */
	public int getXtile() {
		return xtile;
	}

	/**
	 * Returns the Y coordinate.
	 *
	 * @return tile number on the y axis of this tile
	 */
	public int getYtile() {
		return ytile;
	}

	/**
	 * Returns the zoom level.
	 *
	 * @return zoom level of this tile
	 */
	public int getZoom() {
		return zoom;
	}

	public boolean hasError() {
		return error;
	}

	/**
	 * Note that the hash code does not include the {@link #source}. Therefore a
	 * hash based collection can only contain tiles of one {@link #source}.
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + xtile;
		result = prime * result + ytile;
		result = prime * result + zoom;
		return result;
	}

	/**
	 * indicate that loading process for this tile has started
	 */
	public void initLoading() {
		error = false;
		loading = true;
	}

	public boolean isLoaded() {
		return loaded;
	}

	public boolean isLoading() {
		return loading;
	}

	public void loadImage(InputStream input) throws IOException {
		setImage(readInImage(input));
	}

	/**
	 * indicate that loading process for this tile has been canceled
	 */
	public void loadingCanceled() {
		loading = false;
		loaded = false;
	}

	/**
	 * Tries to get tiles of a lower or higher zoom level (one or two level
	 * difference) from cache and use it as a placeholder until the tile has been
	 * loaded.
	 *
	 * @param cache Tile cache
	 */
	public void loadPlaceholderFromCache(TileCache cache) {final int tileSize = source.getTileSize();
	final CachedCallable<BufferedImage> tmpImage = new CachedCallable<>(
	        () -> new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB));

	for (int diff = 1; diff < 5; diff++) {
	    // 1. Higher zoom (Upscaling aus Detail-Tiles)
	    int zoomHigh = zoom + diff;
	    if (diff < 3 && zoomHigh <= JMapViewer.MAX_ZOOM) {
	        int factor = 1 << diff;
	        int xHighBase = xtile << diff;
	        int yHighBase = ytile << diff;
	        int totalParts = factor * factor;

	        // Preliminary check: We only start drawing once ALL parts are loaded.
	        boolean allLoaded = true;
	        for (int i = 0; i < totalParts; i++) {
	            if (!cache.isLoaded(source, xHighBase + (i % factor), yHighBase + (i / factor), zoomHigh)) {
	                allLoaded = false;
	                break;
	            }
	        }

	        if (allLoaded) {
	            double scale = 1.0 / factor;
	            Graphics2D g = getGraphics(tmpImage, scale, scale, 0, 0);
	            for (int x = 0; x < factor; x++) {
	                for (int y = 0; y < factor; y++) {
	                    Tile t = cache.getTile(source, xHighBase + x, yHighBase + y, zoomHigh);
	                    t.paint(g, x * tileSize, y * tileSize);
	                }
	            }
	            this.image = tmpImage.call();
	            return;
	        }
	    }

	    // 2. Lower zoom (Downscaling aus Übersicht-Tile)
	    int zoomLow = zoom - diff;
	    if (zoomLow >= JMapViewer.MIN_ZOOM) {
	        Tile t = cache.getTile(source, xtile >> diff, ytile >> diff, zoomLow);
	        if (t != null && t.isLoaded()) {
	            int factor = 1 << diff;
	            // Use bit masking instead of modulo for performance.
	            int tx = (xtile & (factor - 1)) * tileSize;
	            int ty = (ytile & (factor - 1)) * tileSize;
	            
	            t.paint(getGraphics(tmpImage, factor, factor, -tx, -ty), 0, 0);
	            this.image = tmpImage.call();
	            return;
	        }
	    }
	}
}

	/**
	 * Paints the tile-image on the {@link Graphics} <code>g</code> at the position
	 * <code>x</code>/<code>y</code>.
	 *
	 * @param g the Graphics object
	 * @param x x-coordinate in <code>g</code>
	 * @param y y-coordinate in <code>g</code>
	 */
	public void paint(Graphics g, int x, int y) {
		if (image == null) {
			return;
		}
		g.drawImage(image, x, y, null);
	}

	/**
	 * Paints the tile-image on the {@link Graphics} <code>g</code> at the position
	 * <code>x</code>/<code>y</code>.
	 *
	 * @param g      the Graphics object
	 * @param x      x-coordinate in <code>g</code>
	 * @param y      y-coordinate in <code>g</code>
	 * @param width  width that tile should have
	 * @param height height that tile should have
	 */
	public void paint(Graphics g, int x, int y, int width, int height) {
		if (image == null) {
			return;
		}
		g.drawImage(image, x, y, width, height, null);
	}

	/**
	 * Puts the given key/value pair to the metadata of the tile. If value is null,
	 * the (possibly existing) key/value pair is removed from the meta data.
	 *
	 * @param key   Key
	 * @param value Value
	 */
	public void putValue(String key, String value) {
		if (value == null || value.isEmpty()) {
			if (metadata != null) {
				metadata.remove(key);
			}
			return;
		}
		if (metadata == null) {
			metadata = new HashMap<>();
		}
		metadata.put(key, value);
	}

	public void setError(Exception e) {
		setError(e.toString());
	}

	public void setError(String message) {
		error = true;
		setImage(ERROR_IMAGE);
		errorMessage = message;
	}

	public void setImage(BufferedImage image) {
		this.image = image;
	}

	public void setLoaded(boolean loaded) {
		this.loaded = loaded;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(64).append("Tile ").append(key);

		if (loading) {
			sb.append(" [LOADING...]");
		}
		if (loaded) {
			sb.append(" [loaded]");
		}
		if (error) {
			sb.append(" [ERROR]");
		}

		return sb.toString();
	}

}
