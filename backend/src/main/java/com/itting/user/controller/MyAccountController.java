package com.itting.user.controller;

import com.itting.global.response.ApiResult;
import com.itting.user.dto.MyAccountResponse;
import com.itting.user.dto.PasswordChangeRequest;
import com.itting.user.dto.WithdrawRequest;
import com.itting.user.dto.WithdrawalCheckResponse;
import com.itting.user.service.MyAccountService;
import com.itting.user.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10 설정", description = "계정 정보, 비밀번호 변경, Google 연동, 회원 탈퇴, 알림 설정, 공지사항")
@RestController
@RequestMapping("/api/v1/me")
public class MyAccountController {

	private final MyAccountService myAccountService;

	private final WithdrawalService withdrawalService;

	public MyAccountController(MyAccountService myAccountService, WithdrawalService withdrawalService) {
		this.myAccountService = myAccountService;
		this.withdrawalService = withdrawalService;
	}

	@Operation(summary = "계정 정보",
			description = "가입 방법(비밀번호가 있는지)과 Google 연동 상태를 준다. 비밀번호나 Google 식별자, 로그인 이메일은 주지 않고 있는지만 준다. "
					+ "설정의 화면을 열 때 부른다. 로그인이 필요하다.")
	@GetMapping("/account")
	public ApiResult<MyAccountResponse> getAccount(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			HttpServletRequest request) {
		return ApiResult.ok(myAccountService.getAccount(userId, request));
	}

	@Operation(summary = "비밀번호 변경",
			description = "비밀번호를 바꾸고 이 계정의 다른 기기를 로그아웃시킨다. 지금 쓰는 기기는 그대로 로그인돼 있다. "
					+ "비밀번호가 없는 계정(Google로만 가입)은 409 PASSWORD_NOT_SET, 지금 비밀번호가 틀리면 400 CURRENT_PASSWORD_MISMATCH, "
					+ "새 비밀번호가 지금 비밀번호와 같으면 400 PASSWORD_UNCHANGED다. 로그인이 필요하다.")
	@PatchMapping("/password")
	public ApiResult<Void> changePassword(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody PasswordChangeRequest body, HttpServletRequest request) {
		myAccountService.changePassword(userId, body, request);
		return ApiResult.ok();
	}

	@Operation(summary = "Google 계정 연동",
			description = "방금 설정에서 확인받은 Google 계정(GET /api/v1/oauth/google/verify?purpose=LINK)을 내 계정에 연결하고 연결한 뒤의 "
					+ "계정 정보를 준다. 확인 기록이 없거나 10분이 지났으면 409 GOOGLE_VERIFICATION_REQUIRED(이미 연동된 계정은 바꾸지 않고 "
					+ "성공), 이미 다른 Google 계정이 연결돼 있으면 409 GOOGLE_ALREADY_LINKED, 다른 계정이 쓰는 Google 계정이면 "
					+ "409 GOOGLE_ACCOUNT_DUPLICATED다. 로그인이 필요하다.")
	@PutMapping("/google-link")
	public ApiResult<MyAccountResponse> linkGoogle(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			HttpServletRequest request) {
		return ApiResult.ok(myAccountService.linkGoogle(userId, request));
	}

	@Operation(summary = "Google 연동 해제",
			description = "Google 연동을 해제하고 해제한 뒤의 계정 정보를 준다. 연동돼 있지 않으면 바꾸지 않고 성공한다. 비밀번호가 없는 "
					+ "계정(Google로만 가입)은 409 GOOGLE_UNLINK_NOT_ALLOWED다. 로그인 세션은 지우지 않는다. 로그인이 필요하다.")
	@DeleteMapping("/google-link")
	public ApiResult<MyAccountResponse> unlinkGoogle(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			HttpServletRequest request) {
		return ApiResult.ok(myAccountService.unlinkGoogle(userId, request));
	}

	@Operation(summary = "탈퇴 전 확인",
			description = "지금 탈퇴할 수 있는지를 미리 알려 준다. 탈퇴를 막는 내가 연 예정 모임(시작이 가까운 순)과, 비밀번호가 없는 계정이 "
					+ "이 세션에서 탈퇴를 위한 Google 확인(GET /api/v1/oauth/google/verify?purpose=WITHDRAW)을 받아 아직 유효한지를 "
					+ "준다. 비밀번호가 있는 계정의 isGoogleVerified는 늘 false다. 로그인이 필요하다.")
	@GetMapping("/withdrawal-check")
	public ApiResult<WithdrawalCheckResponse> checkWithdrawal(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId, HttpServletRequest request) {
		return ApiResult.ok(withdrawalService.check(userId, request));
	}

	@Operation(summary = "회원 탈퇴",
			description = "탈퇴한다. 되돌릴 수 없다. 비밀번호가 있는 계정은 password를 보내야 하고(없으면 400 INVALID_INPUT, 틀리면 "
					+ "400 CURRENT_PASSWORD_MISMATCH), 비밀번호가 없는 계정(Google로만 가입)은 이 세션의 Google 확인이 있어야 한다"
					+ "(없거나 10분이 지났으면 409 GOOGLE_VERIFICATION_REQUIRED). 내가 연 예정 모임이 있으면 409 HOSTING_MEETUP_EXISTS다. "
					+ "통과하면 예정 모임의 신청 · 참여 취소, 대화방 나가기, 대화 요청 · 찜 · 좋아요 · 저장 · 단골 · 내 땅굴 · 프로필 · 받은 알림 "
					+ "삭제, 계정 비우기를 한 번에 하고 이 계정의 로그인 세션을 모두 지운다. 하나라도 실패하면 모두 되돌린다. 로그인이 필요하다.")
	@PostMapping("/withdraw")
	public ApiResult<Void> withdraw(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody(required = false) WithdrawRequest body, HttpServletRequest request) {
		withdrawalService.withdraw(userId, body == null ? new WithdrawRequest(null, null) : body, request);
		return ApiResult.ok();
	}

}
