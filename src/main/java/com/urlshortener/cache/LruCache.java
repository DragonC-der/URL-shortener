package com.urlshortener.cache;

import java.util.HashMap;
import java.util.Map;

/**
 * Fixed-capacity LRU cache with O(1) get and put, built from a HashMap
 * (for O(1) key lookup) plus a doubly linked list (for O(1) reordering
 * and eviction) - the same structure real LRU caches use internally.
 *
 * Note: Java's LinkedHashMap can do this in about 5 lines via its
 * access-order constructor + overriding removeEldestEntry(). That's
 * absolutely the right call for production code - reimplementing a
 * well-tested standard-library structure by hand isn't a virtue there.
 * This version exists specifically so the underlying mechanics (why
 * get/put are O(1), how eviction actually works) are visible and
 * explainable rather than hidden behind a library call, since that's
 * exactly the kind of question this project is meant to demonstrate
 * an answer to.
 *
 * Thread safety: methods are synchronized. Under heavy concurrent load
 * this is a real bottleneck (every request serializes on one lock) -
 * a production version would shard the cache across multiple locks/
 * segments, the same way ConcurrentHashMap internally partitions its
 * buckets. Noted here rather than silently glossed over.
 */
public class LruCache<K, V> {

    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev, next;
        Node(K key, V value) { this.key = key; this.value = value; }
    }

    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head; // sentinel: head.next = most recently used
    private final Node<K, V> tail; // sentinel: tail.prev = least recently used

    public LruCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    public synchronized V get(K key) {
        Node<K, V> node = map.get(key);
        if (node == null) return null;
        moveToFront(node);
        return node.value;
    }

    public synchronized void put(K key, V value) {
        Node<K, V> existing = map.get(key);
        if (existing != null) {
            existing.value = value;
            moveToFront(existing);
            return;
        }

        if (map.size() >= capacity) {
            evictLeastRecentlyUsed();
        }

        Node<K, V> node = new Node<>(key, value);
        map.put(key, node);
        addToFront(node);
    }

    public synchronized void invalidate(K key) {
        Node<K, V> node = map.remove(key);
        if (node != null) unlink(node);
    }

    public synchronized int size() {
        return map.size();
    }

    private void evictLeastRecentlyUsed() {
        Node<K, V> lru = tail.prev;
        if (lru == head) return; // cache is empty
        unlink(lru);
        map.remove(lru.key);
    }

    private void moveToFront(Node<K, V> node) {
        unlink(node);
        addToFront(node);
    }

    private void addToFront(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void unlink(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
}
