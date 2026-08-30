package io.github.badim1235.trackdrop.shared.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

	@GetMapping({"/chart", "/recommend"})
	String forwardToIndex() {
		return "forward:/index.html";
	}
}
