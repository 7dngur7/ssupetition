package com4table.ssupetition.domain.post.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com4table.ssupetition.domain.mail.service.MailService;
import com4table.ssupetition.domain.mypage.domain.AgreePost;
import com4table.ssupetition.domain.mypage.domain.WritePost;
import com4table.ssupetition.domain.mypage.repository.AgreePostRepository;
import com4table.ssupetition.domain.mypage.repository.CommentPostRepository;
import com4table.ssupetition.domain.mypage.repository.WritePostRepository;
import com4table.ssupetition.domain.post.domain.EmbeddingValue;
import com4table.ssupetition.domain.post.domain.Post;
import com4table.ssupetition.domain.post.dto.PostRequest;
import com4table.ssupetition.domain.post.dto.PostResponse;
import com4table.ssupetition.domain.post.enums.Category;
import com4table.ssupetition.domain.post.enums.Type;
import com4table.ssupetition.domain.post.repository.EmbeddingValueRepository;
import com4table.ssupetition.domain.post.repository.PostRepository;
import com4table.ssupetition.domain.user.domain.User;
import com4table.ssupetition.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    private final UserRepository userRepository;
    private final EmbeddingValueRepository embeddingValueRepository;
    private final AgreePostRepository agreePostRepository;
    private final MailService emailService;
    private final WritePostRepository writePostRepository;

    public Post addPost(Long userId, PostRequest.AddDTO addDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user ID: " + userId));

        Post post = addDTO.toEntity(user);
        Post savedPost = postRepository.save(post);

        // 외부 서비스에 보낼 페이로드 생성
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", addDTO.getTitle());
        payload.put("content", addDTO.getContent());
        payload.put("post_id", savedPost.getPostId().toString());

        // 페이로드를 JSON으로 변환
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("페이로드를 JSON으로 변환하는 데 실패했습니다.", e);
        }

        // HTTP 헤더 설정
        HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // HTTP 엔티티 생성
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonPayload, headers);

        // 요청 보내기
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> responseEntity = restTemplate.exchange(
                "http://43.203.39.17:5000/embed_post",
                HttpMethod.POST,
                requestEntity,
                Map.class
        );

        // 응답에서 임베딩 값 추출
        List<Double> embeddingList = (List<Double>) ((Map) responseEntity.getBody().get("data")).get("embedding");

        // 임베딩 값을 EmbeddingValue 객체로 변환하여 데이터베이스에 저장
        List<EmbeddingValue> embeddings = new ArrayList<>();
        for (Double value : embeddingList) {
            embeddings.add(new EmbeddingValue(null, post, value));
        }
        embeddingValueRepository.saveAll(embeddings);


        WritePost writePost = WritePost.builder()
                .user(user)
                .post(savedPost)
                .build();
        writePostRepository.save(writePost);

        return savedPost;
    }

    public List<PostResponse.AllListDTO> searchPosts( String keyword) {

        List<Post> posts = postRepository.findAll();

        // 제목 또는 내용에 키워드가 포함된 게시물 필터링
        List<Post> filteredPosts = posts.stream()
                .peek(post -> log.info("원본 포스트 제목: {}, 내용: {}", post.getTitle(), post.getContent()))  // 원본 포스트 로그
                .filter(post -> {
                    boolean matches = post.getTitle().contains(keyword) || post.getContent().contains(keyword);
                    if (matches) {
                        log.info("필터링된 포스트 제목: {}, 내용: {}", post.getTitle(), post.getContent());
                    }
                    return matches;
                })
                .collect(Collectors.toList());

        // DTO로 변환하여 반환
        return filteredPosts.stream()
                .map(post -> {
                    log.info("DTO 변환 포스트 제목: {}, 내용: {}", post.getTitle(), post.getContent());
                    return convertToDto(post);
                })
                .collect(Collectors.toList());
    }


    public List<PostResponse.SimilarityDTO> getSimilarPost(Long userId, PostRequest.AddDTO addDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user ID: " + userId));

        Post post = addDTO.toEntity(user);
        Post savedPost = postRepository.save(post);

        // 외부 서비스에 보낼 페이로드 생성
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", addDTO.getTitle());
        payload.put("content", addDTO.getContent());
        payload.put("post_id", savedPost.getPostId().toString());

        // 페이로드를 JSON으로 변환
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("페이로드를 JSON으로 변환하는 데 실패했습니다.", e);
        }

        // HTTP 헤더 설정
        HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // HTTP 엔티티 생성
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonPayload, headers);

        // 요청 보내기
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> responseEntity = restTemplate.exchange(
                "http://43.203.39.17:5000/find_similar_posts",
                HttpMethod.POST,
                requestEntity,
                Map.class
        );

        // 응답에서 유사 게시물 목록 추출
        List<Map<String, Object>> similarPosts = (List<Map<String, Object>>) responseEntity.getBody().get("similar_posts");
        List<PostResponse.SimilarityDTO> similarPostDTOs = new ArrayList<>();

        log.info("size: {}", similarPostDTOs.size());

        for (Map<String, Object> similarPost : similarPosts) {
            Long postId = Long.valueOf((Integer) similarPost.get("post_id"));
            Double similarity = (Double) similarPost.get("similarity");

            log.info("postid: {}, similarity: {}", postId, similarity);

            similarPostDTOs.add(new PostResponse.SimilarityDTO(postId, similarity));
        }

        return null;
    }


    public void removePost(Long postId, Long userId) {
        embeddingValueRepository.deleteByPost_PostId(postId);
        postRepository.deleteById(postId);
    }

    public PostResponse.AllListDTO addPostAgree(Long postId, Long userId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        if(checkParticipate(postId, userId, post, user, false)){
            return null;
        }

        post.setAgree(post.getAgree() + 1);
        post.setParticipants(post.getParticipants()+1);

        float agreeRate = (float) 100* post.getAgree() / post.getParticipants();

        boolean sendEmail = checkAgreeCountAndSendEmail(agreeRate, post.getAgree(), post.getTitle(), post.getContent());
        checkChangeType(postId, sendEmail);
        Post savedPost = postRepository.save(post);
        return new PostResponse.AllListDTO(savedPost);
    }

    public PostResponse.AllListDTO addPostDisagree(Long postId,Long userId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        if(checkParticipate(postId, userId, post, user, false)){
            return null;
        }
        post.setDisagree(post.getDisagree() + 1);
        post.setParticipants(post.getParticipants()+1);
        checkChangeType(postId, false);
        Post savedPost = postRepository.save(post);
        return new PostResponse.AllListDTO(savedPost);
    }

    public void checkChangeType(Long postId, boolean sendEmail){
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));

        log.info("sendEmail={}, posttype={}, participate={}",sendEmail,post.getPostType(), post.getParticipants());

        if(post.getPostType()==Type.state1 && post.getParticipants()>=30){
            post.setPostType(Type.state2);
        }
        if(sendEmail){
            post.setPostType(Type.state3);
        }
    }

    private boolean checkParticipate(Long postId, Long userId, Post post, User user, boolean state) {
        Optional<AgreePost> existingLike = agreePostRepository.findByPost_PostIdAndUser_UserId(postId, userId);

        if (existingLike.isPresent()) {
            return true;
        }

        AgreePost postLike = AgreePost.builder()
                .post(post)
                .user(user)
                .state(state)
                .build();
        agreePostRepository.save(postLike);

        return false;
    }

    public boolean checkAgreeCountAndSendEmail(float agreeRate, long participate, String title, String content) {
        if (participate>=30 && agreeRate >= 70) {
            String to = "ssupetition@gmail.com";
            String subject = title;
            String text = "이 메일로 답신 부탁드립니다.\n" + content;
            emailService.sendSimpleMessage(to, subject, text);

            return true;
        }
        return false;
    }

    public List<PostResponse.AllListDTO> getAllPosts() {
        List<Post> posts = postRepository.findAll();
        //System.out.println(posts);
        posts.forEach(post -> log.info(post.toString()));
        return posts.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<PostResponse.AllListDTO> getAllPostsSortedByAgree() {
        List<Post> posts = postRepository.findAllOrderByAgreeDesc();
        return posts.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<PostResponse.AllListDTO> getAllPostsSortedByExpiry() {
        List<Post> posts = postRepository.findAllOrderByExpiryAsc();
        return posts.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<PostResponse.AllListDTO> getAllPostsSortedByCreatedDate() {
        List<Post> posts = postRepository.findAllOrderByCreatedDateDesc();
        return posts.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // 정렬 메서드 추가
    public List<PostResponse.AllListDTO> getAllPostsSortedByAgreeExceptCategory(String type) {
        Type typeEnum = Type.valueOf(type);
        List<Post> posts = postRepository.findByPostTypeOrderByAgreeDesc(typeEnum);
        return posts.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<PostResponse.AllListDTO> getAllPostsSortedByExpiryExceptCategory(String type) {
        Type typeEnum = Type.valueOf(type);
        List<Post> posts = postRepository.findByPostTypeOrderByCreatedAtAsc(typeEnum);
        return posts.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<PostResponse.AllListDTO> getAllPostsSortedByCreatedDateExceptCategory(String type) {
        Type typeEnum = Type.valueOf(type);
        List<Post> posts = postRepository.findByPostTypeOrderByCreatedAtDesc(typeEnum);
        return posts.stream().map(this::convertToDto).collect(Collectors.toList());
    }




    // 정렬 메서드 추가
    public List<PostResponse.AllListDTO> getAllPostsSortedByAgree(String category, String type) {
        Category categoryEnum = Category.valueOf(category);
        Type typeEnum = Type.valueOf(type);
        List<Post> posts = postRepository.findByPostCategoryAndPostTypeOrderByAgreeDesc(categoryEnum, typeEnum);
        return posts.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<PostResponse.AllListDTO> getAllPostsSortedByExpiry(String category, String type) {
        Category categoryEnum = Category.valueOf(category);
        Type typeEnum = Type.valueOf(type);
        List<Post> posts = postRepository.findByPostCategoryAndPostTypeOrderByCreatedAtAsc(categoryEnum, typeEnum);
        return posts.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<PostResponse.AllListDTO> getAllPostsSortedByCreatedDate(String category, String type) {
        Category categoryEnum = Category.valueOf(category);
        Type typeEnum = Type.valueOf(type);
        List<Post> posts = postRepository.findByPostCategoryAndPostTypeOrderByCreatedAtDesc(categoryEnum, typeEnum);
        return posts.stream().map(this::convertToDto).collect(Collectors.toList());
    }


    private PostResponse.AllListDTO convertToDto(Post post) {
        User user = userRepository.findById(post.getUser().getUserId()).orElseThrow();

        List<Double> embeddings = embeddingValueRepository.findByPost_PostId(post.getPostId())
                .stream().map(EmbeddingValue::getValue).collect(Collectors.toList());

        //log.info("1:{},2:{}", post.getPostCategory(), post.getPostType());
        return PostResponse.AllListDTO.builder()
                .postId(post.getPostId())
                .userId(user.getUserId())
                .postCategory(post.getPostCategory())
                .postType(post.getPostType())
                .title(post.getTitle())
                .content(post.getContent())
                .participants(post.getParticipants())
                .agree(post.getAgree())
                .disagree(post.getDisagree())
                .createdAt(post.getCreatedAt())
                .build();
    }

    public List<PostResponse.AllListDTO> searchPostsSortedByAgree(String category, String keyword) {
        Category categoryEnum = Category.valueOf(category);

        log.info("카테고리, 키워드 : {}, {}", categoryEnum, keyword);

        List<Post> posts = postRepository.findByPostCategoryOrderByAgreeDesc(categoryEnum);

        // 제목 또는 내용에 키워드가 포함된 게시물 필터링
        List<Post> filteredPosts = posts.stream()
                .peek(post -> log.info("원본 포스트 제목: {}, 내용: {}", post.getTitle(), post.getContent()))
                .filter(post -> post.getTitle().contains(keyword) || post.getContent().contains(keyword))
                .peek(post -> log.info("필터링된 포스트 제목: {}, 내용: {}", post.getTitle(), post.getContent()))
                .collect(Collectors.toList());

        // DTO로 변환하여 반환
        return filteredPosts.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<PostResponse.AllListDTO> searchPostsSortedByExpiry(String category, String keyword) {
        Category categoryEnum = Category.valueOf(category);

        log.info("카테고리, 키워드 : {}, {}", categoryEnum, keyword);

        List<Post> posts = postRepository.findByPostCategoryOrderByCreatedAtAsc(categoryEnum);

        // 제목 또는 내용에 키워드가 포함된 게시물 필터링
        List<Post> filteredPosts = posts.stream()
                .peek(post -> log.info("원본 포스트 제목: {}, 내용: {}", post.getTitle(), post.getContent()))
                .filter(post -> post.getTitle().contains(keyword) || post.getContent().contains(keyword))
                .peek(post -> log.info("필터링된 포스트 제목: {}, 내용: {}", post.getTitle(), post.getContent()))
                .collect(Collectors.toList());

        // DTO로 변환하여 반환
        return filteredPosts.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public List<PostResponse.AllListDTO> searchPostsSortedByCreatedDate(String category, String keyword) {
        Category categoryEnum = Category.valueOf(category);

        log.info("카테고리, 키워드 : {}, {}", categoryEnum, keyword);

        List<Post> posts = postRepository.findByPostCategoryOrderByCreatedAtDesc(categoryEnum);

        // 제목 또는 내용에 키워드가 포함된 게시물 필터링
        List<Post> filteredPosts = posts.stream()
                .peek(post -> log.info("원본 포스트 제목: {}, 내용: {}", post.getTitle(), post.getContent()))
                .filter(post -> post.getTitle().contains(keyword) || post.getContent().contains(keyword))
                .peek(post -> log.info("필터링된 포스트 제목: {}, 내용: {}", post.getTitle(), post.getContent()))
                .collect(Collectors.toList());

        // DTO로 변환하여 반환
        return filteredPosts.stream().map(this::convertToDto).collect(Collectors.toList());

    }
}



