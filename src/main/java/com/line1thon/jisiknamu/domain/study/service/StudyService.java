package com.line1thon.jisiknamu.domain.study.service;

import com.line1thon.jisiknamu.domain.leaf.entity.Leaf;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.leaf.entity.TopicStatus;
import com.line1thon.jisiknamu.domain.leaf.repository.LeafRepository;
import com.line1thon.jisiknamu.domain.leaf.repository.LeafTopicRepository;
import com.line1thon.jisiknamu.domain.quiz.dto.QuizSetResponse;
import com.line1thon.jisiknamu.domain.quiz.entity.Question;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSet;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetStatus;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.quiz.repository.QuestionRepository;
import com.line1thon.jisiknamu.domain.quiz.repository.QuizSetRepository;
import com.line1thon.jisiknamu.domain.quiz.service.QuizSetResponseMapper;
import com.line1thon.jisiknamu.domain.study.dto.StudyCreateRequest;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.domain.study.entity.Study;
import com.line1thon.jisiknamu.domain.study.entity.StudyImage;
import com.line1thon.jisiknamu.domain.study.repository.StudyImageRepository;
import com.line1thon.jisiknamu.domain.study.repository.StudyRepository;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import com.line1thon.jisiknamu.domain.tree.repository.TreeRepository;
import com.line1thon.jisiknamu.global.ai.AiClient;
import com.line1thon.jisiknamu.global.ai.GeneratedQuestion;
import com.line1thon.jisiknamu.global.ai.GeneratedQuiz;
import com.line1thon.jisiknamu.global.ai.QuizGenerateRequest;
import com.line1thon.jisiknamu.global.concurrency.InProgressGuard;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.storage.ImageCleaner;
import com.line1thon.jisiknamu.global.storage.ImageStorage;
import com.line1thon.jisiknamu.global.storage.ImagesDeletedEvent;
import com.line1thon.jisiknamu.global.util.JsonLists;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 호출은 최대 50초가 걸리므로 트랜잭션 밖에서 한다: 확인 → 사진 업로드·AI 호출 → 저장.
 */
@Service
@RequiredArgsConstructor
public class StudyService {

    private static final int MAX_IMAGES = 5;
    private static final int CONTENT_MIN_LENGTH = 20;
    private static final int CONTENT_MAX_LENGTH = 5000;
    private static final int MEMO_MAX_LENGTH = 200;
    private static final int INITIAL_GRADED_COUNT = 6;
    private static final List<QuizSetType> PENDING_QUIZ_SET_TYPES = List.of(QuizSetType.INITIAL, QuizSetType.RETRY);
    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp", "heic", "image/heic");

    private final TreeRepository treeRepository;
    private final LeafRepository leafRepository;
    private final LeafTopicRepository leafTopicRepository;
    private final StudyRepository studyRepository;
    private final StudyImageRepository studyImageRepository;
    private final QuizSetRepository quizSetRepository;
    private final QuestionRepository questionRepository;
    private final AiClient aiClient;
    private final ImageStorage imageStorage;
    private final ImageCleaner imageCleaner;
    private final InProgressGuard inProgressGuard;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final QuizSetResponseMapper responseMapper;
    private final JsonLists jsonLists;

    public QuizSetResponse createStudy(Long userId, Long treeId, StudyCreateRequest rawRequest) {
        TreeSnapshot tree = transactionTemplate.execute(status -> getOwnedTree(userId, treeId));
        StudyInput input = validate(rawRequest);
        // 다른 회원의 요청이 내 나무를 막지 못하도록 소유자 확인 뒤에 잠근다
        return inProgressGuard.run("tree:" + treeId, () -> {
            if (leafRepository.countByTreeId(treeId) >= Leaf.MAX_PER_TREE) {
                throw new BusinessException(ErrorCode.TREE_FULL);
            }
            return generateAndSave(treeId, tree, input);
        });
    }

    private QuizSetResponse generateAndSave(Long treeId, TreeSnapshot tree, StudyInput input) {
        List<String> uploadedKeys = new ArrayList<>();
        try {
            for (ImageInput image : input.images()) {
                uploadedKeys.add(upload(treeId, image));
            }
            GeneratedQuiz quiz = aiClient.generateQuiz(new QuizGenerateRequest(
                    QuizSetType.INITIAL, tree.subjectName(), tree.studyLevel(), input.inputType(),
                    uploadedKeys.stream().map(imageStorage::createReadUrl).toList(),
                    input.content(), input.memo(), List.of(), List.of()));
            return transactionTemplate.execute(status -> save(treeId, input, uploadedKeys, quiz));
        } catch (RuntimeException e) {
            // 에러가 나면 공부 기록을 저장하지 않고, 이미 올린 사진도 지운다
            imageCleaner.deleteQuietly(uploadedKeys);
            throw e;
        }
    }

