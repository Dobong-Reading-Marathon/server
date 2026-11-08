package com.dobong.dobong.admin.submission;

import com.dobong.dobong.admin.submission.AdminSubmissionDtos.InboxResponse;
import com.dobong.dobong.admin.submission.AdminSubmissionDtos.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSubmissionService {

    private static final Set<String> STATUSES =
            Set.of("SUBMITTED", "APPROVED", "REVISION_REQUESTED");

    private final AdminSubmissionRepository repository;

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
                        r.getStatus(), r.getSubmittedAt()))
                .toList();

        return new InboxResponse(repository.countPending(), items);
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