package com.pitchmap.member.api;

import com.pitchmap.member.application.MemberSignupService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
class MemberController {

    private final MemberSignupService memberSignupService;

    MemberController(MemberSignupService memberSignupService) {
        this.memberSignupService = memberSignupService;
    }

    @Operation(summary = "회원가입")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SignupResponse signUp(@Valid @RequestBody SignupRequest request) {
        return SignupResponse.from(memberSignupService.signUp(request.toCommand()));
    }
}
