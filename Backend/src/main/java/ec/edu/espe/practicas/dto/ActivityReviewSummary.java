package ec.edu.espe.practicas.dto;

import java.time.LocalDateTime;

public record ActivityReviewSummary(Long activityId, String observation, LocalDateTime reviewedAt) { }
