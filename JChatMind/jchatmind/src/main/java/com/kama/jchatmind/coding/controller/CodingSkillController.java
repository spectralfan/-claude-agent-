package com.kama.jchatmind.coding.controller;

import com.kama.jchatmind.coding.model.dto.CodingSkillDTO;
import com.kama.jchatmind.coding.service.CodingSkillService;
import com.kama.jchatmind.model.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/coding/skills")
@RequiredArgsConstructor
public class CodingSkillController {

    private final CodingSkillService codingSkillService;

    @GetMapping
    public ApiResponse<List<CodingSkillDTO>> listSkills() {
        return ApiResponse.success(codingSkillService.listSkills());
    }
}
