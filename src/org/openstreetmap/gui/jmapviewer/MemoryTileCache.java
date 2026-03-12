package org.openstreetmap.gui.jmapviewer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;

/**
 * High-performance {@link TileCache} using bit-packed long keys,
 * ConcurrentHashMap for lock-free reads, and object pooling.
 */
public class MemoryTileCache implements TileCache {

	protected static final class CacheEntry {
		private long key;
		private CacheEntry next;
		private CacheEntry prev;
		private Tile tile;

		protected CacheEntry(Tile tile, long key) {
			reuse(tile, key);
		}

		protected void reuse(Tile tile, long key) {
			this.tile = tile;
			this.key = key;
			this.next = this.prev = null;
		}
	}

	protected static class CacheLinkedListElement {

		protected int elementCount;

		protected CacheEntry firstElement;
		protected CacheEntry lastElement;

		public void addFirst(CacheEntry element) {
			if (element == null) {
				return;
			}
			if (elementCount == 0) {
				firstElement = lastElement = element;
			} else {
				element.next = firstElement;
				firstElement.prev = element;
				element.prev = null;
				firstElement = element;
			}
			elementCount++;
		}

		/**
		 * Clears the list by resetting all counters and references. This helps the
		 * Garbage Collector to reclaim memory of the entries.
		 */
		public void clear() {
			// Traverse the list to break references between entries
			CacheEntry current = firstElement;
			while (current != null) {
				CacheEntry next = current.next;
				current.prev = null;
				current.next = null;
				current.tile = null; // Important: Clear tile reference
				current = next;
			}

			// Reset list pointers
			firstElement = null;
			lastElement = null;
			elementCount = 0;
		}

		public void moveElementToFirstPos(CacheEntry entry) {
			if (entry == null || firstElement == entry) {
				return;
			}
			remove(entry);
			addFirst(entry);
		}

		public void remove(CacheEntry element) {
			if (element == null) {
				return;
			}
			if (element == firstElement) {
				firstElement = element.next;
			} else if (element.prev != null) {
				element.prev.next = element.next;
			}
			if (element == lastElement) {
				lastElement = element.prev;
			} else if (element.next != null) {
				element.next.prev = element.prev;
			}
			element.next = element.prev = null;
			elementCount = Math.max(0, elementCount - 1);
		}
	}

	private static final Logger log = Logger.getLogger(MemoryTileCache.class.getName());
	private static final int MAX_POOL_SIZE = 256;

	private static long getTileKey(final TileSource source, final int x, final int y, final int z) {
		return (((source == null ? 0 : source.hashCode()) & 0x7FFL) << 53)

				| ((z & 0x1FL) << 48) | ((x & 0xFFFFFFL) << 24) | (y & 0xFFFFFFL);
	}

	protected int cacheSize;

	private final CacheEntry[] entryPool = new CacheEntry[MAX_POOL_SIZE];
	protected final Map<Long, CacheEntry> hash;
	protected final CacheLinkedListElement lruTiles = new CacheLinkedListElement();
	private final Object modificationLock = new Object();

	private int poolSize = 0;

	public MemoryTileCache() {
		this(200);
	}

	public MemoryTileCache(int cacheSize) {
		this.cacheSize = cacheSize;
		int initialCapacity = (int) (cacheSize / 0.75f) + 1;
		this.hash = new ConcurrentHashMap<>(initialCapacity, 0.75f);
	}

	@Override
	public void addTile(Tile tile) {
		final long key = getTileKey(tile.getSource(), tile.getXtile(), tile.getYtile(), tile.getZoom());
		synchronized (modificationLock) {
			CacheEntry entry = hash.get(key);
			if (entry != null) {
				entry.tile = tile;
				lruTiles.moveElementToFirstPos(entry);
			} else {
				CacheEntry newEntry = (poolSize > 0) ? entryPool[--poolSize] : new CacheEntry(tile, key);
				newEntry.reuse(tile, key);
				hash.put(key, newEntry);
				lruTiles.addFirst(newEntry);
				if (hash.size() > cacheSize) {
					removeOldEntries();
				}
			}
		}
	}

	@Override
	public void clear() {
		synchronized (modificationLock) {
			hash.clear();
			lruTiles.clear();
			for (int i = 0; i < poolSize; i++) {
				entryPool[i] = null;
			}
			poolSize = 0;
		}
	}

	@Override
	public int getCacheSize() {
		return cacheSize;
	}

	@Override
	public Tile getTile(TileSource source, int x, int y, int z) {
		final CacheEntry entry = hash.get(getTileKey(source, x, y, z));
		if (entry != null) {
			synchronized (modificationLock) {
				lruTiles.moveElementToFirstPos(entry);
			}
			return entry.tile;
		}
		return null;
	}

	@Override
	public int getTileCount() {
		return hash.size();
	}

	@Override
	public boolean isLoaded(TileSource source, int x, int y, int z) {
		final CacheEntry entry = hash.get(getTileKey(source, x, y, z));
		return entry != null && entry.tile.isLoaded() && !entry.tile.hasError();
	}

	private void removeOldEntries() {
		while (hash.size() > cacheSize) {
			CacheEntry last = lruTiles.lastElement;
			if (last == null) {
				break;
			}
			hash.remove(last.key);
			lruTiles.remove(last);
			if (poolSize < MAX_POOL_SIZE) {
				last.tile = null;
				entryPool[poolSize++] = last;
			}
		}
	}

	@Override
	public void setCacheSize(int cacheSize) {
		synchronized (modificationLock) {
			this.cacheSize = cacheSize;
			removeOldEntries();
		}
	}
}
