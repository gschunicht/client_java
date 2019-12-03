package io.prometheus.client.hotspot;

import io.prometheus.client.Collector;
import io.prometheus.client.Gauge;
import io.prometheus.client.GaugeMetricFamily;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exports metrics about JVM memory areas.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 *   new MemoryPoolsExports().register();
 * }
 * </pre>
 * Example metrics being exported:
 * <pre>
 *   jvm_memory_bytes_used{area="heap"} 2000000
 *   jvm_memory_bytes_committed{area="nonheap"} 200000
 *   jvm_memory_bytes_max{area="nonheap"} 2000000
 *   jvm_memory_pool_bytes_used{pool="PS Eden Space"} 2000
 * </pre>
 */
public class MemoryPoolsExports extends Collector {
  private final MemoryMXBean memoryBean;
  private final List<MemoryPoolMXBean> poolBeans;

  private static boolean initialized = false;
  private static  Gauge used;
  private static GaugeMetricFamily committed;
  private static GaugeMetricFamily max;
  private static GaugeMetricFamily init;
  private static GaugeMetricFamily poolUsed;
  private static GaugeMetricFamily poolCommitted;
  private static GaugeMetricFamily poolMax;
  private static GaugeMetricFamily poolInit;
  
  private void initMetrics() {
	  
	  if (! initialized) {
	    used =   Gauge.build().name("jvm_memory_bytes_used").help("Used bytes of a given JVM memory area.").labelNames("area").register();
 
		    
	    
	    committed = new GaugeMetricFamily(
	            "jvm_memory_bytes_committed",
	            "Committed (bytes) of a given JVM memory area.",
	            Collections.singletonList("area"));
	    max = new GaugeMetricFamily(
	            "jvm_memory_bytes_max",
	            "Max (bytes) of a given JVM memory area.",
	            Collections.singletonList("area"));

	    init = new GaugeMetricFamily(
	            "jvm_memory_bytes_init",
	            "Initial bytes of a given JVM memory area.",
	            Collections.singletonList("area"));
	 
	    poolUsed = new GaugeMetricFamily(
	            "jvm_memory_pool_bytes_used",
	            "Used bytes of a given JVM memory pool.",
	            Collections.singletonList("pool"));
 
	    poolCommitted = new GaugeMetricFamily(
	            "jvm_memory_pool_bytes_committed",
	            "Committed bytes of a given JVM memory pool.",
	            Collections.singletonList("pool"));
	    poolMax = new GaugeMetricFamily(
	            "jvm_memory_pool_bytes_max",
	            "Max bytes of a given JVM memory pool.",
	            Collections.singletonList("pool"));
	    poolInit = new GaugeMetricFamily(
	            "jvm_memory_pool_bytes_init",
	            "Initial bytes of a given JVM memory pool.",
	            Collections.singletonList("pool"));
	    initialized = true;
	  }

  }
  
  public MemoryPoolsExports() {
    this(
        ManagementFactory.getMemoryMXBean(),
        ManagementFactory.getMemoryPoolMXBeans());
   }

  public MemoryPoolsExports(MemoryMXBean memoryBean,
                             List<MemoryPoolMXBean> poolBeans) {
    this.memoryBean = memoryBean;
    this.poolBeans = poolBeans;
    initMetrics();
  }

  void addMemoryAreaMetrics(List<MetricFamilySamples> sampleFamilies) {
    MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
    MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
     
    
    //used.addMetric(Collections.singletonList("heap"), heapUsage.getUsed());
    //used.addMetric(Collections.singletonList("nonheap"), nonHeapUsage.getUsed());
    // sampleFamilies.add(used);
    
    used.labels("heap").set(heapUsage.getUsed());
    used.labels("nonheap").set(nonHeapUsage.getUsed());
    
    committed.setMetric(Collections.singletonList("heap"), heapUsage.getCommitted());
    committed.setMetric(Collections.singletonList("nonheap"), nonHeapUsage.getCommitted());
    
   // committed.addMetric(Collections.singletonList("heap"), heapUsage.getCommitted());
  //  committed.addMetric(Collections.singletonList("nonheap"), nonHeapUsage.getCommitted());
    sampleFamilies.add(committed);

    max.setMetric(Collections.singletonList("heap"), heapUsage.getMax());
    max.setMetric(Collections.singletonList("nonheap"), nonHeapUsage.getMax());
    sampleFamilies.add(max);

    init.setMetric(Collections.singletonList("heap"), heapUsage.getInit());
    init.setMetric(Collections.singletonList("nonheap"), nonHeapUsage.getInit());
    sampleFamilies.add(init);
  }

  void addMemoryPoolMetrics(List<MetricFamilySamples> sampleFamilies) {

    sampleFamilies.add(poolUsed);
    sampleFamilies.add(poolCommitted);
    sampleFamilies.add(poolMax);
    sampleFamilies.add(poolInit);
    for (final MemoryPoolMXBean pool : poolBeans) {
      MemoryUsage poolUsage = pool.getUsage();
      poolUsed.setMetric(
          Collections.singletonList(pool.getName()),
          poolUsage.getUsed());
      poolCommitted.setMetric(
          Collections.singletonList(pool.getName()),
          poolUsage.getCommitted());
      poolMax.setMetric(
          Collections.singletonList(pool.getName()),
          poolUsage.getMax());
      poolInit.setMetric(
          Collections.singletonList(pool.getName()),
          poolUsage.getInit());
    }
  }

  public List<MetricFamilySamples> collect() {
    List<MetricFamilySamples> mfs = new ArrayList<MetricFamilySamples>();
    addMemoryAreaMetrics(mfs);
    addMemoryPoolMetrics(mfs);
    return mfs;
  }
}
