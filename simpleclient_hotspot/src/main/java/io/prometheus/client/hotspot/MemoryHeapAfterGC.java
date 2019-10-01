package io.prometheus.client.hotspot;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import io.prometheus.client.Collector;

import io.prometheus.client.Gauge;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

import java.util.List;
import java.util.Map;

@SuppressWarnings("restriction")
public class MemoryHeapAfterGC extends Collector {
  private final Gauge heapAfterGC = Gauge.build()
          .name("jvm_memory_pool_gcbytes_bytes_total")
          .help("Total bytes in pool right after a GC. Only updated after GC, not continuously.")
          .labelNames("pool", "gc")
          .create();

  public MemoryHeapAfterGC() {
	AllocationGuageNotificationListener listener = new AllocationGuageNotificationListener(heapAfterGC);
    for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
  ///   	garbageCollectorMXBean.getName();
      ((NotificationEmitter) garbageCollectorMXBean).addNotificationListener(listener, null, null);
    }
  }

  @Override
  public List<MetricFamilySamples> collect() {
    return heapAfterGC.collect();
  }

  static class AllocationGuageNotificationListener implements NotificationListener {
  
    private final Gauge gauge;

    AllocationGuageNotificationListener(Gauge gauge) {
      this.gauge = gauge;
    }

    
    
    //----- fix below
    @Override
    public synchronized void handleNotification(Notification notification, Object handback) {
      GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
      GcInfo gcInfo = info.getGcInfo();
      String collectorName = info.getGcName();

      Map<String, MemoryUsage> memoryUsageAfterGc = gcInfo.getMemoryUsageAfterGc();
      for (Map.Entry<String, MemoryUsage> entry : memoryUsageAfterGc.entrySet()) {
        String memoryPool = entry.getKey();
        
        long after = entry.getValue().getUsed();
        gauge.labels(memoryPool, collectorName).set(after);
      }
    }

   }
}
