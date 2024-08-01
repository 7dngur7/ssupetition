package com4table.ssupetition.domain.comment.controller;

import com4table.ssupetition.domain.comment.domain.Comment;
import com4table.ssupetition.domain.comment.dto.CommentRequest;
import com4table.ssupetition.domain.comment.dto.CommentResponse;
import com4table.ssupetition.domain.comment.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/add/{postId}/{userId}")
    public ResponseEntity<Comment> addComment(@PathVariable(name = "userId") Long userId, @PathVariable(name = "postId") Long postId, @RequestBody CommentRequest.AddDTO addDTO) {
        Boolean checkSuccess = commentService.addComment(userId, postId, addDTO);
        log.info("checksuccess:{}",checkSuccess);
        if(!checkSuccess){
            return ResponseEntity.status(500).body(null);
        }
        return ResponseEntity.status(200).body(null);
    }

    @DeleteMapping("/delete/{commentId}")
    public ResponseEntity<Void> removeComment(@PathVariable(name = "commentId") Long commentId) {
        commentService.removeComment(commentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<List<CommentResponse>> getCommentsByPost(@PathVariable(name = "postId")Long postId) {
        List<CommentResponse> comments = commentService.getCommentsByPost(postId);
        return ResponseEntity.ok(comments);
    }
}
