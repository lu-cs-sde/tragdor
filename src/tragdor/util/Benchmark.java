package tragdor.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

import codeprober.util.BenchmarkTimer;

public class Benchmark {
	private static class Timer {
		public long accumulation;
		public int numSamples;

		public Timer() {
		}

		public Timer(int numSamples, long accumulation) {
			this.numSamples = numSamples;
			this.accumulation = accumulation;
		}

		void add(long duration) {
			accumulation += duration;
			++numSamples;
		}
	}

	private static List<Long> ticks = new ArrayList<>();

	public interface TickTockErr<T> {
		T get() throws Exception;
	}

	public interface TickTockErrv {
		void run() throws Exception;
	}

	private static Map<String, Timer> timers = new HashMap<>();

	private static void tick() {
		ticks.add(System.currentTimeMillis());
	}

	private static void tock(String label) {
		if (ticks.isEmpty()) {
			System.err.println("Inconsistent tick/tock calls");
			return;
		}
		final long duration = System.currentTimeMillis() - ticks.remove(ticks.size() - 1);
		if (!timers.containsKey(label)) {
			timers.put(label, new Timer());
		}
		timers.get(label).add(duration);
//		System.out.println("Finished '" + label + "' in " + duration + "ms");
	}

	public static <T> T tickTock(String label, Supplier<T> body) {
		tick();
		try {
			return body.get();
		} finally {
			tock(label);
		}
	}

	public static void tickTockv(String label, Runnable body) {
		tick();
		try {
			body.run();
		} finally {
			tock(label);
		}
	}

	public static <T> T tickTockErr(String label, TickTockErr<T> body) throws Exception {
		tick();
		try {
			return body.get();
		} finally {
			tock(label);
		}
	}

	public static void tickTockErrv(String label, TickTockErrv body) throws Exception {
		tick();
		try {
			body.run();
		} finally {
			tock(label);
		}
	}

	public static void report(String[] args) {
		timers.put("TracingBuilder:EncodeValue", new Timer( //
				BenchmarkTimer.TRACE_ENCODE_RESPONSE_VALUE.getNumHits(), //
				BenchmarkTimer.TRACE_ENCODE_RESPONSE_VALUE.getAccumulatedNano() / 1_000_000L));
		timers.put("TracingBuilder:CheckAttach", new Timer( //
				BenchmarkTimer.TRACE_CHECK_NODE_ATTACHMENT.getNumHits(), //
				BenchmarkTimer.TRACE_CHECK_NODE_ATTACHMENT.getAccumulatedNano() / 1_000_000L));
		timers.put("TracingBuilder:CheckParams", new Timer( //
				BenchmarkTimer.TRACE_CHECK_PARAMETERS.getNumHits(), //
				BenchmarkTimer.TRACE_CHECK_PARAMETERS.getAccumulatedNano() / 1_000_000L));
		timers.put("CreateLocator", new Timer( //
				BenchmarkTimer.CREATE_LOCATOR.getNumHits(), //
				BenchmarkTimer.CREATE_LOCATOR.getAccumulatedNano() / 1_000_000L));
		timers.put("CreateLocator:NTA", new Timer( //
				BenchmarkTimer.CREATE_LOCATOR_NTA_STEP.getNumHits(), //
				BenchmarkTimer.CREATE_LOCATOR_NTA_STEP.getAccumulatedNano() / 1_000_000L));

		System.out.printf("Dumping timing info for %s..%n", Arrays.toString(args));
		System.out.printf("|                            Label | Total Time (ms) | # Samples | Time/Sample (ms) |%n");
		System.out.print("|-");
		for (int i = 0; i < 32; ++i) {
			System.out.print("-");
		}
		System.out.print("-|");
		for (int i = 0; i < 16 + 1; ++i) {
			System.out.print("-");
		}
		System.out.print("|");
		for (int i = 0; i < 8 + 3; ++i) {
			System.out.print("-");
		}
		System.out.print("|");
		for (int i = 0; i < 8 + 10; ++i) {
			System.out.print("-");
		}
		System.out.println("|");

		final ArrayList<Entry<String, Timer>> timerEntries = new ArrayList<>(timers.entrySet());
		timerEntries.sort((a, b) -> (int) (b.getValue().accumulation - a.getValue().accumulation));
		for (Entry<String, Timer> ent : timerEntries) {
			final Timer t = ent.getValue();
			System.out.printf("| %32s | %15d | %9d | %16s |%n", ent.getKey(), t.accumulation, t.numSamples,
					t.numSamples == 0 ? "" : ("" + (t.accumulation / t.numSamples)));
		}
	}
}
