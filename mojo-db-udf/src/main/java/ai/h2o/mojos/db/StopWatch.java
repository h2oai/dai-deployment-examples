package ai.h2o.mojos.db;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static ai.h2o.mojos.db.Utils.f;

public class StopWatch {
  final private List<Lap> laps = new LinkedList<>();
  private Lap activeLap;

  class Lap implements AutoCloseable {
    private final String name;
    private long startTime;
    private long endTime;

    private Lap(String name) {
      this.name = name;
    }

    Lap start() {
      startTime = System.nanoTime();
      return this;
    }

    Lap stop() {
      endTime = System.nanoTime();
      return this;
    }

    @Override
    public void close() {
      stopLap(this);
    }

    @Override
    public String toString() {
      return f("time(%s)=%s", name, StopWatch.toString(endTime-startTime));
    }
  }

  Lap startLap(String name) {
    return (activeLap = new Lap(name).start());
  }

  public void stopLap() {
    if (activeLap != null) {
      stopLap(activeLap);
      activeLap = null;
    }
  }

  private void stopLap(Lap lap) {
    lap.stop();
    laps.add(lap);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Lap l : laps) {
      sb.append(l.toString()).append(',');
    }
    return sb.toString();
  }

  public static String toString(long nanosec) {
    final long hr = TimeUnit.NANOSECONDS.toHours (nanosec);nanosec -= TimeUnit.HOURS.toNanos(hr);
    final long min = TimeUnit.NANOSECONDS.toMinutes(nanosec); nanosec -= TimeUnit.MINUTES.toNanos(min);
    final long sec = TimeUnit.NANOSECONDS.toSeconds(nanosec); nanosec -= TimeUnit.SECONDS.toNanos(sec);
    final long ms = TimeUnit.NANOSECONDS.toMillis(nanosec); nanosec -= TimeUnit.MILLISECONDS.toNanos(ms);
    final long us = TimeUnit.NANOSECONDS.toMicros(nanosec); nanosec -= TimeUnit.MICROSECONDS.toNanos(us);
    if( hr != 0 ) return String.format("%2d:%02d:%02d.%03d", hr, min, sec, ms);
    if( min != 0 ) return String.format("%2d min %2d.%03d sec", min, sec, ms);
    if( sec != 0 ) return String.format("%2d.%03d sec", sec, ms);
    if( ms != 0 ) return String.format("%03d.%03d msec", ms, us);
    if( us != 0 ) return String.format("%03d.%03d usec", us, nanosec);
    return String.format("%3d nanosec", nanosec);
  }
}
