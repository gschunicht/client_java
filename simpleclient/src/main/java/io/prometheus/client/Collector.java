
package io.prometheus.client;

import java.util.List;
import java.util.ListIterator;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * A collector for a set of metrics.
 * <p>
 * Normal users should use {@link Gauge}, {@link Counter}, {@link Summary} and {@link Histogram}.
 * <p>
 * Subclasssing Collector is for advanced uses, such as proxying metrics from another monitoring system.
 * It is it the responsibility of subclasses to ensure they produce valid metrics.
 * @see <a href="http://prometheus.io/docs/instrumenting/exposition_formats/">Exposition formats</a>.
 */
public abstract class Collector {
  /**
   * Return all of the metrics of this Collector.
   */
  public abstract List<MetricFamilySamples> collect();
  public enum Type {
    COUNTER,
    GAUGE,
    SUMMARY,
    HISTOGRAM,
    UNTYPED,
  }

  /**
   * A metric, and all of its samples.
   */
  static public class MetricFamilySamples {
    public final String name;
    public final Type type;
    public final String help;
    public final String escapedHelp;
    public final List<Sample> samples;

    public MetricFamilySamples(String name, Type type, String help, List<Sample> samples) {
      this.name = name;
      this.type = type;
      this.help = help;
      this.escapedHelp = getEscapeHelpString(help);
      this.samples = samples;
    }

    public MetricFamilySamples(String name, Type type, String help, String escapedHelp, List<Sample> samples) {
        this.name = name;
        this.type = type;
        this.help = help;
        this.escapedHelp = escapedHelp;
        this.samples = samples;
	}

