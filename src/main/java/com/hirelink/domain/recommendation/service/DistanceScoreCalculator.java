package com.hirelink.domain.recommendation.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public class DistanceScoreCalculator {

	public DistanceScore calculate(BigDecimal studentLatitude, BigDecimal studentLongitude, Integer radiusM,
			BigDecimal workplaceLatitude, BigDecimal workplaceLongitude) {
		validateCoordinates(studentLatitude, studentLongitude);
		validateCoordinates(workplaceLatitude, workplaceLongitude);
		if (radiusM == null || radiusM <= 0) {
			throw new IllegalArgumentException("희망 근무 반경은 1m 이상이어야 합니다.");
		}
		double distanceKm = haversine(studentLatitude.doubleValue(), studentLongitude.doubleValue(),
				workplaceLatitude.doubleValue(), workplaceLongitude.doubleValue());
		double radiusKm = radiusM / 1000.0;
		double score = distanceKm <= radiusKm
				? 100.0
				: Math.max(0.0, 100.0 * (1.0 - (distanceKm - radiusKm) / RecommendationPolicyV1.DISTANCE_DECAY_KM));
		return new DistanceScore(RecommendationScoreSupport.clamp(BigDecimal.valueOf(score)), distanceKm);
	}

	static boolean hasValidCoordinates(BigDecimal latitude, BigDecimal longitude) {
		return latitude != null && longitude != null
				&& latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
				&& latitude.compareTo(BigDecimal.valueOf(90)) <= 0
				&& longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
				&& longitude.compareTo(BigDecimal.valueOf(180)) <= 0;
	}

	private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
		if (!hasValidCoordinates(latitude, longitude)) {
			throw new IllegalArgumentException("위도 또는 경도가 유효하지 않습니다.");
		}
	}

	private double haversine(double firstLatitude, double firstLongitude, double secondLatitude, double secondLongitude) {
		double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
		double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
		double firstLatitudeRad = Math.toRadians(firstLatitude);
		double secondLatitudeRad = Math.toRadians(secondLatitude);
		double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
				+ Math.cos(firstLatitudeRad) * Math.cos(secondLatitudeRad) * Math.pow(Math.sin(longitudeDelta / 2), 2);
		double boundedHaversine = Math.min(1.0, haversine);
		return RecommendationPolicyV1.EARTH_RADIUS_KM * 2
				* Math.atan2(Math.sqrt(boundedHaversine), Math.sqrt(1 - boundedHaversine));
	}

	public record DistanceScore(BigDecimal score, double distanceKm) {
	}
}