    private QuizSetResponse save(Long treeId, StudyInput input, List<String> uploadedKeys, GeneratedQuiz quiz) {
        // AI를 기다리는 사이 나무가 삭제됐을 수 있다. 저장하는 동안 삭제되지 않도록 잠근다
        Tree tree = treeRepository.findByIdForUpdate(treeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TREE_NOT_FOUND));
        removePendingQuizSets(treeId);

        Study study = studyRepository.save(Study.builder()
                .tree(tree)
                .inputType(input.inputType())
                .textContent(input.content())
                .memo(input.memo())
                .extractedText(quiz.extractedText())
                .build());
        for (int i = 0; i < uploadedKeys.size(); i++) {
            studyImageRepository.save(StudyImage.builder()
                    .study(study).s3Key(uploadedKeys.get(i)).sortOrder(i + 1).build());
        }
        return saveQuizSet(study, QuizSetType.INITIAL, 1, quiz);
    }

    /**
     * S3. 저장해 둔 공부 내용으로 새 7문제를 받는다. 나무 단위로 잠가 S1과 동시에 돌지 않는다.
     */
    public QuizSetResponse createRetry(Long userId, Long studyId) {
        RetrySource source = transactionTemplate.execute(status -> getRetrySource(userId, studyId));
        return inProgressGuard.run("tree:" + source.treeId(), () -> {
            GeneratedQuiz quiz = aiClient.generateQuiz(new QuizGenerateRequest(
                    QuizSetType.RETRY, source.subjectName(), source.studyLevel(), InputType.TEXT, List.of(),
                    source.content(), source.memo(), source.weakTopics(), source.excludeQuestions()));
            return transactionTemplate.execute(status -> saveRetry(source.treeId(), studyId, quiz));
        });
    }

    private RetrySource getRetrySource(Long userId, Long studyId) {
        Study study = studyRepository.findById(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        if (!study.getTree().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Leaf leaf = getBudLeaf(studyId);
        String content = study.getTextContent() != null ? study.getTextContent() : study.getExtractedText();
        return new RetrySource(study.getTree().getId(), study.getSubjectName(), study.getStudyLevel(), content,
                study.getMemo(), leafTopicRepository.findTopics(leaf.getId(), TopicStatus.WEAK),
                questionRepository.findContents(studyId, PENDING_QUIZ_SET_TYPES));
    }

    private QuizSetResponse saveRetry(Long treeId, Long studyId, GeneratedQuiz quiz) {
        // AI를 기다리는 사이 나무가 삭제됐을 수 있다. 저장하는 동안 삭제되지 않도록 잠근다
        treeRepository.findByIdForUpdate(treeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        Study study = studyRepository.findById(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        getBudLeaf(studyId);
        removePendingQuizSets(treeId);
        int attemptNo = quizSetRepository.findMaxAttemptNo(studyId, QuizSetType.RETRY) + 1;
        return saveQuizSet(study, QuizSetType.RETRY, attemptNo, quiz);
    }

    private Leaf getBudLeaf(Long studyId) {
        return leafRepository.findByStudyId(studyId)
                .filter(leaf -> leaf.getLeafType() == LeafGrade.BUD)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_BUD_RESULT));
    }

    private QuizSetResponse saveQuizSet(Study study, QuizSetType type, int attemptNo, GeneratedQuiz quiz) {
        QuizSet quizSet = quizSetRepository.save(QuizSet.builder()
                .study(study).type(type).attemptNo(attemptNo).gradedCount(INITIAL_GRADED_COUNT).build());
        List<Question> questions = questionRepository.saveAll(quiz.questions().stream()
                .map(generated -> toQuestion(quizSet, generated))
                .toList());
        return responseMapper.toResponse(quizSet, questions);
    }

    /**
     * 나무마다 풀던 세트는 최대 1개다. 첫 풀이 세트를 지우면 그 공부 기록은 잎이 없어 볼 수 있는 곳이 없으므로
     * 공부 기록과 사진도 함께 지운다. 재도전 세트는 꽃봉오리 잎이 공부 기록을 쓰므로 세트만 지운다.
     */
    private void removePendingQuizSets(Long treeId) {
        List<String> imageKeys = new ArrayList<>();
        for (QuizSet pending : quizSetRepository.findAllByStudyTreeIdAndStatusAndTypeIn(
                treeId, QuizSetStatus.CREATED, PENDING_QUIZ_SET_TYPES)) {
            quizSetRepository.delete(pending);
            if (pending.getType() == QuizSetType.INITIAL) {
                imageKeys.addAll(studyImageRepository.findS3KeysByStudyId(pending.getStudy().getId()));
                studyRepository.delete(pending.getStudy());
            }
        }
        // 지운 세트의 attempt_no를 새 세트가 다시 쓸 수 있으니 먼저 DB에 반영한다 (UNIQUE 제약)
        quizSetRepository.flush();
        if (!imageKeys.isEmpty()) {
            eventPublisher.publishEvent(new ImagesDeletedEvent(imageKeys));
        }
    }

    private Question toQuestion(QuizSet quizSet, GeneratedQuestion generated) {
        return Question.builder()
                .quizSet(quizSet)
                .number(generated.number())
                .difficulty(generated.difficulty())
                .type(generated.questionType())
                .topic(generated.topic())
                .content(generated.content())
                .options(generated.questionType() == QuestionType.MULTIPLE_CHOICE ? jsonLists.toJson(generated.options()) : null)
                .answerIndex(generated.answerIndex())
                .explanation(generated.explanation())
                .modelAnswer(generated.modelAnswer())
                .gradingPoints(jsonLists.toJson(generated.gradingPoints()))
                .build();
    }

    private String upload(Long treeId, ImageInput image) {
        String key = "studies/%d/%s.%s".formatted(treeId, UUID.randomUUID(), image.extension());
        MultipartFile file = image.file();
        try (InputStream content = file.getInputStream()) {
            imageStorage.upload(key, content, file.getSize(), CONTENT_TYPE_BY_EXTENSION.get(image.extension()));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        return key;
    }

    private TreeSnapshot getOwnedTree(Long userId, Long treeId) {
        Tree tree = treeRepository.findById(treeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TREE_NOT_FOUND));
        if (!tree.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return new TreeSnapshot(tree.getSubjectName(), tree.getStudyLevel());
    }

    private StudyInput validate(StudyCreateRequest request) {
        if (request.inputType() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String memo = normalizeMemo(request.memo());
        if (request.inputType() == InputType.TEXT) {
            return new StudyInput(InputType.TEXT, List.of(), validateContent(request.content()), memo);
        }
        return new StudyInput(InputType.IMAGE, validateImages(request.images()), null, memo);
    }

    private String validateContent(String rawContent) {
        String content = rawContent == null ? "" : rawContent.strip();
        if (content.isEmpty()) {
            throw new BusinessException(ErrorCode.STUDY_INPUT_EMPTY);
        }
        int length = content.codePointCount(0, content.length());
        if (length < CONTENT_MIN_LENGTH) {
            throw new BusinessException(ErrorCode.CONTENT_TOO_SHORT);
        }
        if (length > CONTENT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return content;
    }

    private List<ImageInput> validateImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.STUDY_INPUT_EMPTY);
        }
        if (files.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        return files.stream().map(file -> new ImageInput(file, resolveExtension(file))).toList();
    }

    private String resolveExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (file.isEmpty() || !CONTENT_TYPE_BY_EXTENSION.containsKey(extension)) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        return extension;
    }

    private String normalizeMemo(String rawMemo) {
        if (rawMemo == null || rawMemo.isBlank()) {
            return null;
        }
        String memo = rawMemo.strip();
        if (memo.codePointCount(0, memo.length()) > MEMO_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return memo;
    }

    private record TreeSnapshot(String subjectName, StudyLevel studyLevel) {
    }

    private record RetrySource(Long treeId, String subjectName, StudyLevel studyLevel, String content, String memo,
                               List<String> weakTopics, List<String> excludeQuestions) {
    }

    private record StudyInput(InputType inputType, List<ImageInput> images, String content, String memo) {
    }

    private record ImageInput(MultipartFile file, String extension) {
    }
}
