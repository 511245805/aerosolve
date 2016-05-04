package com.airbnb.aerosolve.core.models;

import com.airbnb.aerosolve.core.NDTreeMap;
import com.airbnb.aerosolve.core.NDTreeNode;
import com.airbnb.aerosolve.core.util.Util;
import com.google.common.base.Optional;
import lombok.Getter;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/*
  N-Dimensional KDTreeModel.
 */
public class NDTreeModel implements Serializable {
  private static final long serialVersionUID = -2884260218927875615L;
  private static final Logger log = LoggerFactory.getLogger(NDTreeModel.class);
  public static final int LEAF = -1;
  @Getter
  private final NDTreeNode[] nodes;

  @Getter
  private final int dimension;

  public NDTreeModel(NDTreeNode[] nodes) {
    this.nodes = nodes;
    int max = 0;
    for (NDTreeNode node : nodes) {
      max = Math.max(max, node.axisIndex);
    }
    dimension = max + 1;
  }

  public NDTreeModel(List<NDTreeNode> nodeList) {
    this(nodeList.toArray(new NDTreeNode[nodeList.size()]));
  }

  public int leaf(float ... coordinates) {
    if (nodes == null || nodes.length == 0) return -1;
    return binarySearch(nodes, coordinates, 0);
  }

  // Returns the indice of leaf containing the point.
  public <T extends Number> int leaf(List<T> coordinates) {
    if (nodes == null || nodes.length == 0) return -1;
    return binarySearch(nodes, coordinates, 0);
  }

  public NDTreeNode getNode(int id) {
    return nodes[id];
  }

  // Returns the indices of nodes traversed to get to the leaf containing the point.
  public List<Integer> query(List<Float> coordinates) {
    if (nodes == null) return Collections.EMPTY_LIST;
    return query(nodes, coordinates, 0);
  }

  public List<Integer> query(float ... coordinates) {
    if (nodes == null) return Collections.EMPTY_LIST;
    return query(nodes, coordinates, 0);
  }

  // Returns the indices of all node overlapping the box
  public List<Integer> queryBox(List<Double> min, List<Double> max) {
    if (nodes == null) return Collections.EMPTY_LIST;
    List<Integer> idx = new ArrayList<>();
    assert (min.size() == max.size());

    Stack<Integer> stack = new Stack<Integer>();
    stack.push(0);
    while (!stack.isEmpty()) {
      int currIdx = stack.pop();
      idx.add(currIdx);
      NDTreeNode node = nodes[currIdx];
      int index = node.axisIndex;
      if (index > LEAF) {
        if (min.get(index) < node.splitValue) {
          stack.push(node.leftChild);
        }
        if (max.get(index) >= node.splitValue) {
          stack.push(node.rightChild);
        }
      }
    }
    return idx;
  }
  
  public static Optional<NDTreeModel> readFromGzippedStream(InputStream inputStream) {
    List<NDTreeNode> nodes = Util.readFromGzippedStream(NDTreeNode.class, inputStream);
    if (!nodes.isEmpty()) {
      return Optional.of(new NDTreeModel(nodes));
    } else {
      return Optional.absent();
    }
  }

  public static Optional<NDTreeModel> readFromGzippedResource(String name) {
    InputStream inputStream = java.lang.ClassLoader.getSystemResourceAsStream(name);
    Optional<NDTreeModel> modelOptional = readFromGzippedStream(inputStream);
    if (!modelOptional.isPresent()) {
      log.error("Could not load resource named " + name);
    }
    return modelOptional;
  }

  public static Optional<NDTreeModel> readFromGzippedBase64String(String encoded) {
    byte[] decoded = Base64.decodeBase64(encoded);
    InputStream stream = new ByteArrayInputStream(decoded);
    return readFromGzippedStream(stream);
  }

  private static int binarySearch(NDTreeNode[] a, Object key, int currIdx) {
    while (true) {
      int nextIdx = next(a[currIdx], key);
      if (nextIdx == -1) {
        return currIdx;
      } else {
        currIdx = nextIdx;
      }
    }
  }

  private static List<Integer> query(NDTreeNode[] a, Object key, int currIdx) {
    List<Integer> idx = new ArrayList<>();
    while (true) {
      idx.add(currIdx);
      int nextIdx = next(a[currIdx], key);
      if (nextIdx == -1) {
        return idx;
      } else {
        currIdx = nextIdx;
      }
    }
  }

  // TODO use https://github.com/facebook/swift
  private static int next(NDTreeNode node, Object key) {
    int index = node.axisIndex;
    if (index == NDTreeModel.LEAF) {
      // leaf
      return -1;
    } else {
      if (key instanceof float[]) {
        float[] coordinates = (float[]) key;
        return nextChild(node, coordinates[index]);
      } else if (key instanceof double[]) {
        double[] coordinates = (double[]) key;
        return nextChild(node, (float) coordinates[index]);
      } else if (key instanceof List) {
        Number x = (Number) ((List) key).get(index);
        return nextChild(node, x);
      } else {
        throw new RuntimeException("obj " + key + " not supported");
      }
    }
  }

  private static int nextChild(NDTreeNode node, float value) {
    if (value < node.splitValue) {
      return node.leftChild;
    } else {
      return node.rightChild;
    }
  }

  private static int nextChild(NDTreeNode node, Number value) {
    if (value.doubleValue() < node.splitValue) {
      return node.leftChild;
    } else {
      return node.rightChild;
    }
  }

  public static NDTreeMap load(BufferedReader reader) throws IOException {
    return Util.load(NDTreeMap.class, reader);
  }

  public static void save(BufferedWriter writer, NDTreeMap map) throws IOException {
    Util.save(writer, map);
    writer.flush();
  }
}
