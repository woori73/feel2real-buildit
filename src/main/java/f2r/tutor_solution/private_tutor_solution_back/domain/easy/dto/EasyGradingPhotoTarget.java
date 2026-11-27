package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.time.LocalDate;

/** The fields needed to confirm a photo destination; no scores, feedback or image data. */
public record EasyGradingPhotoTarget(Long gradingId, Long studentId, String studentName, LocalDate gradedOn,
                                     String subjectArea, String material, String status, long photoCount) {}