	@Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MetricFamilySamples)) {
        return false;
      }
      MetricFamilySamples other = (MetricFamilySamples) obj;
      
      return other.name.equals(name) && other.type.equals(type)
        && other.help.equals(help) && other.samples.equals(samples) ;
    }

    @Override
    public int hashCode() {
      int hash = 1;
      hash = 37 * hash + name.hashCode();
      hash = 37 * hash + type.hashCode();
      hash = 37 * hash + help.hashCode();
      hash = 37 * hash + samples.hashCode();
      return hash;
    }
    
    public String getHelp()    {
  	  return help;
    }

    @Override
    public String toString() {
      return "Name: " + name + " Type: " + type + " Help: " + help + 
        " Samples: " + samples;
    }

  /**
   * A single Sample, with a unique name and set of labels.
   */
    public static class Sample {
      public final String name;
      public final List<String> labelNames;
      public final List<String> labelValues;  // Must have same length as labelNames.
      public final List<String> escapedLabelValues;
      public double value;
      public final Long timestampMs;  // It's an epoch format with milliseconds value included (this field is subject to change).

      public Sample(String name, List<String> labelNames, List<String> labelValues, double value, Long timestampMs) {
        this.name = name;
        this.labelNames = labelNames;
        this.labelValues = labelValues;
        this.escapedLabelValues =  fillEscapedLabelValues(this.labelValues);       
        this.value = value;
        this.timestampMs = timestampMs;
      }

      public Sample(String name, List<String> labelNames, List<String> labelValues, double value) {
    	  this(name, labelNames, labelValues, value, null);
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof Sample)) {
          return false;
        }
        Sample other = (Sample) obj;

        return other.name.equals(name) && other.labelNames.equals(labelNames)
          && other.labelValues.equals(labelValues) && other.value == value
          && ((timestampMs == null && other.timestampMs == null) || (other.timestampMs != null) && (other.timestampMs.equals(timestampMs)));
      }
      
      public void set(double value) {
    	  this.value = value;
      }

      @Override
      public int hashCode() {
        int hash = 1;
        hash = 37 * hash + name.hashCode();
        hash = 37 * hash + labelNames.hashCode();
        hash = 37 * hash + labelValues.hashCode();
        long d = Double.doubleToLongBits(value);
        hash = 37 * hash + (int)(d ^ (d >>> 32));
        if (timestampMs != null) {
          hash = 37 * hash + timestampMs.hashCode();
        }
        return hash;
      }

      @Override
      public String toString() {
        return "Name: " + name + " LabelNames: " + labelNames + " labelValues: " + labelValues +
          " Value: " + value + " TimestampMs: " + timestampMs;
      }
      

    }
  }

  /**
   * Register the Collector with the default registry.
   */
  public <T extends Collector> T register() {
    return register(CollectorRegistry.defaultRegistry);
  }

  /**
   * Register the Collector with the given registry.
   */
  public <T extends Collector> T register(CollectorRegistry registry) {
    registry.register(this);
    return (T)this;
  }

  public interface Describable {
    /**
     *  Provide a list of metric families this Collector is expected to return.
     *
     *  These should exclude the samples. This is used by the registry to
     *  detect collisions and duplicate registrations.
     *
     *  Usually custom collectors do not have to implement Describable. If
     *  Describable is not implemented and the CollectorRegistry was created
     *  with auto describe enabled (which is the case for the default registry)
     *  then {@link collect} will be called at registration time instead of
     *  describe. If this could cause problems, either implement a proper
     *  describe, or if that's not practical have describe return an empty
     *  list.
     */
    List<MetricFamilySamples> describe();
  }


  /* Various utility functions for implementing Collectors. */

  /**
   * Number of nanoseconds in a second.
   */
  public static final double NANOSECONDS_PER_SECOND = 1E9;
  /**
   * Number of milliseconds in a second.
   */
  public static final double MILLISECONDS_PER_SECOND = 1E3;

  private static final Pattern METRIC_NAME_RE = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");
  private static final Pattern METRIC_LABEL_NAME_RE = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
  private static final Pattern RESERVED_METRIC_LABEL_NAME_RE = Pattern.compile("__.*");

  /**
   * Throw an exception if the metric name is invalid.
   */
  protected static void checkMetricName(String name) {
    if (!METRIC_NAME_RE.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid metric name: " + name);
    }
  }

  private static final Pattern SANITIZE_PREFIX_PATTERN = Pattern.compile("^[^a-zA-Z_:]");
  private static final Pattern SANITIZE_BODY_PATTERN = Pattern.compile("[^a-zA-Z0-9_:]");

  /**
   * Sanitize metric name
   */
  public static String sanitizeMetricName(String metricName) {
    return SANITIZE_BODY_PATTERN.matcher(
            SANITIZE_PREFIX_PATTERN.matcher(metricName).replaceFirst("_")
    ).replaceAll("_");
  }
  
  /**
   * Escape strings
   */
  protected static String getEscapeHelpString(String helpText) {
	  
	  if (helpText != null)
	  {
		  if (helpText.length() == 0) {
			  return "";
		  }			  
		  
		  StringBuffer sb = new StringBuffer(helpText.length()+10);
		    for (int i = 0; i < helpText.length(); i++) {
		      char c = helpText.charAt(i);
		      switch (c) {
		        case '\\':
		          sb.append("\\\\");
		          break;
		        case '\n':
		          sb.append("\\n");
		          break;
		        default:
		          sb.append(c);
		      }
		    }
		    return sb.toString();
	  }
	  return null;
		  
  }
  
  private static List<String> fillEscapedLabelValues(List<String>labelValues)
  {
	  List<String> escapedLabelVals = new ArrayList<String>(labelValues.size());
	  
	  ListIterator<String> iter = labelValues.listIterator();
	  while (iter.hasNext()) 	  {
		  String s = iter.next();
		  if (s == null || s.length() == 0)		  {
			  escapedLabelVals.add("");
		  }
		  else {
			   escapedLabelVals.add(getEscapedLabelValue(s));
		  }
	  }
	  	  
	  return escapedLabelVals;
  }
  
  private static String getEscapedLabelValue( String s)   {
	    StringBuffer sb = new StringBuffer(s.length());
	    for (int i = 0; i < s.length(); i++) {
	      char c = s.charAt(i);
	      switch (c) {
	        case '\\':
	          sb.append("\\\\");
	          break;
	        case '\"':
	          sb.append("\\\"");
	          break;
	        case '\n':
	          sb.append("\\n");
	          break;
	        default:
	          sb.append(c);
	      }
	    }
	    return sb.toString();
	  }


  /**
   * Throw an exception if the metric label name is invalid.
   */
  protected static void checkMetricLabelName(String name) {
    if (!METRIC_LABEL_NAME_RE.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid metric label name: " + name);
    }
    if (RESERVED_METRIC_LABEL_NAME_RE.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid metric label name, reserved for internal use: " + name);
    }
  }

  /**
   * Convert a double to its string representation in Go.
   */
  public static String doubleToGoString(double d) {
    if (d == Double.POSITIVE_INFINITY) {
      return "+Inf";
    } 
    if (d == Double.NEGATIVE_INFINITY) {
      return "-Inf";
    }
    if (Double.isNaN(d)) {
      return "NaN";
    }
    return Double.toString(d);
  }
}
