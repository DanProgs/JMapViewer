package org.openstreetmap.gui.jmapviewer.interfaces;

import org.openstreetmap.gui.jmapviewer.Tile;

/**
 * Interface for custom tile cache implementations used by JMapViewer.
 * Defines methods for tile lifecycle management, including storage, retrieval,
 * and capacity control.
 *
 * @author Jan Peter Stotz
 */
public interface TileCache {

    /**
     * Adds a tile to the cache. The retention period depends on the 
     * implementation's eviction policy (e.g., Least Recently Used).
     *
     * @param tile the {@link Tile} object to be stored
     */
    void addTile(Tile tile);

    /**
     * Clears all tiles from the cache, resetting it to an empty state.
     */
    void clear();

    /**
     * Returns the maximum number of tiles the cache is configured to hold.
     *
     * @return the maximum cache capacity
     */
    int getCacheSize();

    /**
     * Retrieves a tile from the cache. Typically updates the tile's LRU status.
     *
     * @param source the source of the tile
     * @param x      the x-coordinate (column)
     * @param y      the y-coordinate (row)
     * @param z      the zoom level
     * @return the requested {@link Tile}, or {@code null} if not found
     */
    Tile getTile(TileSource source, int x, int y, int z);

    /**
     * Returns the number of tiles currently residing in the cache.
     *
     * @return the current number of cached tiles
     */
    int getTileCount();

    /**
     * Checks if a tile is present and loaded without triggering side effects.
     * This is a "peek" operation for efficient rendering decisions.
     *
     * @param source the source of the tile
     * @param x      the x-coordinate
     * @param y      the y-coordinate
     * @param zoom   the zoom level
     * @return {@code true} if the tile is cached, loaded, and error-free
     */
    boolean isLoaded(TileSource source, int x, int y, int zoom);

    /**
     * Updates the maximum capacity and triggers eviction if necessary.
     *
     * @param cacheSize the new maximum number of tiles
     */
    void setCacheSize(int cacheSize);
}
