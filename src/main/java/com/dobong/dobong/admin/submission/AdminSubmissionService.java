package com.dobong.dobong.admin.submission;

import com.dobong.dobong.admin.submission.AdminSubmissionDtos.Detail;
import com.dobong.dobong.admin.submission.AdminSubmissionDtos.InboxResponse;
import com.dobong.dobong.admin.submission.AdminSubmissionDtos.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSubmissionService {

    private static final Set<String> STATUSES =
            Set.of("SUBMITTED", "APPROVED", "REVISION_REQUESTED");

    private final AdminSubmissionRepository repository;

    // [02 추가] 처리 결과
    public enum Result { OK, NOT_FOUND, INVALID_STATE }

    /** 잘못된 상태값이면 false (컨트롤러가 JSON 오류로 바꿈) */
    public boolean isValidStatus(String status) {
        return status == null || status.isBlank() || STATUSES.contains(status);
    }

    public InboxResponse inbox(String status, String keyword) {
        String s = (status == null || status.isBlank()) ? null : status;
        String pattern = toPattern(keyword);

        var items = repository.search(s, pattern).stream()
                .map(r -> new Item(r.getId(), r.getParticipantName(), r.getBookTitle(),
                        r.getStartPage(), r.getEndPage(), r.getReadDate(),
                        r.getStatus(), r.getSubmittedAt(), r.getReview(), r.getReviewLength()))
                .toList();

        return new InboxResponse(repository.countPending(), items);
    }

    // 기록 한 건 상세. 없거나 접수함에 없는 상태(작성중, 저장)면 빈 값
    public Optional<Detail> detail(Long id) {
        return repository.findDetail(id)
                .map(r -> new Detail(r.getId(), r.getEnrollmentId(), r.getParticipantName(),
                        r.getBookTitle(), r.getAuthor(), r.getStartPage(), r.getEndPage(),
                        r.getReadDate(), r.getStatus(), r.getQuote(), r.getReview(),
                        r.getReviewLength(), r.getLibrarianComment(), r.getSubmittedAt(),
                        r.getReviewedAt()));
    }

    // [02 추가] 승인
    @Transactional
    public Result approve(Long id, Long adminId) {
        return toResult(id, repository.approve(id, adminId));
    }

    // [02 추가] 보완요청(반려)
    @Transactional
    public Result requestRevision(Long id, Long adminId, String comment) {
        return toResult(id, repository.requestRevision(id, adminId, comment.trim()));
    }

    // [02 추가] 승인 취소 (심사 대기로 되돌림)
    @Transactional
    public Result cancelApproval(Long id) {
        return toResult(id, repository.cancelApproval(id));
    }

    // [02 추가] 1건 이상 바뀌면 성공, 0건이면 기록이 없는지 / 상태가 안 맞는지 구분
    private Result toResult(Long id, int updated) {
        if (updated > 0) {
            return Result.OK;
        }
        return repository.findStatus(id).isPresent() ? Result.INVALID_STATE : Result.NOT_FOUND;
    }

    /** 검색어를 %검색어% 형태로 바꾼다. %, _ 는 글자 그대로 검색되게 처리 */
    static String toPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String k = keyword.trim()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + k + "%";
    }
}