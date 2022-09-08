/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Registers measurements that generate metrics about JVM memory and memory pools.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * MemoryPools.registerObservers(GlobalOpenTelemetry.get());
 * }</pre>
 *
 * <p>Example metrics being exported: Component
 *
 * <pre>
 *   process.runtime.jvm.memory.pool.init{type="heap",pool="G1 Eden Space"} 1000000
 *   process.runtime.jvm.memory.pool.usage{type="heap",pool="G1 Eden Space"} 2500000
 *   process.runtime.jvm.memory.pool.committed{type="heap",pool="G1 Eden Space"} 3000000
 *   process.runtime.jvm.memory.pool.max{type="heap",pool="G1 Eden Space"} 4000000
 *   process.runtime.jvm.memory.pool.init{type="non_heap",pool="Metaspace"} 200
 *   process.runtime.jvm.memory.pool.usage{type="non_heap",pool="Metaspace"} 400
 *   process.runtime.jvm.memory.pool.committed{type="non_heap",pool="Metaspace"} 500
 *   process.runtime.jvm.memory.init{type="heap"} 1000000
 *   process.runtime.jvm.memory.usage{type="heap"} 2500000
 *   process.runtime.jvm.memory.committed{type="heap"} 3000000
 *   process.runtime.jvm.memory.max{type="heap"} 4000000
 *   process.runtime.jvm.memory.init{type="non_heap"} 200
 *   process.runtime.jvm.memory.usage{type="non_heap"} 400
 *   process.runtime.jvm.memory.committed{type="non_heap"} 500
 * </pre>
 */
public final class MemoryPools {

  private static final AttributeKey<String> TYPE_KEY = AttributeKey.stringKey("type");
  private static final AttributeKey<String> POOL_KEY = AttributeKey.stringKey("pool");

  private static final String HEAP = "heap";
  private static final String NON_HEAP = "non_heap";

  /** Register observers for java runtime memory metrics. */
  public static void registerObservers(OpenTelemetry openTelemetry) {
    List<MemoryPoolMXBean> poolBeans = ManagementFactory.getMemoryPoolMXBeans();
    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    Meter meter = openTelemetry.getMeter("io.opentelemetry.runtime-metrics");

    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.pool.usage")
        .setDescription("Measure of memory pool used")
        .setUnit("By")
        .buildWithCallback(callback(poolBeans, MemoryUsage::getUsed));

    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.pool.init")
        .setDescription("Measure of initial memory pool requested")
        .setUnit("By")
        .buildWithCallback(callback(poolBeans, MemoryUsage::getInit));

    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.pool.committed")
        .setDescription("Measure of memory pool committed")
        .setUnit("By")
        .buildWithCallback(callback(poolBeans, MemoryUsage::getCommitted));

    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.pool.limit")
        .setDescription("Measure of max obtainable memory pool")
        .setUnit("By")
        .buildWithCallback(callback(poolBeans, MemoryUsage::getMax));


    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.usage")
        .setDescription("Measure of memory used")
        .setUnit("By")
        .buildWithCallback(callback(memoryMXBean, MemoryUsage::getUsed));

    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.init")
        .setDescription("Measure of initial memory requested")
        .setUnit("By")
        .buildWithCallback(callback(memoryMXBean, MemoryUsage::getInit));

    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.committed")
        .setDescription("Measure of memory committed")
        .setUnit("By")
        .buildWithCallback(callback(memoryMXBean, MemoryUsage::getCommitted));

    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.limit")
        .setDescription("Measure of max obtainable memory")
        .setUnit("By")
        .buildWithCallback(callback(memoryMXBean, MemoryUsage::getMax));
  }

  // Visible for testing
  static Consumer<ObservableLongMeasurement> callback(
      List<MemoryPoolMXBean> poolBeans, Function<MemoryUsage, Long> extractor) {
    List<Attributes> attributeSets = new ArrayList<>(poolBeans.size());
    for (MemoryPoolMXBean pool : poolBeans) {
      attributeSets.add(
          Attributes.builder()
              .put(POOL_KEY, pool.getName())
              .put(TYPE_KEY, memoryType(pool.getType()))
              .build());
    }

    return measurement -> {
      for (int i = 0; i < poolBeans.size(); i++) {
        Attributes attributes = attributeSets.get(i);
        long value = extractor.apply(poolBeans.get(i).getUsage());
        if (value != -1) {
          measurement.record(value, attributes);
        }
      }
    };
  }

  // Visible for testing
  static Consumer<ObservableLongMeasurement> callback(
      MemoryMXBean memoryMXBean, Function<MemoryUsage, Long> extractor) {
    Attributes heapAttr = Attributes.builder().put(TYPE_KEY, HEAP).build();
    Attributes nonHeapAttr = Attributes.builder().put(TYPE_KEY, NON_HEAP).build();

    return measurement -> {
      Long heapValue = extractor.apply(memoryMXBean.getHeapMemoryUsage());
      if (heapValue != -1) {
        measurement.record(heapValue, heapAttr);
      }
      Long nonHeapValue = extractor.apply(memoryMXBean.getNonHeapMemoryUsage());
      if (heapValue != -1) {
        measurement.record(nonHeapValue, nonHeapAttr);
      }
    };
  }

  private static String memoryType(MemoryType memoryType) {
    switch (memoryType) {
      case HEAP:
        return HEAP;
      case NON_HEAP:
        return NON_HEAP;
    }
    return "unknown";
  }

  private MemoryPools() {}
}
