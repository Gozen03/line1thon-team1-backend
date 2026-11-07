package com.line1thon.jisiknamu.domain.tree.service;

import com.line1thon.jisiknamu.domain.leaf.entity.Leaf;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.leaf.repository.LeafRepository;
import com.line1thon.jisiknamu.domain.leaf.repository.LeafRepository.TreeLeafStats;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSet;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetStatus;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.quiz.repository.QuizSetRepository;
import com.line1thon.jisiknamu.domain.study.repository.StudyRepository;
import com.line1thon.jisiknamu.domain.study.repository.StudyRepository.TreeLastStudied;
import com.line1thon.jisiknamu.domain.tree.dto.ForestResponse;
import com.line1thon.jisiknamu.domain.tree.dto.TreeCreateRequest;
import com.line1thon.jisiknamu.domain.tree.dto.TreeCreateResponse;
import com.line1thon.jisiknamu.domain.tree.dto.TreeDetailResponse;
import com.line1thon.jisiknamu.domain.tree.dto.TreeUpdateRequest;
import com.line1thon.jisiknamu.domain.tree.dto.TreeUpdateResponse;
import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;
import com.line1thon.jisiknamu.domain.tree.repository.TreeRepository;
import com.line1thon.jisiknamu.domain.user.entity.User;
import com.line1thon.jisiknamu.domain.user.repository.UserRepository;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.util.KstTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TreeService {

    private static final int FREE_TREE_LIMIT = 3;
    private static final int SUBJECT_NAME_MIN_LENGTH = 1;
    private static final int SUBJECT_NAME_MAX_LENGTH = 20;
    private static final List<QuizSetType> PENDING_QUIZ_SET_TYPES = List.of(QuizSetType.INITIAL, QuizSetType.RETRY);

    private final TreeRepository treeRepository;
    private final UserRepository userRepository;
    private final LeafRepository leafRepository;
    private final StudyRepository studyRepository;
    private final QuizSetRepository quizSetRepository;

    @Transactional(readOnly = true)
    public ForestResponse getForest(Long userId) {
        List<Tree> trees = treeRepository.findAllByUserIdOrderByIdAsc(userId);
        Map<Long, TreeLeafStats> leafStats = leafRepository
                .findStatsByUserId(userId, LeafGrade.REVIEWABLE, KstTime.now()).stream()
                .collect(Collectors.toMap(TreeLeafStats::getTreeId, Function.identity()));
        Map<Long, LocalDateTime> lastStudied = studyRepository.findLastStudiedByUserId(userId).stream()
                .collect(Collectors.toMap(TreeLastStudied::getTreeId, TreeLastStudied::getLastStudiedAt));

        List<ForestResponse.TreeCard> cards = trees.stream()
                .map(tree -> {
                    TreeLeafStats stats = leafStats.get(tree.getId());
                    return new ForestResponse.TreeCard(tree.getId(), tree.getSubjectName(), tree.getStudyLevel(),
                            tree.getShape(),
                            stats == null ? 0 : stats.getLeafCount(),
                            KstTime.toOffset(lastStudied.get(tree.getId())),
                            stats == null ? 0 : stats.getReviewPendingCount());
                })
                .toList();
        return new ForestResponse(FREE_TREE_LIMIT, trees.size(), trees.size() < FREE_TREE_LIMIT, cards);
    }

    @Transactional
    public TreeCreateResponse createTree(Long userId, TreeCreateRequest request) {
        String subjectName = normalizeSubjectName(request.subjectName());
        if (request.studyLevel() == null || request.treeShape() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (request.treeShape() != TreeShape.BASIC) {
            throw new BusinessException(ErrorCode.TREE_SHAPE_LOCKED);
        }
        // 버튼을 두 번 눌러도 3그루를 넘지 않도록 회원 행을 잠그고 센다
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (treeRepository.countByUserId(userId) >= FREE_TREE_LIMIT) {
            throw new BusinessException(ErrorCode.TREE_LIMIT_EXCEEDED);
        }
        Tree tree = treeRepository.save(Tree.builder()
                .user(user)
                .subjectName(subjectName)
                .studyLevel(request.studyLevel())
                .shape(request.treeShape())
                .build());
        return new TreeCreateResponse(tree.getId(), tree.getSubjectName(), tree.getStudyLevel(), tree.getShape(),
                0, KstTime.toOffset(tree.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public TreeDetailResponse getTree(Long userId, Long treeId) {
        Tree tree = getOwnedTree(userId, treeId);
        LocalDateTime now = KstTime.now();
        List<Leaf> leaves = leafRepository.findAllWithStudyByTreeId(treeId);
        List<TreeDetailResponse.LeafItem> items = leaves.stream()
                .map(leaf -> new TreeDetailResponse.LeafItem(leaf.getId(), leaf.getPosition(), leaf.getLeafType(),
                        leaf.getOriginalType(), KstTime.toOffset(leaf.getStudy().getCreatedAt()),
                        leaf.isReviewed(), leaf.isReviewAvailable(now)))
                .toList();
        long reviewPendingCount = items.stream().filter(TreeDetailResponse.LeafItem::reviewAvailable).count();
        Long pendingQuizSetId = quizSetRepository
                .findFirstByStudyTreeIdAndStatusAndTypeInOrderByIdDesc(treeId, QuizSetStatus.CREATED, PENDING_QUIZ_SET_TYPES)
                .map(QuizSet::getId)
                .orElse(null);
        return new TreeDetailResponse(tree.getId(), tree.getSubjectName(), tree.getStudyLevel(), tree.getShape(),
                items.size(), reviewPendingCount, pendingQuizSetId, items);
    }

    @Transactional
    public TreeUpdateResponse updateTree(Long userId, Long treeId, TreeUpdateRequest request) {
        Tree tree = getOwnedTree(userId, treeId);
        if (request.subjectName() != null) {
            tree.changeSubjectName(normalizeSubjectName(request.subjectName()));
        }
        if (request.studyLevel() != null) {
            tree.changeStudyLevel(request.studyLevel());
        }
        return new TreeUpdateResponse(tree.getId(), tree.getSubjectName(), tree.getStudyLevel(), tree.getShape());
    }

    /**
     * 잎·공부 기록·문제 세트는 DB의 ON DELETE CASCADE로 함께 지워진다.
     * S3 사진 삭제는 S1에서 S3를 연동할 때 추가한다 (DB를 먼저 지우고, 사진 삭제 실패는 로그만 남긴다).
     */
    @Transactional
    public void deleteTree(Long userId, Long treeId) {
        treeRepository.delete(getOwnedTree(userId, treeId));
    }

    private Tree getOwnedTree(Long userId, Long treeId) {
        Tree tree = treeRepository.findById(treeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TREE_NOT_FOUND));
        if (!tree.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return tree;
    }

    private String normalizeSubjectName(String rawSubjectName) {
        if (rawSubjectName == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String subjectName = rawSubjectName.strip();
        int length = subjectName.codePointCount(0, subjectName.length());
        if (length < SUBJECT_NAME_MIN_LENGTH || length > SUBJECT_NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return subjectName;
    }
}
