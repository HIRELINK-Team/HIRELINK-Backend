package com.hirelink.domain.recommendation.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class WorkTimeNormalizer {
	private static final Set<String> DAYS = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

	private WorkTimeNormalizer() {
	}

	static Map<String, List<MinuteRange>> normalize(List<RecommendationTimeRange> ranges) {
		Map<String, List<MinuteRange>> grouped = new HashMap<>();
		for (RecommendationTimeRange range : ranges) {
			if (!isValid(range)) {
				throw new IllegalArgumentException("근무시간 구간이 유효하지 않습니다.");
			}
			grouped.computeIfAbsent(range.dayOfWeek(), ignored -> new ArrayList<>())
					.add(new MinuteRange(toMinute(range.startTime(), false), toMinute(range.endTime(), true)));
		}
		Map<String, List<MinuteRange>> normalized = new HashMap<>();
		grouped.forEach((day, values) -> normalized.put(day, merge(values)));
		return normalized;
	}

	static boolean allValid(List<RecommendationTimeRange> ranges) {
		return ranges != null && !ranges.isEmpty() && ranges.stream().allMatch(WorkTimeNormalizer::isValid);
	}

	static long totalMinutes(Map<String, List<MinuteRange>> ranges) {
		return ranges.values().stream().flatMap(List::stream).mapToLong(MinuteRange::length).sum();
	}

	private static boolean isValid(RecommendationTimeRange range) {
		if (range == null || !DAYS.contains(range.dayOfWeek())
				|| range.startTime() == null || range.endTime() == null) {
			return false;
		}
		int start = toMinute(range.startTime(), false);
		int end = toMinute(range.endTime(), true);
		return start < end;
	}

	private static int toMinute(LocalTime time, boolean end) {
		if (end && time.equals(LocalTime.MIDNIGHT)) {
			return 24 * 60;
		}
		return time.toSecondOfDay() / 60;
	}

	private static List<MinuteRange> merge(List<MinuteRange> values) {
		List<MinuteRange> sorted = values.stream()
				.sorted(Comparator.comparingInt(MinuteRange::start).thenComparingInt(MinuteRange::end))
				.toList();
		List<MinuteRange> merged = new ArrayList<>();
		for (MinuteRange current : sorted) {
			if (merged.isEmpty() || current.start() > merged.get(merged.size() - 1).end()) {
				merged.add(current);
				continue;
			}
			MinuteRange previous = merged.remove(merged.size() - 1);
			merged.add(new MinuteRange(previous.start(), Math.max(previous.end(), current.end())));
		}
		return List.copyOf(merged);
	}

	record MinuteRange(int start, int end) {
		long length() {
			return end - start;
		}
	}
}
