package com.kama.jchatmind.coding.controller;

import com.kama.jchatmind.coding.model.dto.CodingStackDTO;
import com.kama.jchatmind.coding.service.CodingStackService;
import com.kama.jchatmind.model.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/coding/stacks")
@RequiredArgsConstructor
public class CodingStackController {

    private final CodingStackService codingStackService;

    @GetMapping
    public ApiResponse<List<CodingStackDTO>> listStacks() {
        return ApiResponse.success(codingStackService.listStacks());
    }
}
