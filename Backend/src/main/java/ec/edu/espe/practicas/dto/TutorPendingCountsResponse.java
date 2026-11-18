package ec.edu.espe.practicas.dto;

import java.util.List;

public record TutorPendingCountsResponse(long total, List<TraineePendingCount> trainees) {
    public static TutorPendingCountsResponse from(List<TraineePendingCount> counts) {
        return new TutorPendingCountsResponse(
                counts.stream().mapToLong(TraineePendingCount::pendingActivitiesCount).sum(), counts);
    }
}
