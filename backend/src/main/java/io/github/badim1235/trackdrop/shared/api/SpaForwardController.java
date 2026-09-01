package io.github.badim1235.trackdrop.shared.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

	@GetMapping({"/chart", "/recent", "/recommend", "/login", "/join", "/me", "/recover/id", "/recover/password"})
	String forwardToIndex() {
		return "forward:/index.html";
	}
}
